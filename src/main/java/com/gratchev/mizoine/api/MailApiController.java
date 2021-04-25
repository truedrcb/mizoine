package com.gratchev.mizoine.api;

import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.utils.ImapUtils;
import com.gratchev.utils.ImapUtils.MailBlock;
import com.gratchev.utils.ImapUtils.MailMessage;
import org.apache.tomcat.util.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.Address;
import javax.mail.Header;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/mail")
public class MailApiController extends BaseController {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailApiController.class);
	private static final Charset URL_ENCODE_CHARSET = StandardCharsets.UTF_8;

	@Autowired
	protected ImapComponent imap;

	/**
	 * Safely extract a date from the message
	 *
	 * @param message E-mail message for date extraction.
	 * @return If available: Sent date. Otherwise, if available: Received date. Otherwise: current date.
	 * @throws Exception If message throws an error
	 */
	private static Date getMessageDate(final Message message) throws Exception {
		final Date sentDate = message.getSentDate();
		final Date receivedDate = message.getReceivedDate();
		return sentDate != null ? sentDate : (receivedDate != null ? receivedDate : new Date());
	}

	@GetMapping("list/unread")
	@ResponseBody
	public List<MailMessage> getUnreadMailList() {
		return imap.readInbox(inbox ->
				inbox.getUnseenMessages().sorted((m1, m2) -> {
					// Sort messages from recent to oldest
					try {
						return getMessageDate(m2).compareTo(getMessageDate(m1));
					} catch (final Exception e) {
						LOGGER.warn("Date reading error", e);
						return 0;
					}
				}).map(message -> {
					try {
						final MailMessage mailMessage = new MailMessage();
						mailMessage.subject = message.getSubject();
						mailMessage.from = message.getFrom();
						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug("sendDate: " + message.getSentDate() + " subject: " + message.getSubject());
							LOGGER.debug("contentType: " + message.getContentType());
						}
						final String[] idHeaders = message.getHeader("Message-ID");
						if (idHeaders != null && idHeaders.length > 0) {
							mailMessage.id = idHeaders[0];
							mailMessage.uri = encodeUri(mailMessage.id);
						}
						return mailMessage;
					} catch (final Exception e) {
						LOGGER.warn("Skipped reading message", e);
					}
					return null;
				})).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@GetMapping("preview/{uri}")
	@ResponseBody
	public MailMessage getMailPreview(@PathVariable final String uri) throws Exception {
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

		message.getParts().forEach(part -> {
			try {
				final MailBlock block = ImapUtils.extractMailBlock(part);
				if (block.markdown != null) {
					block.html = render(block.markdown);
				}
				block.id = counter.nextId();
				mailMessage.blocks.add(block);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
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
									final String issueNumber) throws Exception {
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

		message.getParts().forEach(part -> {
			final String partId = counter.nextId();

			try {
				final MailBlock block = ImapUtils.extractMailBlock(part);

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
						} catch (final Exception e) {
							LOGGER.error("getSize", e);
							return 0;
						}
					}

					@Override
					public String getOriginalFilename() {
						try {
							return part.getFileName();
						} catch (final Exception e) {
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
						} catch (Exception e) {
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
			} catch (Exception e) {
				LOGGER.error("Upload failed for block #: " + counter.counter, e);
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

	private static class PartCounter {
		private int counter;

		public String nextId() {
			return "part-" + counter++;
		}
	}

}
