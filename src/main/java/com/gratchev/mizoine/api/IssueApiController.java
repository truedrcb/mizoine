package com.gratchev.mizoine.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Joiner;
import com.gratchev.mizoine.mail.impl.MessageImpl;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.mizoine.repository.RepositoryCache;
import com.gratchev.mizoine.repository.meta.IssueMeta;
import com.gratchev.utils.FileUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.gratchev.mizoine.FlexmarkUtils.generateMarkdownFooterRefs;
import static com.gratchev.mizoine.api.AttachmentApiController.getPageBaseUri;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/issue/{project}-{issueNumber}")
public class IssueApiController extends BaseDescriptionController {
	private static final Logger log = LoggerFactory.getLogger(IssueApiController.class);

	@PostMapping("upload")
	@ResponseBody
	public List<String> uploadAttachment(@PathVariable final String project,
										 @PathVariable final String issueNumber,
										 final MultipartHttpServletRequest request)
			throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Upload attachment(s) for: " + project + "-" + issueNumber);
		}

		final List<String> uploadedAttachmentIds = new ArrayList<>();

		for (final Iterator<String> iterator = request.getFileNames(); iterator.hasNext(); ) {
			final String fileShortName = iterator.next();

			log.debug("File: {}", fileShortName);

			final MultipartFile uploadFile = request.getFile(fileShortName);
			final String originalFilename = uploadFile.getOriginalFilename();
			log.debug("Original filename: {}", originalFilename);

			try {
				if (originalFilename.toLowerCase(Locale.ROOT).endsWith(".eml")) {
					final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()),
							uploadFile.getInputStream());
					final String[] idHeaders = message.getHeader("Message-ID");
					MailApiController.createCommentFromMessage(getRepo(), currentUser,
							idHeaders != null ? Joiner.on("|").join(idHeaders) : "",
							new MessageImpl(message), project, issueNumber, null).attachments.forEach(attachment -> {
								uploadedAttachmentIds.add(attachment.id);
					});
				} else {
						final Attachment attachment = getRepo().issue(project, issueNumber).uploadAttachment(uploadFile);
						uploadedAttachmentIds.add(attachment.id);
				}
			} catch (final Exception e) {
				log.error("Upload failed for file: {} {}", fileShortName, uploadFile, e);
			}
		}
		uploadedAttachmentIds.forEach(attachmentId -> {
			final AttachmentProxy proxy = getRepo().attachment(project, issueNumber, attachmentId);
			proxy.updatePreviewAndLogErrors();
			proxy.extractDescriptionAndLogErrors();
		});
		return uploadedAttachmentIds;
	}

	@PostMapping("description")
	@ResponseBody
	public DescriptionResponse updateDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber, final String description) throws IOException {
		log.debug("Update description for: " + project + "-" + issueNumber);
		log.debug(description);
		final Repository repo = getRepo();
		final File descriptionFile = repo.issue(project, issueNumber).getDescriptionFile();
		FileUtils.overwriteTextFile(description, descriptionFile);
		final ArrayList<Attachment> attachments = repo.issue(project, issueNumber).readAttachments();
		return descriptionResponse(description, parse(description + 
				generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments)));
	}


	@GetMapping("description")
	@ResponseBody
	public DescriptionResponse getDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber) throws IOException {
		log.debug("Get description for: " + project + "-" + issueNumber);

		final Repository repo = getRepo();
		final File descriptionFile = repo.issue(project, issueNumber).getDescriptionFile();
		if (descriptionFile.exists()) {
			final String markdownText = FileUtils.readTextFile(descriptionFile);
			final ArrayList<Attachment> attachments = repo.issue(project, issueNumber).readAttachments();
			return descriptionResponse(markdownText, parse(markdownText + 
					generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments)));
		}
		return new DescriptionResponse();
	}

	@JsonInclude(Include.NON_NULL)
	public static class WithDescription extends IssueMeta.Ment {
		public String descriptionMarkdown;
		public String descriptionHtml;
		public String descriptionPath;
		public String metaPath;
	}

	public static class IssueInfo {
		public String project;
		public Issue issue;
		public ArrayList<WithDescription> ments; // comments or attachments with description
	}

	@GetMapping("info")
	@ResponseBody
	public IssueInfo getInfo(@PathVariable final String project, @PathVariable final String issueNumber)
			throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Reading project: " + project + " issue: " + issueNumber);
		}

		final IssueInfo info = new IssueInfo();
		info.project = project;
		final IssueProxy proxy = getRepo().issue(project, issueNumber);
		info.issue = proxy.readInfo();

		final ArrayList<Attachment> attachments = proxy.readAttachments();
		// PERFORMANCE KILLER!!!
		//final String markdownFooterRefs = generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments);
		info.ments = new ArrayList<>();
		attachments.stream().map(attachment -> {
			final WithDescription ment = new WithDescription();
			ment.attachment = attachment;
			final AttachmentProxy issueAttachment = proxy.issueAttachment(attachment.id);
			ment.descriptionMarkdown = issueAttachment.readDescription();
			ment.descriptionPath = issueAttachment.getDescriptionEditorPath();
			ment.metaPath = issueAttachment.getMetaEditorPath();
			return ment;
		}).collect(Collectors.toCollection(() -> info.ments));

		proxy.readComments().stream().map(comment -> {
			final WithDescription ment = new WithDescription();
			ment.comment = comment;
			final CommentProxy issueComment = proxy.issueComment(comment.id);
			ment.descriptionMarkdown = issueComment.readDescription();
			ment.descriptionPath = issueComment.getDescriptionEditorPath();
			ment.metaPath = issueComment.getMetaEditorPath();
			return ment;
		}).collect(Collectors.toCollection(() -> info.ments));
		
		final Parser parser = flexmark.getParser();
		final HtmlRenderer renderer = flexmark.getRenderer();
		info.ments.sort(IssueMeta.MENTS_COMPARATOR);

		for(final WithDescription ment: info.ments) {
			if (ment.descriptionMarkdown != null) {
				final Node document = parser.parse(ment.descriptionMarkdown
					/* + markdownFooterRefs*/); // PERFORMANCE KILLER!!!
				ment.descriptionHtml = renderer.render(document);
			} else {
				ment.descriptionHtml = null;
			}
		}
		return info;
	}

	@PostMapping("tag")
	@ResponseBody
	public String addTag(@PathVariable final String project, @PathVariable final String issueNumber, final String tag) 
			throws IOException {
		final Repository repo = getRepo();
		final IssueProxy issue = repo.issue(project, issueNumber);
		new RepositoryCache(repo).addTagOrStatus(tag, issue);
		return tag;
	}

	@DeleteMapping("tag")
	@ResponseBody
	public String removeTag(@PathVariable final String project, @PathVariable final String issueNumber, final String tag)
			throws IOException {
		final IssueProxy issue = getRepo().issue(project, issueNumber);
		issue.removeTags(tag);
		issue.removeStatus(tag);
		return tag;
	}

	@PostMapping("comment")
	@ResponseBody
	public String createComment(@PathVariable final String project, 
			@PathVariable final String issueNumber, final String title) throws IOException {
		final ZonedDateTime creationDate = ZonedDateTime.now();
		final Repository repo = getRepo();
		final String commentId = repo.issue(project, issueNumber).newCommentId(creationDate);
		final CommentProxy comment = repo.comment(project, issueNumber, commentId);
		comment.createDirs();
		comment.updateMeta((meta) -> {
			meta.title = title;
			meta.creationDate = Date.from(creationDate.toInstant());
			meta.creator = currentUser.getName();
		}, currentUser);
		return commentId;
	}

	@PutMapping("title")
	@ResponseBody
	public String updateTitle(@PathVariable final String project, 
			@PathVariable final String issueNumber, final String title) throws IOException {
		getRepo().issue(project, issueNumber).updateMeta(meta -> meta.title = title, currentUser);
		return title;
	}

	@PostMapping("update-thumbnails")
	@ResponseBody
	public String updateThumbnails(@PathVariable final String project, 
			@PathVariable final String issueNumber) throws IOException {
		getRepo().issue(project, issueNumber).updateAttachmentPreviews();
		return "Updated";
	}

	@DeleteMapping()
	@ResponseBody
	public String delete(@PathVariable final String project, 
			@PathVariable final String issueNumber) throws IOException {
		getRepo().issue(project, issueNumber).delete();
		return "Deleted";
	}
}
