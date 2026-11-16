package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

	private static final Duration WINDOW = Duration.ofMinutes(5);
	private static final String ADDRESS = "203.0.113.7";

	/** A clock the test moves forward. */
	static final class TestClock extends Clock {
		private Instant now = Instant.parse("2026-11-16T12:00:00Z");

		void advance(final Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final ZoneId zone) {
			return this;
		}
	}

	private final TestClock clock = new TestClock();
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, WINDOW);

	private void fail(final String address, final int times) {
		for (int i = 0; i < times; i++) {
			limiter.failed(address);
		}
	}

	@Test
	void unknownAddressIsNotBlocked() {
		assertThat(limiter.isBlocked(ADDRESS)).isFalse();
	}

	@Test
	void blockedAfterMaxFailures() {
		fail(ADDRESS, 4);
		assertThat(limiter.isBlocked(ADDRESS)).isFalse();

		fail(ADDRESS, 1);
		assertThat(limiter.isBlocked(ADDRESS)).isTrue();
	}

	@Test
	void blockEndsOneWindowAfterLastFailure() {
		fail(ADDRESS, 5);

		clock.advance(WINDOW.minusSeconds(1));
		assertThat(limiter.isBlocked(ADDRESS)).isTrue();
		assertThat(limiter.retryAfter(ADDRESS)).isEqualTo(Duration.ofSeconds(1));

		clock.advance(Duration.ofSeconds(1));
		assertThat(limiter.isBlocked(ADDRESS)).isFalse();
	}

	@Test
	void failuresOlderThanWindowAreForgotten() {
		fail(ADDRESS, 4);
		clock.advance(WINDOW.plusSeconds(1));

		fail(ADDRESS, 4);

		assertThat(limiter.isBlocked(ADDRESS)).isFalse();
	}

	@Test
	void successResetsFailures() {
		fail(ADDRESS, 4);
		limiter.succeeded(ADDRESS);

		fail(ADDRESS, 4);

		assertThat(limiter.isBlocked(ADDRESS)).isFalse();
	}

	@Test
	void addressesAreIndependent() {
		fail(ADDRESS, 5);

		assertThat(limiter.isBlocked("198.51.100.1")).isFalse();
	}

	@Test
	void retryAfterIsZeroWhenNotBlocked() {
		fail(ADDRESS, 2);

		assertThat(limiter.retryAfter(ADDRESS)).isEqualTo(Duration.ZERO);
	}

	@Test
	void expiredAddressesAreRemovedSoMemoryStaysBounded() {
		for (int i = 0; i < 2000; i++) {
			limiter.failed("10.0." + (i / 256) + "." + (i % 256));
		}
		clock.advance(WINDOW.plusSeconds(1));

		limiter.failed(ADDRESS);

		assertThat(limiter.trackedAddresses()).isEqualTo(1);
	}
}
