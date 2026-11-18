package com.gratchev.mizoine;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTML unless the client asks for something else (D6). Without this, a request without {@code Accept}
 * header or with {@code Accept: *}{@code /*} (plain curl) would get JSON wherever a URL offers both.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

	@Override
	public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
		// used only when the Accept header states no preference (missing or */*)
		configurer.defaultContentType(MediaType.TEXT_HTML);
	}
}
