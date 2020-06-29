package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.html.MutableAttributes;

/**
 * Customize attributes output https://github.com/vsch/flexmark-java/wiki/Usage#customize-html-attributes-via-attribute-provider
 * 
 * @deprecated Experimental: Only in test mode yet. Currently assigns class to generated img and a tags depending on link type
 */
public class ImgSrcExtension implements HtmlRenderer.HtmlRendererExtension {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImgSrcExtension.class);

	@Override
	public void rendererOptions(final MutableDataHolder options) {
		// add any configuration settings to options you want to apply to everything, here
	}

	@Override
	public void extend(final HtmlRenderer.Builder rendererBuilder, final String rendererType) {
		rendererBuilder.attributeProviderFactory(ImgSrcExtensionProvider.Factory());
	}

	static ImgSrcExtension create() {
		return new ImgSrcExtension();
	}


	static class ImgSrcExtensionProvider implements AttributeProvider {
		@Override
		public void setAttributes(final Node node, final AttributablePart part, final MutableAttributes attributes) {
			if (node instanceof AutoLink && part == AttributablePart.LINK) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Link: " + node + " part " + part.getName());
					LOGGER.debug("Attributes: " + attributes.values());
				}
				// Put info in custom attribute instead
				attributes.replaceValue("class", "my-autolink-class");
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Modified attributes: " + attributes.values());
				}
			} else if (node instanceof Image) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Image: " + node + " part " + part.getName());
					LOGGER.debug("Attributes: " + attributes.values());
				}
			} else if (node instanceof ImageRef) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Image ref: " + node + " part " + part.getName());
					LOGGER.debug("Attributes: " + attributes.values());
				}
				attributes.replaceValue("class", "my-img-ref-class");
//				ImageRef r = (ImageRef) node;
//				r.getReference().endsWith("thumbnail");
			}
		}

		static AttributeProviderFactory Factory() {
			return new IndependentAttributeProviderFactory() {
				@Override
				public AttributeProvider apply(LinkResolverContext context) {
					return new ImgSrcExtensionProvider();
				}
			};
		}
	}


}
