package com.gratchev.mizoine.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The search index of one repository (step 56, D58–D61), in its own folder per format below
 * {@code mizoine.search.index-dir/<repository>/}. Nothing heavy runs on its own:
 * <ul>
 * <li>{@link #open}: looks at the index folder only, never at the repository's files;</li>
 * <li>{@link #changed}: Mizoine's own writes, at once;</li>
 * <li>{@link #update} (only what changed by version) and {@link #reindex} (everything anew, beside the old index):
 * only when asked for.</li>
 * </ul>
 * One writer per index; searches read a point-in-time view ({@link #lease()}), also during a reindexing.
 */
public final class RepositoryIndex {

	private static final Logger log = LoggerFactory.getLogger(RepositoryIndex.class);
	private static final String FORMAT_KEY = "mizoine.format";
	private static final String LUCENE_KEY = "lucene";
	private static final String BUILDING = ".building";

	private final String repository;
	private final Path dir;
	private final SearchAnalysis analysis;
	private final IndexFormat format;

	private IndexState state;
	/** Open only for an index of the current format. */
	private IndexWriter writer;
	/** The index searches use: the current one, or an older readable one (D57). */
	private SearcherManager searchers;
	private Directory directory;
	/** Changes made while a reindexing walks the files: applied to the new index before it is used. */
	private Set<List<String>> pending;

	private RepositoryIndex(final String repository, final Path dir, final SearchAnalysis analysis) {
		this.repository = repository;
		this.dir = dir;
		this.analysis = analysis;
		this.format = IndexFormat.of(analysis);
	}

	/** Opens what is there; reads no file of the repository (D61). */
	public static RepositoryIndex open(final String repository, final Path dir, final SearchAnalysis analysis) {
		final RepositoryIndex index = new RepositoryIndex(repository, dir, analysis);
		index.state = index.openExisting();
		return index;
	}

	private IndexState openExisting() {
		final Path current = dir.resolve(format.folder());
		if (Files.isDirectory(current)) {
			return openCurrent(current);
		}
		return formats().stream().findFirst().map(this::openOlder).orElse(IndexState.of(IndexStatus.NOT_INDEXED, false));
	}

	private IndexState openCurrent(final Path current) {
		try {
			directory = FSDirectory.open(current);
			final Map<String, String> data = SegmentInfos.readLatestCommit(directory).getUserData();
			if (!format.folder().equals(data.get(FORMAT_KEY))) {
				closeQuietly();
				return IndexState.needsReindexing(IndexState.FORMAT, false);
			}
			writer = new IndexWriter(directory, config(IndexWriterConfig.OpenMode.APPEND));
			searchers = new SearcherManager(writer, null);
			return IndexState.of(IndexStatus.INDEXED, true);
		} catch (final IOException e) {
			log.warn("Search index of {} at {} cannot be read: {}", repository, current, e.toString());
			closeQuietly();
			return IndexState.needsReindexing(IndexState.DAMAGED, false);
		}
	}

	/** An index of another format: read meanwhile only when made by the same Lucene major (D57, D58). */
	private IndexState openOlder(final IndexFormat older) {
		if (older.luceneMajor() != format.luceneMajor()) {
			return IndexState.needsReindexing(IndexState.FORMAT, false);
		}
		try {
			directory = FSDirectory.open(dir.resolve(older.folder()));
			searchers = new SearcherManager(directory, null);
			return IndexState.needsReindexing(IndexState.FORMAT, true);
		} catch (final IOException e) {
			log.warn("Search index of {} in format {} cannot be read: {}", repository, older.folder(), e.toString());
			closeQuietly();
			return IndexState.needsReindexing(IndexState.FORMAT, false);
		}
	}

	/** The formats of the index folders there, newest Lucene first. */
	private List<IndexFormat> formats() {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> folders = Files.list(dir)) {
			return folders.map(folder -> IndexFormat.parse(folder.getFileName().toString()))
					.flatMap(Optional::stream)
					.sorted(Comparator.comparingInt(IndexFormat::luceneMajor).reversed())
					.toList();
		} catch (final IOException e) {
			log.warn("Search indexes of {} at {} cannot be listed: {}", repository, dir, e.toString());
			return List.of();
		}
	}

	public synchronized IndexState state() {
		return state;
	}

	/** A view for one search, if there is an index to search; close it after the search. */
	public synchronized Optional<IndexLease> lease() {
		if (searchers == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(new IndexLease(searchers));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Builds the index anew from all files, beside the old one, which answers meanwhile (D57); then switches and
	 * deletes the folders of other formats. A failure keeps the old index and says why ({@link IndexStatus#FAILED}).
	 */
	public void reindex(final Path root) {
		synchronized (this) {
			if (state.status() == IndexStatus.REINDEXING) {
				return;
			}
			pending = new LinkedHashSet<>();
			state = IndexState.of(IndexStatus.REINDEXING, searchers != null);
		}
		final Path building = dir.resolve(format.folder() + BUILDING);
		try {
			deleteTree(building);
			Files.createDirectories(building);
			final Directory built = FSDirectory.open(building);
			final IndexWriter newWriter = new IndexWriter(built, config(IndexWriterConfig.OpenMode.CREATE));
			IndexDocuments.walk(root, List.of(), entry -> add(newWriter, root, entry));
			switchTo(root, building, built, newWriter);
			log.info("Search index of {} built", repository);
		} catch (final IOException | UncheckedIOException e) {
			log.error("Search index of {} could not be built: {}", repository, e.toString());
			deleteQuietly(building);
			synchronized (this) {
				pending = null;
				state = new IndexState(IndexStatus.FAILED, Optional.of(e.toString()), searchers != null);
			}
		}
	}

	private synchronized void switchTo(final Path root, final Path building, final Directory built,
			final IndexWriter newWriter) throws IOException {
		for (final List<String> path : pending) {
			apply(newWriter, root, path);
		}
		commit(newWriter);
		newWriter.close();
		built.close();
		closeQuietly();
		final Path current = dir.resolve(format.folder());
		deleteTree(current);
		Files.move(building, current, StandardCopyOption.ATOMIC_MOVE);
		pending = null;
		state = openCurrent(current);
		try (Stream<Path> folders = Files.list(dir)) {
			folders.filter(folder -> !folder.equals(current)).forEach(RepositoryIndex::deleteQuietly);
		}
	}

	/** Indexes only what changed since the index's own record (by version): new, changed and deleted files. */
	public synchronized void update(final Path root) {
		if (state.status() != IndexStatus.INDEXED) {
			return;
		}
		try {
			final Map<String, String> known = known();
			final Set<String> seen = new LinkedHashSet<>();
			IndexDocuments.walk(root, List.of(), entry -> {
				seen.add(entry.path());
				if (!entry.version().orElse("").equals(known.get(entry.path()))) {
					update(writer, root, entry);
				}
			});
			for (final String gone : known.keySet()) {
				if (!seen.contains(gone)) {
					writer.deleteDocuments(new Term(IndexDocuments.PATH, gone));
				}
			}
			commit(writer);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every path the index holds, with its version (empty for folders). */
	private Map<String, String> known() throws IOException {
		final Map<String, String> known = new HashMap<>();
		try (DirectoryReader reader = DirectoryReader.open(writer)) {
			for (final LeafReaderContext leaf : reader.leaves()) {
				final Bits live = leaf.reader().getLiveDocs();
				final StoredFields stored = leaf.reader().storedFields();
				for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
					if (live == null || live.get(doc)) {
						final Document document = stored.document(doc);
						known.put(document.get(IndexDocuments.PATH), Optional.ofNullable(
								document.get(IndexDocuments.VERSION)).orElse(""));
					}
				}
			}
		}
		return known;
	}

	/**
	 * Mizoine changed this path (a write, a new folder, a delete, either side of a move): the index takes it at
	 * once, with everything below it. Without a current index, a running reindexing takes it before it is used.
	 */
	public synchronized void changed(final Path root, final List<String> path) {
		if (pending != null) {
			pending.add(path);
		}
		if (writer == null) {
			return;
		}
		try {
			apply(writer, root, path);
			commit(writer);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void apply(final IndexWriter target, final Path root, final List<String> path) throws IOException {
		target.deleteDocuments(new Term(IndexDocuments.PATH, IndexDocuments.path(path, false)));
		target.deleteDocuments(new PrefixQuery(new Term(IndexDocuments.PATH, IndexDocuments.path(path, true))));
		final List<IndexDocuments.Entry> entries = new ArrayList<>();
		IndexDocuments.walk(root, path, entries::add);
		for (final IndexDocuments.Entry entry : entries) {
			add(target, root, entry);
		}
	}

	private void add(final IndexWriter target, final Path root, final IndexDocuments.Entry entry) {
		try {
			target.addDocument(IndexDocuments.document(repository, root, entry, analysis));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void update(final IndexWriter target, final Path root, final IndexDocuments.Entry entry) {
		try {
			target.updateDocument(new Term(IndexDocuments.PATH, entry.path()),
					IndexDocuments.document(repository, root, entry, analysis));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void commit(final IndexWriter target) throws IOException {
		target.setLiveCommitData(Map.of(FORMAT_KEY, format.folder(), LUCENE_KEY, Version.LATEST.toString()).entrySet());
		target.commit();
		if (target == writer) {
			searchers.maybeRefreshBlocking();
		}
	}

	private IndexWriterConfig config(final IndexWriterConfig.OpenMode mode) {
		return new IndexWriterConfig(analysis.analyzer()).setOpenMode(mode);
	}

	public synchronized void close() {
		closeQuietly();
	}

	private void closeQuietly() {
		try {
			if (searchers != null) {
				searchers.close();
			}
			if (writer != null) {
				writer.close();
			}
			if (directory != null) {
				directory.close();
			}
		} catch (final IOException e) {
			log.warn("Search index of {} did not close cleanly: {}", repository, e.toString());
		} finally {
			searchers = null;
			writer = null;
			directory = null;
		}
	}

	private static void deleteTree(final Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(path)) {
			for (final Path each : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(each);
			}
		}
	}

	private static void deleteQuietly(final Path path) {
		try {
			deleteTree(path);
		} catch (final IOException e) {
			log.warn("Search index folder {} could not be deleted: {}", path, e.toString());
		}
	}
}
