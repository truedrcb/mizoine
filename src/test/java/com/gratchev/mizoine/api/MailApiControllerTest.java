package com.gratchev.mizoine.api;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
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

import javax.activation.DataHandler;
import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

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
	public void importMailText() throws IOException, MessagingException {
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
					public int getSize() {
						return 0;
					}

					@Override
					public int getLineCount() {
						return 0;
					}

					@Override
					public String getContentType() {
						return "text/plain";
					}

					@Override
					public boolean isMimeType(String mimeType) {
						return "text/plain".equalsIgnoreCase(mimeType);
					}

					@Override
					public String getDisposition() {
						return null;
					}

					@Override
					public void setDisposition(String disposition) {
					}

					@Override
					public String getDescription() {
						return null;
					}

					@Override
					public void setDescription(String description) {
					}

					@Override
					public String getFileName() {
						return null;
					}

					@Override
					public void setFileName(String filename) {
					}

					@Override
					public InputStream getInputStream() {
						return null;
					}

					@Override
					public DataHandler getDataHandler() {
						return null;
					}

					@Override
					public void setDataHandler(DataHandler dh) {

					}

					@Override
					public Object getContent() {
						return mailText;
					}

					@Override
					public void setContent(Multipart mp) {
					}

					@Override
					public void setContent(Object obj, String type) {

					}

					@Override
					public void setText(String text) {
					}

					@Override
					public void writeTo(OutputStream os) {
					}

					@Override
					public String[] getHeader(String header_name) {
						return null;
					}

					@Override
					public void setHeader(String header_name, String header_value)  {
					}

					@Override
					public void addHeader(String header_name, String header_value) {
					}

					@Override
					public void removeHeader(String header_name) {
					}

					@Override
					public Enumeration<Header> getAllHeaders() {
						return null;
					}

					@Override
					public Enumeration<Header> getMatchingHeaders(String[] header_names) {
						return null;
					}

					@Override
					public Enumeration<Header> getNonMatchingHeaders(String[] header_names) {
						return null;
					}

					@Override
					public Address[] getFrom() {
						return null;
					}

					@Override
					public void setFrom(Address address) {
					}

					@Override
					public void setFrom() {
					}

					@Override
					public void addFrom(Address[] addresses) {
					}

					@Override
					public Address[] getRecipients(RecipientType type) {
						return null;
					}

					@Override
					public void setRecipients(RecipientType type, Address[] addresses) {
					}

					@Override
					public void addRecipients(RecipientType type, Address[] addresses) {
					}

					@Override
					public String getSubject() {
						return mailSubject;
					}

					@Override
					public void setSubject(String subject) {
					}

					@Override
					public Date getSentDate() {
						return mailSentDate;
					}

					@Override
					public void setSentDate(Date date) {
					}

					@Override
					public Date getReceivedDate() {
						return null;
					}

					@Override
					public Flags getFlags() {
						return null;
					}

					@Override
					public void setFlags(Flags flag, boolean set) {
					}

					@Override
					public Message reply(boolean replyToAll) {
						return null;
					}

					@Override
					public void saveChanges() {
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
