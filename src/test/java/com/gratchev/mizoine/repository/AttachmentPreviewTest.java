package com.gratchev.mizoine.repository;

import com.gratchev.mizoine.ShortIdGenerator;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.repository.Repository.AttachmentProxy;
import com.gratchev.mizoine.repository.Repository.IssueProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.Date;

import static com.gratchev.mizoine.repository.TempRepositoryUtils.assertDirExists;
import static com.gratchev.mizoine.repository.TempRepositoryUtils.assertFileExists;
import static org.assertj.core.api.Assertions.assertThat;

public class AttachmentPreviewTest {
	TempRepository repo;
	final ZonedDateTime now = ZonedDateTime.now();

	@BeforeEach
	void setup() throws IOException {
		repo = TempRepository.create();
	}

	@AfterEach
	void tearDown() throws IOException {
		repo.dispose();
	}

	@Test
	void testUpload1() throws IOException {
		final IssueProxy issue = repo.issue("ART", "16");
		issue.createDirs();
		try (final InputStream pngStream = getPngStream()) {
			issue.uploadAttachment(new MockMultipartFile("flyer.png", "FlyerBear3web.png", MediaType.IMAGE_PNG_VALUE,
					pngStream), ZonedDateTime.now());
		}
	}

	private InputStream getPngStream() {
		return AttachmentPreviewTest.class.getResourceAsStream("/com/gratchev/utils/FlyerBear3web.png");
	}

	@Test
	void testPreview1() throws IOException {
		final AttachmentProxy attachment = repo.attachment("PRO", "1", ShortIdGenerator.mizCodeFor(now));
		attachment.createDirs();
		try (final InputStream pngStream = getPngStream(); final OutputStream os =
				new FileOutputStream(new File(attachment.getRoot(), "test.png"))) {
			pngStream.transferTo(os);
		}
		attachment.updatePreview();
		assertPreviewsExist(attachment);

		// idempotency
		attachment.updatePreview();
		assertPreviewsExist(attachment);
	}

	private void assertPreviewsExist(final AttachmentProxy attachment) {
		final File mizDir = assertDirExists(repo.getRootMizoineDir(), "PRO", "1", attachment.getAttachmentId());
		assertFileExists(mizDir, "preview.jpg");
		assertFileExists(mizDir, "thumbnail.jpg");
		assertFileExists(mizDir, "original.png");
	}

	@Test
	void testMultipleAttachments() throws IOException {
		for (int i = 0; i < 10; i++) {
			final ZonedDateTime date = now.minusHours(i);
			final String title = "Attachment " + i;
			final AttachmentProxy attachment = repo.attachment("PRO", "1", ShortIdGenerator.mizCodeFor(date));
			attachment.createDirs();
			attachment.updateMeta(meta -> {
				meta.creationDate = Date.from(date.toInstant());
				meta.title = title;
			}, new SignedInUserComponentMock());
		}
		
		assertThat(repo.issue("PRO", "1").readAttachments()).hasSize(10);
	}

}
