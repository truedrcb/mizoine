package com.gratchev.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;

public class HTMLtoMarkdownTest extends HtmlToMarkdownTestBase {
	@BeforeEach
	public void setup() {
		htmLtoMarkdown.skipImg = true;
	}
	
	@Test
	public void parseBrokenHTML() {
		final Document d1 = Jsoup.parse("<form><span>hello</span></div>");
		assertNotNull(d1);
		LOGGER.info(d1.toString());
		assertEquals(1, d1.children().size());
	}

	@Test
	public void parseResourceHTML() throws IOException {
		final Document d1 = Jsoup.parse(HTMLtoMarkdownTest.class.getResourceAsStream("paste-tables-rowspan.html"), null, "");
		assertNotNull(d1);
		LOGGER.info(d1.toString());
		assertEquals(1, d1.children().size());
	}

	
	@Test
	public void convertSimple() {
		// https://stackoverflow.com/questions/10505418/how-to-find-which-library-slf4j-has-bound-itself-to
		final StaticLoggerBinder binder = StaticLoggerBinder.getSingleton();
		System.out.println(binder.getLoggerFactory());
		System.out.println(binder.getLoggerFactoryClassStr());
		
		final Document d1 = Jsoup.parse("<form><span>hello</span></div>");
		assertNotNull(d1);
		LOGGER.info(d1.toString());
		assertEquals(1, d1.children().size());
		
		final String md1 = htmLtoMarkdown.convert(d1);
		assertEquals("hello", md1);
	}
	
	@Test
	public void convertBwithinSpan() {
		whenHtml("<span>Hello <b>markdown</b></span>");
		thenMd("Hello **markdown**");
	}

	@Test
	public void convertBwithinDiv() {
		whenHtml("<div>Hello <b>markdown</b></div>");
		thenMd("Hello **markdown**");
	}

	@Test
	public void hasNoNonLinerIwithinSpan() {
		whenHtml("<span>Hello <i>markdown</i><!-- This comment should be skipped --> in one line</span>");
		assertFalse(HTMLtoMarkdown.hasNestedNonLineNode(d));
		thenMd("Hello *markdown* in one line");
	}

	@Test
	public void hasUlNonLinerBwithinSpan() {
		whenHtml("<span>Hello <b>markdown</b> with list <ul><li>item1</li></ul></span>");
		assertTrue(HTMLtoMarkdown.hasNestedNonLineNode(d));
	}
	
	@Test
	// https://help.github.com/articles/basic-writing-and-formatting-syntax/
	public void nestedEmphasis() {
		whenHtml("<b>This text is <i>extremely</i> important</b>");
		assertFalse(HTMLtoMarkdown.hasNestedNonLineNode(d));
		thenMd("**This text is *extremely* important**");
	}
	
	
	@Test
	public void convertSkipImg() {
		whenHtml("<img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\">");
		thenMd("Mizoine");
	}

	@Test
	public void convertImg() {
		htmLtoMarkdown.skipImg = false;
		whenHtml("<img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\">");
		thenMd("![Mizoine](https://bitbucket.org/truedrcb/mizoine/avatar/32/)");
	}

