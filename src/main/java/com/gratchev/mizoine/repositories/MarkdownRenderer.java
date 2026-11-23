package com.gratchev.mizoine.repositories;

import java.util.List;
import java.util.Optional;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * Markdown → HTML for the browser (step 38, T3.2): CommonMark with GitHub's tables, strikethrough, task lists and
 * autolinks. A pure function: no files, no requests.
 * <ul>
 * <li>Every heading gets an {@code id} from its text, as GitHub makes them (D23), so {@code document.md#topic}
 * works.</li>
 * <li>The Markdown is not trusted (D24): HTML written in it is shown as text, unsafe link schemes are dropped.</li>
 * <li>Links and pictures stay as written: relative ones are resolved by the browser against the document's
 * folder.</li>
 * </ul>
 */
final class MarkdownRenderer {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
			StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create(),
			HeadingAnchorExtension.create());
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
	private static final HtmlRenderer HTML = HtmlRenderer.builder().extensions(EXTENSIONS)
			.escapeHtml(true)
			.sanitizeUrls(true)
			.build();
	private static final TextContentRenderer TEXT = TextContentRenderer.builder().build();

	/**
	 * @param title the first heading as plain text, or the file name if there is none
	 * @param html the rendered body, safe to insert into a page
	 */
	record Document(String title, String html) {
	}

	private MarkdownRenderer() {
	}

	static Document render(final String markdown, final String fileName) {
		final Node document = PARSER.parse(markdown);
		final String title = firstHeading(document).map(TEXT::render).map(String::strip)
				.filter(text -> !text.isEmpty()).orElse(fileName);
		return new Document(title, HTML.render(document));
	}

	private static Optional<Heading> firstHeading(final Node document) {
		final Heading[] first = new Heading[1];
		document.accept(new AbstractVisitor() {
			@Override
			public void visit(final Heading heading) {
				if (first[0] == null) {
					first[0] = heading;
				}
			}
		});
		return Optional.ofNullable(first[0]);
	}
}
