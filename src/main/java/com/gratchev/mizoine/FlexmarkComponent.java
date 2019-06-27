package com.gratchev.mizoine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.gratchev.mizoine.FlexmarkImgThumbnailExtension.LinkTemplate;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.superscript.SuperscriptExtension;
import com.vladsch.flexmark.util.data.MutableDataSet;

@Component
public class FlexmarkComponent {
	private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkComponent.class);
	
	private final MutableDataSet options = new MutableDataSet();
	
	private Parser parser = null;
	private HtmlRenderer renderer = null;
	
	public Parser getParser() {
		if (parser == null) {
			init();
		}
		return parser;
	}

	public HtmlRenderer getRenderer() {
		if (renderer == null) {
			init();
		}
		return renderer;
	}

	public void init() {
		// uncomment to set optional extensions
		// See https://github.com/vsch/flexmark-java/wiki/Extensions
		options.set(Parser.EXTENSIONS, Arrays.asList(
				StrikethroughExtension.create(), 
				SuperscriptExtension.create(), 
				TablesExtension.create(),
				TypographicExtension.create(),
				FlexmarkImgThumbnailExtension.create(),
				//WikiLinkExtension.create(),
				AutolinkExtension.create()));
		
		// uncomment to convert soft-breaks to hard breaks
		options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

		options.set(TablesExtension.CLASS_NAME, "table table-striped table-bordered table-light");

		if (linkTemplatesConfiguration != null) {
			final Map<String, String> linkTemplates = linkTemplatesConfiguration.getLinkTemplates();
			final List<String> prefixes = new ArrayList<>(linkTemplates.keySet());
			
			// Avoid short to long prefixes interference: For example - "note" and "note:" 
			Collections.sort(prefixes);
			
			final List<LinkTemplate> templates = new ArrayList<>();
			for (final String prefix : prefixes) {
				final String templateConf = linkTemplates.get(prefix);
				LOGGER.info("Configuring link template prefix: " + prefix + " = " + templateConf);
				final String[] split = templateConf.split(";");
				LOGGER.info("Template parameters: " + split.length);
				if (split.length < 1) {
					LOGGER.warn("Skipped");
					continue;
				}
				final String urlTemplate = split[0];
				final String styleClass = split.length > 1 ? split[1] : null;
				final boolean newTab = split.length > 2 ? (!split[2].equals("false")) : true;
				
				// Revert sorted order
				templates.add(0, new LinkTemplate(prefix, urlTemplate, styleClass, newTab));
			}
			options.set(FlexmarkImgThumbnailExtension.TEMPLATES, templates);
		}

		parser = Parser.builder(options).build();
		renderer = HtmlRenderer.builder(options).build();
	}
	
	@Autowired
	private LinkTemplatesConfiguration linkTemplatesConfiguration;

	@Bean
	@ConfigurationProperties
	public LinkTemplatesConfiguration linkTemplatesConfugurationBean() {
		return new LinkTemplatesConfiguration();
	}

	public static class LinkTemplatesConfiguration {

		private Map<String, String> linkTemplates = new HashMap<String, String>();

		public Map<String, String> getLinkTemplates() {
			return this.linkTemplates;
		}
	}	


}
