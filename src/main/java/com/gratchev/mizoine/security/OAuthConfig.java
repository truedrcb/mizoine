package com.gratchev.mizoine.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.mcp.McpPaths;
import com.gratchev.mizoine.users.KnownUsers;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * OAuth for agents in chat apps (step 34, T2): Mizoine is its own authorization server (Spring's) and accepts its
 * tokens at {@code /mcp}. Only when OAuth clients are configured ({@code mizoine.oauth.clients}).
 * <ul>
 * <li>D17: the clients from the configuration, one per chat app, no open registration; authorization code with
 * PKCE, no consent page.</li>
 * <li>D19: the login page is Mizoine's own form; the agent user logs in, and the token stands for that user.</li>
 * <li>D18: the signing key and all authorizations live in memory: after a restart, chat apps log in again.</li>
 * <li>A token is meant for {@code <public-url>/mcp} only (audience), and stops working when its user is removed
 * from the users file.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty("mizoine.oauth.clients[0].id")
class OAuthConfig {

	/** The OAuth endpoints only; the rest of Mizoine is {@link SecurityConfig}'s chain. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	SecurityFilterChain authorizationServerChain(final HttpSecurity http) {
		http.oauth2AuthorizationServer(server -> http.securityMatcher(server.getEndpointsMatcher()))
				.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
				// not logged in at the authorization endpoint: Mizoine's login page, then back (D19)
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
		return http.build();
	}

	/** D17: the clients from the settings, one per chat app; no open registration. */
	@Bean
	RegisteredClientRepository registeredClients(final MizoineProperties properties) {
		return new InMemoryRegisteredClientRepository(properties.oauth().clients().stream()
				.map(OAuthConfig::registered)
				.toList());
	}

	private static RegisteredClient registered(final MizoineProperties.Client client) {
		return RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId(client.id())
				.clientSecret(client.secret())
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUris(uris -> uris.addAll(client.redirectUris()))
				.clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(Duration.ofHours(1))
						.refreshTokenTimeToLive(Duration.ofDays(30))
						.reuseRefreshTokens(false)
						.build())
				.build();
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(final MizoineProperties properties) {
		return AuthorizationServerSettings.builder().issuer(publicUrl(properties)).build();
	}

	/** D18: generated at startup, kept in memory. */
	@Bean
	JWKSource<SecurityContext> signingKey() {
		final KeyPair keys = rsaKeys();
		final RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
				.privateKey((RSAPrivateKey) keys.getPrivate())
				.keyID(UUID.randomUUID().toString())
				.build();
		return new ImmutableJWKSet<>(new JWKSet(key));
	}

	/** Tokens are for the MCP endpoint only. */
	@Bean
	OAuth2TokenCustomizer<JwtEncodingContext> audience(final MizoineProperties properties) {
		return context -> context.getClaims().audience(List.of(resource(properties)));
	}

	/** Checks Mizoine's own tokens: signature, time, issuer, audience, and that the user still exists. */
	@Bean
	JwtDecoder jwtDecoder(final JWKSource<SecurityContext> signingKey, final MizoineProperties properties,
			final KnownUsers users) {
		final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(signingKey).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(publicUrl(properties)),
				claim("the token is not for " + resource(properties), jwt -> jwt.getAudience().contains(resource(properties))),
				claim("the user of the token no longer exists", jwt -> users.exists(jwt.getSubject()))));
		return decoder;
	}

	static String publicUrl(final MizoineProperties properties) {
		if (properties.publicUrl() == null || properties.publicUrl().isBlank()) {
			throw new IllegalStateException("mizoine.oauth.clients are set: set mizoine.public-url too, e.g. https://mizoine.example.org");
		}
		return properties.publicUrl().replaceFirst("/+$", "");
	}

	static String resource(final MizoineProperties properties) {
		return publicUrl(properties) + McpPaths.ENDPOINT;
	}

	private static OAuth2TokenValidator<Jwt> claim(final String description, final java.util.function.Predicate<Jwt> test) {
		return jwt -> test.test(jwt)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
	}

	private static KeyPair rsaKeys() {
		try {
			final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
