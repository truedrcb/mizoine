package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RelativeRedirectStrategyTest {

	@Test
	void absoluteUrlKeepsOnlyPathAndQuery() {
		assertThat(RelativeRedirectStrategy.relative("https://localhost/notes/?q=1")).isEqualTo("/notes/?q=1");
	}

	@Test
	void hostPortAndFragmentAreDropped() {
		assertThat(RelativeRedirectStrategy.relative("http://localhost:12013/a/b?x=1#top")).isEqualTo("/a/b?x=1");
	}

	@Test
	void encodedCharactersStayEncoded() {
		assertThat(RelativeRedirectStrategy.relative("https://h/Projekte%20alt/%C3%A4/?q=a%26b"))
				.isEqualTo("/Projekte%20alt/%C3%A4/?q=a%26b");
	}

	@Test
	void absoluteUrlWithoutPathBecomesRoot() {
		assertThat(RelativeRedirectStrategy.relative("https://localhost")).isEqualTo("/");
	}

	@Test
	void relativeUrlStaysAsItIs() {
		assertThat(RelativeRedirectStrategy.relative("/login?error")).isEqualTo("/login?error");
	}

	@Test
	void otherHostCannotBeReached() {
		// the saved page is always on this server: no redirect to another host, also not protocol-relative
		assertThat(RelativeRedirectStrategy.relative("https://evil.example/x")).isEqualTo("/x");
		assertThat(RelativeRedirectStrategy.relative("//evil.example/x")).isEqualTo("/x");
		assertThat(RelativeRedirectStrategy.relative("https://localhost//evil.example/x")).isEqualTo("/evil.example/x");
	}

	@Test
	void unparsableUrlGoesToRoot() {
		assertThat(RelativeRedirectStrategy.relative("https://localhost/a b")).isEqualTo("/");
	}
}
