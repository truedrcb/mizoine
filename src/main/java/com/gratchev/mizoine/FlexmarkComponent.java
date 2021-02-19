package com.gratchev.mizoine;

import com.gratchev.mizoine.FlexmarkExtension.LinkTemplate;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FlexmarkComponent {
	private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkComponent.class);
	
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
		final MutableDataSet options = new MutableDataSet();
		// uncomment to set optional extensions
		// See https://github.com/vsch/flexmark-java/wiki/Extensions
		options.set(Parser.EXTENSIONS, List.of(
				StrikethroughExtension.create(), 
				SuperscriptExtension.create(), 
				TablesExtension.create(),
				TypographicExtension.create(),
				FlexmarkExtension.create(),
				//WikiLinkExtension.create(),
				AutolinkExtension.create()));
		
		// uncomment to convert soft-breaks to hard breaks
		options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

		options.set(TablesExtension.CLASS_NAME, "table table-striped table-bordered table-light");

		if (linkTemplatesConfiguration != null) {
			final Map<String, String> linkTemplates = linkTemplatesConfiguration.getLinkTemplates();
			LOGGER.info("Link templates: {}", linkTemplates);
			final List<String> prefixes = new ArrayList<>(linkTemplates.keySet());
			LOGGER.info("Link templates prefixes: {}", prefixes);
			
			// Avoid short to long prefixes interference: For example - "note" and "note:" 
			Collections.sort(prefixes);
			
			final List<LinkTemplate> templates = new ArrayList<>();
			for (final String prefix : prefixes) {
				final String templateConf = linkTemplates.get(prefix);
				LOGGER.info("Configuring link template prefix: {} = {}", prefix, templateConf);
				final String[] split = templateConf.split(";");
				LOGGER.info("Template parameters: {}", split.length);
				if (split.length < 1) {
					LOGGER.info("Skipped");
					continue;
				}
				final String urlTemplate = split[0];
				final String styleClass = split.length > 1 ? split[1] : null;
				final boolean newTab = split.length <= 2 || (!split[2].equals("false"));
				
				// Revert sorted order
				templates.add(0, new LinkTemplate(prefix, urlTemplate, styleClass, newTab));
			}
			options.set(FlexmarkExtension.TEMPLATES, templates);
		}

		parser = Parser.builder(options).build();
		renderer = HtmlRenderer.builder(options).build();
	}
	
	@Autowired
	private LinkTemplatesConfiguration linkTemplatesConfiguration;

	@Bean
	@ConfigurationProperties
	public LinkTemplatesConfiguration linkTemplatesConfigurationBean() {
		return new LinkTemplatesConfiguration();
	}

	public static class LinkTemplatesConfiguration {

		private final Map<String, String> linkTemplates = new HashMap<>();

		public Map<String, String> getLinkTemplates() {
			return this.linkTemplates;
		}
	}	


}
