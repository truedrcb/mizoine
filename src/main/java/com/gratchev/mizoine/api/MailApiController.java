package com.gratchev.mizoine.api;

import com.gratchev.mizoine.ImapComponent;
import com.gratchev.mizoine.SignedInUser;
import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.utils.ImapUtils;
import com.gratchev.utils.ImapUtils.MailMessage;
import com.gratchev.utils.ImapUtils.MailPartDto;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.mail.Address;
import javax.mail.Header;
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
				}).filter(Objects::nonNull).collect(Collectors.toList()));
	}

	@GetMapping("preview/{uri}")
	@ResponseBody
	public MailMessage getMailPreview(@PathVariable final String uri) throws Exception {
		final MailMessage mailMessage = new MailMessage();

		return imap.readMessage(decodeUri(uri), message -> {

			mailMessage.subject = message.getSubject();
			mailMessage.from = message.getFrom();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("sendDate: " + message.getSentDate() + " subject: " + message.getSubject());
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
					final MailPartDto block = ImapUtils.extractMailBlock(part);
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
		});
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
	 * <li>Specified part (by <code>commentPartId</code>) - converted to markdown and set as description of the comment
	 * </ul>
	 *
	 * @param uri           Encoded mail Id (see {@link #encodeUri(String)} and {@link #decodeUri(String)}
	 * @param commentPartId Index of mail part (block) to be imported as text of the comment
	 * @param project       Target project of the issue
	 * @param issueNumber   Target issue number (where to create a comment)
	 * @return Comment Id (which was created within target issue)
	 */
	@PostMapping("import-to-issue")
	@ResponseBody
	public String importMailToIssue(final String uri, final String commentPartId, final String project,
			final String issueNumber) throws Exception {
		final String messageId = decodeUri(uri);

		LOGGER.info("Importing mail with ID: " + messageId + " (" + commentPartId + ") to issue " + project + "-"
				+ issueNumber);

		final List<Attachment> attachments = new ArrayList<>();
		final String commentId = imap.readMessage(messageId,
				message -> {
					final CreatedComment createdComment =
							createCommentFromMessage(getRepo(), currentUser, messageId, message, project, issueNumber,
									commentPartId);
					attachments.addAll(createdComment.attachments);
					return createdComment.commentId;
				});
		attachments.forEach(attachment -> {
			final Repository.AttachmentProxy proxy = getRepo().attachment(project, issueNumber, attachment.id);
			proxy.updatePreviewAndLogErrors();
			proxy.extractDescriptionAndLogErrors();
		});
		return commentId;
	}

	static class CreatedComment {
		String commentId;
		List<Attachment> attachments;
	}

	static CreatedComment createCommentFromMessage(final Repository repo, final SignedInUser currentUser, final String messageId,
										   final Message message, final String project, final String issueNumber,
										   final String commentPartId) throws Exception {
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
		final Map<String, MailPartDto> contentParts = new TreeMap<>();

		message.getParts().forEach(part -> {
			final String partId = counter.nextId();

			try {
				final MailPartDto partDto = ImapUtils.extractMailBlock(part);

				if (partDto.content != null) {
					contentParts.put(partId, partDto);
					final String fileName = partId + (partDto.contentSubType.toLowerCase().contains("html") ? ".html" :
							".txt");
					LOGGER.info("Saving mail text to: " + fileName);
					comment.writeFile(fileName, partDto.content);
					return;
				}

				if (partDto.fileName == null) {
					return;
				}

				LOGGER.info("Importing attachment: " + partDto.fileName + " type: " + partDto.contentType);

				final Attachment attachment = issue.uploadAttachment(part.getFileName(), part.getContentType(), part.getSize(),
						ZonedDateTime.ofInstant(messageDate.toInstant(), ZoneId.systemDefault()),
						attachmentFile -> Files.copy(part.getInputStream(), attachmentFile.toPath()));
				attachments.add(attachment);
			} catch (Exception e) {
				LOGGER.error("Upload failed for block #: " + counter.counter, e);
			}

		});

		// Write description
		final StringBuilder sb = new StringBuilder();
		final List<MailPartDto> blocksToAdd = new ArrayList<>();


		if (commentPartId != null && contentParts.containsKey(commentPartId)) {
			blocksToAdd.add(contentParts.get(commentPartId));
		} else {
			blocksToAdd.addAll(contentParts.values());
		}

		int i = 0;
		for (final MailPartDto block : blocksToAdd) {
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
				sb.append("](attachment-");
				sb.append(a.id);
				sb.append(")\n");
			}
		}

		comment.writeDescription(sb.toString());
		final CreatedComment createdComment = new CreatedComment();
		createdComment.commentId = commentId;
		createdComment.attachments = attachments;
		return createdComment;
	}

	private static class PartCounter {
		private int counter;

		public String nextId() {
			return "part-" + counter++;
		}
	}

}
