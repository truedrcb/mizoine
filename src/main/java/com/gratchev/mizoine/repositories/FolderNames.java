package com.gratchev.mizoine.repositories;

/**
 * The one rule for folder names in repositories, used for grants, access and paths.
 * <ul>
 * <li>Hidden (D3): starting with {@code .} (such as {@code .git}, also {@code .} and {@code ..}), or containing
 * {@code %}: Spring Security's firewall rejects an encoded {@code %} in URLs, so such folders could be listed
 * but never opened.</li>
 * <li>Plain: a single, visible name — not empty, not hidden, no separator ({@code /}, {@code \}) and no NUL.</li>
 * </ul>
 */
public final class FolderNames {

	private FolderNames() {
	}

	public static boolean isHidden(final String name) {
		return name.startsWith(".") || name.indexOf('%') >= 0;
	}

	static boolean isPlain(final String name) {
		return !name.isEmpty()
				&& !isHidden(name)
				&& name.indexOf('/') < 0
				&& name.indexOf('\\') < 0
				&& name.indexOf('\0') < 0;
	}
}
