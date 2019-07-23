package com.gratchev.mizoine.api;

import static com.gratchev.mizoine.FlexmarkUtils.generateMarkdownFooterRefs;

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
import org.springframework.web.bind.annotation.ResponseBody;

import com.gratchev.mizoine.api.IssueApiController.WithDescription;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.utils.FileUtils;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/attachment/{project}-{issueNumber}/{attachmentId}")
public class AttachmentApiController extends BaseDescriptionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentApiController.class);

	public static String getPageBaseUri(final String project, final String issueNumber) {
		return "/issue/" + project + "-" + issueNumber + "/attachment/";
	}
	
	@GetMapping("")
	public String attachmentRoot() throws IOException {
		return "redirect:/attachment/{project}-{issueNumber}/{attachmentId}/view";
	}

	@GetMapping("info")
	@ResponseBody
	public WithDescription view(@PathVariable final String project, @PathVariable final String issueNumber, 
			@PathVariable final String attachmentId) throws IOException {

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Reading attachment from project: " + project + " issue: " + issueNumber + " Id: " + attachmentId);
		}

		final Repository repo = getRepo();
		final IssueProxy issue = repo.issue(project, issueNumber);
		final ArrayList<Attachment> attachments = issue.readAttachments();
		final String markdownFooterRefs = generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments);

		final AttachmentProxy attachment = repo.attachment(project, issueNumber, attachmentId);
		Attachment info = attachment.readInfo();
		if (info == null) {
			info = new Attachment();
		}

		final WithDescription ment = new WithDescription();
		ment.attachment = info;
		ment.descriptionMarkdown = attachment.readDescription();
		ment.descriptionPath = attachment.getDescriptionEditorPath();
		ment.metaPath = attachment.getMetaEditorPath();
		if (ment.descriptionMarkdown != null) {
			ment.descriptionHtml =render(ment.descriptionMarkdown + markdownFooterRefs);
		}

		return ment;
	}


	@PostMapping("description")
	@ResponseBody
	public DescriptionResponse updateDescription(@PathVariable final String project, 
			@PathVariable final String issueNumber, @PathVariable final String attachmentId, 
			final String description) throws IOException {
		LOGGER.debug("Update description for: " + project + "-" + issueNumber);
		LOGGER.debug(description);
		final Repository repo = getRepo();
		final File descriptionFile = repo.attachment(project, issueNumber, attachmentId).getDescriptionFile();
		FileUtils.overwriteTextFile(description, descriptionFile);
		final ArrayList<Attachment> attachments = repo.issue(project, issueNumber).readAttachments();
		return descriptionResponse(description, parse(description + 
				generateMarkdownFooterRefs(getPageBaseUri(project, issueNumber), attachments)));
	}


	@GetMapping("description")
	@ResponseBody
	public DescriptionResponse getDescription(@PathVariable final String project,
			@PathVariable final String issueNumber, @PathVariable final String attachmentId) throws IOException {
		LOGGER.debug("Get description for: " + project + "-" + issueNumber);

		final Repository repo = getRepo();
		final File descriptionFile = repo.attachment(project, issueNumber, attachmentId).getDescriptionFile();
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
			@PathVariable final String issueNumber, @PathVariable final String attachmentId, 
			final String title) throws IOException {
		getRepo().attachment(project, issueNumber, attachmentId).updateMeta((meta) -> {
			meta.title = title;
		}, currentUser);
		return title;
	}
	
	@PostMapping("update-thumbnails")
	@ResponseBody
	public String updateThumbnails(@PathVariable final String project,
			@PathVariable final String issueNumber, @PathVariable final String attachmentId) {
		try {
			getRepo().attachment(project, issueNumber, attachmentId).updatePreview();
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
			return e.getMessage();
		}
		return "ok";
	}

	@PostMapping("extract-description")
	@ResponseBody
	public String extractDescription(@PathVariable final String project,
			@PathVariable final String issueNumber, @PathVariable final String attachmentId) throws IOException {
		getRepo().attachment(project, issueNumber, attachmentId).extractDescription();
		
		return "ok";
	}

	@DeleteMapping()
	@ResponseBody
	public String delete(@PathVariable final String project, 
			@PathVariable final String issueNumber, @PathVariable final String attachmentId) throws IOException {
		getRepo().attachment(project, issueNumber, attachmentId).delete();
		return "deleted";
	}

}
