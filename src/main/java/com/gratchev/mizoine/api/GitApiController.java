package com.gratchev.mizoine.api;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.apache.logging.log4j.util.Strings;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.GitComponent;
import com.gratchev.mizoine.GitComponent.GitFile;
import com.gratchev.mizoine.GitComponent.GitFileInfo;
import com.gratchev.mizoine.GitComponent.GitLogEntry;
import com.gratchev.mizoine.GitComponent.WorkingFiles;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/git/")
public class GitApiController extends BaseController {
	private static final Logger LOGGER = LoggerFactory.getLogger(GitApiController.class);
	
	@Deprecated
	private String message;
	
	public static class ShortStatus {
		public int unstaged;
		public int staged;
	}
	
	@GetMapping("status")
	@ResponseBody
	public ShortStatus getStatus() {
		final ShortStatus status = new ShortStatus();
		try {
			getRepo().getGitComponent().command(g -> {
				final Status gitStatus = g.status();
				status.unstaged = gitStatus.getModified().size() + gitStatus.getUntracked().size() + gitStatus.getMissing().size();
				status.staged = gitStatus.getChanged().size() + gitStatus.getAdded().size() + gitStatus.getRemoved().size();
				});
		} catch (Exception e) {
			LOGGER.error("Error reading status", e);
			message = e.getMessage();
		}
		return status;
	}
	
	@JsonInclude(value = Include.NON_NULL)
	public static class Info extends GitFileInfo {
		public Set<String> status = new TreeSet<String>();
	}
	
	@JsonInclude(value = Include.NON_NULL)
	public static class Stage {
		public SortedMap<String, Info> unstaged = new TreeMap<>();
		public SortedMap<String, Info> staged = new TreeMap<>();
		
		private void addFiles(final SortedMap<String, Info> files, final Set<String> paths, final String status) {
			for(final String path : paths) {
				final Info info;
				if (files.containsKey(path)) {
					info = files.get(path);
				} else {
					info = new Info();
					files.put(path, info);
				}
				info.status.add(status);
			}
		}

		private void addUnstaged(final Set<String> paths, final String status) {
			addFiles(unstaged, paths, status);
		}

		private void addStaged(final Set<String> paths, final String status) {
			addFiles(staged, paths, status);
		}
	}

	@GetMapping("log")
	@ResponseBody
	public List<GitLogEntry> getLog() throws GitAPIException {
		return getRepo().getGitComponent().log();
	}

	@GetMapping("stage")
	@ResponseBody
	public Stage getStage() {
		final Stage stage = new Stage();
		final GitComponent gitComponent = getRepo().getGitComponent();
		try {
			gitComponent.command(g -> {
				final Status gitStatus = g.status();
				stage.addUnstaged(gitStatus.getModified(), "modified");
				stage.addUnstaged(gitStatus.getUntracked(), "untracked");
				stage.addUnstaged(gitStatus.getMissing(), "missing");

				stage.addStaged(gitStatus.getChanged(), "changed");
				stage.addStaged(gitStatus.getAdded(), "added");
				stage.addStaged(gitStatus.getRemoved(), "removed");
				});
		} catch (Exception e) {
			LOGGER.error("Error reading stage", e);
			message = e.getMessage();
		}
		Stream.concat(stage.unstaged.entrySet().stream(), stage.staged.entrySet().stream()).forEach(entry -> {
			gitComponent.identifyFile(entry.getKey(), entry.getValue());
		});
		return stage;
	}

	@PostMapping("pull")
	@ResponseBody
	public String pull() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Pull";
		
		try {
			final PullResult pullRes = git.pull(currentUser);
			if (pullRes != null) {
				message += "\n" + pullRes;
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot pull", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}

	@PostMapping("push")
	@ResponseBody
	public String push() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Push";
		
		try {
			final Iterable<PushResult> res = git.push(currentUser);
			if (res != null) {
				for (final PushResult pres : res) {
					message += "\nRemote updates: " + pres.getRemoteUpdates().size();
				}
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot push", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}

	@PostMapping("stage")
	@ResponseBody
	public String stage(final String filePath) {
		try {
			getRepo().getGitComponent().command(git -> {
				message = "Stage " + filePath;
				
					final int res = git.add(filePath);
					if (res >= 0) {
						message += "\nAdded: " + res;
					}
				});
		} catch (GitAPIException e) {
			LOGGER.error("Cannot add", e);
			message += "\n" + e.getMessage();
		}
		return message;
	}

	@PostMapping("commit")
	@ResponseBody
	public String commit(final String commitMessage) {
		final GitComponent git = getRepo().getGitComponent();
		message = "Commit: " + commitMessage;
		if (Strings.isBlank(commitMessage)) {
			message += "\nCommit message missing.";
			return message;
		}
		try {
			final RevCommit commitRes = git.commit(commitMessage, currentUser);
			if (commitRes != null) {
				message += "\n" + commitRes;
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot commit", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}

	@PostMapping("stage-all")
	@ResponseBody
	public String addAll() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Stage all";
		
		try {
			final Status gitStatus = git.getGitStatus();
			if (gitStatus != null) {
				final WorkingFiles workingFiles = git.getWorkingFiles(gitStatus);
				
				git.command( g -> {
					for (final GitFile file : workingFiles.getUntracked()) {
						g.add(file.path);
						message += "\nAdded untracked file: " + file.path;
					}
					for (final GitFile file : workingFiles.getModified()) {
						g.add(file.path);
						message += "\nAdded modified file: " + file.path;
					}
					for (final GitFile file : workingFiles.getMissing()) {
						g.remove(file.path);
						message += "\nRemoved missing: " + file.path;
					}
				});
				
			} else {
				message += "\nGit status unknown";
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot add", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}

	@PostMapping("stage-all-update")
	@ResponseBody
	public String addAllSlow() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Stage all with update";
		
		try {
			int res = git.addAll(false);
			if (res >= 0) {
				message += "\nAdded: " + res;
			}
			res = git.addAll(true);
			if (res >= 0) {
				message += "\nAdded (updating): " + res;
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot add (update mode)", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}

	@PostMapping("unstage-all")
	@ResponseBody
	public String reset() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Reset";
		
		try {
			final Ref res = git.reset();
			if (res != null) {
				message += "\nReset: " + res.getName();
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot reset", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}
	
	@PostMapping("checkout-dot")
	@ResponseBody
	public String checkoutDot() {
		final GitComponent git = getRepo().getGitComponent();
		message = "Undo";
		
		try {
			final Ref res = git.checkoutDot();
			if (res != null) {
				message += "\nUndo: " + res.getName();
			}
		} catch (GitAPIException e) {
			LOGGER.error("Cannot undo", e);
			message += "\n" + e.getMessage();
		}
		
		return message;
	}
}
