package com.gratchev.mizoine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;

import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;

public class GitComponentTestRepoRoot extends GitComponentTest {

	@BeforeEach
	@Override
	public void setup() throws IOException, IllegalStateException, GitAPIException {
		final File gitDir = createGitDir();
		repo = TempRepository.create(gitDir);

		component = new GitComponent(repo);

		assertEquals("", component.getRepoRootPrefix());
	}

}
