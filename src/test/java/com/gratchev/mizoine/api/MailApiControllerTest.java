package com.gratchev.mizoine.api;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.ImapComponent.MessageReader;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.mail.Part;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.CommentMeta;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;

import javax.mail.Address;
import javax.mail.Header;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MailApiControllerTest {
	final static String PROJECT = "TEST";
	final static String MESSAGE_ID = "Me55a9e-1d";
	private static final Logger LOGGER = LoggerFactory.getLogger(MailApiControllerTest.class);
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
		controller.imap = mock(ImapComponent.class);

		issue = repo.createIssue(repo.getProjectIssuesRoot(PROJECT), "Testing attachments refs",
				"", controller.currentUser);
		assertNotNull(issue);

		final ZonedDateTime creationDate = ZonedDateTime.now();
		attachment = repo.issue(PROJECT, issue.issueNumber).uploadAttachment("original-test.png", "image/png", 123456,
				creationDate,
				dest -> Files.copy(MailApiControllerTest.class.getResourceAsStream("/com/gratchev/mizoine/Mizoine-logo" +
						"-transparent.png"), dest.toPath()));

		assertNotNull(attachment);
		assertNotNull(attachment.id);
		assertNotNull(attachment.meta);
		assertThat(attachment.meta.creationDate).hasYear(creationDate.getYear()).hasMonth(creationDate.getMonthValue())
				.hasDayOfMonth(creationDate.getDayOfMonth()).hasHourOfDay(creationDate.getHour())
				.hasMinute(creationDate.getMinute()).hasSecond(creationDate.getSecond());

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
		whenMessage(MESSAGE_ID, new SimpleMessage(List.of(new TextPart(mailText, MediaType.TEXT_PLAIN_VALUE)),
				mailSubject, mailSentDate));

		final String commentId = controller.importMailToIssue(controller.encodeUri(MESSAGE_ID), "?", PROJECT,
				issue.issueNumber);

		final CommentProxy comment = repo.comment(PROJECT, issue.issueNumber, commentId);

		assertThat(comment.readDescription()).isEqualTo(mailText);

		assertContentMeta(mailSubject, mailSentDate, comment);
	}

	@Test
	public void importMailHtml() throws Exception {
		final String mailText = "We are uncovering better ways of developing\n" +
				"software by doing it and helping others do it.\n" +
				"Through this work we have come to value:";
		final String mailSubject = "Manifesto for Agile Software Development";
		final Date mailSentDate = new Date();

		whenMessage(MESSAGE_ID, new SimpleMessage(List.of(new TextPart(mailText, MediaType.TEXT_PLAIN_VALUE),
				new TextPart("<p>See <a href=https://agilemanifesto.org/>Manifesto</a></p>",
						MediaType.TEXT_HTML_VALUE)), mailSubject, mailSentDate));

		final String commentId = controller.importMailToIssue(controller.encodeUri(MESSAGE_ID), "part-1", PROJECT,
				issue.issueNumber);

		final CommentProxy comment = repo.comment(PROJECT, issue.issueNumber, commentId);

		assertThat(comment.readDescription()).isEqualTo("See [Manifesto](https://agilemanifesto.org/)");

		assertContentMeta(mailSubject, mailSentDate, comment);
	}

	@Test
	public void importMailAttachment() throws Exception {
		final String mailText = "Our highest priority is to satisfy the customer\n" +
				"through early and continuous delivery\n" +
				"of valuable software.";
		final String mailText2 = "Welcome changing requirements, even late in\n" +
				"development. Agile processes harness change for\n" +
				"the customer's competitive advantage.";
		final String mailSubject = "We follow these principles:";
		final Date mailSentDate = new Date();

		final String fileName = "invoice.pdf";
		whenMessage(MESSAGE_ID, new SimpleMessage(List.of(new TextPart(mailText, MediaType.TEXT_PLAIN_VALUE),
				new TextPart(mailText2, MediaType.TEXT_PLAIN_VALUE),
				new BinaryPart("/com/gratchev/utils/Invoice251217.pdf",
						MediaType.APPLICATION_PDF_VALUE, fileName)), mailSubject, mailSentDate));

		final String commentId = controller.importMailToIssue(controller.encodeUri(MESSAGE_ID), null, PROJECT,
				issue.issueNumber);

		final CommentProxy comment = repo.comment(PROJECT, issue.issueNumber, commentId);
		assertContentMeta(mailSubject, mailSentDate, comment);

		final String descriptionPrefix = mailText + "\n\n---\n\n" + mailText2 + "\n\n---\n\n- [" + fileName + "]" +
				"(attachment-";
		final String description = comment.readDescription();
		assertThat(description).startsWith(descriptionPrefix);
		final String attachmentId = description.substring(descriptionPrefix.length()).substring(0, 5);

		final AttachmentProxy attachment = repo.attachment(PROJECT, issue.issueNumber, attachmentId);
		assertThat(attachment.readInfo().files).hasSize(1).allSatisfy(file -> {
			assertThat(file.fileName).isEqualTo(fileName);
		});
		assertThat(attachment.readDescription()).contains("Please do not pay.");
	}

	private void whenMessage(final String messageId, final SimpleMessage message) throws Exception {
		when(controller.imap.readMessage(eq(messageId), any())).then(i -> ((MessageReader) i.getArgument(1)).read(message));
	}

	private void assertContentMeta(String mailSubject, Date mailSentDate, CommentProxy comment) {
		final CommentMeta meta = comment.readMeta();
		assertThat(meta.creator).isEqualTo(controller.currentUser.getName());
		assertThat(meta.creationDate.toString()).isEqualTo(mailSentDate.toString());
		assertThat(meta.title).isEqualTo(mailSubject);
	}

	public static class SimpleMessage implements Message {

		private final String mailSubject;
		private final Date mailSentDate;
		private final List<Part> parts;

		public SimpleMessage(final List<Part> parts, final String mailSubject, final Date mailSentDate) {
			this.parts = parts;
			this.mailSubject = mailSubject;
			this.mailSentDate = mailSentDate;
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
			return parts.stream();
		}

		@Override
		public int getMessageNumber() {
			return 0;
		}

		@Override
		public Address[] getAllRecipients() {
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

	}

	public static class TextPart implements Part {

		private final String content;
		private final String contentType;


		public TextPart(final String content, final String contentType) {
			this.content = content;
			this.contentType = contentType;
		}

		@Override
		public int getSize() {
			return content.getBytes(StandardCharsets.UTF_8).length;
		}

		@Override
		public String getFileName() {
			return "simpleText-" + content.hashCode() + "-" + getSize();
		}

		@Override
		public @NotNull InputStream getInputStream() {
			return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public Object getContent() {
			return content;
		}

		@Override
		public String getContentType() {
			return contentType;
		}

		@Override
		public Enumeration<Header> getAllHeaders() {
			return Collections.enumeration(List.of());
		}
	}

	public static class BinaryPart implements Part {

		private final byte[] content;
		private final String contentType;
		private final String fileName;

		public BinaryPart(final String contentPath, final String contentType, final String fileName) throws IOException {
			this.content = FileCopyUtils.copyToByteArray(MailApiControllerTest.class.getResourceAsStream(contentPath));
			this.contentType = contentType;
			this.fileName = fileName;
		}

		@Override
		public int getSize() {
			return content.length;
		}

		@Override
		public String getFileName() {
			return fileName;
		}

		@Override
		public @NotNull InputStream getInputStream() {
			return new ByteArrayInputStream(content);
		}

		@Override
		public Object getContent() {
			return content;
		}

		@Override
		public String getContentType() {
			return contentType;
		}

		@Override
		public Enumeration<Header> getAllHeaders() {
			return Collections.enumeration(List.of());
		}
	}
}
