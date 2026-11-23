package com.gratchev.mizoine.repositories;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A repository that the logged-in user has at least one grant in. Unknown repository, unreadable repository file
 * and no grant all give the same 404 (D2, D7).
 */
record GrantedRepository(RepositoryConfig config, RepositoryAccess access) {

	static GrantedRepository find(final RepositoryRegistry registry, final String name, final Principal principal) {
		final RepositoryConfig config = registry.find(name).orElseThrow(GrantedRepository::notFound);
		final RepositoryAccess access = RepositoryAccess.of(config, principal.getName());
		if (!access.hasAccess()) {
			throw notFound();
		}
		return new GrantedRepository(config, access);
	}

	boolean hasProblems() {
		return !config.problems().isEmpty();
	}

	/** The one "not found" for everything a user may not see (D2). */
	static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}
}
