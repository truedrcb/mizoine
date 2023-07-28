package com.gratchev.mizoine.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Splitter;
import com.gratchev.mizoine.repository.Project;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.Repository.FilePathInfo;
import com.gratchev.mizoine.repository.meta.RepositoryMeta;
import com.gratchev.utils.FileUtils;
import com.gratchev.utils.HTMLtoMarkdown;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api")
public class CommonApiController extends BaseController {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommonApiController.class);

	private static final Comparator<Project> BY_PROJECT_ASCENDING = new Comparator<Project>() {
		@Override
		public int compare(final Project o1, final Project o2) {
			return o1.project.compareTo(o2.project);
		}
	};

	private HTMLtoMarkdown htmLtoMarkdown = new HTMLtoMarkdown();
	
	public CommonApiController() {
		htmLtoMarkdown.skipImg = false;
	}
	
	public static class AppInfo {
		public RepositoryMeta repositoryMeta;
		public String userName;
	}
	
	@GetMapping("/app")
	@ResponseBody
	public AppInfo applicationInfo() {
		final AppInfo info = new AppInfo();
		info.repositoryMeta = getRepo().readRepositoryMeta();
		info.userName = currentUser.getName();
		return info;
	}

	@GetMapping("/projects")
	@ResponseBody
	public List<Project> projectsJs() throws FileNotFoundException, IOException {
		final List<Project> projects = getRepo().getProjects();
		projects.sort(BY_PROJECT_ASCENDING);
		return projects;
	}

	public static class Html2MdResponse {
		public String markdown;
	}

	@PostMapping("/html2md")
	@ResponseBody
	public Html2MdResponse html2md(final String html) {
		final Html2MdResponse r = new Html2MdResponse();
		r.markdown = html == null ? "" : htmLtoMarkdown.convert(Jsoup.parse(html));
		return r;
	}
	
	@ResponseStatus(value = HttpStatus.NOT_FOUND)
	public static class NotFoundException extends Exception {
		private static final long serialVersionUID = 7815369667686265001L;

		public NotFoundException(final String message, final Throwable cause) {
			super(message, cause);
		}

		public NotFoundException(final String message) {
			super(message);
		}
	}
	
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	public static class NotAllowedException extends Exception {
		private static final long serialVersionUID = 8185504021752382597L;

		public NotAllowedException(final String message) {
			super(message);
		}
	}

	@JsonInclude(Include.NON_NULL)
	public static class FileContentResponse {
		public String text;
		public FilePathInfo info;
		public Long length;
	}
	
	private File getRepoFile(final String filePath) throws NotAllowedException {
		final String cleanFilePath = decodePath(filePath);
		final String lastChars = cleanFilePath.toLowerCase();
		if (!(lastChars.endsWith(".md") || lastChars.endsWith(".json") || lastChars.endsWith(".txt"))) {
			throw new NotAllowedException(cleanFilePath);
		}
		
		final File file = new File(getRepo().getRoot(), cleanFilePath);
		LOGGER.debug("Reading file: " + file.getAbsolutePath());
		return file;
	}

	private String decodePath(final String filePath) throws NotAllowedException {
		final String cleanFilePath = filePath.replace('*', '/').trim();
		if(cleanFilePath.contains("..") || cleanFilePath.startsWith("/")) 
			throw new NotAllowedException(cleanFilePath);
		return cleanFilePath;
	}

	private static final Splitter PATH_SPLITTER = Splitter.on('*');

	@GetMapping("/textfile/{filePath}")
	@ResponseBody
	public FileContentResponse getFile(@PathVariable final String filePath) throws NotFoundException, NotAllowedException {
		final File file = getRepoFile(filePath);
		final FileContentResponse response = new FileContentResponse();
		response.info = getRepo().identifyFile(PATH_SPLITTER.split(filePath), new FilePathInfo());
		
		try {
			response.text = FileUtils.readTextFile(file);
			response.length = file.length();
		} catch (IOException e) {
			throw new NotFoundException(filePath, e);
		}
		return response;
	}

	@PutMapping("/textfile/{filePath}")
	@ResponseBody
	public FileContentResponse putFile(@PathVariable final String filePath, final String fileContent) throws NotFoundException, NotAllowedException {
		final File file = getRepoFile(filePath);
		try {
			// Force Unix LF instead of Windows CRLF
			FileUtils.overwriteTextFile(fileContent.replace("\r\n", "\n"), file);
		} catch (IOException e) {
			LOGGER.warn("Issue overwriting file:" + filePath, e);
			throw new NotFoundException(filePath, e);
		}
		return getFile(filePath);
	}

	@GetMapping("/file/{filePath}")
	@ResponseBody
	public FileContentResponse getFileInfo(@PathVariable final String filePath) throws NotFoundException, NotAllowedException {
		final Repository repo = getRepo();
		final File file = new File(repo.getRoot(), decodePath(filePath));
		if (!file.exists() || !file.isFile()) {
			throw new NotFoundException(filePath);
		}
		final FileContentResponse response = new FileContentResponse();
		response.info = repo.identifyFile(PATH_SPLITTER.split(filePath), new FilePathInfo());
		response.length = file.length();
		return response;
	}

}
