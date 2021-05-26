package com.gratchev.mizoine.api;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.api.BaseDescriptionController.DescriptionResponse;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.CommentMeta;
import com.gratchev.utils.PDFBoxTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static com.gratchev.mizoine.ShortIdGenerator.mizCodeFor;
import static com.gratchev.mizoine.ShortIdGenerator.mizCodeToInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IssueApiControllerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(IssueApiController.class);
	final String project = "TEST";
	TempRepository repo;
	IssueApiController controller;
	Issue issue;
	Attachment attachment;

	@BeforeEach
	void setUp() throws Exception {
		repo = TempRepository.create();

		controller = new IssueApiController() {
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

		final ZonedDateTime creationDate = ZonedDateTime.now();
		attachment = repo.issue(project, issue.issueNumber).uploadAttachment(getPngFile(), creationDate);

		assertNotNull(attachment);
		assertNotNull(attachment.id);
		assertNotNull(attachment.meta);
		assertThat(attachment.meta.creationDate).hasYear(creationDate.getYear()).hasMonth(creationDate.getMonthValue())
				.hasDayOfMonth(creationDate.getDayOfMonth()).hasHourOfDay(creationDate.getHour())
				.hasMinute(creationDate.getMinute()).hasSecond(creationDate.getSecond());

		LOGGER.info("Attachment uploaded: " + attachment);
	}

	@AfterEach
	void tearDown() throws IOException {
		if (repo != null) {
			repo.dispose();
			repo = null;
		}
	}


	@Test
	void attachmentRefs() throws IOException {

		assertThat(updateDescription("").html).isEqualTo("");

		assertThat(updateDescription("# Hello").html).isEqualTo("<h1>Hello</h1>\n");

		assertThat(updateDescription("![A thumbnail][" + attachment.id + "]").html).isEqualTo(
				"<p><img src=\"/attachments/projects/TEST/issues/0/attachments/"
						+ attachment.id
						+ "/.mizoine/thumbnail.jpg\" alt=\"A thumbnail\" title=\"original-test.png\" " +
						"class=\"miz-md-thumbnail\" "
						+ "miz-ref=\"" + attachment.id + "\" /></p>\n"
		);

		assertThat(updateDescription("![Full image][" + attachment.id + ".lg]").html).isEqualTo(
				"<p><img src=\"/attachments/projects/TEST/issues/0/attachments/"
						+ attachment.id
						+ "/.mizoine/preview.jpg\" alt=\"Full image\" title=\"original-test.png\" class=\"miz-md-img\" "
						+ "miz-ref=\"" + attachment.id + ".lg\" /></p>\n"
		);

		assertThat(updateDescription("[File][" + attachment.id + ".file]").html).isEqualTo(
				"<p><a href=\"/attachments/projects/TEST/issues/0/attachments/"
						+ attachment.id
						+ "/original-test.png\" title=\"original-test.png\">File</a></p>\n"
		);

		assertThat(updateDescription("[Page][" + attachment.id + ".page]").html).isEqualTo(
				"<p><a href=\"/issue/TEST-0/attachment/"
						+ attachment.id
						+ "\" title=\"original-test.png\">Page</a></p>\n"
		);
	}

	@Test
	void createComment() throws IOException {
		final String commentTitle = "# My comment\nLorem ipsum dolor sit amet.";
		final String commentId = controller.createComment(project, issue.issueNumber, commentTitle);

		final CommentProxy comment = repo.comment(project, issue.issueNumber, commentId);

		final CommentMeta meta = comment.readMeta();
		assertEquals(commentTitle, meta.title);
		assertEquals(controller.currentUser.getName(), meta.creator);
		assertNotNull(meta.creationDate);
	}

	@Test
	void uploadSimpleAttachment() throws IOException {
		final MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
		request.addFile(getPngFile());
		final List<String> attachmentIds = controller.uploadAttachment(project, issue.issueNumber, request);
		final int mizNow = mizCodeToInt(mizCodeFor(ZonedDateTime.now()));
		assertThat(attachmentIds).hasSize(1).allSatisfy(id -> {
			assertThat(id).isNotBlank();
			assertThat(mizCodeToInt(id)).isBetween(mizNow - 1, mizNow + 1);
		});
	}

	@Test
	void uploadMultipleAttachments() throws IOException {
		final MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
		request.addFile(getPngFile());
		request.addFile(getPdfFile());
		final List<String> attachmentIds = controller.uploadAttachment(project, issue.issueNumber, request);
		final int mizNow = mizCodeToInt(mizCodeFor(ZonedDateTime.now()));
		assertThat(attachmentIds).hasSize(2).allSatisfy(id -> {
			assertThat(id).isNotBlank();
			assertThat(mizCodeToInt(id)).isBetween(mizNow - 1, mizNow + 3);
		});
	}

	@NotNull
	private MockMultipartFile getPngFile() throws IOException {
		return new MockMultipartFile(
				"test.png", "original-test.png", MediaType.IMAGE_PNG_VALUE,
				Objects.requireNonNull(this.getClass().getResourceAsStream("/com/gratchev/mizoine/Mizoine-logo" +
						"-transparent.png")));
	}

	@NotNull
	private MockMultipartFile getPdfFile() throws IOException {
		return new MockMultipartFile(
				"test.pdf", "original-test.pdf", MediaType.APPLICATION_PDF_VALUE,
				Objects.requireNonNull(PDFBoxTest.class.getResourceAsStream(PDFBoxTest.APPLE_PDF)));
	}

	private DescriptionResponse updateDescription(final String description) throws IOException {
		controller.updateDescription(project, issue.issueNumber, description);
		final DescriptionResponse response = controller.getDescription(project, issue.issueNumber);
		LOGGER.info("Response: " + response);

		assertEquals(description, response.markdown);
		return response;
	}
}
