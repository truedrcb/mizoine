package com.gratchev.mizoine;

import java.time.Duration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.RelativeRedirectFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <ul>
 * <li>HTML unless the client asks for something else (D6). Without this, a request without {@code Accept}
 * header or with {@code Accept: *}{@code /*} (plain curl) would get JSON wherever a URL offers both.</li>
 * <li>Redirects stay relative ({@code Location: /login}), so the browser stays on the host it used.</li>
 * <li>{@code /ui/**}: public files for the user interface from {@code classpath:/ui/}, and {@code /favicon.ico}
 * from there too (no login, see {@code SecurityConfig}).</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/** URL pattern of the public user interface files; {@code ui} is therefore no repository name. */
	public static final String UI_PATTERN = "/ui/**";
	/** Where they are on the classpath: {@code src/main/resources/ui/}. */
	public static final String UI_LOCATION = "ui/";
	/** Browsers ask for it on every page, also the login page; the file is {@code ui/favicon.ico}. */
	public static final String FAVICON_PATH = "/favicon.ico";

	@Override
	public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
		// used only when the Accept header states no preference (missing or */*)
		configurer.defaultContentType(MediaType.TEXT_HTML);
	}

	/**
	 * Files are looked up before the controllers: {@code /{repository}/{*path}} would otherwise take
	 * {@code /ui/...}, and {@code /{repository}} would take {@code /favicon.ico}. Spring Boot's own static mapping {@code /**} is off ({@code spring.web.resources.add-mappings}),
	 * as it would then take every URL.
	 */
	@Override
	public void addResourceHandlers(final ResourceHandlerRegistry registry) {
		registry.addResourceHandler(UI_PATTERN).addResourceLocations("classpath:/" + UI_LOCATION);
		registry.addResourceHandler(FAVICON_PATH).addResourceLocations("classpath:/" + UI_LOCATION)
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
		registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
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
