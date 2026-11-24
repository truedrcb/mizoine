package com.gratchev.mizoine.git;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.git.GitStatus.Upstream;

/**
 * Reads {@code git status --porcelain=v2 --branch -z --untracked-files=all} (D36), Git's stable machine format: one
 * record per NUL; a rename's old path follows as a field of its own; names are never quoted. Ignored files are not
 * asked for. See {@code git help status}, "Porcelain Format Version 2".
 */
final class GitStatusParser {

	private static final String OID = "# branch.oid ";
	private static final String HEAD = "# branch.head ";
	private static final String UPSTREAM = "# branch.upstream ";
	private static final String AHEAD_BEHIND = "# branch.ab ";
	/** Fields before the path: ordinary 8, renamed or copied 9, unmerged 10. */
	private static final int ORDINARY = 9;
	private static final int RENAMED = 10;
	private static final int UNMERGED = 11;

	private GitStatusParser() {
	}

	/** @throws GitException for records this format does not have */
	static GitStatus parse(final String output) {
		final String[] fields = output.split("\0");
		final Branch branch = new Branch();
		final List<Change> changes = new ArrayList<>();
		for (int i = 0; i < fields.length; i++) {
			final String record = fields[i];
			if (record.startsWith("#")) {
				branch.read(record);
			} else if (record.startsWith("1 ")) {
				final String[] parts = parts(record, ORDINARY);
				changes.add(new Change(parts[ORDINARY - 1], Optional.empty(), kind(parts[1], 0), kind(parts[1], 1)));
			} else if (record.startsWith("2 ") && i + 1 < fields.length) {
				final String[] parts = parts(record, RENAMED);
				i++;
				changes.add(new Change(parts[RENAMED - 1], Optional.of(fields[i]), kind(parts[1], 0), kind(parts[1], 1)));
			} else if (record.startsWith("u ")) {
				changes.add(new Change(parts(record, UNMERGED)[UNMERGED - 1], Optional.empty(), Kind.CONFLICT, Kind.CONFLICT));
			} else if (record.startsWith("? ")) {
				changes.add(new Change(record.substring(2), Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED));
			} else if (!record.isEmpty()) {
				throw unexpected(record);
			}
		}
		return branch.status(changes);
	}

	/** The header lines ({@code # branch.…}); other headers ({@code # stash}) are skipped. */
	private static final class Branch {

		private Optional<String> head = Optional.empty();
		private boolean hasCommits = true;
		private Optional<String> upstream = Optional.empty();
		/** No {@code # branch.ab} with an upstream: the upstream branch is gone. */
		private boolean gone = true;
		private int ahead;
		private int behind;

		void read(final String record) {
			if (record.startsWith(OID)) {
				hasCommits = !"(initial)".equals(record.substring(OID.length()));
			} else if (record.startsWith(HEAD)) {
				final String name = record.substring(HEAD.length());
				head = "(detached)".equals(name) ? Optional.empty() : Optional.of(name);
			} else if (record.startsWith(UPSTREAM)) {
				upstream = Optional.of(record.substring(UPSTREAM.length()));
			} else if (record.startsWith(AHEAD_BEHIND)) {
				// "+<ahead> -<behind>"
				final String[] counts = record.substring(AHEAD_BEHIND.length()).split(" ");
				gone = false;
				ahead = Integer.parseInt(counts[0].substring(1));
				behind = Integer.parseInt(counts[1].substring(1));
			}
		}

		GitStatus status(final List<Change> changes) {
			return new GitStatus(head, hasCommits, upstream.map(name -> new Upstream(name, gone, ahead, behind)), changes);
		}
	}

	private static String[] parts(final String record, final int count) {
		final String[] parts = record.split(" ", count);
		if (parts.length < count) {
			throw unexpected(record);
		}
		return parts;
	}

	/** @param side 0 = index (staged), 1 = working tree (unstaged) */
	private static Kind kind(final String xy, final int side) {
		return switch (xy.charAt(side)) {
			case '.' -> Kind.UNCHANGED;
			case 'M' -> Kind.MODIFIED;
			case 'T' -> Kind.TYPE_CHANGED;
			case 'A' -> Kind.ADDED;
			case 'D' -> Kind.DELETED;
			case 'R' -> Kind.RENAMED;
			case 'C' -> Kind.COPIED;
			default -> throw unexpected(xy);
		};
	}

	private static GitException unexpected(final String record) {
		return new GitException("Unexpected output of git status: " + record);
	}
}
