package com.gratchev.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FileUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

	/**
	 * Write to text file using UTF-8 as encoding. Existing files are being overwritten.
	 * 
	 * @param fullText Complete file content
	 * @param targetFile File to write to
	 * @throws IOException
	 */
	public static void overwriteTextFile(final String fullText, final File targetFile) throws IOException {
		if (LOGGER.isDebugEnabled()) {
			if (targetFile.exists()) {
				LOGGER.debug("Overwriting file: " + targetFile.getAbsolutePath());
			} else {
				LOGGER.debug("Creating file: " + targetFile.getAbsolutePath());
			}
		}
		final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), 
				StandardCharsets.UTF_8));
		try {
			writer.write(fullText);
		} finally {
			writer.close();
		}
	}
	
	
	public static String readTextFile(final File targetFile) throws IOException {
		return Streams.asString(new FileInputStream(targetFile), StandardCharsets.UTF_8.name());
	}
	
	
	/**
	 * https://community.oracle.com/blogs/kohsuke/2007/04/25/how-convert-javaneturl-javaiofile
	 */
	public static File urlToFile(URL url) {
		try {
			return new File(url.toURI());
		} catch(URISyntaxException e) {
			return new File(url.getPath());
		}
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
		assertTrue(root.getAbsolutePath(), !root.exists());
		LOGGER.info("Directory suscessfully removed.");
	}

	private static void assertTrue(final String message, final boolean expected) throws IOException {
		if (!expected) {
			throw new IOException(message);
		}
	}

}
