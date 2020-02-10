package com.gratchev.mizoine.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.IssueMeta;


public class RepositoryIndexerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexer.class);
	
	TempRepository repo;
	Path indexPath;


	@BeforeEach
	public void setUp() throws Exception {
		repo = TempRepository.create();
		LOGGER.debug("||| All set up ||||||||||||||| " + this.toString());

		indexPath = repo.getLuceneDir().toPath();
	}

	@AfterEach
	public void tearDown() throws IOException {
		LOGGER.debug("||| Tearing down ||||||||||||||| " + this.toString());
		repo.dispose();
	}
	
	
	private IssueMeta im(final String title, final String creator) {
		final IssueMeta meta = new IssueMeta();
		meta.creationDate = new Date();
		meta.creator = creator;
		meta.title = title;
		return meta;
	}
	
	private Iterable<String> t(final String ... tags) {
		return Sets.newHashSet(tags);
	}

	private void assertContentSearch(final String searchQuery, final String... issues) throws IOException {
		LOGGER.info("Query: '"  + searchQuery + "'");
		final Set<String> issueHitSet = new HashSet<>();
		try (final Directory directory = FSDirectory.open(indexPath)) {
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				final IndexSearcher isearcher = new IndexSearcher(ireader);
				final BooleanQuery.Builder builder = new BooleanQuery.Builder();
				final String updatedSearchQuery = RepositoryIndexer.createContentQuery(searchQuery, new StandardAnalyzer(), builder);
				LOGGER.info("Updated query: " + updatedSearchQuery);
				final Query query = builder.build();
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;

				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					final String project = hitDoc.get("project");
					final String issueNumber = hitDoc.get("issueNumber");
					final String issue = project + "-" + issueNumber;
					LOGGER.debug("Found [" + issue + "]: "  + hitText + ", tags: " + hitDoc.get("tag"));
					issueHitSet.add(issue);
				}
			}
		}
		
		assertEquals(Sets.newHashSet(issues), issueHitSet);
	}

	
	private void assertTagsSearch(final Iterable<String> tags, final String... issues) throws IOException {
		assertTagsSearch(false, tags, issues);
	}
	
	
	private void assertTagsSearch(final boolean checkStatus, final Iterable<String> tags, final String... issues) throws IOException {
		LOGGER.info("Search tags: "  + tags);
		if (checkStatus) {
			LOGGER.info("Search in status as well");
		}
		final Set<String> issueHitSet = new HashSet<>();
		try (final Directory directory = FSDirectory.open(indexPath)) {
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				final IndexSearcher isearcher = new IndexSearcher(ireader);
				final BooleanQuery.Builder builder = new BooleanQuery.Builder();
				if (checkStatus) {
					RepositoryIndexer.createTagsQuery(tags, Sets.newHashSet("tags", "status"), builder);
				} else {
					RepositoryIndexer.createTagsQuery(tags, "tags", builder);
				}
				final Query query = builder.build();
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;

				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					final String project = hitDoc.get("project");
					final String issueNumber = hitDoc.get("issueNumber");
					final String issue = project + "-" + issueNumber;
					LOGGER.debug("Found [" + issue + "]: "  + hitText + ", tags: " + hitDoc.get("tag"));
					issueHitSet.add(issue);
				}
			}
		}
		
		assertEquals(Sets.newHashSet(issues), issueHitSet);
	}
	
	
	@Test
	public void indexAndSearch() throws IOException {
		LOGGER.info("Start");
		try (final Directory directory = FSDirectory.open(indexPath)) {
			try (final RepositoryIndexer ri = new RepositoryIndexer(directory)) {
				ri.indexIssue("HOME", "15", im("Sed consequat, leo eget ", "user1"), 
						"Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. ", 
						t("consequat"), 
						t("open"));
				ri.indexIssue("HOME", "3", im("Quisque rutrum", "user1"), 
						"Aenean imperdiet. Etiam ultricies nisi vel augue. Curabitur ullamcorper ultricies nisi.", 
						t("justo", "tincidunt"), 
						t("open"));
				ri.indexIssue("HOME", "4", im("Etiam rhoncus", "user2"), 
						"Nullam quis ante. Etiam sit amet orci eget eros faucibus tincidunt. Duis leo.", 
						t("justo", "leo"), 
						t());
				ri.indexIssue("HOME", "80", im("Aenean massa.", "user1"), 
						"Maecenas tempus, tellus eget condimentum rhoncus, sem quam semper libero, sit amet adipiscing sem neque sed ipsum.", 
						t("ante", "leo", "justo"), 
						t());
			}
		}
		assertContentSearch(" nisi", "HOME-3");
		assertTagsSearch(Sets.newHashSet("justo"), "HOME-3", "HOME-4", "HOME-80");
		assertTagsSearch(Sets.newHashSet("justo", "leo"), "HOME-4", "HOME-80");
		assertContentSearch(" LEO,", "HOME-15", "HOME-4", "HOME-80");
		assertContentSearch("leo", "HOME-15", "HOME-4", "HOME-80");
		assertTagsSearch(true, Sets.newHashSet("justo"), "HOME-3", "HOME-4", "HOME-80");
		assertTagsSearch(true, Sets.newHashSet("justo", "open"), "HOME-3");
		assertTagsSearch(true, Sets.newHashSet("open"), "HOME-15", "HOME-3");
		
		try (final Directory directory = FSDirectory.open(indexPath)) {
			try (final RepositoryIndexer ri = new RepositoryIndexer(directory)) {
				ri.indexIssue("HOME", "15", im("Sed consequat, eget ", "user1"), 
						"Cum sociis natoque penatibus et magnis dis XXXX ", 
						t("consequat"), 
						t("closed"));
				ri.indexIssue("WEB", "3", im("Aenean leo ligula", "user1"), 
						"Curabitur ullamcorper ultricies nisi. Nam eget dui. Etiam rhoncus.", 
						t("venenatis"), 
						t("canceled"));
			}
		}
		
		assertContentSearch("leo", "WEB-3", "HOME-4", "HOME-80");
	}
}
