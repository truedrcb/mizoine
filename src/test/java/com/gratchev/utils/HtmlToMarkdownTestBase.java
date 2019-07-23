package com.gratchev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HtmlToMarkdownTestBase {

	protected static final Logger LOGGER = LoggerFactory.getLogger(HtmlToMarkdownTestBase.class);
	private String html;
	protected Document d;
	private String md;
	protected HTMLtoMarkdown htmLtoMarkdown = new HTMLtoMarkdown();

	public HtmlToMarkdownTestBase() {
		super();
	}

	protected void whenHtml(final String htmlToConvert) {
		this.html = htmlToConvert;
		d = Jsoup.parse(this.html);
		assertThat(d).as(htmlToConvert).isNotNull();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(d.toString());
		}
		assertEquals(1, d.children().size());
	}

	protected void whenHtmlRes(final String fileName) throws IOException {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Loading resource: " + fileName);
		}
		d = Jsoup.parse(HTMLtoMarkdownTest.class.getResourceAsStream(fileName), "UTF-8" , "");
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(d.toString());
		}
		assertEquals(1, d.children().size());
	}

	protected void thenMd(final String expectedMd) {
		assertThat(d).as("Call 'whenHtml' before calling 'thenMd'").isNotNull();
		md = htmLtoMarkdown.convert(d);
		
		assertEquals(expectedMd, md);
		
		// Just for fun (and for sequential calls to 'wnen/then')
		d = null;
		html = null;
	}

	protected void showMd() {
		assertThat(d).as("Call 'whenHtml' before calling 'thenMd'").isNotNull();
		md = htmLtoMarkdown.convert(d);
	}

}