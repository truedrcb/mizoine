package com.gratchev.mizoine;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Splitter;
import com.gratchev.mizoine.WebSecurityConfig.UserCredentials;
import com.gratchev.mizoine.api.SearchApiController;
import com.gratchev.mizoine.repository.Repository;
import com.gratchev.mizoine.repository.RepositoryCache;
import com.gratchev.mizoine.repository.Repository.FilePathInfo;
import com.gratchev.mizoine.repository.meta.RepositoryMeta;

public class GitComponent {
	private static final Logger LOGGER = LoggerFactory.getLogger(GitComponent.class);

	private static final char SLASH = FastIgnoreRule.PATH_SEPARATOR;
	private static final Splitter GIT_PATH_SPLITTER = Splitter.on(SLASH);

	private final Repository repo;
	
	public GitComponent(final Repository repo) {
		this.repo = repo;
	}

	private File gitDir = null;
	private String repoRootPrefix = null;

	public File getGitDir() {
		if (gitDir == null) {
			gitDir = new File(repo.getRoot().getAbsolutePath());
			repoRootPrefix = "";
			while(gitDir != null) {
				final File gd = new File(gitDir, ".git");
				if (gd.exists() && gd.isDirectory()) {
					gitDir = gd;
					break;
				}
				repoRootPrefix = gitDir.getName() + SLASH + repoRootPrefix;
				gitDir = gitDir.getParentFile();
			}
			if (gitDir != null) {
				LOGGER.debug("Git dir: " + gitDir.getAbsolutePath());
			} else {
				LOGGER.warn("Git dir not found for: " + repo.getRoot().getAbsolutePath());
			}
		}

		return gitDir;
	}

	public String getRepoRootPrefix() {
		getGitDir();
		return repoRootPrefix;
	}
	
	public static class GitFileInfo extends FilePathInfo {
		public String path;
	}
	
	public GitFileInfo identifyFile(final String path, final GitFileInfo info) {
		final String repoRootPrefix = getRepoRootPrefix();
		if (path.startsWith(repoRootPrefix)) {
			info.path = path.substring(repoRootPrefix.length());
			repo.identifyFile(GIT_PATH_SPLITTER.split(info.path), info);
		}
		return info;
	}


	public static class GitFile  extends SearchApiController.SearchEntry {
		@Override
		public String toString() {
			return "GitFile [path=" + path + ", project=" + project + ", issueNumber=" + issueNumber + ", attachmentId="
					+ attachmentId + ", commentId=" + commentId + "]";
		}
		public String path;
	}

	public class WorkingFiles {
		private final List<GitFile> added = new ArrayList<>(), changed = new ArrayList<>(), removed = new ArrayList<>(); // staged
		private final List<GitFile> modified = new ArrayList<>(), untracked = new ArrayList<>(), missing = new ArrayList<>(); // unstaged

		private WorkingFiles(final Status status) {
			LOGGER.debug("Uncommitted: " + status.getUncommittedChanges());
			// Staged
			addGitFiles(status.getAdded(), added);
			addGitFiles(status.getChanged(), changed);
			addGitFiles(status.getRemoved(), removed);
			
			// Unstaged
			addGitFiles(status.getModified(), modified);
			addGitFiles(status.getUntracked(), untracked);
			addGitFiles(status.getMissing(), missing);
		}

		private void addGitFiles(final Set<String> src, final List<GitFile> target) {

			for (final String path : src) {
				final GitFile f = new GitFile();
				target.add(f);

				f.path = path;
				final GitFileInfo info = identifyFile(path, new GitFileInfo());
				f.project = info.project;
				f.issueNumber = info.issueNumber;
				f.attachmentId = info.attachmentId;
				f.commentId = info.commentId;
			}
		}

		public List<GitFile> getAdded() {
			return added;
		}

		public List<GitFile> getChanged() {
			return changed;
		}

		public List<GitFile> getRemoved() {
			return removed;
		}

		public List<GitFile> getModified() {
			return modified;
		}

		public List<GitFile> getUntracked() {
			return untracked;
		}

		public List<GitFile> getMissing() {
			return missing;
		}
	}

	public WorkingFiles getWorkingFiles(final Status status) {
		return new WorkingFiles(status);
	}

