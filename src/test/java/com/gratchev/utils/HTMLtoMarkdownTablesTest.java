package com.gratchev.utils;

import static org.junit.Assert.*;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

public class HTMLtoMarkdownTablesTest extends HtmlToMarkdownTestBase {
	@Before
	public void setup() {
		htmLtoMarkdown.skipImg = true;
	}

	@Test
	public void convertSimpleTable() {
		whenHtml("<table><tr><td>one cell</td></tr></table>Hello");
		thenMd("| ---- |\n| one cell |\n\nHello");
	}

	@Test
	public void skipHrWithinSimpleTable() {
		whenHtml("start<table><tr><td><hr/>one cell</td></tr></table>Hello");
		thenMd("start\n\n| ---- |\n| one cell |\n\nHello");
	}

	@Test
	public void keepHrOutsideSimpleTable() {
		whenHtml("start<hr/><table><tr><td>one cell</td></tr></table>Hello");
		thenMd("start\n\n---\n\n| ---- |\n| one cell |\n\nHello");
	}
	
	@Test
	public void convertTable2x1() {
		whenHtml("<table><tr><td>first cell</td><td>second cell</td></tr></table>");
		thenMd("| ---- | ---- |\n| first cell | second cell |");
	}
	
	@Test
	public void convertTable2x2() {
		whenHtml("<h1>Table sample</h1><table>"
				+ "<tr><td>first cell</td><td>second cell</td></tr>"
				+ "<tr><td>third cell</td><td>fourth cell</td></tr>"
				+ "</table>");
		thenMd("# Table sample\n\n| ---- | ---- |\n"
				+ "| first cell | second cell |\n"
				+ "| third cell | fourth cell |"
				);
	}

	@Test
	public void convertTable3x2() {
		whenHtml("<h1>Table sample</h1>Here comes a table<table>"
				+ "<tr><td>first cell</td><td>second </td><td>cell</td></tr>"
				+ "<tr><td>third cell</td><td>fourth</td><td> cell</td></tr>"
				+ "</table>");
		thenMd("# Table sample\n\n"
				+ "Here comes a table\n\n"
				+ "| ---- | ---- | ---- |\n"
				+ "| first cell | second | cell |\n"
				+ "| third cell | fourth | cell |"
				);
	}

	@Test
	public void convertTable3x2tbody() {
		whenHtml("<h1>Table sample</h1>Here comes a table<table>"
				+ "<tr><td>first cell</td><td>second </td><td>cell</td></tr>"
				+ "<tr><td>third cell</td><td>fourth</td><td> cell</td></tr>"
				+ "</table>");
		thenMd("# Table sample\n\n"
				+ "Here comes a table\n\n"
				+ "| ---- | ---- | ---- |\n"
				+ "| first cell | second | cell |\n"
				+ "| third cell | fourth | cell |"
				);
	}

	@Test
	public void convertTable2x1withDivs() {
		whenHtml("<b>Table test</b><table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table>");
		thenMd("**Table test**\n\n"
				+ "| ---- | ---- |\n"
				+ "| first cell | second cell |");
	}

	@Test
	public void convertTable2x1withDivsInTable() {
		whenHtml("<table><tr><td><b>Table test</b><table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table></td></tr></table>");
		thenMd("**Table test**\n\n"
				+ "| ---- | ---- |\n"
				+ "| first cell | second cell |");
	}

	@Test
	public void convertTable2x1withDivsInTables() {
		whenHtml("<table><tr><td>a header</td></tr><tr><td>"
					+ "<table><tr><td><b>Table test</b></td></tr><tr><td>"
						+ "<table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table>"
					+ "</td></tr></table>"
				+ "</td></tr></table>");
		thenMd("a header\n\n**Table test**\n\n"
				+ "| ---- | ---- |\n"
				+ "| first cell | second cell |");
	}

	@Test
	public void convertTable2x1ignoringBrAndHrInCell() {
		whenHtml("<table><tr><td>a header</td></tr><tr><td>"
					+ "<table><tr><td><b>Table test</b></td></tr><tr><td>"
						+ "<table><tr><td><br/>first<hr/>cell<br/></td><td>second<br/>\n"
						+ "cell</td></tr></table>"
					+ "</td></tr></table>"
				+ "</td></tr></table>");
		thenMd("a header\n\n**Table test**\n\n"
				+ "| ---- | ---- |\n"
				+ "| first cell | second cell |");
	}

	@Test 
	public void convertTableRes() throws IOException {
		whenHtmlRes("paste-tables.html");
		showMd();
	}

	@Test 
	public void convertInheritedTableRes() throws IOException {
		whenHtmlRes("paste-inherited-tables.html");
		showMd();
	}

	@Test 
	public void convertSimpleTableRes() throws IOException {
		whenHtmlRes("paste-simple-table.html");
		thenMd("| ---- | ---- |\n" + 
				"| **Muster Firma** Musterweg 123 68794 Oberhausen-Rheinhausen Deutschland |\n" + 
				"| Telefon | 02345 - 24446667 |\n" + 
				"| Telefax | 012345 - 22 44 6666 |\n" + 
				"| [info@muster-seite.de](mailto:info@muster-seite.de) |");
	}

	@Test 
	public void convertTable6x2() throws IOException {
		whenHtmlRes("paste-table6x2.html");
		showMd();
	}
}