	@Test
	public void convertSimpleA() {
		whenHtml("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\">Mizoine</a>");
		thenMd("[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
	}

	@Test
	public void convertBrokenAWithNewLineInURL() {
		whenHtml("<a href=\"https://bitbucket.org/truedrcb \r\n/mizoine\" target=\"_blank\">Mizoine</a>");
		thenMd("[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
	}

	
	@Test
	public void convertAWithinForm() {
		whenHtml("<form method='post' action='/something/'><a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\">Mizoine</a></form>");
		thenMd("[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
	}

	@Test
	public void convertImgWithinA() {
		whenHtml("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\"> </a>");
		thenMd("[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
		whenHtml("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" border=\"0\" height=\"28\" width=\"28\"> </a>");
		thenMd("[Mizoine alt](https://bitbucket.org/truedrcb/mizoine)");
		whenHtml("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\"> </a>");
		thenMd("[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
	}

	@Test
	public void convertAWithinBPreserveSpace() {
		whenHtml("Before;  <b><a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\">Mizoine</a></b>. After.");
		
		// TODO: note the space "Before; **[Mizoine](https://bitbucket.org/truedrcb/mizoine)**. After."
		thenMd("Before;**[Mizoine](https://bitbucket.org/truedrcb/mizoine)**. After.");
	}
	
	
	@Test
	public void convertSimpleH1() {
		whenHtml("<h1>Mizoine</h1>Supports headers");
		thenMd("# Mizoine\n\nSupports headers");
	}

	@Test
	public void convertH1H2() {
		whenHtml("<h1>Mizoine</h1><h2>Markdown</h2><p>Supports headers</p>");
		thenMd("# Mizoine\n\n## Markdown\n\nSupports headers");
	}

	@Test
	public void convertHR() {
		whenHtml("<h4>Mizoine</h4><p>Supports horizontal<hr>rulers</p>");
		thenMd("#### Mizoine\n\nSupports horizontal\n\n---\n\nrulers");
	}

	@Test
	public void convertBR() {
		whenHtml("<br><h8>Mizoine</h8><div>Supports horizontal<hr/>rulers.<br>  And line breaks\n\n\nas well.</div>");
		thenMd("######## Mizoine\n\nSupports horizontal\n\n---\n\nrulers.\nAnd line breaks as well.");
	}

	@Test
	public void convertSimpleMultilinePre() {
		// JSoup trims text?
		whenHtml("<pre>\n\n this is *code*\nwith line breaks</pre>");
		thenMd("```\nthis is *code*\nwith line breaks\n```");
	}
	
	@Test
	public void convertMultilinePre() {
		// JSoup trims text?
		whenHtml("<pre>  abc\n\n this is *code*\nwith line breaks</pre>");
		thenMd("```\nabc\n\n this is *code*\nwith line breaks\n```");
	}
	
	@Test
	public void convertSimpleInlinePre() {
		whenHtml("Hello,<pre> this is **code** without line breaks </pre>!");
		thenMd("Hello, `this is **code** without line breaks` !");
	}
	
	@Test
	public void convertSimpleInlineCode() {
		whenHtml("Hello,<code> this is **code** without line breaks </code>!");
		thenMd("Hello, `this is **code** without line breaks` !");
	}
	
	@Test
	public void convertMultilineStrong() {
		whenHtml("<strong>Hello <i>italic</i><blockquote>Block quote</blockquote>All strong skipped</strong>");
		thenMd("Hello *italic*\n"
				+ "> Block quote\n"
				+ "\n"
				+ "All strong skipped");
	}

	@Test
	public void convertMultilineStrongIgnoreBr() {
		whenHtml("<strong>Hello <i>italic</i><br>BR skipped</strong>");
		thenMd("**Hello *italic* BR skipped**");
	}

	@Test
	public void convertMultilineItalicIgnoreHr() {
		whenHtml("<i>Hello italic<hr>HR skipped</i>");
		thenMd("*Hello italic HR skipped*");
	}

	@Test 
	public void convertIndentedListLinksRes() throws IOException {
		whenHtmlRes("paste-indented-list-links.html");
		showMd();
	}

	@Test
	public void convertSimpleUl() {
		whenHtml("Hello list<ul><li>item 1<li> item 2</ul>End");
		thenMd("Hello list\n\n- item 1\n- item 2\n\nEnd");
	}

	@Test
	public void convertNestedUl() {
		whenHtml("Hello list<ul><li>item 1<ul><li>item 1.1<li>item 1.2</ul><li> item 2</ul>End");
		thenMd("Hello list\n\n- item 1\n    - item 1.1\n    - item 1.2\n- item 2\n\nEnd");
	}

	@Test
	public void convertNestedUlDirectInUl() {
		whenHtml("Hello list<ul><li>item 1</li><ul><li>item 1.1<li>item 1.2</ul><li> item 2</ul>End");
		thenMd("Hello list\n\n- item 1\n    - item 1.1\n    - item 1.2\n- item 2\n\nEnd");
	}
	
	@Test
	public void convertNoSeparators() {
		whenHtml("Hello mail link &lt;<a href=mailto:artem@mizoine.info>Artem</a>&gt; End");
		thenMd("Hello mail link &lt;[Artem](mailto:artem@mizoine.info)&gt; End");
	}
	

	@Test
	public void convertBlockquote() {
		whenHtml("Hello block quote<blockquote>first line<br>link <a href=mailto:artem@mizoine.info>Artem</a><br>end quote</blockquote>End");
		thenMd("Hello block quote\n"
				+ "> first line\n"
				+ "> link [Artem](mailto:artem@mizoine.info)\n"
				+ "> end quote\n"
				+ "\n"
				+ "End");
	}

	@Test
	public void convertBlockquoteDivs() {
		whenHtml("Hello block quote<blockquote><div>first line</div><div>link <a href=mailto:artem@mizoine.info>Artem</a></div>end quote</blockquote>End");
		thenMd("Hello block quote\n"
				+ "> \n"
				+ "> first line\n"
				+ "> \n"
				+ "> link [Artem](mailto:artem@mizoine.info)\n"
				+ "> end quote\n"
				+ "\n"
				+ "End");
	}

	@Test
	public void convertRemoveSpace() {
		whenHtml("Hello <b> bold </b> End");
		thenMd("Hello **bold** End");
	}
	
	@Test
	public void convertEmptyB() {
		whenHtml("Hello <b> bold </b> and empty <b>  </b> End");
		thenMd("Hello **bold** and empty End");
	}
	
	@Test
	public void convertPreserveSpaceDueToSeparator() {
		whenHtml("Hello; <b> bold </b> End");

		// TODO: note the space "Hello; **bold** End"
		thenMd("Hello;**bold** End");
	}

	@Test
	public void convertMultipleLineFeeds() {
		whenHtml("<div>Hello <b> bold </b></div><br> <br> End");
		thenMd("Hello **bold**\n\nEnd");
	}

	@Test
	public void convertSeparatedLinks() {
		whenHtml("<a href=http://abc.com>First</a> | <a href=http://def.com>Second</a>");
		thenMd("[First](http://abc.com) &#124; [Second](http://def.com)");
	}

	@Test 
	public void convertErrorNosuchelements() throws IOException {
		whenHtmlRes("paste-error-nosuchelement.html");
		showMd();
	}
}
