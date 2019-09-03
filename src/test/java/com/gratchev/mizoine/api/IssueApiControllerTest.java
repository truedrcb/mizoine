package com.gratchev.mizoine.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.api.BaseDescriptionController.DescriptionResponse;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.CommentMeta;

public class IssueApiControllerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(IssueApiController.class);

	TempRepository repo;
	IssueApiController controller;
	final String project = "TEST";
	Issue issue;
	Attachment attachment;

	@Before
	public void setUp() throws Exception {
		repo = TempRepository.create();
		
		controller = new IssueApiController() {
			@Override
			public Repository getRepo() {
				return repo;
			}
		};
		controller.flexmark = new FlexmarkComponent();
		controller.currentUser = new SignedInUserComponentMock();
		
		issue = repo.createIssue(repo.getProjectIssuesRoot(project), "Testing attachments refs", 
				"", controller.currentUser);
		assertNotNull(issue);
		
		final Date creationDate = new Date();
		attachment = repo.issue(project, issue.issueNumber).uploadAttachment(new MockMultipartFile(
				"test.png", "original-test.png", "image/png", 
				this.getClass().getResourceAsStream("Mizoine-logo-transparent.png")), creationDate);

		assertNotNull(attachment);
		assertNotNull(attachment.id);
		assertNotNull(attachment.meta);
		assertEquals(creationDate, attachment.meta.creationDate);
		
		LOGGER.info("Attachment uploaded: " + attachment);
	}
	
	@After
	public void tearDown() throws IOException {
		if (repo != null) {
			repo.dispose();
			repo = null;
		}
	}

	private DescriptionResponse updateDescription(final String description) throws IOException {
		controller.updateDescription(project, issue.issueNumber, description);
		final DescriptionResponse response = controller.getDescription(project, issue.issueNumber);
		LOGGER.info("Response: " + response);
		
		assertEquals(description, response.markdown);
		return response;
	}
	
	
	@Test
	public void attachmentRefs() throws IOException {
		
		assertEquals("", 
				updateDescription("").html);
		
		
		assertEquals("<h1>Hello</h1>\n", 
				updateDescription("# Hello").html);
		
		assertEquals(
				"<p><img src=\"/attachments/projects/TEST/issues/0/attachments/" 
				+ attachment.id 
				+ "/original-test.png\" alt=\"A thumbnail\" title=\"original-test.png\" class=\"miz-md-thumbnail\" "
				+ "miz-ref=\"" + attachment.id + "\" /></p>\n", 
				updateDescription("![A thumbnail][" + attachment.id + "]").html);
		
		assertEquals(
				"<p><img src=\"/attachments/projects/TEST/issues/0/attachments/" 
				+ attachment.id 
				+ "/original-test.png\" alt=\"Full image\" title=\"original-test.png\" class=\"miz-md-img\" "
				+ "miz-ref=\"" + attachment.id + ".lg\" /></p>\n", 
				updateDescription("![Full image][" + attachment.id + ".lg]").html);

		assertEquals(
				"<p><a href=\"/attachments/projects/TEST/issues/0/attachments/" 
				+ attachment.id 
				+ "/original-test.png\" title=\"original-test.png\">File</a></p>\n", 
				updateDescription("[File][" + attachment.id + ".file]").html);

		assertEquals(
				"<p><a href=\"/issue/TEST-0/attachment/" 
				+ attachment.id 
				+ "\" title=\"original-test.png\">Page</a></p>\n", 
				updateDescription("[Page][" + attachment.id + ".page]").html);
	}
	
	
	@Test
	public void createComment() throws IOException {
		final String commentTitle = "# My comment\nLorem ipsum dolor sit amet.";
		final String commentId = controller.createComment(project, issue.issueNumber, commentTitle);
		
		final CommentProxy comment = repo.comment(project, issue.issueNumber, commentId);
		
		final CommentMeta meta = comment.readMeta();
		assertEquals(commentTitle, meta.title);
		assertEquals(controller.currentUser.getName(), meta.creator);
		assertNotNull(meta.creationDate);
	}
}
