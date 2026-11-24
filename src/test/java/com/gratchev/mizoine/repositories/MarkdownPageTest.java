package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 38, D21, D22: which requests get the rendered page, and which files can be shown as one. */
class MarkdownPageTest {

	private static final String BROWSER = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

	private final Path dir;

	MarkdownPageTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	// D22: rendered only when a person's browser asks for HTML

	@ParameterizedTest
	@ValueSource(strings = {BROWSER, "text/html", "text/html;q=0.5,text/markdown;q=0.4"})
	void browserNavigationGetsThePage(final String accept) {
		assertThat(MarkdownPage.isWanted(accept, null, false)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"*/*", "text/markdown", "application/json", "image/*", "text/html;q=0", "not a type"})
	void everythingElseGetsTheRawFile(final String accept) {
		assertThat(MarkdownPage.isWanted(accept, null, false)).isFalse();
	}

	@Test
	void noAcceptHeaderGetsTheRawFile() {
		// curl, wget: they expect the bytes
		assertThat(MarkdownPage.isWanted(null, null, false)).isFalse();
	}

	@Test
	void rawParameterGetsTheRawFile() {
		assertThat(MarkdownPage.isWanted(BROWSER, null, true)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"Bearer abc", "bearer abc", "BEARER abc"})
	void agentsWithAnOauthTokenNeverGetThePage(final String authorization) {
		assertThat(MarkdownPage.isWanted(BROWSER, authorization, false)).isFalse();
	}

	@Test
	void httpBasicInTheBrowserStillGetsThePage() {
		assertThat(MarkdownPage.isWanted(BROWSER, "Basic YXJ0ZW06eA==", false)).isTrue();
	}

	// which files: Markdown (and readable as text, TextFileTest)

	@Test
	void onlyMarkdownFilesAreRendered() {
		assertThat(MarkdownPage.isMarkdown(Path.of("a/plan.md"))).isTrue();
		assertThat(MarkdownPage.isMarkdown(Path.of("a/plan.markdown"))).isTrue();
		assertThat(MarkdownPage.isMarkdown(Path.of("a/plan.txt"))).isFalse();
		assertThat(MarkdownPage.isMarkdown(Path.of("a/page.html"))).isFalse();
	}
}
