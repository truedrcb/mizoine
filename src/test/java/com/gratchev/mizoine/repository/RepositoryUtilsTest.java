package com.gratchev.mizoine.repository;

import static com.gratchev.mizoine.repository.RepositoryUtils.checkOrCreateDirectory;
import static com.gratchev.mizoine.repository.RepositoryUtils.checkOrCreateHiddenDirectory;
import static com.gratchev.mizoine.repository.TempRepositoryUtils.assertDirExists;
import static com.gratchev.mizoine.repository.TempRepositoryUtils.assertDirNotExists;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;

public class RepositoryUtilsTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUtilsTest.class);

	TempRepository repo;
	

	@BeforeEach
	public void setUp() throws Exception {
		repo = TempRepository.create();
		
	}
	
	@AfterEach
	public void tearDown() throws IOException {
		repo.dispose();
	}
	
	@Test
	public void createHiddenDirectory() throws IOException {
		final File mizoineDir = new File(repo.getRoot(), Repository.MIZOINE_DIR);

		assertDirNotExists(mizoineDir);

		checkOrCreateHiddenDirectory(mizoineDir);

		assertDirExists(mizoineDir);
		
		assertTrue(mizoineDir.isHidden());
	}

	@Test
	public void createDirectory() throws IOException {
		final File dir = repo.getProjectRoot("TEST");

		assertDirNotExists(dir);

		checkOrCreateDirectory(dir);

		assertDirExists(dir);
		
		assertFalse(dir.isHidden());
	}
}
