package com.gratchev.mizoine;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HelloController {

	@GetMapping("/")
	String hello(final Principal principal, final Model model) {
		model.addAttribute("username", principal.getName());
		return "hello";
	}
}
