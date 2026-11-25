package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Step 47, D38: the author of a commit is the Mizoine user, as Git writes it: {@code Name <email>}. */
class AuthorTest {

	@Test
	void theUserWithTheEmailFromTheUsersFile() {
		assertThat(Author.of("artem", Optional.of("artem@example.org")).git()).isEqualTo("artem <artem@example.org>");
	}

	@Test
	void withoutEmailTheUserAtMizoine() {
		assertThat(Author.of("guest", Optional.empty()).git()).isEqualTo("guest <guest@mizoine>");
	}

	@Test
	void whatWouldBreakGitsFormIsLeftOut() {
		// '<', '>' and line breaks end the name or the email in Git's "Name <email>"
		assertThat(Author.of("a<b>\nc", Optional.of("x>@<y\n")).git()).isEqualTo("abc <x@y>");
	}
}
