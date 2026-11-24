package com.gratchev.mizoine.repositories;

import java.util.List;

/**
 * What one user may do in one repository. Folders are path segments relative to the repository root
 * (empty list = root).
 * <ul>
 * <li>A grant includes its folder and everything below it.</li>
 * <li>Folders <em>above</em> a granted folder may be entered too, but show only the way down to the
 * granted folders (D4).</li>
 * <li>Hidden names ({@link FolderNames}: starting with {@code .} such as {@code .git}, or containing {@code %})
 * are never visible nor enterable (D3), whatever the grants say.</li>
 * <li>Usernames and folder names are case-sensitive.</li>
 * </ul>
 */
public final class RepositoryAccess {

	private final List<FolderGrant> grants;
	/** The grant {@code .git} (step 43). */
	private final boolean git;

	private RepositoryAccess(final List<FolderGrant> grants, final boolean git) {
		this.grants = grants;
		this.git = git;
	}

	/** @param username {@code null} for an anonymous user: no access */
	public static RepositoryAccess of(final RepositoryConfig config, final String username) {
		final List<FolderGrant> grants = username == null ? null : config.grants().get(username);
		return new RepositoryAccess(grants == null ? List.of() : grants, username != null && config.gitUsers().contains(username));
	}

	/** @return the user has at least one grant in this repository, a folder or Git */
	public boolean hasAccess() {
		return !grants.isEmpty() || git;
	}

	/** @return the user may use the Git page of the repository: grant {@code .git} or {@code /} (step 43, D33) */
	public boolean canUseGit() {
		return git || isWholeRepository();
	}

	/** @return the user may access the whole repository (and so may see its configuration problems, D8) */
	public boolean isWholeRepository() {
		return grants.stream().anyMatch(FolderGrant::isWholeRepository);
	}

	/** The root may also be entered with Git access alone: it is above the Git page, as above a grant (D4). */
	public boolean canEnter(final List<String> folder) {
		if (folder.stream().anyMatch(FolderNames::isHidden)) {
			return false;
		}
		if (folder.isEmpty() && git) {
			return true;
		}
		return grants.stream().anyMatch(grant -> startsWith(folder, grant.segments()) || startsWith(grant.segments(), folder));
	}

	/**
	 * @param children names of the subfolders of {@code folder}, in the order to keep
	 * @return those the user may see: all inside a granted folder, only the way down above one
	 */
	public List<String> visibleChildren(final List<String> folder, final List<String> children) {
		if (!canEnter(folder)) {
			return List.of();
		}
		if (grants.stream().anyMatch(grant -> startsWith(folder, grant.segments()))) {
			return children.stream().filter(child -> !FolderNames.isHidden(child)).toList();
		}
		// above granted folders: only the next step towards one of them
		return children.stream()
				.filter(child -> !FolderNames.isHidden(child))
				.filter(child -> grants.stream().anyMatch(grant -> grant.segments().size() > folder.size()
						&& startsWith(grant.segments(), folder)
						&& grant.segments().get(folder.size()).equals(child)))
				.toList();
	}

	/**
	 * @param files names of the files in {@code folder}, in the order to keep
	 * @return those the user may see: all inside a granted folder, none above one (only the way down, D4)
	 */
	public List<String> visibleFiles(final List<String> folder, final List<String> files) {
		if (!canEnter(folder) || grants.stream().noneMatch(grant -> startsWith(folder, grant.segments()))) {
			return List.of();
		}
		return files.stream().filter(file -> !FolderNames.isHidden(file)).toList();
	}

	/** @param file path segments of a file: readable when it would be listed in its folder */
	public boolean canReadFile(final List<String> file) {
		return !file.isEmpty()
				&& !visibleFiles(file.subList(0, file.size() - 1), List.of(file.getLast())).isEmpty();
	}

	/**
	 * @param folder where a file or folder would be created, replaced or deleted
	 * @return the folder lies inside a granted folder and is not hidden; folders above a grant stay read-only (D4)
	 */
	public boolean canWriteIn(final List<String> folder) {
		return folder.stream().noneMatch(FolderNames::isHidden)
				&& grants.stream().anyMatch(grant -> startsWith(folder, grant.segments()));
	}

	/** @param entry a file or folder to be created, replaced, moved or deleted: its folder must be writable */
	public boolean canChange(final List<String> entry) {
		return !entry.isEmpty() && canWriteIn(entry.subList(0, entry.size() - 1));
	}

	/** @return the folder is exactly one of the user's granted folders (the root for a whole-repository grant) */
	public boolean isGrantedFolder(final List<String> folder) {
		return grants.stream().anyMatch(grant -> grant.segments().equals(folder));
	}

	private static boolean startsWith(final List<String> path, final List<String> prefix) {
		return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
	}
}
