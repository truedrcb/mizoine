package com.gratchev.mizoine.api2;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Operation;

@Controller
@RequestMapping("/api2")
public class CommonApi2Controller {
	private static final Logger log = LoggerFactory.getLogger(CommonApi2Controller.class);

	@Value("${config:mizoine-config.json}")
	protected String configPath;

	public static class AppInfo {
		public String name = "pwn";
		public String config;
	}

	@GetMapping("/app")
	@ResponseBody
	public AppInfo getAppInfo() {
		final AppInfo appInfo = new AppInfo();
		appInfo.config = configPath;
		return appInfo;
	}
	
	public static class DescriptionDto {
		public String fileName;
		public String md;
		public String text;
		public String html;
	}

	public static class ProjectDto {
		public String repositoryId;
		public String projectId;
		
		public List<DescriptionDto> texts;
	}

	@Operation(summary = "Retrieve projects list")
	@GetMapping("/repositories({repositoryId})/projects")
	@ResponseBody
	public List<ProjectDto> getProjects(@PathVariable final String repositoryId) {
		return List.of();
	}

	@Operation(summary = "Retrieve project")
	@GetMapping("/repositories({repositoryId})/projects({projectId})")
	@ResponseBody
	public ProjectDto getProject(@PathVariable final String repositoryId, @PathVariable final String projectId) {
		return null;
	}

	public static class MentDto {
		public String repositoryId;
		public String projectId;
		public String issueId;
		public String mentId;
	}

	@Operation(summary = "Retrieve comment or attachment")
	@GetMapping("/repositories({repositoryId})/projects({projectId})/issues({issueId})/ments({mentId})")
	@ResponseBody
	public MentDto getMent(@PathVariable final String repositoryId, @PathVariable final String projectId,
			@PathVariable final String issueId, @PathVariable final String mentId) {
		return null;
	}
}
