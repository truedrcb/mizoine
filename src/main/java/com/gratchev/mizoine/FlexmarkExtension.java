package com.gratchev.mizoine;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ext.emoji.Emoji;
import com.vladsch.flexmark.ext.emoji.internal.EmojiDelimiterProcessor;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.HtmlRenderer.Builder;
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.DataKey;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.html.MutableAttributes;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * Customize attributes output
 * https://github.com/vsch/flexmark-java/wiki/Usage#customize-html-attributes-via-attribute-provider
 */
public class FlexmarkExtension implements Parser.ParserExtension, HtmlRendererExtension {
	private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkExtension.class);
	
	
	public static final String LARGE_IMG_SUFFIX = ".lg"; // same suffix as in similar bootstrap text classes
	public static final Map<String, String> SHORTCUT_TO_FA = new HashMap<>();

	static {
		SHORTCUT_TO_FA.put("!", "fas fa-exclamation-triangle text-warning");
		SHORTCUT_TO_FA.put("?", "fas fa-question-circle text-primary");
		SHORTCUT_TO_FA.put("x", "fas fa-times-circle text-danger");
		SHORTCUT_TO_FA.put("i", "fas fa-info-circle text-info");
		SHORTCUT_TO_FA.put("/", "fas fa-check-square text-success");
	}

	public static class LinkTemplate {
		public String prefix;
		public String urlTemplate;
		public String styleClass;
		@Override
		public String toString() {
			return "LinkTemplate [prefix=" + prefix + ", urlTemplate=" + urlTemplate + ", styleClass=" + styleClass
					+ ", newTab=" + newTab + ", urlFormat=" + urlFormat + "]";
		}

		public boolean newTab;
		private MessageFormat urlFormat;

		public LinkTemplate() {
		}

		public LinkTemplate(final String prefix, final String urlTemplate, 
				final String styleClass, final boolean newTab) {
			this.prefix = prefix;
			this.urlTemplate = urlTemplate;
			this.styleClass = styleClass;
			this.newTab = newTab;
			
			LOGGER.debug("New: {}", this);
		}
	}

	public static final DataKey<List<LinkTemplate>> TEMPLATES = new DataKey<>("TEMPLATES",
			new ArrayList<>());
	
	@Override
	public void rendererOptions(final @NotNull MutableDataHolder options) {
		// add any configuration settings to options you want to apply to everything,
		// here
	}

	@Override
	public void extend(final Builder rendererBuilder, final @NotNull String rendererType) {
		rendererBuilder.attributeProviderFactory(ImgExtensionProvider.Factory());
		rendererBuilder.nodeRendererFactory(new EmojiNodeRenderer.Factory());

	}

	static FlexmarkExtension create() {
		return new FlexmarkExtension();
	}

	static class ImgExtensionProvider implements AttributeProvider {
		public static final LinkTemplate ISSUE = new LinkTemplate("issue", "/issue/{0}", "text-primary", false);
		public static final LinkTemplate ISSUE_NAMED = new LinkTemplate("issue-", "/issue/{1}", "text-primary", false);
		private final List<LinkTemplate> linkTemplates = new ArrayList<>();
		
		public ImgExtensionProvider(DataHolder dataHolder) {
			linkTemplates.add(ISSUE_NAMED);
			linkTemplates.add(ISSUE);
			linkTemplates.addAll(TEMPLATES.get(dataHolder));
		}

		@Override
		public void setAttributes(final @NotNull Node node, final @NotNull AttributablePart part, final @NotNull MutableAttributes attributes) {
			if (node instanceof ImageRef) {
				final ImageRef r = (ImageRef) node;
				final BasedSequence reference = r.getReference();
				attributes.replaceValue("class",
						reference.endsWith(LARGE_IMG_SUFFIX) ? "miz-md-img" : "miz-md-thumbnail");
				attributes.replaceValue("miz-ref", reference);
			} else if (node instanceof Image) {
				attributes.replaceValue("class", "miz-md-img");
//				final Image image = (Image) node;
//				if (image.getUrl().equals("thumb")) {
//					attributes.replaceValue("src", "thumb-replaced");
//					attributes.replaceValue("miz-ref", image.getText());
//				}
			} else if (node instanceof AutoLink) {
				attributes.replaceValue("target", "_blank");
			} else if (node instanceof Link) {
				final Link l = (Link) node;
				final BasedSequence url = l.getUrl();
				if (url != null) {
					if (url.startsWith("http:", true) || url.startsWith("https:", true)) {
						attributes.replaceValue("target", "_blank");
					} else {
						for (final LinkTemplate lt : linkTemplates) {
							if(url.startsWith(lt.prefix)) {
								if (lt.urlFormat == null) {
									lt.urlFormat = new MessageFormat(lt.urlTemplate);
								}
								final String linkText = l.getText().toString();
								final String linkParam = url.removePrefix(lt.prefix).toString();
								attributes.replaceValue("href", lt.urlFormat.format(new Object[] {linkText, linkParam}));
								if (!lt.newTab) {
									attributes.remove("target");
								} else {
									attributes.replaceValue("target", "_blank");
								}
								if (lt.styleClass != null) {
									attributes.replaceValue("class", lt.styleClass);
								}
								break;
							}
						}
					}
				}
			}
		}

		static AttributeProviderFactory Factory() {
			return new IndependentAttributeProviderFactory() {
				@Override
				public @NotNull AttributeProvider apply(final @NotNull LinkResolverContext context) {
					return new ImgExtensionProvider(context.getOptions());
				}
			};
		}
	}

	@Override
	public void parserOptions(final MutableDataHolder options) {

	}

	@Override
	public void extend(Parser.Builder parserBuilder) {
		parserBuilder.customDelimiterProcessor(new EmojiDelimiterProcessor());
	}

	public static class EmojiNodeRenderer implements NodeRenderer {

		public EmojiNodeRenderer(DataHolder options) {
		}

		@Override
		public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
			HashSet<NodeRenderingHandler<?>> set = new HashSet<>();
			set.add(new NodeRenderingHandler<>(Emoji.class, EmojiNodeRenderer.this::render));
			return set;
		}

		private void render(final Emoji emoji, final NodeRendererContext context, final HtmlWriter html) {
			final String faClass = SHORTCUT_TO_FA.get(emoji.getText().toString());
			if (faClass == null) {
				// output as text
				html.text(":");
				context.renderChildren(emoji);
				html.text(":");
			} else {
				html.attr("class", faClass);
				html.withAttr();
				html.tag("i");
				// context.renderChildren(emoji);
				html.closeTag("i");
			}
		}

		public static class Factory implements NodeRendererFactory {
			@Override
			public @NotNull NodeRenderer apply(final @NotNull DataHolder options) {
				return new EmojiNodeRenderer(options);
			}
		}
	}

}
