package com.gratchev.mizoine.repository2;

import java.io.IOException;
import java.util.List;

import com.gratchev.mizoine.repository2.dto.ProjectDto;

public interface Administrator {
	List<ProjectDto> getProjects();
	void createProject(String projectId) throws IOException;
}
