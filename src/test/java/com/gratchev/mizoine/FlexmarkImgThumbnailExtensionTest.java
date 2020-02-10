package com.gratchev.mizoine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.FlexmarkImgThumbnailExtension.LinkTemplate;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.builder.Extension;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class FlexmarkImgThumbnailExtensionTest {
	static String commonMark(String markdown) {
		

//		FlexmarkImgThumbnailExtension.LINK_TEMPLATES.add(new LinkTemplate("note:", "http://test.com/notes/{1}/sub/{0}", null, true));
//		FlexmarkImgThumbnailExtension.LINK_TEMPLATES.add(new LinkTemplate("note", "http://test.com/notes/{0}", null, true));
		
		MutableDataHolder options = new MutableDataSet();
		options.set(Parser.EXTENSIONS, Arrays.asList(new Extension[] { 
				AutolinkExtension.create(), 
				FlexmarkImgThumbnailExtension.create(),
				}));

		// change soft break to hard break
		options.set(HtmlRenderer.SOFT_BREAK, "<br/>");
		
		options.set(FlexmarkImgThumbnailExtension.TEMPLATES, Arrays.asList(new LinkTemplate[] { 
				new LinkTemplate("issue", "/issue/{0}", "text-primary", false),
				new LinkTemplate("note-", "http://test.com/notes/{1}/sub/{0}", null, true), 
				new LinkTemplate("note", "http://test.com/notes/{0}", null, true),
				}));

		Parser parser = Parser.builder(options).build();
		Node document = parser.parse(markdown);
		HtmlRenderer renderer = HtmlRenderer.builder(options).build();
		final String html = renderer.render(document);
		System.out.println(html); 
		return html;
	}

	@Test
	public void testImg() {
		final String html = commonMark("hello ![Test](imgurl.jpg) image");
		assertEquals("<p>hello <img src=\"imgurl.jpg\" alt=\"Test\" class=\"miz-md-img\" /> image</p>\n", html);
		
	}

	@Test
	public void testImgRef() {
		final String html = commonMark("hello ![Test][ABC123.lg] image\n\n[ABC123.lg]: /attachments/image1.jpg");
		assertEquals("<p>hello <img src=\"/attachments/image1.jpg\" alt=\"Test\" class=\"miz-md-img\" miz-ref=\"ABC123.lg\" /> image</p>\n", html);
		
	}

	@Test
	public void testImgRefThumbnail() {
		final String html = commonMark("hello ![Test][ABC123] image\n\n[ABC123]: /attachments/.mizoine/image1-thumbnail.jpg");
		assertEquals("<p>hello <img src=\"/attachments/.mizoine/image1-thumbnail.jpg\" alt=\"Test\" class=\"miz-md-thumbnail\" miz-ref=\"ABC123\" /> image</p>\n", html);
	}

	@Test
	public void testIcon1() {
		final String html = commonMark("hello :!: icon");
		assertEquals("<p>hello <i class=\"fas fa-exclamation-triangle text-warning\"></i> icon</p>\n", html);
		
	}

	@Test
	public void testIssueLink() {
		final String html = commonMark("hello [DEV-45](issue)");
		assertEquals("<p>hello <a href=\"/issue/DEV-45\" class=\"text-primary\">DEV-45</a></p>\n", html);
	}
	
	@Test
	public void testCustomUrl1() {
		final String html = commonMark("hello [12345](note)");
		assertEquals("<p>hello <a href=\"http://test.com/notes/12345\" target=\"_blank\">12345</a></p>\n", html);
	}

	@Test
	public void testCustomUrl2() {
		final String html = commonMark("hello [12345](note-XY)");
		assertEquals("<p>hello <a href=\"http://test.com/notes/XY/sub/12345\" target=\"_blank\">12345</a></p>\n", html);
	}

	@Test
	public void testExternalLink() {
		final String html = commonMark("hello [external](https://ya.ru)");
		assertEquals("<p>hello <a href=\"https://ya.ru\" target=\"_blank\">external</a></p>\n", html);
	}

	@Test
	public void testInternalLink() {
		final String html = commonMark("hello [internal](/attachments/a/b)");
		assertEquals("<p>hello <a href=\"/attachments/a/b\">internal</a></p>\n", html);
	}

	@Test
	public void testExternalAutoLink() {
		final String html = commonMark("hello https://ya.ru");
		assertEquals("<p>hello <a href=\"https://ya.ru\" target=\"_blank\">https://ya.ru</a></p>\n", html);
	}
}
