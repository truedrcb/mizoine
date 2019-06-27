package com.gratchev.mizoine.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.CommentMeta;

public class MailApiControllerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MailApiControllerTest.class);

	TempRepository repo;
	MailApiController controller;
	final String project = "TEST";
	Issue issue;
	Attachment attachment;

	@Before
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
	
	@After
	public void tearDown() throws IOException {
		if (repo != null) {
			repo.dispose();
			repo = null;
		}
	}

	@Test
	public void importMailText() throws IOException {
		final String mailText = "1. Dupressoir A, Lavialle C, Heidmann T: From ancestral infectious retroviruses "
				+ "to bona fide cellular genes: role of the captured syncytins in placentation.\n"
				+ "Placenta 2012, 33(9):663-671.";
		final String mailSubject = "6. Parrish NF, Tomonaga K: A Viral (Arc)hive for Metazoan Memory. Cell 2018, 172(1-2):8-10.";
		final Date mailSentDate = new Date();
		
		controller.imap = new ImapComponent() {
			@Override
			public String readMessage(final String messageId, final MessageReader reader) {
				try {
					return reader.read(new Message() {

						@Override
						public int getSize() throws MessagingException {
							return 0;
						}

						@Override
						public int getLineCount() throws MessagingException {
							return 0;
						}

						@Override
						public String getContentType() throws MessagingException {
							return "text/plain";
						}

						@Override
						public boolean isMimeType(String mimeType) throws MessagingException {
							return "text/plain".equalsIgnoreCase(mimeType);
						}

						@Override
						public String getDisposition() throws MessagingException {
							return null;
						}

						@Override
						public void setDisposition(String disposition) throws MessagingException {
						}

						@Override
						public String getDescription() throws MessagingException {
							return null;
						}

						@Override
						public void setDescription(String description) throws MessagingException {
						}

						@Override
						public String getFileName() throws MessagingException {
							return null;
						}

						@Override
						public void setFileName(String filename) throws MessagingException {
						}

						@Override
						public InputStream getInputStream() throws IOException, MessagingException {
							return null;
						}

						@Override
						public DataHandler getDataHandler() throws MessagingException {
							return null;
						}

						@Override
						public Object getContent() throws IOException, MessagingException {
							return mailText;
						}

						@Override
						public void setDataHandler(DataHandler dh) throws MessagingException {
							
						}

						@Override
						public void setContent(Object obj, String type) throws MessagingException {
							
						}

						@Override
						public void setText(String text) throws MessagingException {
						}

						@Override
						public void setContent(Multipart mp) throws MessagingException {
						}

						@Override
						public void writeTo(OutputStream os) throws IOException, MessagingException {
						}

						@Override
						public String[] getHeader(String header_name) throws MessagingException {
							return null;
						}

						@Override
						public void setHeader(String header_name, String header_value) throws MessagingException {
						}

						@Override
						public void addHeader(String header_name, String header_value) throws MessagingException {
						}

						@Override
						public void removeHeader(String header_name) throws MessagingException {
						}

						@Override
						public Enumeration<Header> getAllHeaders() throws MessagingException {
							return null;
						}

						@Override
						public Enumeration<Header> getMatchingHeaders(String[] header_names) throws MessagingException {
							return null;
						}

						@Override
						public Enumeration<Header> getNonMatchingHeaders(String[] header_names) throws MessagingException {
							return null;
						}

						@Override
						public Address[] getFrom() throws MessagingException {
							return null;
						}

						@Override
						public void setFrom() throws MessagingException {
						}

						@Override
						public void setFrom(Address address) throws MessagingException {
						}

						@Override
						public void addFrom(Address[] addresses) throws MessagingException {
						}

						@Override
						public Address[] getRecipients(RecipientType type) throws MessagingException {
							return null;
						}

						@Override
						public void setRecipients(RecipientType type, Address[] addresses) throws MessagingException {
						}

						@Override
						public void addRecipients(RecipientType type, Address[] addresses) throws MessagingException {
						}

						@Override
						public String getSubject() throws MessagingException {
							return mailSubject;
						}

						@Override
						public void setSubject(String subject) throws MessagingException {
						}

						@Override
						public Date getSentDate() throws MessagingException {
							return mailSentDate;
						}

						@Override
						public void setSentDate(Date date) throws MessagingException {
						}

						@Override
						public Date getReceivedDate() throws MessagingException {
							return null;
						}

						@Override
						public Flags getFlags() throws MessagingException {
							return null;
						}

						@Override
						public void setFlags(Flags flag, boolean set) throws MessagingException {
						}

						@Override
						public Message reply(boolean replyToAll) throws MessagingException {
							return null;
						}

						@Override
						public void saveChanges() throws MessagingException {
						}
						
					});
				} catch (Exception e) {
					LOGGER.error("Unexpected", e);
				}
				
				return null;
			}
		};
		
		final String commentId = controller.importMailToIssue(controller.encodeUri("test-uri"), "0", project, issue.issueNumber);
		
		final CommentProxy comment = repo.comment(project, issue.issueNumber, commentId);
		
		assertEquals(mailText, comment.readDescription());
		
		final CommentMeta meta = comment.readMeta();
		assertEquals(controller.currentUser.getName(), meta.creator);
		assertEquals(mailSentDate.toString(), meta.creationDate.toString());
		assertEquals(mailSubject, meta.title);
	}
}
