package com.gratchev.mizoine.repository;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.Attachment.FileInfo;
import com.gratchev.mizoine.repository.meta.AttachmentMeta;
import com.gratchev.mizoine.repository.meta.BaseMeta;
import com.gratchev.mizoine.repository.meta.CommentMeta;
import com.gratchev.mizoine.repository.meta.IssueMeta;
import com.gratchev.mizoine.repository.meta.ProjectMeta;

/**
 * Updates Lucene index (overwrites existing documents)
 * 
 * TODO: Extract interface
 */
public class RepositoryIndexer implements AutoCloseable {


	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexer.class);
	
	public static final String DOCUMENT_KEY_FIELD = "key";
	public static final String DOCUMENT_TAGS_FIELD = "tags";
	public static final String DOCUMENT_STATUS_FIELD = "status";
	public static final String DOCUMENT_FILE_FIELD = "file";
	private static final String DOCUMENT_DESCRIPTION_FIELD = "description";

	final IndexWriter writer;
	final Analyzer analyzer = new StandardAnalyzer();
	final IndexWriterConfig config = new IndexWriterConfig(analyzer);
	
	class DocumentBuilder {
		final Document doc = new Document();
		final SortedMap<String, String> keyBuilder = new TreeMap<>();
		String key = null;

		private DocumentBuilder meta(final String field, final String value) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(field + " = " + value);
			}
			if (key == null) {
				if(keyBuilder.put(field, value) != null) {
					LOGGER.error("Unexpected overwritten document key field: " + field 
							+ " = " + value);
				}
			}
			doc.add(new Field(field, value, StringField.TYPE_STORED));
			return this;
		}

		private DocumentBuilder content(final String field, final String value) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(field + " <= " + value);
			}
			if (value != null) {
				doc.add(new Field(field, value, TextField.TYPE_STORED));
				doc.add(new Field("content", value, TextField.TYPE_STORED));
			}
			return this;
		}

		private DocumentBuilder tags(final String field, final Iterable<String> tags) {
			if (tags == null) {
				return this;
			}
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(field + " <= " + tags);
			}
			for (final String value : tags) {
				doc.add(new Field(field, value, StringField.TYPE_STORED));
				doc.add(new Field("content", value, TextField.TYPE_STORED));
			}
			return this;
		}
		
		private DocumentBuilder keyFinished() {
			final StringBuilder sb = new StringBuilder();
			for (final Entry<String, String> e : keyBuilder.entrySet()) {
				sb.append(e.getKey());
				sb.append('=');
				sb.append(e.getValue());
				sb.append(';');
			}
			key = sb.toString();
			if (key.length() <= 0) {
				LOGGER.error("Unexpected empty document key");
			} else {
				meta(DOCUMENT_KEY_FIELD, key);
			}
			return this;
		}
		
		private DocumentBuilder overwrite() {
			keyFinished();
			
			return this;
		}


		private <T extends BaseMeta>DocumentBuilder title(final T meta) {
			if (meta != null) {
				if (meta.title != null) {
					content("title", meta.title);
				}
				if (meta.creator != null) {
					content("creator", meta.creator);
				}
			}
			return this;
		}

		private void finish() throws IOException {
			LOGGER.debug("Storing document");
			if (key == null) {
				LOGGER.error("Undefined key fields for document: " + keyBuilder);
			}
			writer.deleteDocuments(new Term(DOCUMENT_KEY_FIELD, key));
			writer.addDocument(doc);
		}

		public DocumentBuilder files(Iterable<FileInfo> files) {
			if (files == null) {
				return this;
			}
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Files: " + files);
			}
			for (final FileInfo file : files) {
				doc.add(new Field(DOCUMENT_FILE_FIELD, file.fileName, StringField.TYPE_STORED));
				doc.add(new Field("content", file.fileName, TextField.TYPE_STORED));
			}
			
			return this;
		}

	}

	private DocumentBuilder type(final String type) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Indexing " + type);
		}
		DocumentBuilder d = new DocumentBuilder();
		d.meta("type", type);
		return d;
	}
	
	public RepositoryIndexer(final Directory directory) throws IOException {
		LOGGER.debug("Indexing!");
		writer = new IndexWriter(directory, config);
	}
	
	
	public void indexProject(
			final String project, 
			final ProjectMeta meta,
			final String description,
			final Iterable<String> tags,
			final Iterable<String> status) throws IOException {
		type("project")
		.meta("project", project)
		.overwrite()
		.title(meta)
		.content(DOCUMENT_DESCRIPTION_FIELD, description)
		.tags(DOCUMENT_TAGS_FIELD, tags)
		.tags(DOCUMENT_STATUS_FIELD, status)
		.finish();
	}
	
	public void indexIssue(
			final String project, 
			final String issueNumber,
			final IssueMeta meta,
			final String description,
			final Iterable<String> tags,
			final Iterable<String> status) throws IOException {
		type("issue")
		.meta("project", project)
		.meta("issueNumber", issueNumber)
		.overwrite()
		.title(meta)
		.content(DOCUMENT_DESCRIPTION_FIELD, description)
		.tags(DOCUMENT_TAGS_FIELD, tags)
		.tags(DOCUMENT_STATUS_FIELD, status)
		.finish();
	}


	public void indexAttachment(
			final String project, 
			final String issueNumber,
			final String attachmentId,
			final AttachmentMeta meta,
			final String description,
			final Iterable<String> tags,
			final Iterable<String> status, 
			final Iterable<FileInfo> files) throws IOException {
		type("attachment")
		.meta("project", project)
		.meta("issueNumber", issueNumber)
		.meta("attachmentId", attachmentId)
		.overwrite()
		.title(meta)
		.content(DOCUMENT_DESCRIPTION_FIELD, description)
		.tags(DOCUMENT_TAGS_FIELD, tags)
		.tags(DOCUMENT_STATUS_FIELD, status)
		.files(files)
		.finish();
	}

	public void indexComment(
			final String project, 
			final String issueNumber,
			final String commentId,
			final CommentMeta meta,
			final String description,
			final Iterable<String> tags,
			final Iterable<String> status) throws IOException {
		type("comment")
		.meta("project", project)
		.meta("issueNumber", issueNumber)
		.meta("commentId", commentId)
		.overwrite()
		.title(meta)
		.content(DOCUMENT_DESCRIPTION_FIELD, description)
		.tags(DOCUMENT_TAGS_FIELD, tags)
		.tags(DOCUMENT_STATUS_FIELD, status)
		.finish();
	}

	@Override
	public void close() throws IOException {
		try {
			writer.close();
		} finally {
			analyzer.close();
		}
	}

	public static String createContentQuery(final String query, final Analyzer analyzer, final BooleanQuery.Builder builder) throws IOException {
		// https://stackoverflow.com/questions/37904977/lucene-6-0-how-to-instantiate-a-booleanquery-and-add-other-search-queries-in-it
		final StringBuilder querySB = new StringBuilder();
		LOGGER.debug("Building content query");
		try (final TokenStream ts = analyzer.tokenStream("none", query)) {
			ts.reset();
			for (int i = 0; i < 1000; i++) {
				final CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);
				if (term != null && term.length() > 0) {
					final String w = term.toString();
					LOGGER.debug(w);
					builder.add(new TermQuery(new Term("content", w)), BooleanClause.Occur.MUST);
					if (querySB.length() > 0) {
						querySB.append(' ');
					}
					querySB.append(w);
				}
				
				if (!ts.incrementToken()) {
					break;
				}
			}
			ts.end();
		}
		return querySB.toString();
	}
	
	public String createContentQuery(final String query, final BooleanQuery.Builder builder) throws IOException {
		return createContentQuery(query, analyzer, builder);
	}

	
	public static void createTagsQuery(final Iterable<String> tags, final String field, final BooleanQuery.Builder builder) 
			throws IOException {
		for (final String tag : tags) {
			builder.add(new TermQuery(new Term(field, tag)), BooleanClause.Occur.MUST);
		}
	}

	/**
	 * @param tags Tags, which must be matched in document (required)
	 * @param fields List of possible fields, where tag to be matched
	 * @param builder Common builder to add required condition
	 * @throws IOException
	 */
	public static void createTagsQuery(final Iterable<String> tags, final Iterable<String> fields, final BooleanQuery.Builder builder) 
			throws IOException {
		// https://stackoverflow.com/questions/3130908/how-to-create-nested-boolean-query-with-lucene-api-a-and-b-or-c
		for (final String tag : tags) {
			final BooleanQuery.Builder orBuilder = new BooleanQuery.Builder();
			for (final String field : fields) {
				orBuilder.add(new TermQuery(new Term(field, tag)), BooleanClause.Occur.SHOULD);
			}
			builder.add(orBuilder.build(), BooleanClause.Occur.MUST);
		}
	}

	public long deleteAll() throws IOException {
		return writer.deleteAll();
	}
 }
