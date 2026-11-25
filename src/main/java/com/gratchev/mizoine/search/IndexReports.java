package com.gratchev.mizoine.search;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryFiles.SearchArea;

/**
 * The index state for a user, and reindexing and updates on request (step 60). Whoever may search a repository
 * sees its state and dates; counts and unreadable files only with the whole repository; the last commit only with
 * Git access. Reindexing and updates only with the whole repository (D56), else "not found" (D2).
 */
public class IndexReports {

	private final RepositoryFiles files;
	private final SearchIndexes indexes;
	/** The time of the last commit of a repository root, if it is a Git working tree with commits. */
	private final Function<Path, Optional<Instant>> lastCommit;

	public IndexReports(final RepositoryFiles files, final SearchIndexes indexes,
			final Function<Path, Optional<Instant>> lastCommit) {
		this.files = files;
		this.indexes = indexes;
		this.lastCommit = lastCommit;
	}

	/** @throws RepositoryFiles.NotVisibleException without a grant */
	public IndexReport report(final String user, final String repository) {
		return report(user, files.searchArea(user, repository));
	}

	/** The reports of every repository the user may search, by name. */
	public List<IndexReport> reports(final String user) {
		return files.searchAreas(user).stream().map(area -> report(user, area)).toList();
	}

	/** @throws RepositoryFiles.NotVisibleException without the whole repository */
	public IndexReport reindex(final String user, final String repository) {
		final SearchArea area = files.wholeSearchArea(user, repository);
		indexes.reindexLater(repository, area.root());
		return report(user, area);
	}

	/** @return the start, and the report after it */
	public Updated update(final String user, final String repository) {
		final SearchArea area = files.wholeSearchArea(user, repository);
		final RepositoryIndex.Start start = indexes.updateLater(repository, area.root());
		return new Updated(start, report(user, area));
	}

	public record Updated(RepositoryIndex.Start start, IndexReport report) {
	}

	private IndexReport report(final String user, final SearchArea area) {
		final IndexDetails details = indexes.index(area.repository()).details();
		final IndexState state = details.state();
		final boolean whole = area.wholeRepository();
		return new IndexReport(area.repository(), state.status(), state.reason(), state.searchable(), details.updating(),
				details.progress(), details.indexedAt(), details.updatedAt(),
				IndexDocuments.newest(area.root(), area.folders()),
				gitRoot(user, area.repository()).flatMap(lastCommit),
				whole ? Optional.of(details.files()) : Optional.empty(),
				whole ? Optional.of(details.unreadable()) : Optional.empty());
	}

	private Optional<Path> gitRoot(final String user, final String repository) {
		try {
			return Optional.of(files.gitRoot(user, repository));
		} catch (final RepositoryFiles.NotVisibleException e) {
			return Optional.empty();
		}
	}
}
