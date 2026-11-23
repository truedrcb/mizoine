package com.gratchev.mizoine.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The version of a file on disk: its size and modification time. Every write changes it, also writes by editors
 * outside Mizoine. One rule for all: reloading the users file and repository files when they change, and the
 * HTTP {@code ETag} of the file API ({@code If-Match}).
 */
public record FileVersion(long size, FileTime modified) {

	/** @return the version of a regular file; empty if it is missing, not a regular file or unreadable */
	public static Optional<FileVersion> of(final Path file) {
		try {
			final BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
			return attributes.isRegularFile()
					? Optional.of(new FileVersion(attributes.size(), attributes.lastModifiedTime()))
					: Optional.empty();
		} catch (final IOException e) {
			return Optional.empty();
		}
	}

	/** Strong HTTP ETag. */
	public String etag() {
		return "\"" + Long.toHexString(size) + "-" + Long.toHexString(modified.to(TimeUnit.NANOSECONDS)) + "\"";
	}

	/**
	 * Whether an {@code If-Match} names this version. Compares the tag values, so a weak tag counts too:
	 * compressing proxies and CDNs (Cloudflare, nginx with gzip) turn the strong ETag into {@code W/"…"} on the way
	 * to the client, which sends it back like that.
	 */
	public boolean isNamedBy(final String ifMatch) {
		return (ifMatch.startsWith("W/") ? ifMatch.substring(2) : ifMatch).equals(etag());
	}
}
