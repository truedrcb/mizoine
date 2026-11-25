package com.gratchev.mizoine.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.repositories.RepositoryChange;
import com.gratchev.mizoine.repositories.RepositoryConfig;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryRegistry;

/**
 * Search (steps 55–60), unless {@code mizoine.search.enabled} is {@code false}: then there is no index, and the
 * search addresses are "not found" like any hidden name.
 */
@Configuration
@SearchEnabled
public class SearchConfig {

	private static final Logger log = LoggerFactory.getLogger(SearchConfig.class);

	/** @throws IllegalArgumentException at startup for an unknown language, naming the known ones */
	@Bean(destroyMethod = "close")
	SearchIndexes searchIndexes(final MizoineProperties properties) {
		return new SearchIndexes(properties.search().indexDirPath(),
				SearchAnalysis.of(Language.parse(properties.search().languages())));
	}

	@Bean
	Search search(final RepositoryFiles files, final SearchIndexes indexes) {
		return new Search(files, indexes);
	}

	@Bean
	IndexUpdates indexUpdates(final SearchIndexes indexes, final RepositoryRegistry registry) {
		return new IndexUpdates(indexes, registry);
	}

	/** Mizoine's own changes go into the index at once; the startup only looks at each index's state (D61). */
	static final class IndexUpdates {

		private final SearchIndexes indexes;
		private final RepositoryRegistry registry;

		IndexUpdates(final SearchIndexes indexes, final RepositoryRegistry registry) {
			this.indexes = indexes;
			this.registry = registry;
		}

		/** The change is done: an index that cannot take it is logged, never a failed write. */
		@EventListener
		void changed(final RepositoryChange change) {
			try {
				indexes.index(change.repository()).changed(change.root(), change.path());
			} catch (final RuntimeException e) {
				log.error("Search index of {} did not take the change at {}: {}", change.repository(),
						String.join("/", change.path()), e.toString());
			}
		}

		/** One line per repository whose index is not ready. */
		@EventListener(ApplicationReadyEvent.class)
		void opened() {
			for (final RepositoryConfig repository : registry.all()) {
				final IndexState state = indexes.index(repository.name()).state();
				if (state.status() != IndexStatus.INDEXED) {
					log.warn("Search index of {} is {}{}: search answers from it after a reindexing", repository.name(),
							state.status().code(), state.reason().map(reason -> " (" + reason + ")").orElse(""));
				}
			}
		}
	}
}
