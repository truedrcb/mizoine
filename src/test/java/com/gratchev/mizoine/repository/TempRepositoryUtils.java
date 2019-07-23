package com.gratchev.mizoine.repository;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;

public class TempRepositoryUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempRepositoryUtils.class);

	public static File assertDirExists(final File dir, final String... children) {
		assertThat(dir).exists().isDirectory();
		File subdir = dir;
		for (final String child : children) {
			subdir = new File(subdir, child);
			assertThat(dir).exists().isDirectory();
		}
		return subdir;
	}

	public static File assertFileExists(final File dir, final String fileName) {
		assertDirExists(dir);
		final File file = new File(dir, fileName);
		assertThat(file).exists().isFile();
		return file;
	}

	public static void assertDirNotExists(final File dir) {
		assertThat(dir).doesNotExist();
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
		Files.walkFileTree(root.toPath(), new FileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				assertThat(file.toFile().delete()).as(file.toFile().getAbsolutePath()).isTrue();
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
				assertThat(dir.toFile().delete()).as(dir.toFile().getAbsolutePath()).isTrue();
				return FileVisitResult.CONTINUE;
			}
		});
		assertDirNotExists(root);
		LOGGER.info("Directory successfully removed.");
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
				assertThat(tempRepoRoot.mkdir()).as(subfolder).isTrue();
			}
		
			final TempRepository repo = new TempRepository(tempRepoRoot);

			
			
			assertDirExists(repo.getRoot());
			LOGGER.debug("Testing within root dir: " + repo.getRoot().getAbsolutePath());
			
			repo.createInitialRepositoryDirectories();
			assertDirExists(repo.getProjectsRoot());
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
