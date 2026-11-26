package com.gratchev.mizoine.repositories;

import com.gratchev.mizoine.tags.TagsFile;

/**
 * The one rule for folder names in repositories, used for grants, access and paths.
 * <ul>
 * <li>Hidden (D3): starting with {@code .} (such as {@code .git}, also {@code .} and {@code ..}), or containing
 * {@code %}: Spring Security's firewall rejects an encoded {@code %} in URLs, so such folders could be listed
 * but never opened.</li>
 * <li>The one exception: a folder's tags file {@code .tags} is not hidden, a text file like any other; a folder may
 * not have that name (step 70, D81).</li>
 * <li>Plain: a single, visible name — not empty, not hidden, no separator ({@code /}, {@code \}) and no NUL.</li>
 * </ul>
 */
public final class FolderNames {

	private FolderNames() {
	}

	/**
	 * Hidden names (D3) start with {@code .} or contain {@code %}; the one exception is a folder's tags file
	 * {@code .tags}, a text file like any other (step 70, D81).
	 */
	public static boolean isHidden(final String name) {
		return (name.startsWith(".") || name.indexOf('%') >= 0) && !TagsFile.NAME.equals(name);
	}

	/** A plain name a folder may have: not {@code .tags}, which would hide the tags of the folder it is in (D81). */
	static boolean isPlainFolder(final String name) {
		return isPlain(name) && !TagsFile.NAME.equals(name);
	}

	static boolean isPlain(final String name) {
		return !name.isEmpty()
				&& !isHidden(name)
				&& name.indexOf('/') < 0
				&& name.indexOf('\\') < 0
				&& name.indexOf('\0') < 0;
	}
}
