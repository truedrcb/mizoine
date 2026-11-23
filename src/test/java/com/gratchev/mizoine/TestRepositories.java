package com.gratchev.mizoine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * A repositories folder for one test class, in a temporary directory: repository files ({@code <name>.yaml}) and
 * their trees, written as the class is loaded. Paths are relative to the repositories folder, so a repository
 * file with {@code path: tree} has its root in {@code tree/}.
 *
 * <pre>{@code
 * private static final TestRepositories REPOSITORIES = TestRepositories.create()
 *         .file("tree/notes/plan.md", "# Plan")
 *         .repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n");
 *
 * @DynamicPropertySource static void repositories(final DynamicPropertyRegistry registry) { REPOSITORIES.register(registry); }
 * @AfterAll static void delete() throws IOException { REPOSITORIES.delete(); }
 * }</pre>
 */
public final class TestRepositories {

	/** Access for the test users in most tests: artem everything. */
	public static final String ARTEM_EVERYTHING = "access:\n  artem: [\"/\"]\n";

	private final Path base;

	private TestRepositories(final Path base) {
		this.base = base;
	}

	public static TestRepositories create() {
		try {
			return new TestRepositories(Files.createTempDirectory("mizoine-test"));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** {@code <name>.yaml} with this content. */
	public TestRepositories repository(final String name, final String yaml) {
		return file(name + ".yaml", yaml);
	}

	/** A text file (UTF-8), with its folders. */
	public TestRepositories file(final String path, final String content) {
		return write(path, () -> Files.writeString(prepared(path), content));
	}

	public TestRepositories file(final String path, final byte[] content) {
		return write(path, () -> Files.write(prepared(path), content));
	}

	public TestRepositories folder(final String path) {
		return write(path, () -> Files.createDirectories(path(path)));
	}

	/** A symbolic link at {@code path} to {@code target} (both relative to the repositories folder). */
	public TestRepositories link(final String path, final String target) {
		return write(path, () -> Files.createSymbolicLink(prepared(path), path(target)));
	}

	public Path path(final String path) {
		return base.resolve(path);
	}

	public void register(final DynamicPropertyRegistry registry) {
		registry.add("mizoine.repositories-dir", base::toString);
	}

	public void delete() throws IOException {
		try (Stream<Path> paths = Files.walk(base)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	private Path prepared(final String path) throws IOException {
		Files.createDirectories(path(path).getParent());
		return path(path);
	}

	@FunctionalInterface
	private interface Write {
		Object run() throws IOException;
	}

	private TestRepositories write(final String path, final Write write) {
		try {
			write.run();
			return this;
		} catch (final IOException e) {
			throw new UncheckedIOException("cannot create " + path, e);
		}
	}
}
