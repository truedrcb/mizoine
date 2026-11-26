package com.gratchev.mizoine.uploads;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Step 75 (D83–D85, D88): upload links in memory: valid for 15 minutes and a number of files, per user at most 20
 * open ones. The time comes from a clock the test moves.
 */
class UploadLinksTest {

	/** A clock the test moves forward. */
	private static final class TestClock extends Clock {

		private Instant now = Instant.parse("2026-11-26T10:00:00Z");

		void pass(final Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final ZoneId zone) {
			return this;
		}
	}

	private final TestClock clock = new TestClock();
	private final UploadLinks links = new UploadLinks(clock);

	private UploadLink create(final String user, final Optional<String> name) {
		return ((UploadLinks.Created) links.create(user, "notes", List.of("receipts"), name)).link();
	}

	@Test
	void aLinkHoldsWhereAndForWhomItUploads() {
		final UploadLink link = create("agent", Optional.empty());

		assertThat(link.user()).isEqualTo("agent");
		assertThat(link.repository()).isEqualTo("notes");
		assertThat(link.folder()).containsExactly("receipts");
		assertThat(link.name()).isEmpty();
		assertThat(link.expires()).isEqualTo(Instant.parse("2026-11-26T10:15:00Z"));
		assertThat(link.filesLeft()).isEqualTo(UploadLinks.MAX_FILES);
		assertThat(links.find(link.token())).contains(link);
	}

	@Test
	void tokensAreLongRandomAndUrlSafeAndTheIdIsNotPartOfThem() {
		final Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 20; i++) {
			final UploadLink link = create("user" + i, Optional.empty());
			// 256 bits in base64url without padding
			assertThat(link.token()).matches("[A-Za-z0-9_-]{43}");
			assertThat(link.id()).matches("[0-9a-f]{8}");
			assertThat(link.token()).doesNotContain(link.id());
			tokens.add(link.token());
		}
		assertThat(tokens).hasSize(20);
		// what a log prints: the id, never the token (D88)
		final UploadLink logged = create("x", Optional.empty());
		assertThat(logged.toString()).contains(logged.id()).doesNotContain(logged.token());
	}

	@Test
	void validUntilItExpires() {
		final UploadLink link = create("agent", Optional.empty());

		clock.pass(Duration.ofMinutes(15).minusSeconds(1));
		assertThat(links.find(link.token())).isPresent();
		clock.pass(Duration.ofSeconds(1));
		assertThat(links.find(link.token())).isEmpty();
		assertThat(links.reserve(link.token())).isEmpty();
	}

	@Test
	void usedUpAfterItsFilesAndAGivenBackUseCountsAgain() {
		final UploadLink link = create("agent", Optional.empty());
		for (int i = 0; i < UploadLinks.MAX_FILES - 1; i++) {
			assertThat(links.reserve(link.token())).isPresent();
		}
		final Optional<UploadLink> last = links.reserve(link.token());
		assertThat(last).isPresent();
		assertThat(links.reserve(link.token())).isEmpty();
		assertThat(links.find(link.token())).isEmpty();

		// a refused write gives its use back
		links.release(link.token());
		assertThat(links.find(link.token())).map(UploadLink::filesLeft).contains(1);
	}

	@Test
	void aFixedNameTakesOneFile() {
		final UploadLink link = create("agent", Optional.of("receipt.jpg"));

		assertThat(link.name()).contains("receipt.jpg");
		assertThat(link.filesLeft()).isEqualTo(1);
		assertThat(links.reserve(link.token())).isPresent();
		assertThat(links.reserve(link.token())).isEmpty();
	}

	@Test
	void atMostTwentyOpenLinksPerUser() {
		for (int i = 0; i < UploadLinks.MAX_OPEN_PER_USER; i++) {
			create("agent", Optional.empty());
		}

		assertThat(links.create("agent", "notes", List.of(), Optional.empty())).isInstanceOf(UploadLinks.TooMany.class);
		assertThat(links.create("other", "notes", List.of(), Optional.empty())).isInstanceOf(UploadLinks.Created.class);
		// expired ones do not count
		clock.pass(Duration.ofMinutes(15));
		assertThat(links.create("agent", "notes", List.of(), Optional.empty())).isInstanceOf(UploadLinks.Created.class);
	}

	@Test
	void anUnknownTokenIsNoLink() {
		assertThat(links.find("no-such-token")).isEmpty();
		assertThat(links.reserve("no-such-token")).isEmpty();
		links.release("no-such-token");
		assertThat(links.find("no-such-token")).isEmpty();
	}
}
