package com.gratchev.mizoine.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.utils.HTMLtoMarkdown;
import com.gratchev.utils.ImapUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.*;
import javax.mail.search.FlagTerm;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/mail")
public class MailApiController extends BaseController {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailApiController.class);
	private static final Charset URL_ENCODE_CHARSET = StandardCharsets.UTF_8;

	@Autowired
	protected ImapComponent imap;

	private final HTMLtoMarkdown mailHTMLtoMarkdown = new HTMLtoMarkdown();

	/**
	 * Safely extract a date from the message
	 *
	 * @param message E-mail message for date extraction.
	 * @return If available: Sent date. Otherwise, if available: Received date. Otherwise: current date.
	 * @throws MessagingException If message throws an error
	 */
	private static Date getMessageDate(final Message message) throws MessagingException {
		final Date sentDate = message.getSentDate();
		final Date receivedDate = message.getReceivedDate();
		return sentDate != null ? sentDate : (receivedDate != null ? receivedDate : new Date());
	}

	private MailBlock extractMailBlock(final Part part) throws MessagingException, IOException {
		final MailBlock block = new MailBlock();
		block.contentType = part.getContentType();
		final int indexOfSemicolon = block.contentType.indexOf(';');
		if (indexOfSemicolon > 0) {
			block.contentSubType = block.contentType.substring(0, indexOfSemicolon);
		} else {
			block.contentSubType = block.contentType;
		}

		block.size = part.getSize();
		block.fileName = part.getFileName();
		final String mimeType = block.contentSubType.toLowerCase();
		if (mimeType.startsWith("text/")) {
			block.content = part.getContent().toString();
			if (mimeType.contains("html")) {
				block.markdown = mailHTMLtoMarkdown.convert(Jsoup.parse(block.content));
			} else {
				block.markdown = block.content
						// Redundant line feeds (inserted by some formatting programs)
						.replace(" \n", " ")
						// Remove potential HTML injections
						.replace("<", "&lt;");
			}
		}

		return block;
	}

	@GetMapping("list/unread")
	@ResponseBody
	public List<MailMessage> getUnreadMailList() {
		final List<MailMessage> mailMessages = new ArrayList<>();

		imap.readInbox((inbox) -> {
			// Fetch unseen messages from inbox folder
			final Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

			// Sort messages from recent to oldest
			Arrays.sort(messages, (m1, m2) -> {
				try {
					return getMessageDate(m2).compareTo(getMessageDate(m1));
				} catch (final MessagingException e) {
					LOGGER.warn("Date reading error", e);
					return 0;
				}
			});

			for (final Message message : messages) {
				final MailMessage mailMessage = new MailMessage();
				mailMessage.subject = message.getSubject();
				mailMessage.from = message.getFrom();
				mailMessages.add(mailMessage);
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("sendDate: " + message.getSentDate() + " subject: " + message.getSubject());
					LOGGER.debug("contentType: " + message.getContentType());
				}
				final String[] idHeaders = message.getHeader("Message-ID");
				if (idHeaders != null && idHeaders.length > 0) {
					mailMessage.id = idHeaders[0];
					mailMessage.uri = encodeUri(mailMessage.id);
				}
			}
			return null;
		});

		return mailMessages;
	}

	@GetMapping("preview/{uri}")
	@ResponseBody
	public MailMessage getMailPreview(@PathVariable final String uri) throws MessagingException, IOException {
		final MailMessage mailMessage = new MailMessage();

		final Message message = imap.readMessage(decodeUri(uri));

		mailMessage.subject = message.getSubject();
		mailMessage.from = message.getFrom();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("sendDate: " + message.getSentDate() + " subject: " + message.getSubject());
			LOGGER.debug("contentType: " + message.getContentType());
		}
		final Enumeration<Header> headers = message.getAllHeaders();
		while (headers.hasMoreElements()) {
			final Header header = headers.nextElement();
			if (mailMessage.id == null && "Message-ID".equalsIgnoreCase(header.getName())) {
				mailMessage.id = header.getValue();
				mailMessage.uri = encodeUri(mailMessage.id);
			}
			mailMessage.headers.add(header);
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Header: " + header.getName() + " = " + header.getValue());
			}
		}
		final PartCounter counter = new PartCounter();

		ImapUtils.forParts(message, (part) -> {
			final MailBlock block = extractMailBlock(part);
			if (block.markdown != null) {
				block.html = render(block.markdown);
			}
			block.id = counter.nextId();
			mailMessage.blocks.add(block);
		});

		return mailMessage;
	}

	protected String encodeUri(final String id) {
		return Base64.encodeBase64String(id.getBytes(URL_ENCODE_CHARSET));
	}

	protected String decodeUri(final String uri) {
		return new String(Base64.decodeBase64(uri), URL_ENCODE_CHARSET);
	}

	/**
	 * Read specified e-mail and create new comment out of it. Following items are imported:
	 * <ul>
	 * <li>Attachments - each as separate attachment to issue
	 * <li>Text parts - each as separate file (part-0.txt, part-1.html, etc.) within comment directory
	 * <li>Specified part (by blockId) - converted to markdown and set as description of the comment
	 * </ul>
	 *
	 * @param uri         Encoded mail Id (see {@link #encodeUri(String)} and {@link #decodeUri(String)}
	 * @param blockId     Index of mail part (block) to be imported as text of the comment
	 * @param project     Target project of the issue
	 * @param issueNumber Target issue number (where to create a comment)
	 * @return Comment Id (which was created within target issue)
	 */
	@PostMapping("import-to-issue")
	@ResponseBody
	public String importMailToIssue(final String uri, final String blockId, final String project,
									final String issueNumber) throws MessagingException, IOException {
		final String messageId = decodeUri(uri);

		LOGGER.info("Importing mail with ID: " + messageId + " (" + blockId + ") to issue " + project + "-" + issueNumber);

		final Message message = imap.readMessage(messageId);
		final Repository repo = getRepo();
		final Date messageDate = getMessageDate(message);

		final IssueProxy issue = repo.issue(project, issueNumber);
		final String commentId = issue.newCommentId(ZonedDateTime.ofInstant(messageDate.toInstant(),
				ZoneId.systemDefault()));
		final CommentProxy comment = repo.comment(project, issueNumber, commentId);
		comment.createDirs();
		comment.updateMeta((meta) -> {
			meta.creationDate = messageDate;
			meta.title = message.getSubject();
			meta.messageId = messageId;
			final Address[] from = message.getFrom();
			if (from != null && from.length > 0) {
				meta.creator = from[0].toString();
			} else {
				meta.creator = currentUser.getName();
			}
			meta.messageHeaders = new TreeMap<>();
			final Enumeration<Header> allHeaders = message.getAllHeaders();
			if (allHeaders != null) {
				while (allHeaders.hasMoreElements()) {
					final Header header = allHeaders.nextElement();
					meta.messageHeaders.put(header.getName(), header.getValue());
				}
			}
		}, currentUser);
		final List<Attachment> attachments = new ArrayList<>();

		final PartCounter counter = new PartCounter();
		final Map<String, MailBlock> contentParts = new TreeMap<>();

		ImapUtils.forParts(message, (part) -> {
			final String partId = counter.nextId();

			final MailBlock block = extractMailBlock(part);

			if (block.content != null) {
				contentParts.put(partId, block);
				final String fileName = partId + (block.contentSubType.toLowerCase().contains("html") ? ".html" :
						".txt");
				LOGGER.info("Saving mail text to: " + fileName);
				comment.writeFile(fileName, block.content);
				return;
			}

			if (block.fileName == null) {
				return;
			}

			LOGGER.info("Importing attachment: " + block.fileName + " type: " + block.contentType);

			try {
				final MultipartFile uploadFile = new MultipartFile() {

					@Override
					public void transferTo(final File dest) throws IOException, IllegalStateException {
						Files.copy(getInputStream(), dest.toPath());
					}

					@Override
					public boolean isEmpty() {
						return getSize() <= 0;
					}

					@Override
					public long getSize() {
						try {
							return part.getSize();
						} catch (final MessagingException e) {
							LOGGER.error("getSize", e);
							return 0;
						}
					}

					@Override
					public String getOriginalFilename() {
						try {
							return part.getFileName();
						} catch (final MessagingException e) {
							LOGGER.error("getOriginalFilename", e);
							return null;
						}
					}

					@Override
					public String getName() {
						return getOriginalFilename();
					}

					@Override
					public @NotNull InputStream getInputStream() throws IOException {
						try {
							return part.getInputStream();
						} catch (MessagingException e) {
							throw new IOException(e);
						}
					}

					@Override
					public String getContentType() {
						return block.contentType;
					}

					@Override
					public byte @NotNull [] getBytes() throws IOException {
						throw new IOException("Not implemented");
					}
				};

				attachments.add(issue.uploadAttachment(uploadFile, messageDate));
			} catch (IOException e) {
				LOGGER.error("Upload failed for file: " + block.fileName, e);
			}

		});

		// Write description
		final StringBuilder sb = new StringBuilder();
		final List<MailBlock> blocksToAdd = new ArrayList<>();


		if (contentParts.containsKey(blockId)) {
			blocksToAdd.add(contentParts.get(blockId));
		} else {
			blocksToAdd.addAll(contentParts.values());
		}

		int i = 0;
		for (final MailBlock block : blocksToAdd) {
			if (block.markdown != null && !block.markdown.isBlank()) {
				if (i > 0) {
					sb.append("\n\n---\n\n");
				}
				sb.append(block.markdown);
				i++;
			}
		}

		if (attachments.size() > 0) {
			sb.append("\n\n---\n\n");

			for (Attachment a : attachments) {
				sb.append("- [");
				sb.append(a.meta.fileName);
				sb.append("][");
				sb.append(a.id);
				sb.append(".page]\n");
			}
		}

		comment.writeDescription(sb.toString());

		return commentId;
	}

	@JsonInclude(Include.NON_EMPTY)
	public static class MailBlock {
		public String id;
		public String contentType;
		public String contentSubType;
		public String content;
		public String markdown;
		public String html;
		public int size;
		public String fileName;
	}

	@JsonInclude(Include.NON_EMPTY)
	public static class MailMessage {
		public final List<Header> headers = new ArrayList<>();
		public final List<MailBlock> blocks = new ArrayList<>();
		public String id;
		public String uri;
		public String subject;
		public Address[] from;
	}

	private static class PartCounter {
		private int counter;

		public String nextId() {
			return "part-" + counter++;
		}
	}

}
