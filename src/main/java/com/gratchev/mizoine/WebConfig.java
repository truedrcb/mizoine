package com.gratchev.mizoine;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.RelativeRedirectFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <ul>
 * <li>HTML unless the client asks for something else (D6). Without this, a request without {@code Accept}
 * header or with {@code Accept: *}{@code /*} (plain curl) would get JSON wherever a URL offers both.</li>
 * <li>Redirects stay relative ({@code Location: /login}), so the browser stays on the host it used.</li>
 * </ul>
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

	@Override
	public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
		// used only when the Accept header states no preference (missing or */*)
		configurer.defaultContentType(MediaType.TEXT_HTML);
	}

	/**
	 * Tomcat turns {@code sendRedirect("/login")} into {@code scheme://host/login} from the {@code Host} header,
	 * always for HTTP/1.0 requests (nginx's default towards the application). Behind a proxy that does not
	 * pass the public host on, that is the wrong host (bug: https://localhost/login). This filter sets the
	 * {@code Location} header itself. First in the chain, so it also covers Spring Security's redirects.
	 */
	@Bean
	FilterRegistrationBean<RelativeRedirectFilter> relativeRedirectFilter() {
		final RelativeRedirectFilter filter = new RelativeRedirectFilter();
		filter.setRedirectStatus(HttpStatus.FOUND);
		final FilterRegistrationBean<RelativeRedirectFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
