package com.gratchev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gratchev.utils.HTMLtoMarkdown.FlatMDBuilder;
import com.gratchev.utils.HTMLtoMarkdown.MDNode;

public class HTMLtoMarkdownTablesTest extends HtmlToMarkdownTestBase {
	@BeforeEach
	public void setup() {
		htmLtoMarkdown.skipImg = true;
	}

	@Test
	public void convertSimpleTable() {
		whenHtml("<table><tr><td>cell 1</td></tr><tr><td>cell 2</td></tr></table>Hello");
		thenMd("| cell 1 |\n| ---- |\n| cell 2 |\n\nHello");
	}

	@Test
	public void skipHrWithinSimpleTable() {
		whenHtml("start<table><tr><td><hr/>cell with hr</td><td>cell 2</td></tr></table>Hello");
		thenMd("start\n\n"
				+ "| cell with hr | cell 2 |\n"
				+ "| ---- | ---- |\n"
				+ "\nHello");
	}

	@Test
	public void keepHrOutsideSimpleTable() {
		whenHtml("start<hr/><table><tr><td>cell 1</td><td>cell 2</td></tr></table>Hello");
		thenMd("start\n\n---\n\n"
				+ "| cell 1 | cell 2 |\n"
				+ "| ---- | ---- |\n"
				+ "\nHello");
	}
	
	@Test
	public void convertTable2x1() {
		whenHtml("<table><tr><td>first cell</td><td>second cell</td></tr></table>");
		thenMd("| first cell | second cell |\n| ---- | ---- |");
	}
	
	@Test
	public void convertTable2x2() {
		whenHtml("<h1>Table sample</h1><table>"
				+ "<tr><td>first cell</td><td>second cell</td></tr>"
				+ "<tr><td>third cell</td><td>fourth cell</td></tr>"
				+ "</table>");
		thenMd("# Table sample\n\n"
				+ "| first cell | second cell |\n"
				+ "| ---- | ---- |\n"
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
				+ "| first cell | second | cell |\n"
				+ "| ---- | ---- | ---- |\n"
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
				+ "| first cell | second | cell |\n"
				+ "| ---- | ---- | ---- |\n"
				+ "| third cell | fourth | cell |"
				);
	}

	@Test
	public void convertTable2x1withDivs() {
		whenHtml("<b>Table test</b><table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table>");
		thenMd("**Table test**\n\n"
				+ "| first cell | second cell |\n"
				+ "| ---- | ---- |"
				);
	}

	@Test
	public void convertTable2x1withDivsInTable() {
		whenHtml("<table><tr><td><b>Table test</b><table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table></td></tr></table>");
		thenMd("**Table test**\n\n"
				+ "| first cell | second cell |\n"
				+ "| ---- | ---- |"
				);
	}

	@Test
	public void convertTable2x1withDivsInTables() {
		whenHtml("<table><tr><td>a header</td></tr><tr><td>"
					+ "<table><tr><td><b>Table test</b></td></tr><tr><td>"
						+ "<table><tr><td><div>first cell</div></td><td><div>second</div><p>cell</p></td></tr></table>"
					+ "</td></tr></table>"
				+ "</td></tr></table>");
		thenMd("a header\n\n**Table test**\n\n"
				+ "| first cell | second cell |\n"
				+ "| ---- | ---- |");
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
				+ "| first cell | second cell |\n"
				+ "| ---- | ---- |");
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
		thenMd(
				"| **Muster Firma** Musterweg 123 68794 Oberhausen-Rheinhausen Deutschland |\n" +
				"| ---- | ---- |\n" +
				"| Telefon | 02345 - 24446667 |\n" + 
				"| Telefax | 012345 - 22 44 6666 |\n" + 
				"| [info@muster-seite.de](mailto:info@muster-seite.de) |");
	}

	@Test 
	public void convertTable6x2() throws IOException {
		whenHtmlRes("paste-table6x2.html");
		showMd();
	}
	
	@Test
	public void removeEmptyCell() {
		whenHtml("<table><tr><td>cell 1</td><td> </td><td>cell 2</td></tr></table>Hello");
		thenMd("| cell 1 | cell 2 |\n| ---- | ---- |\n\nHello");
	}

	@Test
	public void removeEmptyCellInRow() {
		whenHtml("<table><tr><td>cell 1</td><td>cell 2</td></tr><tr><td> </td></tr></table>Hello");
		thenMd("| cell 1 | cell 2 |\n| ---- | ---- |\n\nHello");
	}

	final MDNode emptyNode = new MDNode() {
		
		@Override
		public boolean isEmpty() {
			return true;
		}
		
		@Override
		public void build(FlatMDBuilder builder) {
			builder.text(" ");
		}
	};
	final MDNode notEmptyNode = new MDNode() {
		
		@Override
		public boolean isEmpty() {
			return false;
		}
		
		@Override
		public void build(FlatMDBuilder builder) {
			builder.text("X");
		}
	};
	
	private ArrayList<ArrayList<MDNode>> genNodeTable(final String... rows) {
		final ArrayList<ArrayList<MDNode>> table = new ArrayList<>();
		for(final String row : rows) {
			final ArrayList<MDNode> tableRow = new ArrayList<>();
			table.add(tableRow);
			for(int i = 0; i < row.length(); i++) {
				tableRow.add(row.charAt(i) == ' ' ? emptyNode : notEmptyNode);
			}
		}
		return table;
	}
	
	@Test
	public void removeEmptyRowsUtilEmptyTable() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				"", 
				"");
		HTMLtoMarkdown.removeEmptyRows(table);
		
		assertThat(table).hasSize(0);
	}

	@Test
	public void removeEmptyColsUtilEmptyTable() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				"", 
				"");
		HTMLtoMarkdown.removeEmptyColumns(table);
		
		assertThat(table).isEqualTo(genNodeTable(
				"",
				""));
	}

	@Test
	public void removeEmptyRowsUtil1() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				"", 
				" X ",
				"  ");
		HTMLtoMarkdown.removeEmptyRows(table);
		
		assertThat(table).hasSize(1);
		assertThat(table.get(0)).containsExactly(emptyNode, notEmptyNode, emptyNode);
		assertThat(table).isEqualTo(genNodeTable(" X "));
	}

	@Test
	public void removeEmptyRowsUtil2() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				"  X  ",
				"     ",
				"X    ");
		HTMLtoMarkdown.removeEmptyRows(table);
		
		assertThat(table).isEqualTo(genNodeTable(
				"  X  ",
				"X    "));
	}

	@Test
	public void removeEmptyColsUtil1() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				" X", 
				" X");
		HTMLtoMarkdown.removeEmptyColumns(table);
		
		assertThat(table).isEqualTo(genNodeTable(
				"X",
				"X"));
	}

	@Test

	public void removeEmptyColsUtil2() {
		final ArrayList<ArrayList<MDNode>> table = genNodeTable(
				"X   X", 
				"  X", 
				"      X", 
				"  X       X");
		HTMLtoMarkdown.removeEmptyColumns(table);
		
		assertThat(table).isEqualTo(genNodeTable(
				"X X",
				" X",
				"   X",
				" X  X"));
	}
}
