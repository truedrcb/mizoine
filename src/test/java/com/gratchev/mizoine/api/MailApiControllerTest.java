package com.gratchev.mizoine.api;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.mail.Part;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.CommentMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import javax.mail.Address;
import javax.mail.Header;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MailApiControllerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MailApiControllerTest.class);
	final String project = "TEST";
	TempRepository repo;
	MailApiController controller;
	Issue issue;
	Attachment attachment;

	@BeforeEach
	public void setUp() throws Exception {
		repo = TempRepository.create();

		controller = new MailApiController() {
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

	@AfterEach
	public void tearDown() throws IOException {
		if (repo != null) {
			repo.dispose();
			repo = null;
		}
	}

	@Test
	public void importMailText() throws Exception {
		final String mailText = "1. Dupressoir A, Lavialle C, Heidmann T: From ancestral infectious retroviruses "
				+ "to bona fide cellular genes: role of the captured syncytins in placentation.\n"
				+ "Placenta 2012, 33(9):663-671.";
		final String mailSubject = "6. Parrish NF, Tomonaga K: A Viral (Arc)hive for Metazoan Memory. Cell 2018, 172" +
				"(1-2):8-10.";
		final Date mailSentDate = new Date();

		controller.imap = new ImapComponent() {
			@Override
			public Message readMessage(final String messageId) {
				return new Message() {

					@Override
					public String getContentType() {
						return "text/plain";
					}

					@Override
					public String[] getHeader(String header_name) {
						return null;
					}

					@Override
					public Enumeration<Header> getAllHeaders() {
						return null;
					}

					@Override
					public Stream<Part> getParts() {
						return null;
					}

					@Override
					public int getMessageNumber() {
						return 0;
					}

					@Override
					public Address[] getAllRecipients() throws Exception {
						return new Address[0];
					}

					@Override
					public Address[] getFrom() {
						return null;
					}

					@Override
					public String getSubject() {
						return mailSubject;
					}

					@Override
					public Date getSentDate() {
						return mailSentDate;
					}

					@Override
					public Date getReceivedDate() {
						return null;
					}
				};
			}
		};

		final String commentId = controller.importMailToIssue(controller.encodeUri("test-uri"), "0", project,
				issue.issueNumber);

		final CommentProxy comment = repo.comment(project, issue.issueNumber, commentId);

		assertEquals(mailText, comment.readDescription());

		final CommentMeta meta = comment.readMeta();
		assertEquals(controller.currentUser.getName(), meta.creator);
		assertEquals(mailSentDate.toString(), meta.creationDate.toString());
		assertEquals(mailSubject, meta.title);
	}
}
