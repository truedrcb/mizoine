package com.gratchev.mizoine.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts failed logins per client address. After {@code maxFailures} failures, each less than
 * {@code window} after the previous one, the address is blocked until {@code window} after the last failure.
 * A successful login resets the count. Works the same for HTTP Basic and the login form.
 * <p>
 * Per address, not per username: otherwise anyone could lock out a known user.
 */
public class LoginAttemptLimiter {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

	/** Above this many tracked addresses, expired ones are removed on the next failure. */
	private static final int PRUNE_THRESHOLD = 1000;

	private record Failures(int count, Instant last) {
	}

	private final Clock clock;
	private final int maxFailures;
	private final Duration window;
	private final Map<String, Failures> failures = new ConcurrentHashMap<>();

	public LoginAttemptLimiter(final Clock clock, final int maxFailures, final Duration window) {
		this.clock = clock;
		this.maxFailures = maxFailures;
		this.window = window;
	}

	public void failed(final String address) {
		final Instant now = clock.instant();
		if (failures.size() > PRUNE_THRESHOLD) {
			failures.values().removeIf(entry -> isExpired(entry, now));
		}
		final Failures updated = failures.compute(address, (key, entry) ->
				entry == null || isExpired(entry, now) ? new Failures(1, now) : new Failures(entry.count() + 1, now));
		if (updated.count() == maxFailures) {
			log.warn("{} failed logins from {}: further login attempts from there are rejected for {}",
					maxFailures, address, window);
		}
	}

	public void succeeded(final String address) {
		failures.remove(address);
	}

	public boolean isBlocked(final String address) {
		return !retryAfter(address).isZero();
	}

	/** @return how long the address stays blocked; zero if it is not blocked */
	public Duration retryAfter(final String address) {
		final Failures entry = failures.get(address);
		final Instant now = clock.instant();
		if (entry == null || entry.count() < maxFailures || isExpired(entry, now)) {
			return Duration.ZERO;
		}
		return Duration.between(now, entry.last().plus(window));
	}

	int trackedAddresses() {
		return failures.size();
	}

	private boolean isExpired(final Failures entry, final Instant now) {
		return !now.isBefore(entry.last().plus(window));
	}
}
