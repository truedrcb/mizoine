package com.gratchev.mizoine;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.builder.Extension;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class ImgSrcExtensionTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImgSrcExtensionTest.class);

	static String commonMark(String markdown) {
		MutableDataHolder options = new MutableDataSet();
		options.set(Parser.EXTENSIONS, Arrays.asList(new Extension[] { 
				AutolinkExtension.create(), 
				ImgSrcExtension.create(),
				}));

		// change soft break to hard break
		options.set(HtmlRenderer.SOFT_BREAK, "<br/>");

		Parser parser = Parser.builder(options).build();
		Node document = parser.parse(markdown);
		HtmlRenderer renderer = HtmlRenderer.builder(options).build();
		final String html = renderer.render(document);
		return html;
	}

	@Test
	public void test() {
		//ImgSrcExtension cut = new ImgSrcExtension();

		String html = commonMark("http://github.com/vsch/flexmark-java");
		LOGGER.info(html); // output: <p><a href="http://github.com/vsch/flexmark-java" class="my-autolink-class">http://github.com/vsch/flexmark-java</a></p>

		html = commonMark("hello\nworld");
		LOGGER.info(html); // output: <p>hello<br/>world</p>

		html = commonMark("hello ![Test](imgurl.jpg) image");
		LOGGER.info(html); 

		html = commonMark("hello ![Test number two][aCfff] image\n\n[aCfff]: /attachments/bcc/image.jpg \"Optional title\"");
		LOGGER.info(html); 

		html = commonMark("hello ![Test number 3][aCff_-] image\n\n[lnk1]: http://drcb.ru\n\n[aCff_-]: /attachments/bcc/image2.jpg \"Optional title 2\"");
		LOGGER.info(html); 

		assertEquals("<p>hello<br/>world</p>\n", commonMark("hello\nworld"));
	}

}
