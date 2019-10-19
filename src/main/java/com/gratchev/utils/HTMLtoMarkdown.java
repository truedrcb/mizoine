package com.gratchev.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

public class HTMLtoMarkdown {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTMLtoMarkdown.class);
	private static final Set<String> SKIP_TAGS = ImmutableSet.of(
			"head", "script", "#doctype", "#comment");  
	private static final Set<String> IGNORE_TAGS = ImmutableSet.of(
			"#document", "abbr", "acronym", "aside", "base", "basefont", "bdi", "bdo", "big", 
			"body", "button", "canvas", "center", "cite", "details", "html",
			"form", "span", "small", "tbody", "thead", "td", "th",
			"label", "nowrap", "fieldset", "title", "font", "li", "dt", "dd", "nobr", 
			"nav", "section", "header", "picture", "source");
	private static final Set<String> PARAGRAPH_TAGS = ImmutableSet.of(
			"p", "div", "tr", "thead", "tbody", "article");  
	private static final Set<String> HEADER_TAGS = ImmutableSet.of(
			"h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "h9");
	private static final Set<String> NON_LINE_TAGS = ImmutableSet.of(
			"table", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "h9", 
			"ul", "ol", "dl", "pre", "code", "blockquote", "tbody");
	private static final String SEPARATORS = " .\n\r\t;<>/\\&";
	
	/**
	 * True = do not render img tag as image, but use only title text if available.
	 * False = try to render in markdown image format. 
	 */
	public boolean skipImg = true;

	interface MDNode {
		abstract boolean isEmpty();
		
		abstract void build(FlatMDBuilder builder);
	}
	
	static class FlatMDBuilder {
		enum ElementType {
			NONE(0),
			SPACE(1),
			NO_SPACE(2),
			SEPARATOR_EXISTS(5),
			FORCE_SPACE(6),
			LINE_FEED(10),
			DOUBLE_LINE_FEED(20),
			TRIPLE_LINE_FEED(30),
			TEXT(100);
			
			final int priority;
			
			ElementType(final int priority) {
				this.priority = priority;
			}
		}
		
		static class Element {
			final ElementType type;
			String content;
			Element(ElementType type, final String text) {
				this.type = type;
				this.content = text;
			}
			
			Element(final String text) {
				this.type = ElementType.TEXT;
				this.content = text;
			}
		}
		
		static Element NONE = new Element(ElementType.NONE, "");
		static Element SPACE = new Element(ElementType.SPACE, " ");
		static Element NO_SPACE = new Element(ElementType.NO_SPACE, "");
		static Element SEPARATOR_EXISTS = new Element(ElementType.SEPARATOR_EXISTS, "");
		static Element FORCE_SPACE = new Element(ElementType.FORCE_SPACE, " ");
		static Element LINE_FEED = new Element(ElementType.LINE_FEED, "\n");
		static Element DOUBLE_LINE_FEED = new Element(ElementType.DOUBLE_LINE_FEED, "\n\n");
		static Element TRIPLE_LINE_FEED = new Element(ElementType.TRIPLE_LINE_FEED, "\n\n\n");
		
		final List<Element> sequence = new LinkedList<>();

		public FlatMDBuilder build(final MDNode node) {
			node.build(this);
			return this;
		}
		
		public FlatMDBuilder prependLinesWith(final String prefix, final MDNode node) {
			final FlatMDBuilder prependingBuilder = new FlatMDBuilder() {
				{
					super.mark(prefix);
				}
				@Override
				public FlatMDBuilder lf() {
					super.lf();
					super.mark(prefix);
					return this;
				}
				@Override
				public FlatMDBuilder lflf() {
					super.lf();
					super.mark(prefix);
					super.lf();
					super.mark(prefix);
					return this;
				}
			};

			node.build(prependingBuilder);
			
			sequence.addAll(prependingBuilder.sequence);
			
			return this;
		}
		
		public FlatMDBuilder none() {
			sequence.add(NONE);
			return this;
		}
		
		public FlatMDBuilder space() {
			sequence.add(SPACE);
			return this;
		}

		public FlatMDBuilder nospace() {
			sequence.add(NO_SPACE);
			return this;
		}
		
		public FlatMDBuilder lf() {
			sequence.add(LINE_FEED);
			return this;
		}
		
		public FlatMDBuilder lflf() {
			sequence.add(DOUBLE_LINE_FEED);
			return this;
		}
		
		private FlatMDBuilder detectSeparators(final String text) {
			if (text.length() > 0 && SEPARATORS.indexOf(text.charAt(0)) >= 0) {
				sequence.add(SEPARATOR_EXISTS);
			}
			sequence.add(new Element(escapeMarkdown(text)));
			if (text.length() > 0 && SEPARATORS.indexOf(text.charAt(text.length() - 1)) >= 0) {
				sequence.add(SEPARATOR_EXISTS);
			}
			return this;
		}
		
		public FlatMDBuilder text(String text) {
			// detect heading space
			if (text.length() > 0 && text.charAt(0) == ' ') {
				text = text.substring(1);
				sequence.add(SPACE);
			}
			// detect trailing space
			if (text.length() > 0 && text.charAt(text.length() - 1) == ' ') {
				detectSeparators(text.substring(0, text.length() - 1));
				sequence.add(SPACE);
			} else {
				detectSeparators(text);
			}
			return this;
		}
		
		public FlatMDBuilder mark(final String text) {
			sequence.add(new Element(text));
			return this;
		}
		
		public String toString() {
			LOGGER.trace("Generating MD string");
			
			final StringBuilder sb = new StringBuilder();
			final Iterator<Element> iterator = sequence.iterator();
			
			Element element = null;
			Element separator = null;

			// remove all separators from the beginning of the list
			while (iterator.hasNext()) {
				element = iterator.next();
				LOGGER.trace(element.type.name());
				if(element.type == ElementType.TEXT) {
					break;
				}
			}
			
			int numLf = 0;
			int numLflf = 0;
			
			while (element != null) {
				if(element.type == ElementType.TEXT) {
					LOGGER.trace(element.content);
					if (separator != null) {
						sb.append(separator.content);
						separator = null;
					}
					numLf = 0;
					numLflf = 0;
					sb.append(element.content);
				} else {
					if (LINE_FEED.type.equals(element.type)) {
						numLf++;
						if (numLf > 1) {
							numLf = 0;
							element = DOUBLE_LINE_FEED;
						}
					}
					if (DOUBLE_LINE_FEED.type.equals(element.type)) {
						numLflf++;
						if (numLflf > 2) {
							numLflf = 0;
							element = TRIPLE_LINE_FEED;
						}
					}
					if (separator == null || element.type.priority > separator.type.priority) {
						separator = element;
					}
				}
				if (!iterator.hasNext()) break;
				element = iterator.next();
				LOGGER.trace(element.type.name());
			}
			return sb.toString();
		}
		
	}
	

	static final MDNode EMPTY = new MDNode() {
		@Override
		public void build(FlatMDBuilder builder) {
		}
		
		@Override
		public boolean isEmpty() {
			return true;
		}
	};
	
	static final MDNode SPACE = new MDNode() {
		@Override
		public void build(FlatMDBuilder builder) {
			builder.space();
		}
		
		@Override
		public boolean isEmpty() {
			return false;
		}
	};
	
	static final MDNode HR = new MDNode() {
		@Override
		public void build(FlatMDBuilder builder) {
			builder.lflf().mark("---").lflf();
		}
		
		@Override
		public boolean isEmpty() {
			return false;
		}
	};
	
	static class MDTextNode implements MDNode {
		private final String text;

		MDTextNode(final String text) {
			this.text = text;
		}
		
		@Override
		public void build(FlatMDBuilder builder) {
			builder.text(text);
		}

		@Override
		public boolean isEmpty() {
			return text == null || text.trim().isEmpty();
		}
	}
	
	static class MDSeparated implements MDNode {
		private final MDNode content;
		
		MDSeparated(final String text) {
			this.content = new MDTextNode(text);
		}
		
		MDSeparated(final MDNode content) {
			this.content = content;
		}

		@Override
		public void build(FlatMDBuilder builder) {
			builder.space();
			content.build(builder);
			builder.space();
		}

		@Override
		public boolean isEmpty() {
			return content.isEmpty();
		}
	}

	static final MDNode BR = new MDNode() {
		@Override
		public void build(FlatMDBuilder builder) {
			builder.lf();
		}
		
		@Override
		public boolean isEmpty() {
			return false;
		}
	};
	
	static class MDNodeChain implements MDNode {
		private final ArrayList<MDNode> childs = new ArrayList<>();

		@Override
		public void build(FlatMDBuilder builder) {
			final Iterator<MDNode> iterator = childs.iterator();
			
			while (iterator.hasNext()) {
				MDNode child = iterator.next();
				if (child.isEmpty()) {
					continue;
				}
				child.build(builder);
			}
		}
		

		@Override
		public boolean isEmpty() {
			for (final MDNode child : childs) {
				if (!child.isEmpty()) {
					return false;
				}
			}
			return true;
		}
		
	}
	
	static class MDPreSufNode implements MDNode {
		final String prefix, suffix;
		final MDNode content;
		MDPreSufNode(final String prefix, final String suffix, final MDNode content) {
			this.prefix = prefix;
			this.suffix = suffix;
			this.content = content;
		}

		MDPreSufNode(final String prefixAndSuffix, final MDNode content) {
			this.prefix = prefixAndSuffix;
			this.suffix = prefixAndSuffix;
			this.content = content;
		}
		
		@Override
		public void build(FlatMDBuilder builder) {
			builder.space().mark(prefix)
				.nospace().build(content).nospace()
				.mark(suffix).space();
		}
		
		@Override
		public boolean isEmpty() {
			return content.isEmpty();
		}
	}

	public String convert(final Element e) {
		final MDNode dom = convertNode(e, false);
		if (dom != null) {
			FlatMDBuilder builder = new FlatMDBuilder();
			dom.build(builder);
			final String md = builder.toString();
			LOGGER.debug("Markdown result:");
			LOGGER.debug(md);
			return md;
		} 
		return "";
	}
	
	public static boolean hasNestedNonLineNode(final Node n) {
		if (n == null) {
			return false;
		}
		
		final List<Node> childNodes = n.childNodes();
		if (childNodes.size() > 0) {
			for (final Node c : childNodes) {
				final String name = c.nodeName();
				if ("#text".equals(name)) {
					continue; //? Perhaps check if text contains '\n'?
				}
				
				if (SKIP_TAGS.contains(name)) {
					if (LOGGER.isTraceEnabled()) {
						LOGGER.trace("hNNLN tag skipped: " + name);
					}
					continue;
				}
				
				if (NON_LINE_TAGS.contains(name)) {
					if (LOGGER.isTraceEnabled()) {
						LOGGER.trace("Non-line tag found: " + name);
					}
					return true;
				}
				
				if (hasNestedNonLineNode(c)) {
					return true;
				}
			}
		}
		return false;
	
	}

	public static String removeCR(final String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		
		// trim first lf (JSoup inserts redundant line feeds at text start)
		// and last lf (count as non-space)
		return text
			.replaceAll("^\n", "")
			.replaceAll("\n$", "")
			.replace("\n", " ")
			.replace("\r", "")
			.replace("&nbsp;", " ")
			// remove multiple spaces
			.replaceAll(" +", " ");
	}
	
	public static String escapeMarkdown(final String text) {
		return text
				.replace("|", "&#124;")
				.replace("\\", "&#92;");
	}

	private MDNode convertImg(final Node n, final boolean inLine) {
		final String title = removeCR(n.attr("title"));
		final String alt =removeCR(n.attr("alt"));
		final String text = (!title.isEmpty()) ? title : alt;
		if (inLine || skipImg) {
			return new MDSeparated(text);
		}
		return new MDNode() {
			final String src = removeCR(n.attr("src"));
			{
				LOGGER.debug("Image: " + src);
			}
			@Override
			public void build(FlatMDBuilder builder) {
				builder.space()
					.mark("![").nospace().text(text).nospace()
					.mark("](").nospace().text(src).nospace().mark(")").space();
			}
			@Override
			public boolean isEmpty() {
				return src == null || src.isEmpty();
			}
		};
	}
	
	private MDNode convertA(final Node n) {
		final String href = removeCR(n.attr("href")).replace(" ", "").replace("\t", "");
		MDNode content = convertChilds(n, true);
		if (content.isEmpty()) {
			final String text = n.attr("title");
			final String alt = n.attr("alt");
			if (!text.isEmpty() || !alt.isEmpty()) {
				content = new MDTextNode(text.isEmpty() ? alt : text);
			} else {
				content = new MDTextNode("link");
			}
		}
		final MDNode text = content;
		if (href.isEmpty()) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Tag A skipped because of empty href");
			}
			return new MDSeparated(content);
		}
		
		if (hasNestedNonLineNode(n)) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Tag A skipped because of content");
			}
			return new MDSeparated(content);
		}
		
		return new MDNode() {
			{
				LOGGER.debug("A: " + href);
			}
			@Override
			public void build(FlatMDBuilder builder) {
				builder.space().mark("[")
					.nospace().build(text).nospace()
					.mark("](").nospace().text(href).nospace().mark(")").space();
			}
			
			@Override
			public boolean isEmpty() {
				return false;
			}
		};
	}
	
	private MDNode convertTable(final Node n, final boolean inLine) {
		if (n instanceof Element) {
			final Element e = (Element) n;
			
			final Elements rows = e.select("tr");

			
			if (rows.size() <= 0) {
				LOGGER.debug("Ignorig table with no rows: " + n);
				return convertChilds(n, inLine);
			}

			for (final Element row : rows) {
				if (hasNestedNonLineNode(row)) {
					LOGGER.debug("Table row has nested non-line elements. Ignoring table.");
					return new MDSeparated(convertChilds(n, inLine));
				}
			}
			
			int colMax = 0;
			
			final ArrayList<ArrayList<MDNode>> table = new ArrayList<>();
			for (final Element row : rows) {
				final Elements cells = row.children();
				if (cells.size() <= 0) {
					LOGGER.debug("Ignorig row with no cells: " + row);
					continue;
				}
				final ArrayList<MDNode> tableRow = new ArrayList<>();
				table.add(tableRow);
				for (final Element cell : cells) {
					final String nodeName = cell.nodeName();
					if ("td".equals(nodeName) || "th".equals(nodeName) ) {
						tableRow.add(convertChilds(cell, true));
					} else {
						LOGGER.debug("Ignored non-td element: " + cell);
					}
				}
				if (colMax < tableRow.size()) {
					colMax = tableRow.size();
				}
			}
			final int colCount = colMax;
			
			if (colCount < 1) {
				LOGGER.debug("Ignorig strange table with no columns: " + n);
				return convertChilds(n, inLine);
			}
			
			return new MDNode() {
				@Override
				public boolean isEmpty() {
					for (final ArrayList<MDNode> tableRow : table) {
						for(final MDNode tableCell : tableRow) {
							if (!tableCell.isEmpty()) {
								return false;
							}
						}
					}
					
					return true;
				}
				
				@Override
				public void build(FlatMDBuilder builder) {
					builder.lflf().mark("|");
					for (int i = 0; i < colCount; i++) {
						builder.space().mark("----").space().mark("|");
					}
					for (final ArrayList<MDNode> tableRow : table) {
						builder.lf();
						for(final MDNode tableCell : tableRow) {
							builder.mark("|").space();
							tableCell.build(builder);
							builder.space();
						}
						builder.mark("|");
					}
					builder.lflf();
				}
			};
			
		}
		LOGGER.debug("Ignorig strange element: " + n);
		return convertChilds(n, inLine);
	}
	
	static class UListNode {
		public final int level;
		public MDNode content;
		public List<UListNode> nested;
		
		UListNode(final int level) {
			this.level = level;
		}
	}

	/**
	 * @param n UL or OL element
	 * @param level nesting level
	 * @return null if structure is not compatible (should be skipped)
	 */
	private List<UListNode> parseList(final Node n, final int level) {
		if (!(n instanceof Element)) {
			LOGGER.debug("Ignorig strange list element: " + n);
			return null;
		}
		final Element e = (Element) n;
		final List<UListNode> items = new ArrayList<>();
		// Parse list
		for (final Element item : e.children()) {
			final String tagName = item.tagName();
			LOGGER.debug("- " + level + " <" + tagName + ">");
			UListNode listNode = new UListNode(level);
			items.add(listNode);
			if ("li".equals(tagName)) {
				MDNodeChain chain = new MDNodeChain();
				listNode.content = chain;
				for (final Node ci : item.childNodes()) {
					final String ciName = ci.nodeName();
					LOGGER.debug("-- " + level + " <" + ciName + ">");
					if ("ul".equals(ciName) || "ol".equals(ciName)) {
						listNode = new UListNode(level);
						items.add(listNode);
						listNode.nested = parseList(ci, level + 1);
						if (listNode.nested == null) {
							return null;
						}
						listNode = new UListNode(level);
						items.add(listNode);
						chain = new MDNodeChain();
						listNode.content = chain;
					} else 
					if (!hasNestedNonLineNode(ci)) {
						final MDNode node = convertNode(ci, true);
						if (node != null) {
							chain.childs.add(node);
						}
					} else {
						LOGGER.debug("Unexpected element: " + ci);
						return null;
					}
				}
			} else 
			if ("ul".equals(tagName) || "ol".equals(tagName)) {
				listNode.nested = parseList(item, level + 1);
				if (listNode.nested == null) {
					return null;
				}
			} else {
				LOGGER.debug("Unexpected element: " + item);
				return null;
			}
		}
		return items;
	}

	void render(final FlatMDBuilder builder, final List<UListNode> items, final int level) {
		for (final Iterator<UListNode> iterator = items.iterator();iterator.hasNext();) {
			final UListNode listNode = iterator.next();
			boolean lf = false;
			
			if (listNode.content != null && !listNode.content.isEmpty()) {
				for (int i = 0; i < level; i++) {
					builder.mark("    ");
				}
				builder.mark("- ").nospace();
				listNode.content.build(builder);
				lf = true;
			}
			
			if (listNode.nested != null) {
				render(builder, listNode.nested, level + 1);
				lf = true;
			}
			
			if (!iterator.hasNext()) {
				break;
			}
			if (lf) {
				builder.lf();
			}
		}
	}

	private MDNode convertList(final Node n, final boolean inLine) {
		if (!(n instanceof Element)) {
			LOGGER.debug("Ignorig strange element: " + n);
			return convertChilds(n, inLine);
		}
		final Element e = (Element) n;

		// Parse list
		final List<UListNode> items = parseList(e, 0);
		if (items == null) {
			LOGGER.debug("Ignoring list. Incompatible with parser." + e);
			return convertChilds(n, inLine);
		}
		
		return new MDNode() {

			@Override
			public boolean isEmpty() {
				return false;
			}
			
			@Override
			public void build(FlatMDBuilder builder) {
				builder.lflf();
				render(builder, items, 0);
				builder.lflf();
			}
			
		};
	}

	
	private MDNode convertNode(final Node n, final boolean inLine) {
		if (n == null) {
			LOGGER.debug("Null element skipped");
			return null;
		}
		final String name = n.nodeName();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("<" + name + ">");
		}
		
		if (name == null) {
			LOGGER.debug("Null tag skipped");
			return null;
		}
		
		if (SKIP_TAGS.contains(name)) {
			LOGGER.debug("Tag skipped: " + name);
			return null;
		}
		
		if (IGNORE_TAGS.contains(name)) {
			return new MDSeparated(convertChilds(n, inLine));
		}
		
		// ignore tags with any namespaces
		if (name.contains(":")) {
			return new MDSeparated(convertChilds(n, inLine));
		}
		
		if (PARAGRAPH_TAGS.contains(name)) {
			final MDNode content = convertChilds(n, inLine);
			if (content.isEmpty()) {
				LOGGER.debug("Empty tag skipped: " + name);
				return null;
			}
			return new MDNode() {
				@Override
				public boolean isEmpty() {
					return content.isEmpty();
				}
				@Override
				public void build(FlatMDBuilder builder) {
					if (inLine) {
						builder.space().build(content).space();
					} else {
						builder.lf().build(content).lf();
					}
				}
			};
		}

		if ("blockquote".equals(name)) {
			final MDNode content = convertChilds(n, inLine);
			if (content.isEmpty()) {
				LOGGER.debug("Empty blockquote skipped");
				return null;
			}
			return new MDNode() {
				@Override
				public boolean isEmpty() {
					return content.isEmpty();
				}
				@Override
				public void build(FlatMDBuilder builder) {
					builder
						.lf()
						.prependLinesWith("> ", content)
						.lflf();
				}
			};
		}
		

		if ("hr".equals(name)) {
			return inLine ? SPACE : HR;
		}

		if ("br".equals(name)) {
			return  inLine ? SPACE : BR;
		}
		
		if ("b".equals(name) || "strong".equals(name)) {
			return convertChildsOfInline("**", n);
		}
		
		if ("i".equals(name) || "em".equals(name)) {
			return convertChildsOfInline("*", n);
		}

		if ("s".equals(name) || "strike".equals(name) || "del".equals(name)) {
			return convertChildsOfInline("~~", n);
		}

		if ("u".equals(name)) {
			return convertChildsOfInline("<u>", n, "</u>");
		}
		
		if ("sub".equals(name)) {
			return convertChildsOfInline("<sub>", n, "</sub>");
		}
		
		if ("sup".equals(name)) {
			return convertChildsOfInline("<sup>", n, "</sup>");
		}
		
		if ("address".equals(name)) {
			return convertChilds("<address>", n, "</address>", inLine);
		}

		if ("time".equals(name)) {
			return convertChilds("<time>", n, "</time>", inLine);
		}
		
		if ("img".equals(name)) {
			return convertImg(n, inLine);
		}
		
		if ("a".equals(name)) {
			return convertA(n);
		}

		if ("table".equals(name) || "tbody".equals(name)) {
			return convertTable(n, inLine);
		}
		
		if ("ul".equals(name) || "ol".equals(name)) {
			return convertList(n, inLine);
		}
		
		if ("pre".equals(name) || "code".equals(name)) {
			final Element e = (Element) n;
			final String innerHtml = e.html();
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("'" + innerHtml + "'");
			}
			
			if (innerHtml.contains("\n")) {
				return new MDNode() {
					@Override
					public void build(FlatMDBuilder builder) {
						builder.lflf().mark("```\n").text(innerHtml).mark("\n```").lflf();
					}
					

					@Override
					public boolean isEmpty() {
						return innerHtml.trim().isEmpty();
					}
				};
			} else {
				return new MDNode() {
					@Override
					public void build(FlatMDBuilder builder) {
						builder.space().mark("`").text(innerHtml).mark("`").space();
					}

					@Override
					public boolean isEmpty() {
						return innerHtml.trim().isEmpty();
					}
				};
			}
		}

		if (HEADER_TAGS.contains(name)) {
			final int level = Integer.parseInt(name.substring(1));
			final MDNode text = convertChilds(n, true);
			if (text.isEmpty()) {
				LOGGER.debug("Empty header tag skipped: " + name);
				return null;
			}
			
			final String prefix = Strings.repeat("#", level);
			
			return new MDNode() {
				@Override
				public void build(FlatMDBuilder builder) {
					builder.lflf().mark(prefix).space();
					text.build(builder);
					builder.lflf();
				}
				
				@Override
				public boolean isEmpty() {
					return text.isEmpty();
				}
			};
		}
		
		if ("#text".equals(name)) {
			final String outerHtml = n.outerHtml();
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("'" + outerHtml + "'");
			}
			final String text = removeCR(outerHtml);
			if (text.isEmpty()) {
				return null;
			}
			return new MDTextNode(text);
		}
		
		LOGGER.debug("Unknown name: " + name);
		return convertChilds(name + ">[", n, "]< ", inLine);
	}

	private MDNode convertChildsOfInline(final String prefixAndSuffix, final Node n) {
		return convertChildsOfInline(prefixAndSuffix, n, prefixAndSuffix);
	}
	
	private MDNode convertChildsOfInline(final String prefix, final Node n, final String suffix) {
		if (hasNestedNonLineNode(n)) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Inline tag skipped because of content: " + n.nodeName());
			}
			return convertChilds(n, false);
		}
		return convertChilds(prefix, n, suffix, true);
	}
	

	private MDNode convertChilds(final String prefix, final Node n, final String suffix, final boolean inLine) {
		return new MDPreSufNode(prefix, suffix, convertNodes(n.childNodes(), inLine));
	}

	
	private MDNode convertChilds(final Node n, final boolean inLine) {
		return convertNodes(n.childNodes(), inLine);
	}
	
	private MDNode convertNodes(final List<Node> list, final boolean inLine) {
		final MDNodeChain chain = new MDNodeChain();
		for (final Node c : list) {
			final MDNode node = convertNode(c, inLine);
			if (node != null) {
				chain.childs.add(node);
			}
		}
		return chain;
	}

}
