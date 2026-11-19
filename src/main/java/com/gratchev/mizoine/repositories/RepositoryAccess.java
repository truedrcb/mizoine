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

	private RepositoryAccess(final List<FolderGrant> grants) {
		this.grants = grants;
	}

	/** @param username {@code null} for an anonymous user: no access */
	public static RepositoryAccess of(final RepositoryConfig config, final String username) {
		final List<FolderGrant> grants = username == null ? null : config.grants().get(username);
		return new RepositoryAccess(grants == null ? List.of() : grants);
	}

	/** @return the user has at least one grant in this repository */
	public boolean hasAccess() {
		return !grants.isEmpty();
	}

	/** @return the user may access the whole repository (and so may see its configuration problems, D8) */
	public boolean isWholeRepository() {
		return grants.stream().anyMatch(FolderGrant::isWholeRepository);
	}

	public boolean canEnter(final List<String> folder) {
		if (folder.stream().anyMatch(FolderNames::isHidden)) {
			return false;
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

	private static boolean startsWith(final List<String> path, final List<String> prefix) {
		return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
	}
}
