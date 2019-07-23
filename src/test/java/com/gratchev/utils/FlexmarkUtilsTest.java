package com.gratchev.utils;

import static com.gratchev.mizoine.FlexmarkUtils.generateMarkdownFooterRefs;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.FlexmarkUtils;
import com.gratchev.mizoine.api.AttachmentApiController;

public class FlexmarkUtilsTest {

	@Test
	void testQuotationEscape() {
		final String escaped = FlexmarkUtils.escapeQuotation("test \"fancy\"");
		assertThat(escaped).isEqualTo("test \\\"fancy\\\"");

		assertThat(FlexmarkUtils.escapeQuotation(null)).isNull();
	}

	@Test
	void testGenerateRefs() {
		assertThat(generateMarkdownFooterRefs("DEV", "3", Set.of())).isBlank();

		assertThat(generateMarkdownFooterRefs("DEV", "3", Set.of("aaa11")))
				.startsWith("\n\n")
				.contains("\n[aaa11]: /attachments/.mizoine/DEV/3/aaa11/thumbnail.jpg\n")
				.contains("\n[aaa11.lg]: /attachments/.mizoine/DEV/3/aaa11/preview.jpg\n")
				.contains("\n[aaa11.page]: " + AttachmentApiController.getPageBaseUri("DEV", "3") + "aaa11" + "\n");

		assertThat(generateMarkdownFooterRefs("HOME", "11", Set.of("aaa22", "33cde")))
				.startsWith("\n\n")
				.contains("\n[aaa22]: /attachments/.mizoine/HOME/11/aaa22/thumbnail.jpg\n")
				.contains("\n[aaa22.lg]: /attachments/.mizoine/HOME/11/aaa22/preview.jpg\n")
				.contains("\n[aaa22.page]: " + AttachmentApiController.getPageBaseUri("HOME", "11") + "aaa22" + "\n")
				.contains("\n[33cde]: /attachments/.mizoine/HOME/11/33cde/thumbnail.jpg\n")
				.contains("\n[33cde.lg]: /attachments/.mizoine/HOME/11/33cde/preview.jpg\n")
				.contains("\n[33cde.page]: " + AttachmentApiController.getPageBaseUri("HOME", "11") + "33cde" + "\n");
	}

	@Test
	void testMentAllPaths() {
		assertThat(FlexmarkUtils.mentThumbnailPath("DEV", "15", "abc12"))
				.isEqualTo("/attachments/.mizoine/DEV/15/abc12/thumbnail.jpg");
		assertThat(FlexmarkUtils.mentPreviewPath("TEST", "G0", "abc12"))
				.isEqualTo("/attachments/.mizoine/TEST/G0/abc12/preview.jpg");
		assertThat(FlexmarkUtils.mentPagePath("HOME", "33", "34def"))
				.isEqualTo(AttachmentApiController.getPageBaseUri("HOME", "33") + "34def");
	}
}
