package com.gratchev.mizoine.api;

import static com.gratchev.mizoine.FlexmarkUtils.generateMarkdownFooterRefs;
import static com.gratchev.mizoine.api.AttachmentApiController.getPageBaseUri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.gratchev.mizoine.api.IssueApiController.WithDescription;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Comment;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.mizoine.repository.RepositoryCache;
import com.gratchev.utils.FileUtils;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/comment/{project}-{issueNumber}/{commentId}")
public class CommentApiController extends BaseDescriptionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommentApiController.class);
	
	@GetMapping("info")
	@ResponseBody
	public WithDescription info(
			@PathVariable final String project, 
			@PathVariable final String issueNumber,
			@PathVariable final String commentId) throws IOException {
		final Repository repo = getRepo();
		final IssueProxy issue = repo.issue(project, issueNumber);
		final CommentProxy issueComment = issue.issueComment(commentId);
		final Comment comment = issueComment.read();
		final ArrayList<Attachment> attachments = issue.readAttachments();
		final String markdownFooterRefs = generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments);
		final WithDescription ment = new WithDescription();
		ment.comment = comment;
		ment.descriptionMarkdown = issueComment.readDescription();
		ment.descriptionPath = issueComment.getDescriptionEditorPath();
		ment.metaPath = issueComment.getMetaEditorPath();
		if (ment.descriptionMarkdown != null) {
			ment.descriptionHtml =render(ment.descriptionMarkdown + markdownFooterRefs);
		}
		return ment;
	}
	
	@PostMapping("description")
	@ResponseBody
	public DescriptionResponse updateDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber,
			@PathVariable final String commentId, final String description) throws IOException {
		LOGGER.debug("Update description for: " + project + "-" + issueNumber + " " + commentId);
		LOGGER.debug(description);
		final Repository repo = getRepo();
		final File descriptionFile = repo.comment(project, issueNumber, commentId).getDescriptionFile();
		FileUtils.overwriteTextFile(description, descriptionFile);
		final ArrayList<Attachment> attachments = repo.issue(project, issueNumber).readAttachments();
		return descriptionResponse(description, parse(description + 
				generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments)));
	}


	@GetMapping("description")
	@ResponseBody
	public DescriptionResponse getDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber,
			@PathVariable final String commentId) throws IOException {
		LOGGER.debug("Get description for: " + project + "-" + issueNumber + " " + commentId);

		final Repository repo = getRepo();
		final File descriptionFile = repo.comment(project, issueNumber, commentId).getDescriptionFile();
		if (descriptionFile.exists()) {
			final String markdownText = FileUtils.readTextFile(descriptionFile);
			final ArrayList<Attachment> attachments = repo.issue(project, issueNumber).readAttachments();
			return descriptionResponse(markdownText, parse(markdownText + 
					generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments)));
		}
		return new DescriptionResponse();
	}

	
	@PostMapping("update-title")
	@ResponseBody
	public String updateTitle(@PathVariable final String project, 
			@PathVariable final String issueNumber, 
			@PathVariable final String commentId, final String title) throws IOException {
		getRepo().comment(project, issueNumber, commentId).updateMeta((meta) -> {
			meta.title = title;
		}, currentUser);
		return title;
	}
	
	
	@PostMapping("tag")
	@ResponseBody
	public String addTag(@PathVariable final String project, @PathVariable final String issueNumber, 
			@PathVariable final String commentId,
			@RequestParam(name = "tag", required = false) final String tag) throws IOException {
		final Repository repo = getRepo();
		final CommentProxy comment = repo.comment(project, issueNumber, commentId);
		new RepositoryCache(repo).addTagOrStatus(tag, comment);
		return tag;
	}

	@DeleteMapping("tag")
	@ResponseBody
	public String removeTag(@PathVariable final String project, @PathVariable final String issueNumber, 
			@PathVariable final String commentId,
			@RequestParam(name = "tag", required = false) final String tag) throws IOException {
		final CommentProxy comment = getRepo().comment(project, issueNumber, commentId);
		comment.removeTags(tag);
		comment.removeStatus(tag);
		return tag;
	}
	

	@DeleteMapping()
	@ResponseBody
	public String delete(@PathVariable final String project, 
			@PathVariable final String issueNumber, 
			@PathVariable final String commentId) throws IOException {
		getRepo().comment(project, issueNumber, commentId).delete();
		return "deleted";
	}

}
