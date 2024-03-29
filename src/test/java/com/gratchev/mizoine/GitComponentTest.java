package com.gratchev.mizoine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.gratchev.mizoine.GitComponent.GitFile;
import com.gratchev.mizoine.GitComponent.GitLogEntry;
import com.gratchev.mizoine.GitComponent.WorkingFiles;
import com.gratchev.mizoine.repository.Issue;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;
import com.gratchev.utils.FileUtils;


public abstract class GitComponentTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(GitComponentTest.class);
	final String project = "TEST";
	final SignedInUserComponentMock currentUser = new SignedInUserComponentMock();

	protected File createGitDir() throws IOException, GitAPIException {
		final File gitDir = Files.createTempDirectory("Mizoine-test-git").toFile();
		gitInit(gitDir);
		return gitDir;
	}

	private static void gitInit(final File gitDir) throws GitAPIException {
		final Git init = Git.init().setDirectory(gitDir).call();

		LOGGER.info("git init: " + init);
		assertNotNull(init);
	}
	

	TempRepository repo;
	GitComponent component;

	@BeforeEach
	public abstract void setup() throws IOException, IllegalStateException, GitAPIException;

	@AfterEach
	public void tearDown() throws IOException {
		repo.dispose();
	}

	@Test
	public void gitInitStatus() {
		final Status status = component.getGitStatus();

		LOGGER.info("git status: " + status);
		assertNotNull(status);

		assertTrue(status.isClean());

	}


	private static void assertGitFile(final String project, final Issue issue, final GitFile actual) {
		assertEquals(project, actual.project);
		assertEquals(issue.issueNumber, actual.issueNumber);
	}



	GitStatusAssert assertStatus() {
		final Status status = component.getGitStatus();

		LOGGER.info("git status: " + status);
		assertNotNull(status);

		return new GitStatusAssert(status);
	}

	static class GitStatusAssert {
		private final Status s;

		private GitStatusAssert(final Status s) {
			this.s = s;
		}

		private static void assertPaths(Set<String> actual, String ... paths) {
			assertEquals(Sets.newHashSet(paths), actual);
		}

		GitStatusAssert added(String ... paths) {
			assertPaths(s.getAdded(), paths);
			return this;
		}

		GitStatusAssert changed(String ... paths) {
			assertPaths(s.getChanged(), paths);
			return this;
		}

		GitStatusAssert missing(String ... paths) {
			assertPaths(s.getMissing(), paths);
			return this;
		}

		GitStatusAssert modified(String ... paths) {
			assertPaths(s.getModified(), paths);
			return this;
		}

		GitStatusAssert removed(String ... paths) {
			assertPaths(s.getRemoved(), paths);
			return this;
		}

		GitStatusAssert conflicting(String ... paths) {
			assertPaths(s.getConflicting(), paths);
			return this;
		}

		GitStatusAssert ignoredNotInIndex(String ... paths) {
			assertPaths(s.getIgnoredNotInIndex(), paths);
			return this;
		}

		GitStatusAssert uncommittedChanges(String ... paths) {
			assertPaths(s.getUncommittedChanges(), paths);
			return this;
		}

		GitStatusAssert untracked(String ... paths) {
			assertPaths(s.getUntracked(), paths);
			return this;
		}

		private void assertWorkingPaths(final List<GitFile> gitFiles, String... paths) {
			assertNotNull(gitFiles);

			assertEquals(Sets.newHashSet(paths), 
					FluentIterable.from(gitFiles).transform(new Function<GitFile, String>() {

						@Override
						public String apply(GitFile input) {
							return input.path;
						}
					}).toSet());
		}


		Status getStatus() {
			return s;
		}

	}

	private String gitPath(final String project, final Issue issue, final String filename) {
		return component.getRepoRootPrefix() + "projects/" + project + "/issues/" + issue.issueNumber + "/" + filename;
	}

	@Test
	public void unstagedFiles() throws IOException, GitAPIException {
		final Issue issue1 = repo.createIssue(repo.getProjectIssuesRoot(project), "Testing new issue", 
				"Initial description", currentUser);
		assertNotNull(issue1);

		final Status s1 = 
				assertStatus()
				.added()
				.changed()
				.missing()
				.modified()
				.removed()
				.conflicting()
				.ignoredNotInIndex()
				.uncommittedChanges()
				.untracked(
						gitPath(project, issue1, "description.md"),
						gitPath(project, issue1, "meta.json")
						)
				.getStatus();

		assertFalse(s1.isClean());
		assertEquals(0, s1.getConflictingStageState().size());


		final Set<String> untracked = s1.getUntracked();
		LOGGER.info("untracked: " + untracked);
		assertEquals(2, untracked.size());

		component.addAll(false);

		final Status s2 = 
				assertStatus()
				.added(
						gitPath(project, issue1, "description.md"),
						gitPath(project, issue1, "meta.json"))
				.changed()
				.missing()
				.modified()
				.removed()
				.conflicting()
				.ignoredNotInIndex()
				.uncommittedChanges(
						gitPath(project, issue1, "description.md"),
						gitPath(project, issue1, "meta.json"))
				.untracked()
				.getStatus();

		assertFalse(s2.isClean());
		LOGGER.info("added: " + s2.getAdded());

		final WorkingFiles workingFiles2 = component.getWorkingFiles(s2);
		
		//assertg

		component.commit("Issue #1 created", currentUser);

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked();

		repo.issue(project, issue1.issueNumber).updateMeta((meta) -> {
			meta.title = "test";
		}, currentUser);

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified(gitPath(project, issue1, "meta.json"))
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "meta.json"))
		.untracked();

		component.addAll(false);

		assertStatus()
		.added()
		.changed(gitPath(project, issue1, "meta.json"))
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "meta.json"))
		.untracked();

		repo.issue(project, issue1.issueNumber).updateMeta((meta) -> {
			meta.title = "test 2";
		}, currentUser);

		assertStatus()
		.added()
		.changed(gitPath(project, issue1, "meta.json"))
		.missing()
		.modified(gitPath(project, issue1, "meta.json"))
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "meta.json"))
		.untracked();


		component.addAll(false);

		assertStatus()
		.added()
		.changed(gitPath(project, issue1, "meta.json"))
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "meta.json"))
		.untracked();

		component.commit("Issue #1 modified", currentUser);

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked();


		final File issueDescriptionFile = repo.issue(project, issue1.issueNumber).getDescriptionFile();

		assertTrue(issueDescriptionFile.exists());
		issueDescriptionFile.delete();
		assertFalse(issueDescriptionFile.exists());

		assertStatus()
		.added()
		.changed()
		.missing(gitPath(project, issue1, "description.md"))
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();



		component.addAll(true);

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed(gitPath(project, issue1, "description.md"))
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();

		for (final GitLogEntry rc : component.log()) {
			LOGGER.info("rc " + rc.name + " | " + rc.shortMessage + " | " + rc.commitTime);
		}



		LOGGER.info("Unstage last changes: " + component.reset());

		assertStatus()
		.added()
		.changed()
		.missing(gitPath(project, issue1, "description.md"))
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();



		LOGGER.info("Revert unstaged changes: " + component.checkoutDot());

		assertTrue(issueDescriptionFile.exists());
		assertEquals("Initial description", FileUtils.readTextFile(issueDescriptionFile));

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked();


		FileUtils.overwriteTextFile("Updated description", issueDescriptionFile);

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified(gitPath(project, issue1, "description.md"))
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();


		LOGGER.info("Revert unstaged changes 2: " + component.checkoutDot());

		assertTrue(issueDescriptionFile.exists());
		assertEquals("Initial description", FileUtils.readTextFile(issueDescriptionFile));

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked();

	}

	@Test
	public void stageSeparate() throws IOException, GitAPIException {
		final Issue issue1 = repo.createIssue(repo.getProjectIssuesRoot(project), "Testing new issue", 
				"Initial description", currentUser);
		assertNotNull(issue1);
		
		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked(
				gitPath(project, issue1, "description.md"),
				gitPath(project, issue1, "meta.json")
				);
		
		
		component.command(git -> git.add(gitPath(project, issue1, "description.md")));
		
		assertStatus()
		.added(
				gitPath(project, issue1, "description.md")
				)
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(
				gitPath(project, issue1, "description.md")
				)
		.untracked(
				gitPath(project, issue1, "meta.json")
				);
		
		final File issueDescriptionFile = repo.issue(project, issue1.issueNumber).getDescriptionFile();

		assertTrue(issueDescriptionFile.exists());
	}

	@Test
	public void stageSeparateRemoved() throws IOException, GitAPIException {
		final Issue issue1 = repo.createIssue(repo.getProjectIssuesRoot(project), "Testing new issue", 
				"Initial description", currentUser);
		assertNotNull(issue1);
		
		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges()
		.untracked(
				gitPath(project, issue1, "description.md"),
				gitPath(project, issue1, "meta.json")
				);
		
		
		component.command(git -> git.add(gitPath(project, issue1, "description.md")));
		component.command(git -> git.add(gitPath(project, issue1, "meta.json")));
		
		assertStatus()
		.added(	gitPath(project, issue1, "description.md"),
				gitPath(project, issue1, "meta.json")
				)
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(
				gitPath(project, issue1, "description.md"),
				gitPath(project, issue1, "meta.json")
				)
		.untracked();
		
		component.commit("Initial commit", currentUser);
		
		final File issueDescriptionFile = repo.issue(project, issue1.issueNumber).getDescriptionFile();

		assertTrue(issueDescriptionFile.exists());

		assertStatus()
		.added(	)
		.changed()
		.missing()
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(	)
		.untracked();
		
		issueDescriptionFile.delete();

		assertStatus()
		.added()
		.changed()
		.missing(gitPath(project, issue1, "description.md"))
		.modified()
		.removed()
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();
		
		component.command(git -> git.remove(gitPath(project, issue1, "description.md")));

		assertStatus()
		.added()
		.changed()
		.missing()
		.modified()
		.removed(gitPath(project, issue1, "description.md"))
		.conflicting()
		.ignoredNotInIndex()
		.uncommittedChanges(gitPath(project, issue1, "description.md"))
		.untracked();
	}
}

