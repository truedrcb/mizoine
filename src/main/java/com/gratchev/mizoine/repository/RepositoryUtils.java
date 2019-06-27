package com.gratchev.mizoine.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;

public class RepositoryUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUtils.class);

	/**
	 * Check if directory exists: Create if missing.
	 * 
	 * @param dir Targed directory to check/create
	 * @throws IOException If a file exists with same name or there are other problems with directory creation
	 */
	synchronized static void checkOrCreateDirectory(final File dir) throws IOException {
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				throw new IOException(dir.getAbsolutePath() + " exists but not a directory");
			}
		} else {
			LOGGER.info("Creating directory: " + dir.getAbsolutePath());
			if (!dir.mkdirs()) {
				throw new IOException("Cannot create " + dir.getAbsolutePath());
			}
		}
	}

	/**
	 * Check if directory exists: Create if missing.
	 * Set 'hidden' flag for directory (only Windows). Ignore if setting failed.
	 * 
	 * @param dir Targed directory to check/create
	 * @throws IOException If a file exists with same name or there are other problems with directory creation
	 */
	synchronized static void checkOrCreateHiddenDirectory(final File dir) throws IOException {
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				throw new IOException(dir.getAbsolutePath() + " exists but not a directory");
			}
		} else {
			if (!dir.mkdirs()) {
				throw new IOException("Cannot create " + dir.getAbsolutePath());
			}
			// https://stackoverflow.com/questions/1999437/how-to-make-a-folder-hidden-using-java
			// https://docs.oracle.com/javase/tutorial/essential/io/fileAttr.html
			try {
				Files.setAttribute(dir.toPath(), "dos:hidden", true);
			} catch (IOException | UnsupportedOperationException e) {
				LOGGER.warn("Setting directory to hidden failed: " + dir.getAbsolutePath(), e);
			}
		}
	}

	/**
	 * Create new directory. Fail if it already exists.
	 * 
	 * @param dir Targed directory to check/create
	 * @throws IOException If directory already exists or there are other problems with directory creation
	 */
	synchronized static void createNewDirectory(final File dir) throws IOException {
		if (dir.exists()) {
			throw new IOException(dir.getAbsolutePath() + " already exists");
		} else {
			LOGGER.info("Creating directory: " + dir.getAbsolutePath());
			if (!dir.mkdirs()) {
				throw new IOException("Cannot create " + dir.getAbsolutePath());
			}
		}
	}

	static String uriEncodePath(final String path) {
		return UriUtils.encodePath(path.replace(File.separatorChar, '/'), "UTF-8");
	}
}
