package com.gratchev.mizoine.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.gratchev.mizoine.repositories.TextFile;

/**
 * Step 68, D73: a file is editable when its name is a known text format, from the one list the editor reads too
 * ({@code ui/editor/text-formats.json}); by name only, no file is read.
 */
class TextFormatsTest {

	private static final TextFormats FORMATS = TextFormats.STANDARD;

	@ParameterizedTest
	@CsvSource({
		"plan.md, markdown", "README.markdown, markdown", "data.json, json", "config.yaml, yaml", "config.yml, yaml",
		"page.html, html", "page.htm, html", "style.css, css", "app.js, javascript", "app.mjs, javascript",
		"app.cjs, javascript", "app.ts, javascript", "pom.xml, xml", "icon.svg, xml", "schema.xsd, xml",
		"style.xsl, xml", "style.xslt, xml", "page.xhtml, xml", "Main.java, java", "app.properties, properties",
		"run.sh, shell", "run.bash, shell",
		"notes.txt, text", "table.csv, text", "table.tsv, text", "server.log, text", "doc.adoc, text", "doc.rst, text",
		"run.bat, text", "run.cmd, text", "run.ps1, text", "app.py, text", "pyproject.toml, text", "app.ini, text",
		"app.cfg, text", "nginx.conf, text", "query.sql, text", "main.c, text", "main.h, text", "main.cpp, text",
		"Main.cs, text", "Main.kt, text", "build.gradle, text", "main.go, text", "main.rs, text", "app.rb, text",
		"paper.tex, text", "Dockerfile, text", "Makefile, text", ".tags, text"})
	void knownTextFormatsAreEditableWithTheirLanguage(final String name, final String language) {
		assertThat(FORMATS.isEditable(name)).as(name).isTrue();
		assertThat(FORMATS.language(name)).as(name).hasValue(language);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PLAN.MD", "Data.Json", "a.md"})
	void theExtensionCountsInAnyCase(final String name) {
		assertThat(FORMATS.isEditable(name)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"photo.png", "photo.jpg", "doc.pdf", "data.bin", "archive.tar.gz", "LICENSE", "README",
		"makefile", "config.yaml.example", ".md", "md", "plan.", ""})
	void everythingElseIsNotEditable(final String name) {
		assertThat(FORMATS.isEditable(name)).as(name).isFalse();
		assertThat(FORMATS.language(name)).as(name).isEmpty();
	}

	/** D21: the largest text file, the same for the editor and for the server's text reading. */
	@Test
	void textFilesAreAtMostTwoMegabytes() {
		assertThat(FORMATS.maxBytes()).isEqualTo(2L * 1024 * 1024);
		assertThat(TextFile.MAX_BYTES).isEqualTo(FORMATS.maxBytes());
	}
}
