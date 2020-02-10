package com.gratchev.mizoine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class TempRepositoryUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempRepositoryUtils.class);

	public static void assertDirExists(final File dir) {
		assertThat(dir.exists()).describedAs("Directory must exist to continue: " + dir.getAbsolutePath()).isTrue();
		assertTrue(dir.isDirectory());
	}

	public static void assertDirNotExists(final File dir) {
		assertThat(dir.exists()).describedAs("Unexpected existing directory: " + dir.getAbsolutePath()).isFalse();
	}
	
	public static void printDirectory(final File dir, final int level) {
		if (!dir.exists()) {
			LOGGER.error("Unexpected! Doesn't exist: " + dir.getAbsolutePath());
			return;
		}
		final String offsetStr = Strings.repeat("  ", level);
		if (!dir.isDirectory()) {
			LOGGER.info(offsetStr + dir.getName() + (dir.isHidden() ? " hidden" : "")
					+ " bytes: " + dir.length());
			return;
		}
		LOGGER.info(offsetStr + "[" + dir.getName() + "]" + (dir.isHidden() ? " hidden" : ""));
		final int nextLevel = level + 1;
		for (final File file : dir.listFiles()) {
			printDirectory(file, nextLevel);
		}
	}

	public static void printDirectory(final File dir) {
		printDirectory(dir, 0);
	}
	
	public static void removeDirectory(final File root) throws IOException {
		LOGGER.info("Removing directory: " + root.getAbsolutePath());
		Files.walkFileTree(root.toPath(), new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				assertTrue(file.toFile().getAbsolutePath(), file.toFile().delete());
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				assertTrue(dir.toFile().getAbsolutePath(), dir.toFile().delete());
				return FileVisitResult.CONTINUE;
			}
		});
		assertDirNotExists(root);
		LOGGER.info("Directory suscessfully removed.");
	}
	
	public static class TempRepository extends Repository {
		private final File tempRoot;
		
		public TempRepository(final File tempRoot) {
			super(tempRoot.getAbsolutePath(), "");
			this.tempRoot = tempRoot;
		}
		
		public static TempRepository create() throws IOException {
			return create(Files.createTempDirectory("Mizoine-test-repo").toFile());
		}
			
		public static TempRepository create(File tempRepoRoot, final String... subfolders) throws IOException {
			assertDirExists(tempRepoRoot);
			
			for(final String subfolder : subfolders) {
				tempRepoRoot = new File(tempRepoRoot, subfolder);
				assertTrue(subfolder, tempRepoRoot.mkdir());
			}
		
			final TempRepository repo = new TempRepository(tempRepoRoot);

			
			
			assertDirExists(repo.getRoot());
			LOGGER.debug("Testing within root dir: " + repo.getRoot().getAbsolutePath());
			
			repo.createInitialRepositoryDirectories();
			assertDirExists(repo.getProjectsRoot());
			assertDirExists(repo.getUsersRoot());
			printDirectory(repo.getRoot());

			return repo;
		}
		
		public void dispose() throws IOException {
			final File root = tempRoot;
			assertDirExists(root);
			LOGGER.debug("Root dir after test: " + root.getAbsolutePath());
			printDirectory(root);
			removeDirectory(root);
		}

	}
}