	public Status getGitStatus() {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final Status status = git.status().call();
					return status;
				} catch (NoWorkTreeException | GitAPIException e) {
					LOGGER.error("Git status reading problem: " + gitDir, e);
				}
			} catch (IOException e) {
				LOGGER.error("Git status IO problem: " + gitDir, e);
			}
		}

		return null;
	}


	public RevCommit commit(final String pushMessage, final SignedInUser currentUser) throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final RevCommit res = git.commit().setMessage(pushMessage)
							.setAuthor(currentUser.getName(), currentUser.getEmail())
							.setCommitter(currentUser.getName(), currentUser.getEmail())
							.call();
					return res;
				}
			} catch (IOException e) {
				LOGGER.error("Git commit IO problem: " + gitDir, e);
			}
		}

		return null;
	}

	public Iterable<PushResult> push(final SignedInUser currentUser) throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final Iterable<PushResult> res = git.push()
							.setCredentialsProvider(getCredentialsProvider(currentUser))
							.call();
					return res;
				}
			} catch (IOException e) {
				LOGGER.error("Git push IO problem: " + gitDir, e);
			}
		}

		return null;
	}

	public PullResult pull(final SignedInUser currentUser) throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final PullResult res = git.pull()
							.setCredentialsProvider(getCredentialsProvider(currentUser))
							.call();
					return res;
				}
			} catch (IOException e) {
				LOGGER.error("Git pull IO problem: " + gitDir, e);
			}
		}

		return null;
	}

	public int addAll(final boolean update) throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					int count = 1;
					git.add().setUpdate(update).addFilepattern(".").call();
					return count;
				}
			} catch (IOException e) {
				LOGGER.error("Git addAll IO problem: " + gitDir, e);
			}
		}

		return -1;
	}

	
	public class ManagedGit {
		final Git git;
		private ManagedGit(final Git git) {
			this.git = git;
		}

		public int add(final String... filePatterns) throws GitAPIException {
			int count = 0;
			for (final String filePattern : filePatterns) {
				git.add().addFilepattern(filePattern).call();
				count ++;
			}
			return count;
		}
		
		public int remove(final String... filePatterns) throws GitAPIException {
			int count = 0;
			for (final String filePattern : filePatterns) {
				git.rm().addFilepattern(filePattern).call();
				count ++;
			}
			return count;
		}
		
		public PullResult pull(final SignedInUser currentUser) throws GitAPIException {
			final PullResult res = git.pull()
					.setCredentialsProvider(getCredentialsProvider(currentUser))
					.call();
			return res;
		}
		
		public Status status() throws GitAPIException {
			return git.status().call();
		}
	}
	
	public interface GitConsumer {
		void accept(ManagedGit git) throws GitAPIException;
	}
	
	public void command(final GitConsumer consumer) throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					consumer.accept(new ManagedGit(git));
				}
			} catch (IOException e) {
				LOGGER.error("Git add IO problem: " + gitDir, e);
			}
		}
	}
	
	@JsonInclude(Include.NON_EMPTY)
	public static class GitLogEntry {
		public String name;
		public String shortMessage;
		public int commitTime;
		public final Set<String> files = new TreeSet<>();
		public final Set<String> projects = new TreeSet<>();
		public final Set<String> issues = new TreeSet<>();
		public final Set<String> tags = new TreeSet<>();
		public RepositoryMeta repository;
	}
	
	private void addLogFile(final RepositoryCache repoCache, final GitLogEntry logEntry, final String path) {
		final GitFileInfo info = identifyFile(path, new GitFileInfo());
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Path: " + path + " info: " + info);
		}
		logEntry.files.add(path);
		if (info.project != null) {
			if (info.issueNumber != null) {
				logEntry.issues.add(info.project + "-" + info.issueNumber);
				if (repoCache != null) {
					repoCache.getIssue(info.project, info.issueNumber);
				}
			} else {
				logEntry.projects.add(info.project);
				if (repoCache != null) {
					repoCache.getProject(info.project);
				}
			}
		}
	}
	
	private void addTagRef(final Map<String, GitLogEntry> changesMap, final Ref ref) {
		if (ref != null && ref.getObjectId() != null) {
			final String refName = ref.getObjectId().name();
			if (refName != null && changesMap.containsKey(refName)) {
				changesMap.get(refName).tags.add(ref.getName());
			}
		}
	}

	public List<GitLogEntry> log() throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final List<GitLogEntry> changes = new LinkedList<>();
					final Map<String, GitLogEntry> changesMap = new HashMap<>();
					final Iterable<RevCommit> log = git.log().setMaxCount(50).call();
					int commitCount = 0;
					// https://stackoverflow.com/questions/13537734/how-to-use-jgit-to-get-list-of-changed-files
					// https://github.com/gitblit/gitblit/blob/3e0c6ca8a65bd4b076cac1451c9cdfde4be1d4b8/src/main/java/com/gitblit/utils/JGitUtils.java#L917
					for (final RevCommit commit : log) {
						LOGGER.debug("* " + commit.name() + commit.getShortMessage() + " # " + commit.getCommitTime());
						
						final GitLogEntry entry = new GitLogEntry();
						final RepositoryCache repoCache = (commitCount++ < 5) ? new RepositoryCache(repo) : null;
						entry.name = commit.name();
						entry.commitTime = commit.getCommitTime();
						entry.shortMessage = commit.getShortMessage();
						changes.add(entry);
						changesMap.put(entry.name, entry);

						if (commit.getParentCount() == 0) {
							
							try (final TreeWalk tw = new TreeWalk(git.getRepository())) {
								tw.reset();
								tw.setRecursive(true);
								tw.addTree(commit.getTree());
								while (tw.next()) {
									LOGGER.debug(tw.getPathString());
									addLogFile(repoCache, entry, tw.getPathString());
								}
							}
						} else {
							// https://www.eclipse.org/forums/index.php/t/213979/
							try (final RevWalk rw = new RevWalk(git.getRepository())){
								final RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
								try (final DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)){
									df.setRepository(git.getRepository());
									df.setDiffComparator(RawTextComparator.DEFAULT);
									df.setDetectRenames(true);
									final List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
									for (final DiffEntry diff : diffs) {
										if (LOGGER.isDebugEnabled()) {
											LOGGER.debug(MessageFormat.format("({0} {1} -> {2}", 
												diff.getChangeType().name(),
												diff.getOldPath(), diff.getNewPath()));
										}
										if (ChangeType.DELETE.equals(diff.getChangeType())) {
											addLogFile(repoCache, entry, diff.getOldPath());
										} else {
											addLogFile(repoCache, entry, diff.getNewPath());
										}
									}
								}
							}
						}
						
						if (repoCache != null) {
							entry.repository = repoCache.getRepositoryMeta();
						}
					}
					
					for(final Ref ref : git.branchList().setListMode(ListMode.ALL).call()) {
						addTagRef(changesMap, ref);
					}
					for(final Ref ref : git.tagList().call()) {
						addTagRef(changesMap, ref);
					}
					addTagRef(changesMap, git.getRepository().exactRef(Constants.HEAD));
					return changes;
				}
			} catch (IOException e) {
				LOGGER.error("Git add IO problem: " + gitDir, e);
			}
		}

		return null;
	}

	public Ref reset() throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final Ref res = git.reset().call();
					return res;
				}
			} catch (IOException e) {
				LOGGER.error("Git reset IO problem: " + gitDir, e);
			}
		}

		return null;
	}

	/**
	 * https://stackoverflow.com/questions/1146973/how-do-i-revert-all-local-changes-in-git-managed-project-to-previous-state
	 * https://stackoverflow.com/questions/22620393/various-ways-to-remove-local-git-changes
	 * @return
	 * @throws GitAPIException
	 */
	public Ref checkoutDot() throws GitAPIException {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					return git.checkout().setAllPaths(true).call();
				}
			} catch (IOException e) {
				LOGGER.error("Git checkout IO problem: " + gitDir, e);
			}
		}

		return null;
	}


	// TODO: Move to separate component
	private final Map<String, CredentialsProvider> credentialsProviders = new HashMap<>();

	private CredentialsProvider getCredentialsProvider(final SignedInUser currentUser) throws GitAPIException {
		final String name = currentUser.getName();
		final UserCredentials credentials = currentUser.getCredentials();
		final CredentialsProvider provider = credentialsProviders.get(name);
		if (provider != null) {
			return provider;
		}

		LOGGER.debug("Searching for git credentials: " + name);
		if ( credentials == null || credentials.getGit() == null 
				|| credentials.getGit().getPassword() == null 
				) {
			throw new InvalidConfigurationException("Credentials configuration not found. Set users." + name + ".git.username/password");
		}

		String gitName = credentials.getGit().getUsername();
		if (gitName == null) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Git user name not provided. Assuming the same as of user: " + name);
			}
			gitName = name;
		}
		final String gitPassword = credentials.getGit().getPassword();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Git name for " + name + ": " + gitName);
		}

		final UsernamePasswordCredentialsProvider newProvider = new UsernamePasswordCredentialsProvider(gitName, gitPassword);
		credentialsProviders.put(name, newProvider);
		return newProvider;
	}
}
