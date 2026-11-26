package com.gratchev.mizoine.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.files.FileVersion;

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
	private static final String INDEXED_AT_KEY = "mizoine.indexed-at";
	private static final String UPDATED_AT_KEY = "mizoine.updated-at";
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
	/** Changes made while an update reads the files: taken anew after it, so its older reading does not win. */
	private Set<List<String>> touched;
	private boolean updating;
	private Optional<Instant> indexedAt = Optional.empty();
	private Optional<Instant> updatedAt = Optional.empty();
	private List<String> unreadable = List.of();
	/** How far a reindexing is: written by its own thread, read by anyone. */
	private final AtomicInteger done = new AtomicInteger();
	private final AtomicInteger total = new AtomicInteger();

	/** Whether an update was started (step 60). */
	public enum Start {
		STARTED, RUNNING, NEEDS_REINDEXING
	}

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
			indexedAt = Optional.ofNullable(data.get(INDEXED_AT_KEY)).map(Instant::parse);
			updatedAt = Optional.ofNullable(data.get(UPDATED_AT_KEY)).map(Instant::parse);
			return IndexState.of(IndexStatus.INDEXED, true);
		} catch (final LockObtainFailedException e) {
			log.error("Search index of {} at {} is in use by another Mizoine: give each its own mizoine.search.index-dir",
					repository, current);
			closeQuietly();
			return IndexState.needsReindexing(IndexState.IN_USE, false);
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

	public synchronized IndexDetails details() {
		return new IndexDetails(state, updating, state.status() == IndexStatus.REINDEXING
				? Optional.of(new IndexDetails.Progress(done.get(), total.get())) : Optional.empty(),
				indexedAt, updatedAt, writer == null ? 0 : writer.getDocStats().numDocs, unreadable);
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
		if (begin()) {
			build(root);
		}
	}

	/** @return whether a reindexing starts now: {@code false} while one runs, which counts instead */
	public synchronized boolean begin() {
		if (state.status() == IndexStatus.REINDEXING || state.reason().filter(IndexState.IN_USE::equals).isPresent()) {
			return false;
		}
		pending = new LinkedHashSet<>();
		done.set(0);
		total.set(0);
		state = IndexState.of(IndexStatus.REINDEXING, searchers != null);
		return true;
	}

	/** The reindexing started by {@link #begin()}: long, without the lock, so search answers meanwhile. */
	public void build(final Path root) {
		final Path building = dir.resolve(format.folder() + BUILDING);
		try {
			deleteTree(building);
			Files.createDirectories(building);
			final Directory built = FSDirectory.open(building);
			final IndexWriter newWriter = new IndexWriter(built, config(IndexWriterConfig.OpenMode.CREATE));
			IndexDocuments.walk(root, List.of(), entry -> total.incrementAndGet());
			final List<String> left = new ArrayList<>();
			IndexDocuments.walk(root, List.of(), entry -> {
				add(newWriter, root, entry);
				done.incrementAndGet();
			}, left::add);
			switchTo(root, building, built, newWriter);
			synchronized (this) {
				unreadable = List.copyOf(left);
			}
			log.info("Search index of {} built", repository);
		} catch (final IOException | RuntimeException e) {
			// also an unexpected failure ends the reindexing: it runs in the background, nobody else would see it
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
		commit(newWriter, true);
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

	/** @return whether an update starts now; it runs only on an index of the current format, one at a time */
	public synchronized Start beginUpdate() {
		if (state.status() != IndexStatus.INDEXED) {
			return Start.NEEDS_REINDEXING;
		}
		if (updating) {
			return Start.RUNNING;
		}
		updating = true;
		return Start.STARTED;
	}

	/** The update started by {@link #beginUpdate()}. */
	public void runUpdate(final Path root) {
		try {
			update(root);
		} finally {
			synchronized (this) {
				updating = false;
			}
		}
	}

	/**
	 * Indexes only what changed since the index's own record (by version): new, changed and deleted files. Unchanged
	 * files are not read. The files are read without the lock ({@link #readChanges}); only the writing holds it
	 * ({@link #writeChanges}).
	 */
	public void update(final Path root) {
		readChanges(root).ifPresent(update -> writeChanges(update, root));
	}

	/**
	 * What an update read: the changed entries as documents, the paths it saw, what it could not read.
	 *
	 * @param known the paths the index held when the update began, with their versions
	 */
	record Update(Map<String, String> known, Set<String> seen, List<Document> changed, List<String> unreadable) {
	}

	/** @return what changed, read without the lock; empty if the index cannot take an update */
	Optional<Update> readChanges(final Path root) {
		final Map<String, String> known;
		synchronized (this) {
			if (state.status() != IndexStatus.INDEXED) {
				return Optional.empty();
			}
			known = known();
			touched = new LinkedHashSet<>();
		}
		final Set<String> seen = new LinkedHashSet<>();
		final List<Document> changed = new ArrayList<>();
		final List<String> left = new ArrayList<>();
		IndexDocuments.walk(root, List.of(), entry -> {
			seen.add(entry.path());
			if (!entry.version().map(FileVersion::etag).orElse("").equals(known.get(entry.path()))) {
				changed.add(IndexDocuments.document(repository, root, entry, analysis));
			}
		}, left::add);
		return Optional.of(new Update(known, seen, changed, left));
	}

	/** Writes what the update read, then takes anew what Mizoine changed meanwhile: the newest state wins. */
	synchronized void writeChanges(final Update update, final Path root) {
		final Set<List<String>> meanwhile = touched;
		touched = null;
		if (writer == null) {
			return;
		}
		try {
			for (final Document document : update.changed()) {
				writer.updateDocument(new Term(IndexDocuments.PATH, document.get(IndexDocuments.PATH)), document);
			}
			for (final String gone : update.known().keySet()) {
				if (!update.seen().contains(gone)) {
					writer.deleteDocuments(new Term(IndexDocuments.PATH, gone));
				}
			}
			for (final List<String> path : meanwhile) {
				apply(writer, root, path);
			}
			commit(writer, false);
			unreadable = List.copyOf(update.unreadable());
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every path the index holds, with its version (empty for folders). */
	private Map<String, String> known() {
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
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
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
		if (touched != null) {
			touched.add(path);
		}
		if (writer == null) {
			return;
		}
		try {
			apply(writer, root, path);
			commit(writer, false);
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

	/**
	 * With the format and the dates in the commit data, so they are kept across a restart.
	 *
	 * @param rebuilt a reindexing: built and updated at this moment; otherwise built when it was
	 */
	private void commit(final IndexWriter target, final boolean rebuilt) throws IOException {
		final Instant now = Instant.now();
		final Map<String, String> data = new HashMap<>(Map.of(FORMAT_KEY, format.folder(),
				LUCENE_KEY, Version.LATEST.toString(), UPDATED_AT_KEY, now.toString()));
		(rebuilt ? Optional.of(now) : indexedAt).ifPresent(at -> data.put(INDEXED_AT_KEY, at.toString()));
		target.setLiveCommitData(data.entrySet());
		target.commit();
		if (target == writer) {
			updatedAt = Optional.of(now);
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
