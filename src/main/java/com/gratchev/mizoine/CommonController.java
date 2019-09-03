package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.gratchev.mizoine.api.BaseController;

@Controller
@EnableAutoConfiguration
public class CommonController extends BaseController {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CommonController.class);

	@GetMapping("")
	public String repositoryRoot() {
		return "forward:/index.html";
	}

	@GetMapping("/issues/**")
	public String projectRoot() {
		return "forward:/index.html";
	}
	
	@GetMapping("/project/{project}")
	public String projectRootRedirect(@PathVariable final String project) {
		return "redirect:/issues/{project}";
	}
	
	@GetMapping("/attachment/{project}-{issueNumber}/{attachmentId}")
	public String attachmentRedirect(@PathVariable final String project, 
			@PathVariable final String issueNumber, @PathVariable final String attachmentId) {
		return "redirect:/issue/{project}-{issueNumber}/attachment/{attachmentId}";
	}
	
	@GetMapping("/issue/**")
	public String issueRoot() {
		return "forward:/index.html";
	}
	
	@GetMapping("/edit/**")
	public String editRoot() {
		return "forward:/index.html";
	}
	
	@GetMapping("/view/**")
	public String viewRoot() {
		return "forward:/index.html";
	}
	
	@GetMapping("/git/**")
	public String gitRoot() {
		return "forward:/index.html";
	}
	
	@GetMapping("/search/**")
	public String searchRoot() {
		return "forward:/index.html";
	}

	@Autowired
	private ApplicationContext appContext;

	@PostMapping("/shutdown")
	@ResponseBody
	public String shutdown() {
		new Thread(() -> {
			SpringApplication.exit(appContext, () -> 0);
		}).start();
		return "<html><body>Bye!<br/><a href=/>Mizoine</a></body></html>";
	}
	
}
