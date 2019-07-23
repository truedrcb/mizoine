package com.gratchev.mizoine.repository2.file;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gratchev.mizoine.repository2.Administrator;
import com.gratchev.mizoine.repository2.dto.ProjectDto;
import com.gratchev.mizoine.repository2.dto.RepositoryDto;


/**
 * File system based {@link Administrator} implementation
 * 
 * @author artem@gratchev.com
 */
public class AdministratorImpl implements Administrator {
	private static final Logger log = LoggerFactory.getLogger(AdministratorImpl.class);
	private final Path rootPath;

	public AdministratorImpl(final Path rootPath) {
		this.rootPath = rootPath;
	}

	public Path getRootPath() {
		return rootPath;
	}

	public RepositoryDto updateRepositoryMeta(final String metadata) throws IOException {
		final RepositoryDto parsedMeta = new ObjectMapper().readValue(metadata, RepositoryDto.class);
		try (FileOutputStream f = new FileOutputStream(rootPath.resolve(RepositoryConstants.META_JSON_FILENAME).toFile())) {
			f.write(metadata.getBytes(StandardCharsets.UTF_8));
		}
		return parsedMeta;
	}

	@Override
	public void createProject(final String projectId) throws IOException {
		log.debug("Creating project: {}", projectId);
		final Path projectPath = rootPath.resolve(Path.of(projectId));
		Files.createDirectories(projectPath);
		log.info("New project has been created in folder: {}", projectPath);
	}

	@Override
	public List<ProjectDto> getProjects() {
		final ArrayList<ProjectDto> repositoriesIds = new ArrayList<>();
		for (final var file : rootPath.toFile().listFiles()) {
			if (file.isDirectory() && !file.isHidden()) {
				final ProjectDto dto = new ProjectDto();
				dto.setProjectId(file.getName());
				repositoriesIds.add(dto);
			}
		}
		return repositoriesIds;
	}
}
