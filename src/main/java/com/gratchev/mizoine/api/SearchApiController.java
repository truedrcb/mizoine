package com.gratchev.mizoine.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.Sets;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Comment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Project;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.mizoine.repository.Repository.Visitor;
import com.gratchev.mizoine.repository.RepositoryIndexer;;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/search")
public class SearchApiController extends BaseController {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchApiController.class);

	@JsonInclude(Include.NON_NULL)
	public static class SearchEntry {
		@Override
		public String toString() {
			return "SearchEntry [project=" + project + ", issueNumber=" + issueNumber + ", attachmentId=" + attachmentId
					+ ", commentId=" + commentId + "]";
		}
		public String project;
		public String issueNumber;
		public String attachmentId;
		public String commentId;
	}
	
	@JsonInclude(Include.NON_NULL)
	public static class SearchResult {
		public final List<SearchEntry> hits = new ArrayList<>();
		public String query;
		public String tag;
	}

	@GetMapping("find")
	@ResponseBody
	public SearchResult search(
			@RequestParam(name = "q", required = false) final String query,
			@RequestParam(name = "tag", required = false) final String tag
			) throws IOException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Query: '"  + query + "'");
			LOGGER.debug("Tag: '"  + tag + "'");
		}
		try (final Directory directory = FSDirectory.open(getRepo().getLuceneDir().toPath())) {
			try (final DirectoryReader ireader = DirectoryReader.open(directory)) {
				IndexSearcher isearcher = new IndexSearcher(ireader);

				// https://stackoverflow.com/questions/37904977/lucene-6-0-how-to-instantiate-a-booleanquery-and-add-other-search-queries-in-it
				final BooleanQuery.Builder builder = new BooleanQuery.Builder();
				final SearchResult result = new SearchResult();
				
				if (query != null && query.length() > 0) {
					result.query = RepositoryIndexer.createContentQuery(query, new StandardAnalyzer(), builder);
					LOGGER.info("Search query: " + result.query);
				}
				if (tag != null && tag.length() > 0) {
					RepositoryIndexer.createTagsQuery(Sets.newHashSet(tag), Sets.newHashSet("tags", "status"), builder);
					result.tag = tag;
				}
				
				final Query luceneQuery = builder.build();
				final ScoreDoc[] hits = isearcher.search(luceneQuery, 1000).scoreDocs;
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Found hits: " + hits.length);
				}

				for (int i = 0; i < hits.length; i++) {
					final Document hitDoc = isearcher.doc(hits[i].doc);
					final SearchEntry hit = new SearchEntry();
					result.hits.add(hit);
					hit.project = hitDoc.get("project");
					hit.issueNumber = hitDoc.get("issueNumber");
					hit.attachmentId = hitDoc.get("attachmentId");
					hit.commentId = hitDoc.get("commentId");
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Found: " + hit + " type " + hitDoc.get("type"));
					}
				}
				return result;
			}
		}
	}

	@PostMapping("index")
	@ResponseBody
	public String index() throws IOException {
		LOGGER.debug("Indexing!");
		final Repository repo = getRepo();
		try (final Directory directory = FSDirectory.open(repo.getLuceneDir().toPath())) {
			try (final RepositoryIndexer indexer = new RepositoryIndexer(directory)) {
				LOGGER.info("Index deleted: " + indexer.deleteAll());

				repo.fullScan(new Visitor() {

					@Override
					public void user(final String user) throws IOException {
					}

					@Override
					public void project(final String project) throws IOException {
						final Project info = repo.readProjectInfo(project);
						indexer.indexProject(project, 
								info.meta, repo.readProjectDescription(project), 
								info.tags, info.status);
					}

					@Override
					public void issue(final String project, final String issueNumber) throws IOException {
						final IssueProxy proxy = repo.issue(project, issueNumber);
						final Issue info = proxy.readInfo();
						indexer.indexIssue(project, issueNumber, 
								info.meta, proxy.readDescription(), 
								info.tags, info.status);
					}

					@Override
					public void comment(final String project, final String issueNumber, final String commentId) throws IOException {
						final Comment info = repo.issue(project, issueNumber).issueComment(commentId).read();
						indexer.indexComment(project, issueNumber, commentId,
								info.meta, repo.comment(project, issueNumber, commentId).readDescription(), 
								info.tags, info.status);
					}

					@Override
					public void attachment(final String project, final String issueNumber, final String attachmentId) throws IOException {
						final AttachmentProxy proxy = repo.attachment(project, issueNumber, attachmentId);
						final Attachment info = proxy.readInfo();
						indexer.indexAttachment(project, issueNumber, attachmentId,
								info.meta, proxy.readDescription(), 
								info.tags, info.status, info.files);
					}
				});
			}
		}
		LOGGER.debug("Indexing finished.");

		return "finished";
	}

}
