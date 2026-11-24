package com.gratchev.mizoine.repositories;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A repository that the logged-in user has at least one grant in. Unknown repository, unreadable repository file
 * and no grant all give the same 404 (D2, D7).
 */
record GrantedRepository(RepositoryConfig config, RepositoryAccess access) {

	/** @return the repository, if the user has at least one grant in it */
	static Optional<GrantedRepository> find(final RepositoryRegistry registry, final String name, final String user) {
		return registry.find(name)
				.map(config -> new GrantedRepository(config, RepositoryAccess.of(config, user)))
				.filter(granted -> granted.access().hasAccess());
	}

	boolean hasProblems() {
		return !config.problems().isEmpty();
	}

	/** The one "not found" for everything a user may not see (D2). */
	static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}
}
