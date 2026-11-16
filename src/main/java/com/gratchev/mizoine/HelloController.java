package com.gratchev.mizoine;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HelloController {

	@GetMapping("/")
	String hello() {
		return "hello";
	}
}
