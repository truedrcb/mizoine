package com.gratchev.mizoine.api;

import static com.gratchev.mizoine.FlexmarkUtils.generateMarkdownFooterRefs;
import static com.gratchev.mizoine.api.AttachmentApiController.getPageBaseUri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.BaseEntityInfo;
import com.gratchev.mizoine.repository.Comment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.mizoine.repository.RepositoryCache;
import com.gratchev.mizoine.repository.meta.AttachmentMeta;
import com.gratchev.mizoine.repository.meta.BaseMeta;
import com.gratchev.utils.FileUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/issue/{project}-{issueNumber}")
public class IssueApiController extends BaseDescriptionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(IssueApiController.class);
	public static final Comparator<WithDescription> MENTS_COMPARATOR = new Comparator<WithDescription>() {
		private Date getDate(final WithDescription o) {
			if (o == null) return null;
			final BaseEntityInfo<? extends BaseMeta> ment = o.ment();
			if (ment == null) return null;
			final BaseMeta meta = ment.meta;
			if (meta == null) return null;
			return meta.creationDate;
		}

		private String getId(final WithDescription o) {
			if (o == null) return null;
			final BaseEntityInfo<? extends BaseMeta> ment = o.ment();
			if (ment == null) return null;
			return ment.id;
		}
		/**
		 * Compare by creation date if possible. Otherwise by id (if possible).<br>
		 * Objects come in following order:
		 * <ol>
		 * <li>Both dates present: Greatest date (most recent)</li>
		 * <li>Only one date present: With date first</li>
		 * <li>Both dates missing: Both ids present: Greatest id</li>
		 * <li>Both dates missing: One id present: With id first</li>
		 * <li>Both dates missing: Both ids missing: Incomparable, equal</li>
		 * </ol>
		 * 
		 */
		@Override
		public int compare(final WithDescription o1, final WithDescription o2) {
			final Date d1 = getDate(o1);
			final Date d2 = getDate(o2);
			if (d1 != null && d2 != null) {
				return -d1.compareTo(d2);
			}
			if (d1 != null) {
				return -1;
			}
			if (d2 != null) {
				return 1;
			}
			final String id1 = getId(o1);
			final String id2 = getId(o2);
			if (id1 != null && id2 != null) {
				return -id1.compareTo(id2);
			}
			if (id1 != null) {
				return -1;
			}
			if (id2 != null) {
				return 1;
			}
			return 0;
		}
	};
	
	@PostMapping("upload")
	@ResponseBody
	public List<AttachmentMeta> uploadAttachment(@PathVariable final String project, 
			@PathVariable final String issueNumber, final MultipartHttpServletRequest request) {
		//LOGGER.debug(request.getParameterMap().toString());
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Upload attachment(s) for: " + project + "-" + issueNumber);
		}

		final List<AttachmentMeta> uploadedAttachments = new ArrayList<>();

		for (final Iterator<String> iterator = request.getFileNames(); iterator.hasNext();) {
			final String fileName = iterator.next();

			LOGGER.debug("File: " + fileName);

			final MultipartFile uploadfile = request.getFile(fileName);

			try {
				getRepo().issue(project, issueNumber).uploadAttachment(uploadfile, new Date());
			} catch (IOException e) {
				LOGGER.error("Upload failed for file: " + fileName + " " + uploadfile, e);
			}

		}
		return uploadedAttachments;
	}

	@PostMapping("description")
	@ResponseBody
	public DescriptionResponse updateDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber, final String description) throws IOException {
		LOGGER.debug("Update description for: " + project + "-" + issueNumber);
		LOGGER.debug(description);
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
		LOGGER.debug("Get description for: " + project + "-" + issueNumber);

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
	public static class WithDescription {
		public String descriptionMarkdown;
		public String descriptionHtml;
		public String descriptionPath;
		public String metaPath;
		
		public Comment comment;
		public Attachment attachment;
		
		private BaseEntityInfo<? extends BaseMeta> ment() {
			if (comment != null) {
				return comment;
			} else {
				return attachment;
			}
		}
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
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Reading project: " + project + " issue: " + issueNumber);
		}

		final IssueInfo info = new IssueInfo();
		info.project = project;
		final IssueProxy proxy = getRepo().issue(project, issueNumber);
		info.issue = proxy.readInfo();

		final ArrayList<Attachment> attachments = proxy.readAttachments();
		final String markdownFooterRefs = generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments);
		
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
		
		for(final WithDescription ment: info.ments) {
			if (ment.descriptionMarkdown != null) {
				final Node document = parser.parse(ment.descriptionMarkdown + markdownFooterRefs);
				ment.descriptionHtml = renderer.render(document);
			} else {
				ment.descriptionHtml = null;
			}
		}

		info.ments.sort(MENTS_COMPARATOR);
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
		final Date creationDate = new Date();
		final Repository repo = getRepo();
		final String commentId = repo.newCommentId(creationDate, title);
		final CommentProxy comment = repo.comment(project, issueNumber, commentId);
		comment.createDirs();
		comment.updateMeta((meta) -> {
			meta.title = title;
			meta.creationDate = creationDate;
			meta.creator = currentUser.getName();
		}, currentUser);
		return commentId;
	}

	@PutMapping("title")
	@ResponseBody
	public String updateTitle(@PathVariable final String project, 
			@PathVariable final String issueNumber, final String title) throws IOException {
		getRepo().issue(project, issueNumber).updateMeta((meta) -> {
			meta.title = title;
		}, currentUser);
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
