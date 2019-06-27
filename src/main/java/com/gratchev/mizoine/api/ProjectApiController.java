package com.gratchev.mizoine.api;

import static com.gratchev.utils.StringUtils.isUnsignedInteger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.Project;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.utils.FileUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/project/{project}")
public class ProjectApiController extends BaseDescriptionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectApiController.class);
	public static final Comparator<Issue> BY_ISSUE_NUMBER_DESCENDING = (o1, o2) -> {
			final String s1 = o1.issueNumber;
			final String s2 = o2.issueNumber;
			
			if(isUnsignedInteger(s1)) {
				if(isUnsignedInteger(s2)) {
					int i1 = Integer.parseInt(s1);
					int i2 = Integer.parseInt(s2);
					if (i1 != i2) {
						return i2 - i1; 
					}
				} else {
					return 1;
				}
			} else {
				if(isUnsignedInteger(s2)) {
					return -1; 
				}
			}
			return -s1.compareTo(s2);
		};

	@GetMapping("issues")
	@ResponseBody
	public List<Issue> projectIssues(@PathVariable final String project) {
		final Repository repo = getRepo();
		final List<Issue> issues = repo.getIssues(project);
		issues.sort(BY_ISSUE_NUMBER_DESCENDING);
		return issues;
	}
	
	public static class ProjectInfo {
		public DescriptionResponse description;
		public Project project;
		
	}

	@GetMapping("info")
	@ResponseBody
	public ProjectInfo projectInfo(@PathVariable final String project) throws FileNotFoundException, IOException {
		final Repository repo = getRepo();
		final ProjectInfo info = new ProjectInfo();

		info.project = repo.readProjectInfo(project);

		final File descriptionFile = repo.getProjectDescriptionFile(project);
		if (descriptionFile.exists()) {
			final Parser parser = flexmark.getParser();
			final HtmlRenderer renderer = flexmark.getRenderer();
			final String markdownText = FileUtils.readTextFile(descriptionFile);
			final DescriptionResponse description = new DescriptionResponse();
			final Node document = parser.parse(markdownText);
			description.markdown = markdownText;
			description.html = renderer.render(document);
			LOGGER.debug(description.html);
			info.description = description;
		}
		return info;
	}

	@PostMapping("issue")
	@ResponseBody
	public String createIssue(@PathVariable final String project, final String title, final String description) throws IOException {
		final Repository repo = getRepo();
		final File projectIssuesRoot = repo.getProjectIssuesRoot(project);
		final Issue issue = repo.createIssue(projectIssuesRoot, title, description, currentUser);
		return project + "-" + issue.issueNumber;
	}

	@PostMapping("description")
	@ResponseBody
	public DescriptionResponse updateDescription(@PathVariable final String project, final String description) throws IOException {
		LOGGER.debug("Update description for: " + project);
		LOGGER.debug(description);
		final File descriptionFile = getRepo().project(project).getDescriptionFile();
		FileUtils.overwriteTextFile(description, descriptionFile);
		return descriptionResponse(description, parse(description));
	}


	@GetMapping("description")
	@ResponseBody
	public DescriptionResponse getDescription(@PathVariable final String project) throws IOException {
		LOGGER.debug("Get description for: " + project);

		final File descriptionFile = getRepo().project(project).getDescriptionFile();
		if (descriptionFile.exists()) {
			final String markdownText = FileUtils.readTextFile(descriptionFile);
			return descriptionResponse(markdownText, parse(markdownText));
		}
		return new DescriptionResponse();
	}
}
