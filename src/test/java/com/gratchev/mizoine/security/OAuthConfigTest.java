package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.gratchev.mizoine.MizoineProperties;

/** Step 34: OAuth needs the public address; a missing one is said clearly at startup. */
class OAuthConfigTest {

	private static MizoineProperties withPublicUrl(final String publicUrl) {
		return new MizoineProperties("users.yaml", new MizoineProperties.LoginLimit(5, Duration.ofMinutes(5)), ".",
				DataSize.ofMegabytes(100), publicUrl, new MizoineProperties.OAuth(
						List.of(new MizoineProperties.Client("claude", "{bcrypt}x", List.of("https://claude.ai/cb")))));
	}

	@Test
	void resourceIsTheMcpEndpointAtThePublicAddress() {
		assertThat(OAuthConfig.resource(withPublicUrl("https://mizoine.example/"))).isEqualTo("https://mizoine.example/mcp");
	}

	@Test
	void missingPublicAddressIsSaid() {
		for (final String missing : new String[] {null, " "}) {
			assertThatThrownBy(() -> OAuthConfig.publicUrl(withPublicUrl(missing)))
					.hasMessageContaining("set mizoine.public-url");
		}
	}
}
