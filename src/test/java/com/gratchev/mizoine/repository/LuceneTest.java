package com.gratchev.mizoine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;

public class LuceneTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(LuceneTest.class);

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


	/**
	 * See https://lucene.apache.org/core/7_2_1/core/overview-summary.html
	 * 
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void standardDemo() throws IOException, ParseException {
		Analyzer analyzer = new StandardAnalyzer();

		// Store the index in memory:
		Directory directory = new ByteBuffersDirectory();
		// To store an index on disk, use this instead:
		//Directory directory = FSDirectory.open("/tmp/testindex");
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		try (IndexWriter iwriter = new IndexWriter(directory, config)) {
			Document doc = new Document();
			String text = "This is the text to be indexed.";
			doc.add(new Field("fieldname", text, TextField.TYPE_STORED));
			iwriter.addDocument(doc);
		}

		// Now search the index:
		DirectoryReader ireader = DirectoryReader.open(directory);
		IndexSearcher isearcher = new IndexSearcher(ireader);
		// Parse a simple query that searches for "text":
		QueryParser parser = new QueryParser("fieldname", analyzer);
		Query query = parser.parse("text");
		ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
		assertEquals(1, hits.length);
		// Iterate through the results:
		for (int i = 0; i < hits.length; i++) {
			Document hitDoc = isearcher.doc(hits[i].doc);
			assertEquals("This is the text to be indexed.", hitDoc.get("fieldname"));
		}
		ireader.close();
		directory.close();		
	}


	@Test
	public void standardFsDemo() throws IOException, ParseException {
		Analyzer analyzer = new StandardAnalyzer();

		// To store an index on disk, use this instead:
		try (final Directory directory = FSDirectory.open(new File(repo.getRootMizoineDir(), ".lucene").toPath())) {
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			try (final IndexWriter iwriter = new IndexWriter(directory, config)) {
				Document doc = new Document();
				String text = "This is the text to be indexed.";
				doc.add(new Field("fieldname", text, TextField.TYPE_STORED));
				iwriter.addDocument(doc);
			}

			// Now search the index:
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				// Parse a simple query that searches for "text":
				QueryParser parser = new QueryParser("fieldname", analyzer);
				Query query = parser.parse("text");
				ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
				assertEquals(1, hits.length);
				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					assertEquals("This is the text to be indexed.", hitDoc.get("fieldname"));
				}
			}
		}		
	}

	@Test
	public void multipleDocuments() throws IOException, ParseException {
		final Analyzer analyzer = new StandardAnalyzer();

		try (final Directory directory = FSDirectory.open(indexPath)) {
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			try (final IndexWriter iwriter = new IndexWriter(directory, config)) {

				final Document doc1 = new Document();
				doc1.add(new Field("project", "HOME", TextField.TYPE_STORED));
				doc1.add(new Field("content", "This is the mizoine text to be indexed.", TextField.TYPE_STORED));
				iwriter.addDocument(doc1);

				final Document doc2 = new Document();
				doc2.add(new Field("project", "HOME", TextField.TYPE_STORED));
				doc2.add(new Field("content", "Another Mizoine test.", TextField.TYPE_STORED));
				iwriter.addDocument(doc2);

				final Document doc3 = new Document();
				doc3.add(new Field("project", "TEST", TextField.TYPE_STORED));
				doc3.add(new Field("content", "Experimental mizo1ne typo!", TextField.TYPE_STORED));
				iwriter.addDocument(doc3);
			}

			// Now search the index:
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				// Parse a simple query that searches for "text":
				QueryParser parser = new QueryParser("content", analyzer);
				Query query = parser.parse("mizoine");
				ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
				assertEquals(2, hits.length);
				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					LOGGER.debug("Found: "  + hitText);
					assertThat(hitText.toLowerCase()).contains("mizoine");

					assertEquals("HOME", hitDoc.get("project"));
				}
			}
		}		
	}

	@Test
	public void separateWriteRead() throws IOException, ParseException {
		try (final Directory directory = FSDirectory.open(indexPath)) {
			final Analyzer analyzer = new StandardAnalyzer();
			final IndexWriterConfig config = new IndexWriterConfig(analyzer);
			try (final IndexWriter iwriter = new IndexWriter(directory, config)) {

				final Document doc1 = new Document();
				doc1.add(new Field("project", "HOME", TextField.TYPE_STORED));
				doc1.add(new Field("content", "This is the mizoine text to be indexed.", TextField.TYPE_STORED));
				iwriter.addDocument(doc1);

				final Document doc2 = new Document();
				doc2.add(new Field("project", "HOME", TextField.TYPE_STORED));
				doc2.add(new Field("content", "Another Mizoine test.", TextField.TYPE_STORED));
				iwriter.addDocument(doc2);

				final Document doc3 = new Document();
				doc3.add(new Field("project", "TEST", TextField.TYPE_STORED));
				doc3.add(new Field("content", "Experimental mizo1ne typo!", TextField.TYPE_STORED));
				iwriter.addDocument(doc3);
			}
		}		

		try (final Directory directory = FSDirectory.open(indexPath)) {
			// Now search the index:
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				// Parse a simple query that searches for "text":
				final Analyzer analyzer = new StandardAnalyzer();
				final QueryParser parser = new QueryParser("content", analyzer);
				final Query query = parser.parse("mizoine");
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
				assertEquals(2, hits.length);
				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					LOGGER.debug("Found: "  + hitText);
					assertThat(hitText.toLowerCase()).contains("mizoine");

					assertEquals("HOME", hitDoc.get("project"));
				}
			}
		}		
	}


	interface DocFieldsAdder {
		void addFields(final Document doc);
	}

	private void addDocs(final DocFieldsAdder... adders) throws IOException, ParseException {
		try (final Directory directory = FSDirectory.open(indexPath)) {
			final Analyzer analyzer = new StandardAnalyzer();
			final IndexWriterConfig config = new IndexWriterConfig(analyzer);
			try (final IndexWriter iwriter = new IndexWriter(directory, config)) {
				for (final DocFieldsAdder adder : adders) {
					final Document doc1 = new Document();
					adder.addFields(doc1);
					iwriter.addDocument(doc1);
				}
			}
		}		
	}

	/**
	 * Classic query parser
	 * 
	 * http://lucene.apache.org/core/7_2_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description
	 * 
	 * @param searchQuery
	 * @param issues
	 * @throws IOException
	 * @throws ParseException
	 */
	private void assertSearch(final String searchQuery, final String... issues) throws IOException, ParseException {
		LOGGER.debug("Query: '"  + searchQuery + "'");
		final Set<String> issueHitSet = new HashSet<>();
		try (final Directory directory = FSDirectory.open(indexPath)) {
			// Now search the index:
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				// Parse a simple query that searches for "text":
				final Analyzer analyzer = new StandardAnalyzer();
				final QueryParser parser = new QueryParser("content", analyzer);
				final Query query = parser.parse(searchQuery);
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					final String issue = hitDoc.get("issue");
					LOGGER.debug("Found [" + issue + "]: "  + hitText + ", tags: " + hitDoc.get("tag"));
					issueHitSet.add(issue);
				}
			}
		}
		
		assertEquals(Sets.newSet(issues), issueHitSet);
	}
	
	/**
	 * https://stackoverflow.com/questions/37904977/lucene-6-0-how-to-instantiate-a-booleanquery-and-add-other-search-queries-in-it
	 * 
	 * @param searchQuery
	 * @param issues
	 * @throws IOException
	 * @throws ParseException
	 */
	private void assertTermSearch(final String searchQuery, final String... issues) throws IOException, ParseException {
		LOGGER.debug("Query: '"  + searchQuery + "'");
		final Set<String> issueHitSet = new HashSet<>();
		try (final Directory directory = FSDirectory.open(indexPath)) {
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				
				// https://stackoverflow.com/questions/37904977/lucene-6-0-how-to-instantiate-a-booleanquery-and-add-other-search-queries-in-it
				final BooleanQuery.Builder builder = new BooleanQuery.Builder();
				for (final String word : Splitter.on(' ').omitEmptyStrings().split(searchQuery)) {
					builder.add(new TermQuery(new Term("content", word)), BooleanClause.Occur.MUST);
				}
				
				final Query query = builder.build();
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;

				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					final String issue = hitDoc.get("issue");
					LOGGER.debug("Found [" + issue + "]: "  + hitText + ", tags: " + hitDoc.get("tag"));
					issueHitSet.add(issue);
				}
			}
		}
		
		assertEquals(Sets.newSet(issues), issueHitSet);
	}

	/**
	 * https://stackoverflow.com/questions/2438000/how-do-i-implement-tag-searching-with-lucene/2442642#2442642
	 * 
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void tags() throws IOException, ParseException {
		addDocs(
				(doc) -> {
					// https://lucene.apache.org/core/7_2_1/core/index.html?org/apache/lucene/document/StringField.html
					doc.add(new Field("issue", "HOME-1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "test1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "long_tag_with_multiple_words", StringField.TYPE_STORED));
					doc.add(new Field("tag", "marked", StringField.TYPE_STORED));
					doc.add(new Field("content", "This is the mizoine text to be indexed.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "HOME-2", StringField.TYPE_STORED));
					doc.add(new Field("tag", "test1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "test", StringField.TYPE_STORED));
					doc.add(new Field("content", "Another Mizoine test.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "TEST-1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "long_tag_with_multiple_words", StringField.TYPE_STORED));
					doc.add(new Field("tag", "marked", StringField.TYPE_STORED));
					doc.add(new Field("tag", "testing", StringField.TYPE_STORED));
					doc.add(new Field("content", "Experimental mizo1ne typo!", TextField.TYPE_STORED));
				});

		assertSearch("tag:test1", "HOME-1", "HOME-2");
		assertSearch("tag:test", "HOME-2");
		assertSearch("tag:marked", "HOME-1", "TEST-1");
		assertSearch("tag:marked AND tag:test1", "HOME-1");
		assertSearch("miz*", "HOME-1", "HOME-2", "TEST-1");
		assertSearch("test*", "HOME-2");
		assertSearch("tag:test*", "HOME-1", "HOME-2", "TEST-1");
		assertSearch("tag:long*", "HOME-1", "TEST-1");
		assertSearch("tag:multi*");
		assertSearch("tag:long_tag_with_multiple_words", "HOME-1", "TEST-1");
		assertSearch("!tag:test1 miz*", "TEST-1");
		assertSearch("!tag:long_tag_with_multiple_words miz*", "HOME-2");
	}

	/**
	 * https://stackoverflow.com/questions/2438000/how-do-i-implement-tag-searching-with-lucene/2442642#2442642
	 * 
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void titles() throws IOException, ParseException {
		addDocs(
				(doc) -> {
					// https://lucene.apache.org/core/7_2_1/core/index.html?org/apache/lucene/document/StringField.html
					doc.add(new Field("issue", "HOME-1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "test1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "status-done", StringField.TYPE_STORED));
					doc.add(new Field("tag", "marked", StringField.TYPE_STORED));
					doc.add(new Field("title", "First home issue", TextField.TYPE_STORED));
					doc.add(new Field("content", "This is the mizoine text to be indexed.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "HOME-2", StringField.TYPE_STORED));
					doc.add(new Field("tag", "test1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "status-resolved", StringField.TYPE_STORED));
					doc.add(new Field("title", "Second problem in home", TextField.TYPE_STORED));
					doc.add(new Field("content", "Another Mizoine test.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "TEST-1", StringField.TYPE_STORED));
					doc.add(new Field("tag", "status-in-process", StringField.TYPE_STORED));
					doc.add(new Field("tag", "marked", StringField.TYPE_STORED));
					doc.add(new Field("tag", "testing", StringField.TYPE_STORED));
					doc.add(new Field("title", "Testing additional functionality", TextField.TYPE_STORED));
					doc.add(new Field("content", "Experimental mizo1ne typo!", TextField.TYPE_STORED));
				});

		assertSearch("tag:\"test1\"", "HOME-1", "HOME-2");
		assertSearch("title:\"home issue\"", "HOME-1");
		assertSearch("title:\"home\"", "HOME-1", "HOME-2");
		assertSearch("Second");
		assertSearch("title:second", "HOME-2");
		assertSearch("title:functionality typo", "TEST-1");
		assertSearch("title:\"Testing additional functionality\" AND miz*", "TEST-1");
		assertSearch("title:\"testing additional functionality\" AND miz*", "TEST-1");

		//assertSearch("tag:(status\\-in\\-process)", "TEST-1");
		
		final Set<String> issueHitSet = new HashSet<>();
		try (final Directory directory = FSDirectory.open(indexPath)) {
			// Now search the index:
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				// Parse a simple query that searches for "text":
				final TermQuery query = new TermQuery(new Term("tag", "status-in-process"));
				final ScoreDoc[] hits = isearcher.search(query, 1000).scoreDocs;
				// Iterate through the results:
				for (int i = 0; i < hits.length; i++) {
					Document hitDoc = isearcher.doc(hits[i].doc);
					final String hitText = hitDoc.get("content");
					final String issue = hitDoc.get("issue");
					LOGGER.debug("Found by TQ [" + issue + "]: "  + hitText + ", tags: " + hitDoc.get("tag"));
					issueHitSet.add(issue);
				}
			}
		}
		assertEquals(Sets.newSet("TEST-1"), issueHitSet);

	}

	@Test
	public void combinedContent() throws IOException, ParseException {
		addDocs(
				(doc) -> {
					// https://lucene.apache.org/core/7_2_1/core/index.html?org/apache/lucene/document/StringField.html
					doc.add(new Field("issue", "HOME-9", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "car", StringField.TYPE_STORED));
					doc.add(new Field("title", "Spring inspection 2018", TextField.TYPE_STORED));
					doc.add(new Field("description", "Take an appointment at Ferrari service.", TextField.TYPE_STORED));
					
					doc.add(new Field("content", "car", TextField.TYPE_STORED));
					doc.add(new Field("content", "Spring inspection 2018", TextField.TYPE_STORED));
					doc.add(new Field("content", "Take an appointment at Ferrari service.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "HOME-12", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "cleaning", StringField.TYPE_STORED));
					doc.add(new Field("title", "Spring clean up 2018", TextField.TYPE_STORED));
					doc.add(new Field("description", "Change windows in bath.", TextField.TYPE_STORED));
					
					doc.add(new Field("content", "cleaning", TextField.TYPE_STORED));
					doc.add(new Field("content", "Spring clean up 2018", TextField.TYPE_STORED));
					doc.add(new Field("content", "Change windows in bath.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "TEST-3", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "car", StringField.TYPE_STORED));
					doc.add(new Field("title", "Verify ash tray", TextField.TYPE_STORED));
					doc.add(new Field("description", "Tray must be empty (or missing) in non-smoking cars. **Cleaning** recommended.", 
							TextField.TYPE_STORED));

					doc.add(new Field("content", "car", TextField.TYPE_STORED));
					doc.add(new Field("content", "Verify ash tray", TextField.TYPE_STORED));
					doc.add(new Field("content", "Tray must be empty (or missing) in non-smoking cars. **Cleaning** recommended.", 
							TextField.TYPE_STORED));
				});

		assertSearch("tag:car", "HOME-9", "TEST-3");
		assertSearch("car", "HOME-9", "TEST-3");
		assertSearch("cars", "TEST-3");
		assertSearch("description:car");
		assertSearch("title:spring", "HOME-9", "HOME-12");
		assertSearch("2018", "HOME-9", "HOME-12");
		assertSearch("cleaning", "HOME-12", "TEST-3");
		assertSearch("+cleaning", "HOME-12", "TEST-3");
		assertSearch("tag:cleaning", "HOME-12");
		assertSearch("description:cleaning", "TEST-3");
		assertSearch("smoking", "TEST-3");
		assertSearch("+cleaning +tag:car", "TEST-3");
		assertSearch("+\"cleaning recommended\" +tag:car", "TEST-3");
	}

	@Test
	public void allWordsQuery() throws IOException, ParseException {
		addDocs(
				(doc) -> {
					// https://lucene.apache.org/core/7_2_1/core/index.html?org/apache/lucene/document/StringField.html
					doc.add(new Field("issue", "HOME-8", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "car", StringField.TYPE_STORED));
					doc.add(new Field("title", "Spring inspection 2018", TextField.TYPE_STORED));
					doc.add(new Field("description", "Take an appointment at Ferrari service.", TextField.TYPE_STORED));
					
					doc.add(new Field("content", "car", TextField.TYPE_STORED));
					doc.add(new Field("content", "Spring inspection 2018", TextField.TYPE_STORED));
					doc.add(new Field("content", "Take an appointment at Ferrari service.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "HOME-22", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "cleaning", StringField.TYPE_STORED));
					doc.add(new Field("title", "Spring clean up 2018", TextField.TYPE_STORED));
					doc.add(new Field("description", "Change windows in bath.", TextField.TYPE_STORED));
					
					doc.add(new Field("content", "cleaning", TextField.TYPE_STORED));
					doc.add(new Field("content", "Spring clean up 2018", TextField.TYPE_STORED));
					doc.add(new Field("content", "Change windows in bath.", TextField.TYPE_STORED));
				},
				(doc) -> {
					doc.add(new Field("issue", "TEST-23", StringField.TYPE_STORED));
					
					doc.add(new Field("tag", "car", StringField.TYPE_STORED));
					doc.add(new Field("title", "Verify ash tray", TextField.TYPE_STORED));
					doc.add(new Field("description", "Tray must be empty (or missing) in non-smoking cars. **Cleaning** recommended.", 
							TextField.TYPE_STORED));

					doc.add(new Field("content", "car", TextField.TYPE_STORED));
					doc.add(new Field("content", "Verify ash tray", TextField.TYPE_STORED));
					doc.add(new Field("content", "Tray must be empty (or missing) in non-smoking cars. **Cleaning** recommended.", 
							TextField.TYPE_STORED));
				});

		assertTermSearch("ferrari", "HOME-8");
		assertTermSearch("cleaning", "HOME-22", "TEST-23");
		assertTermSearch("cleaning recommended", "TEST-23");
		assertTermSearch("bath cleaning", "HOME-22");
		assertTermSearch("service   2018  ", "HOME-8");
		assertTermSearch(" 2018  ", "HOME-8", "HOME-22");
	}
	
}
