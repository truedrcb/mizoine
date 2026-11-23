package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.repositories.MarkdownRenderer.Document;

/** Step 38: Markdown → HTML for the browser (T3.2), with GitHub's features, heading IDs (D23) and safety (D24). */
class MarkdownRendererTest {

	private static String html(final String markdown) {
		return MarkdownRenderer.render(markdown, "doc.md").html();
	}

	// heading IDs (D23): GitHub's rules, so links written for GitHub keep working

	@Test
	void headingsGetGitHubStyleIds() {
		assertThat(html("## Costs and Risks")).contains("<h2 id=\"costs-and-risks\">Costs and Risks</h2>");
	}

	@Test
	void repeatedHeadingsAreNumbered() {
		assertThat(html("# Notes\n\n# Notes\n\n# Notes"))
				.contains("id=\"notes\"").contains("id=\"notes-1\"").contains("id=\"notes-2\"");
	}

	@Test
	void nonAsciiLettersStayInIds() {
		assertThat(html("## Über uns")).contains("id=\"über-uns\"");
		assertThat(html("## Мой дом")).contains("id=\"мой-дом\"");
	}

	@Test
	void punctuationIsRemovedFromIds() {
		assertThat(html("## What's new? (2026)")).contains("id=\"whats-new-2026\"");
	}

	// relative references stay as written: the browser resolves them against the document's folder

	@Test
	void picturesAndLinksAreKeptAsWritten() {
		final String html = html("![Diagram](images/a.png)\n\n[Part](other.md#part) [Folder](sub/) [Up](../x.md)");

		assertThat(html)
				.contains("<img src=\"images/a.png\" alt=\"Diagram\" />")
				.contains("href=\"other.md#part\">Part</a>")
				.contains("href=\"sub/\">Folder</a>")
				.contains("href=\"../x.md\">Up</a>");
	}

	@Test
	void linkToAHeadingOnTheSamePageMatchesItsId() {
		assertThat(html("[see](#costs-and-risks)\n\n## Costs and Risks"))
				.contains("href=\"#costs-and-risks\"").contains("id=\"costs-and-risks\"");
	}

	// GitHub's Markdown features

	@Test
	void tables() {
		assertThat(html("| a | b |\n|---|---|\n| 1 | 2 |")).contains("<table>").contains("<td>1</td>");
	}

	@Test
	void strikethroughTaskListsAndAutolinks() {
		assertThat(html("~~gone~~")).contains("<del>gone</del>");
		assertThat(html("- [x] done\n- [ ] open")).contains("checkbox").contains("checked");
		assertThat(html("see https://example.org")).contains("href=\"https://example.org\">https://example.org</a>");
	}

	@Test
	void fencedCodeKeepsItsLanguageForHighlightingLater() {
		assertThat(html("```java\nint x = 1;\n```")).contains("<code class=\"language-java\">int x = 1;");
	}

	// safety (D24): the Markdown is not trusted

	@Test
	void htmlInsideMarkdownIsShownAsText() {
		final String html = html("<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>");

		assertThat(html).doesNotContain("<script").doesNotContain("<img src=x").contains("&lt;script&gt;");
	}

	@Test
	void unsafeLinkSchemesAreDropped() {
		assertThat(html("[x](javascript:alert(1)) ![y](javascript:alert(2))")).doesNotContain("javascript:");
	}

	// title

	@Test
	void titleIsTheFirstHeading() {
		final Document document = MarkdownRenderer.render("Intro\n\n## Plan *2026*\n\n# Later", "plan.md");

		assertThat(document.title()).isEqualTo("Plan 2026");
	}

	@Test
	void titleWithoutHeadingIsTheFileName() {
		assertThat(MarkdownRenderer.render("just text", "notes.md").title()).isEqualTo("notes.md");
	}

	@Test
	void emptyFirstHeadingFallsBackToTheFileName() {
		assertThat(MarkdownRenderer.render("#\n\ntext", "notes.md").title()).isEqualTo("notes.md");
	}

	@Test
	void textStaysAsWritten() {
		assertThat(html("мой дом - Über")).contains("<p>мой дом - Über</p>");
	}
}
