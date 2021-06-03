package com.gratchev.mizoine.api;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.api.BaseDescriptionController.DescriptionResponse;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.mizoine.repository.meta.AttachmentMeta;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
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
		final MockMultipartFile pngFile = getPngFile();
		attachment = repo.issue(project, issue.issueNumber).uploadAttachment("original-test.png",
				pngFile.getContentType(), 12345, creationDate, pngFile::transferTo);
		repo.attachment(project, issue.issueNumber, attachment.id).updatePreview();

		assertNotNull(attachment);
		assertNotNull(attachment.id);
		assertNotNull(attachment.meta);
		assertThat(attachment.meta.fileName).isEqualTo("original-test.png");
		assertThat(attachment.meta.creationDate).hasYear(creationDate.getYear()).hasMonth(creationDate.getMonthValue())
				.hasDayOfMonth(creationDate.getDayOfMonth()).hasHourOfDay(creationDate.getHour())
				.hasMinute(creationDate.getMinute()).hasSecond(creationDate.getSecond());
		assertNotNull(attachment.meta.upload);
		assertThat(attachment.meta.upload.size).isEqualTo(12345);

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
		request.addFile(getJpgFile());
		request.addFile(getPngFile());
		request.addFile(getPdfFile("original-test.pdf"));
		final List<String> attachmentIds = controller.uploadAttachment(project, issue.issueNumber, request);
		final int mizNow = mizCodeToInt(mizCodeFor(ZonedDateTime.now()));
		assertThat(attachmentIds).hasSize(3).allSatisfy(id -> {
			assertThat(id).isNotBlank();
			assertThat(mizCodeToInt(id)).isBetween(mizNow - 1, mizNow + 3);
		});
	}

	@Test
	void uploadDatedAttachment() throws IOException {
		Files.writeString(repo.getMetaFile().toPath(),
				"{\"uploadFilenameDateTemplates\":[\".*{yyyy}_{MM}_{dd}.*\",\".*_{dd}\\\\.{MM}\\\\.{yyyy}_.*\"]}",
				StandardCharsets.UTF_8);

		final String fileName = "Depotauszug_vom_21.05.2017_zu_Depot_1234567_-_202104020987JD65.pdf";
		final Date date = repo.extractDateFromFilename(fileName);
		assertThat(date).hasYear(2017).hasMonth(5).hasDayOfMonth(21);

		final MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
		request.addFile(getPdfFile(fileName));
		final List<String> attachmentIds = controller.uploadAttachment(project, issue.issueNumber, request);
		assertThat(attachmentIds).hasSize(1).allSatisfy(id -> {
			assertThat(id).isNotBlank();
			final AttachmentMeta meta = repo.attachment(project, issue.issueNumber, id).readMeta();
			assertThat(meta.fileName).isEqualTo(fileName);
			assertThat(meta.creationDate).hasYear(2017).hasMonth(5).hasDayOfMonth(21);
			final int miz = mizCodeToInt(mizCodeFor(ZonedDateTime.ofInstant(meta.creationDate.toInstant(),
					ZoneId.systemDefault())));
			assertThat(mizCodeToInt(id)).isBetween(miz - 1, miz + 1);
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
	private MockMultipartFile getPdfFile(final String originalFilename) throws IOException {
		return new MockMultipartFile(
				"test.pdf", originalFilename, MediaType.APPLICATION_PDF_VALUE,
				Objects.requireNonNull(PDFBoxTest.class.getResourceAsStream(PDFBoxTest.APPLE_PDF)));
	}

	@NotNull
	private MockMultipartFile getJpgFile() throws IOException {
		return new MockMultipartFile(
				"test.jpg", "original-test.jpg", MediaType.IMAGE_PNG_VALUE,
				Objects.requireNonNull(this.getClass().getResourceAsStream("/com/gratchev/utils/20190310_174614.jpg")));
	}

	private DescriptionResponse updateDescription(final String description) throws IOException {
		controller.updateDescription(project, issue.issueNumber, description);
		final DescriptionResponse response = controller.getDescription(project, issue.issueNumber);
		LOGGER.info("Response: " + response);

		assertEquals(description, response.markdown);
		return response;
	}
}
