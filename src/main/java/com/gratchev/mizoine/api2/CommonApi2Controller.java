package com.gratchev.mizoine.api2;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.gratchev.mizoine.repository2.Configuration;
import com.gratchev.mizoine.repository2.dto.RepositoryDto;
import com.gratchev.mizoine.repository2.file.AdministratorImpl;
import com.gratchev.mizoine.repository2.file.ConfigurationImpl;

import io.swagger.v3.oas.annotations.Operation;

@Controller
@RequestMapping("/api2")
public class CommonApi2Controller {
	private static final Logger log = LoggerFactory.getLogger(CommonApi2Controller.class);
	private Configuration configuration;

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

	@Operation(summary = "Retrieve repositories list")
	@GetMapping("/repositories")
	@ResponseBody
	public Collection<Configuration.RepositoryDto> getRepositories() throws IOException {
		return getConfiguration().getRepositories().values();
	}

	@NotNull
	private Configuration getConfiguration() throws IOException {
		if (configuration == null) {
			configuration = new ConfigurationImpl(new FileInputStream(configPath));
		}
		return configuration;
	}

	@Operation(summary = "Re-read configuration")
	@PutMapping("/updateConfig")
	public ResponseEntity<String> updateConfiguration() {
		configuration = null;
		return ResponseEntity.status(HttpStatus.ACCEPTED).body("Config will be read: " + configPath);
	}

	@Operation(summary = "Update the repository metadata")
	@PutMapping("/repositories('{repositoryId}')/meta")
	public ResponseEntity<RepositoryDto> updateRepositoryMeta(@PathVariable final String repositoryId, @RequestBody final String metadata) throws IOException {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(
				getAdministrator(repositoryId).updateRepositoryMeta(metadata));
	}

	private AdministratorImpl getAdministrator(final String repositoryId) throws IOException {
		final Configuration.RepositoryDto repositoryDto = getConfiguration().getRepositories().get(repositoryId);
		if (repositoryDto == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, repositoryId);
		}
		return new AdministratorImpl(Path.of(repositoryDto.home));
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
