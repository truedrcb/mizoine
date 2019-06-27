package com.gratchev.mizoine.repository;

import static com.gratchev.mizoine.repository.RepositoryUtils.*;
import static com.gratchev.mizoine.repository.TempRepositoryUtils.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryUtilsTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUtilsTest.class);

	TempRepository repo;
	

	@Before
	public void setUp() throws Exception {
		repo = TempRepository.create();
		
	}
	
	@After
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
