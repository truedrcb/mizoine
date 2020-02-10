package com.gratchev.mizoine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.gratchev.utils.ConcatReader;
import com.gratchev.utils.Streams;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.SubSequence;

public class FlexmarkTest {
	private final FlexmarkComponent fm = new FlexmarkComponent();
	
	
	@Test
	public void test() {
		final Parser parser = fm.getParser();
		final HtmlRenderer renderer = fm.getRenderer();

		// You can re-use parser and renderer instances
		final Node document1 = parser.parse("Test **strong** ![Test][id1]");
		final Node document2 = parser.parse("Test **strong** ![Test][id1]\n\n[id1]: http://drcb.ru/test.jpg");

		System.out.println(document1);
		System.out.println(renderer.render(document1));

		for (Iterator<Node> iterator = document1.getChildIterator(); iterator.hasNext();) {
			Node type = iterator.next();
			System.out.println(type);

		}

		Reference r1 = new Reference(SubSequence.of("[id1]:", 0), SubSequence.of("http://drcb.ru/t.gif", 0), SubSequence.of("  ", 0));

		document1.appendChild(r1);

		System.out.println(document1);
		System.out.println(renderer.render(document1));

		for (Iterator<Node> iterator = document1.getChildIterator(); iterator.hasNext();) {
			Node type = iterator.next();
			System.out.println(type);
			type.getClass();
			if (type instanceof Reference) {
				Reference t1 = (Reference) type;
				System.out.println("Ref: " + t1.getReference() + ", ");
			}

		}



		System.out.println("--- 2 ---");

		System.out.println(document2);
		System.out.println(renderer.render(document2));

		for (Iterator<Node> iterator = document2.getChildIterator(); iterator.hasNext();) {
			Node type = iterator.next();
			System.out.println(type);
			type.getClass();
			if (type instanceof Reference) {
				Reference t1 = (Reference) type;
				System.out.println("Ref: " + t1.getReference() + ", ");
			}

		}

	}

	@Test
	public void testStreamConcatenation() throws IOException {
		final Parser parser = fm.getParser();
		final HtmlRenderer renderer = fm.getRenderer();

		final Node document3 = parser.parseReader(new ConcatReader(new InputStreamReader(FlexmarkTest.class.getResourceAsStream("description.md")), 
				new StringReader("\nsome small test\n\n[--_980]: /attachments/HOME/issues/0/--_980/test.jpg")));

		System.out.println(document3);
		System.out.println(renderer.render(document3));


		final Node document1 = parser.parseReader(new ConcatReader(new StringReader("Test **strong** ![Attachment image 1][--_980]"), 
				new StringReader("\n\n[--_980]: /attachments/HOME/issues/0/--_980/test.jpg")));

		System.out.println(document1);
		System.out.println(renderer.render(document1));


		final Node document2 = parser.parseReader(new StringReader("Test **strong** ![Test][id1]\n\n[id1]: http://drcb.ru/test.jpg"));
		System.out.println(document2);
		System.out.println(renderer.render(document2));



	}

	@Test
	public void testCompleteFileReading() throws IOException {
		final Parser parser = fm.getParser();
		final HtmlRenderer renderer = fm.getRenderer();

		final String s = Streams.asString(FlexmarkTest.class.getResourceAsStream("description.md"));

		final Node document3 = parser.parse(s + "\nsome small test\n\n[--_980]: /attachments/HOME/issues/0/--_980/test.jpg");

		System.out.println(document3);
		System.out.println(renderer.render(document3));


	}


	@Test
	public void testReferenceSyntax() {
		final Parser parser = fm.getParser();
		final HtmlRenderer renderer = fm.getRenderer();

		// You can re-use parser and renderer instances
		final Node document1 = parser.parse("Test **strong** ![Test][id1]\n\n[id1]: http://drcb.ru/test.jpg \"Some \\\"fancy\\\" title\"");

		System.out.println(document1);
		System.out.println(renderer.render(document1));
	
	}

	@Test
	public void testTables() {
		final Parser parser = fm.getParser();
		final HtmlRenderer renderer = fm.getRenderer();

		// You can re-use parser and renderer instances
		final Node document1 = parser.parse(
				"# Table\n\n" + 
				"day|time|spent\n" + 
				":---|:---:|--:\n" + 
				"nov. 2. tue|10:00|4h 40m");

		System.out.println(document1);
		System.out.println(renderer.render(document1));
	
	}
}
