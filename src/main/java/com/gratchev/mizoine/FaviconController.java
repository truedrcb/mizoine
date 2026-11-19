package com.gratchev.mizoine;

import java.time.Duration;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /favicon.ico}: the icon of the old version, without login (browsers also ask for it on the login
 * page). The file is {@code ui/favicon.ico}, so it is also at {@code /ui/favicon.ico}. A controller, not a static file: {@code /{repository}} would otherwise take the URL.
 */
@RestController
@Tag(name = "Resources")
public class FaviconController {

	public static final String PATH = "/favicon.ico";

	private static final String ICON_TYPE = "image/x-icon";
	private static final MediaType ICON = MediaType.parseMediaType(ICON_TYPE);
	private static final Resource FAVICON = new ClassPathResource(WebConfig.UI_LOCATION + "favicon.ico");

	// no "produces": browsers send varying Accept headers, and without one D6 assumes text/html
	@GetMapping(PATH)
	@Operation(summary = "Favicon", description = "The icon shown by browsers. Needs no login.")
	@SecurityRequirements // no login: overrides the global basicAuth requirement
	@ApiResponse(responseCode = "200", description = "The icon", content = @Content(mediaType = ICON_TYPE))
	ResponseEntity<Resource> favicon() {
		return ResponseEntity.ok()
				.contentType(ICON)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
				.body(FAVICON);
	}
}
