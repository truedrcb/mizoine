package com.gratchev.mizoine.search;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.files.FileVersion;
import com.gratchev.mizoine.repositories.FolderNames;
import com.gratchev.mizoine.repositories.TextFile;

/**
 * What the index holds of a repository (step 56, D50): every file and folder by name and path, and the content of
 * text files (as {@link TextFile}: UTF-8, up to 2 MB). Hidden names never (D3), and links are not followed (D54):
 * a linked file or folder is found at its own place, where it is not a link.
 */
final class IndexDocuments {

	static final String REPOSITORY = "repository";
	/** Below the repository root; folders end with {@code /}: {@code projects/plan.md}, {@code projects/archive/}. */
	static final String PATH = "path";
	static final String KIND = "kind";
	static final String VERSION = "version";
	static final String FILE = "file";
	static final String FOLDER = "folder";

	private static final Logger log = LoggerFactory.getLogger(IndexDocuments.class);
	/** The first heading of a Markdown file: {@code # Title}. */
	private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}[ \\t]+(.+?)[ \\t#]*$");

	/** A file or folder as it is now; {@code version} only for files. */
	record Entry(List<String> segments, Optional<String> version) {

		boolean folder() {
			return version.isEmpty();
		}

		String path() {
			return IndexDocuments.path(segments, folder());
		}
	}

	private IndexDocuments() {
	}

	static String path(final List<String> segments, final boolean folder) {
		return String.join("/", segments) + (folder ? "/" : "");
	}

	/**
	 * Every entry at {@code start} and below it that the index holds; nothing if a part of the way is hidden or a
	 * link. The root itself is no entry.
	 */
	static void walk(final Path repositoryRoot, final List<String> start, final Consumer<Entry> entries) {
		final Path root;
		try {
			// a root that is a link counts where it leads: the walk follows no link, also not its start
			root = repositoryRoot.toRealPath();
		} catch (final IOException e) {
			log.warn("Search index: the repository folder {} cannot be read: {}", repositoryRoot, e.toString());
			return;
		}
		if (!indexable(root, start)) {
			return;
		}
		final Path first = resolve(root, start);
		try {
			Files.walkFileTree(first, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attributes) {
					if (!dir.equals(first) && FolderNames.isHidden(dir.getFileName().toString())) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					if (!dir.equals(root)) {
						entries.accept(new Entry(segments(root, dir), Optional.empty()));
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
					if (attributes.isRegularFile() && !FolderNames.isHidden(file.getFileName().toString())) {
						FileVersion.of(file).ifPresent(version -> entries.accept(new Entry(segments(root, file),
								Optional.of(version.etag()))));
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(final Path file, final IOException e) {
					log.warn("Search index: {} cannot be read, left out: {}", file, e.toString());
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			// gone since it was checked: nothing to index
			log.debug("Search index: {} is gone", first, e);
		}
	}

	/** No part hidden, none on the way a link (the walk does not follow the last one either), and it exists. */
	private static boolean indexable(final Path root, final List<String> segments) {
		if (segments.stream().anyMatch(FolderNames::isHidden)) {
			return false;
		}
		for (int i = 1; i < segments.size(); i++) {
			if (Files.isSymbolicLink(resolve(root, segments.subList(0, i)))) {
				return false;
			}
		}
		return Files.exists(resolve(root, segments), LinkOption.NOFOLLOW_LINKS);
	}

	private static Path resolve(final Path root, final List<String> segments) {
		Path path = root;
		for (final String segment : segments) {
			path = path.resolve(segment);
		}
		return path;
	}

	private static List<String> segments(final Path root, final Path path) {
		final List<String> segments = new ArrayList<>();
		root.relativize(path).forEach(part -> segments.add(part.toString()));
		return segments;
	}

	static Document document(final String repository, final Path root, final Entry entry, final SearchAnalysis analysis) {
		final String name = entry.segments().getLast();
		final Optional<String> text = entry.folder() ? Optional.empty() : TextFile.read(resolve(root, entry.segments()));
		final Document document = new Document();
		document.add(new StringField(REPOSITORY, repository, Field.Store.YES));
		document.add(new StringField(PATH, entry.path(), Field.Store.YES));
		document.add(new StringField(KIND, entry.folder() ? FOLDER : FILE, Field.Store.YES));
		entry.version().ifPresent(version -> document.add(new StoredField(VERSION, version)));
		document.add(new TextField(SearchAnalysis.NAME, name, Field.Store.NO));
		document.add(new TextField(SearchAnalysis.NAME_PARTS, name, Field.Store.NO));
		document.add(new TextField(SearchAnalysis.TITLE, text.flatMap(content -> title(name, content)).orElse(name),
				Field.Store.YES));
		text.ifPresent(content -> Stream.concat(analysis.textFields().stream(), Stream.of(SearchAnalysis.TOKENS))
				.forEach(field -> document.add(new TextField(field, content, Field.Store.NO))));
		return document;
	}

	/** A Markdown file's first heading. */
	private static Optional<String> title(final String name, final String content) {
		if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
			return Optional.empty();
		}
		final Matcher heading = HEADING.matcher(content);
		return heading.find() ? Optional.of(heading.group(1)) : Optional.empty();
	}
}
