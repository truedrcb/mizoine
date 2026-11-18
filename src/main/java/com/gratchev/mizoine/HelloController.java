package com.gratchev.mizoine;

import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller
@Tag(name = "Start page")
class HelloController {

	@GetMapping("/")
	@Operation(summary = "Start page", description = "Greets the logged-in user.")
	@ApiResponse(responseCode = "200", description = "The start page",
			content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	@ApiResponse(responseCode = "302", description = "Browser not logged in: redirect to the login page",
			content = @Content)
	@ApiResponse(responseCode = "401", description = "Not logged in (non-browser clients): HTTP Basic challenge",
			content = @Content)
	String hello(final Principal principal, final Model model) {
		model.addAttribute("username", principal.getName());
		return "hello";
	}
}
