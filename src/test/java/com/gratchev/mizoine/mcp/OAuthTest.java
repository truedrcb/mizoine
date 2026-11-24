package com.gratchev.mizoine.mcp;

import static com.gratchev.mizoine.TestUsers.GUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gratchev.mizoine.TestRepositories;
import com.gratchev.mizoine.TestUsers;
import com.gratchev.mizoine.users.PasswordHashing;

import io.modelcontextprotocol.client.McpSyncClient;

/**
 * Step 34: what a chat app ("custom connector", e.g. Claude) does to connect: discovery, OAuth 2.1 authorization code
 * with PKCE, the agent user logging in with Mizoine's login form (D19), the token exchange with the one configured
 * client (D17), then MCP with the token. The token stands for that user, with that user's grants only.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OAuthTest {

	private static final String PUBLIC_URL = "https://mizoine.example";
	/** A chat app's OAuth client as configured in Mizoine: the app knows the secret, Mizoine its hash. */
	private record App(String id, String secret, String callback) {
	}

	private static final App CLAUDE = new App("claude", "claude-secret", "https://claude.ai/api/mcp/auth_callback");
	private static final App OTHER = new App("other", "other-secret", "https://other.example/oauth/callback");

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/knowledge/ideas.md", "# Ideen")
			.file("tree/private/secret.md", "secret")
			.repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n  guest: [knowledge]\n");
	private static final Path USERS = copyOfTestUsers();

	private static Path copyOfTestUsers() {
		try {
			return Files.copy(Path.of("src/test/resources/users-test.yaml"), REPOSITORIES.path("users.yaml"));
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void configuration(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		registry.add("mizoine.users-file", USERS::toString);
		registry.add("mizoine.public-url", () -> PUBLIC_URL);
		final List<App> apps = List.of(CLAUDE, OTHER);
		for (int index = 0; index < apps.size(); index++) {
			final App app = apps.get(index);
			final String client = "mizoine.oauth.clients[" + index + "].";
			registry.add(client + "id", app::id);
			registry.add(client + "secret", () -> PasswordHashing.encoder().encode(app.secret()));
			registry.add(client + "redirect-uris", app::callback);
		}
	}

	@AfterAll
	static void delete() throws IOException {
		REPOSITORIES.delete();
	}

	private final int port;
	/** The phone's browser: keeps cookies, follows no redirects. */
	private final HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NEVER).build();
	/** The chat app's server: no cookies. */
	private final HttpClient app = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	OAuthTest(@LocalServerPort final int port) {
		this.port = port;
	}

	private String url(final String path) {
		return "http://127.0.0.1:" + port + path;
	}

	private HttpResponse<String> get(final HttpClient client, final String path) throws IOException, InterruptedException {
		return client.send(HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : url(path))).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static String form(final Map<String, String> fields) {
		return String.join("&", fields.entrySet().stream()
				.map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8)).toList());
	}

	private static String query(final String url, final String name) {
		final Matcher matcher = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
		return matcher.find() ? java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8) : null;
	}

	// discovery

	@Test
	void protectedResourceMetadataNamesTheAuthorizationServer() throws Exception {
		final String metadata = get(app, "/.well-known/oauth-protected-resource/mcp").body();

		assertThat(metadata).contains("\"resource\":\"" + PUBLIC_URL + "/mcp\"")
				.contains("\"authorization_servers\":[\"" + PUBLIC_URL + "\"]")
				// tokens are not bound to client certificates: no such promise
				.doesNotContain("tls_client_certificate_bound_access_tokens");
	}

	@Test
	void authorizationServerMetadataOffersCodeWithPkce() throws Exception {
		final String metadata = get(app, "/.well-known/oauth-authorization-server").body();

		assertThat(metadata).contains("\"issuer\":\"" + PUBLIC_URL + "\"")
				.contains(PUBLIC_URL + "/oauth2/authorize").contains(PUBLIC_URL + "/oauth2/token").contains("S256");
	}

	@Test
	void mcpWithoutTokenPointsToTheMetadata() throws Exception {
		final HttpResponse<String> response = app.send(HttpRequest.newBuilder(URI.create(url("/mcp")))
						.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
						.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
				.startsWith("Bearer").contains("resource_metadata=\"" + PUBLIC_URL + "/.well-known/oauth-protected-resource/mcp\""));
	}

	// the flow

	/** @return the access token for {@code user}, after the whole authorization code flow with PKCE */
	private String connect(final String user) throws Exception {
		return field(tokens(user, CLAUDE), "access_token");
	}

	private static String field(final String json, final String name) {
		final Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
		assertThat(matcher.find()).as(name + " in " + json).isTrue();
		return matcher.group(1);
	}

	/** @return the token answer (JSON) for {@code user}, after the whole authorization code flow with PKCE */
	private String tokens(final String user, final App app) throws Exception {
		final byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		final String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		final String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
				MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		final String authorize = "/oauth2/authorize?" + form(Map.of("response_type", "code", "client_id", app.id(),
				"redirect_uri", app.callback(), "state", "s1", "code_challenge", challenge, "code_challenge_method", "S256",
				"resource", PUBLIC_URL + "/mcp"));

		// the phone's browser: authorize → login page → log in as the agent user → back → code for the app
		final HttpResponse<String> toLogin = get(browser, authorize);
		assertThat(toLogin.statusCode()).isEqualTo(302);
		final String loginPage = get(browser, "/login").body();
		final Matcher hidden = Pattern.compile("name=\"_csrf\" type=\"hidden\" value=\"([^\"]+)\"").matcher(loginPage);
		assertThat(hidden.find()).isTrue();
		final HttpResponse<String> loggedIn = browser.send(HttpRequest.newBuilder(URI.create(url("/login")))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form(Map.of("username", user, "password", TestUsers.password(user),
						"_csrf", hidden.group(1))))).build(), HttpResponse.BodyHandlers.ofString());
		final String back = loggedIn.headers().firstValue("Location").orElseThrow();
		final HttpResponse<String> toCallback = get(browser, back.startsWith("http") ? back : url(back));
		final String callback = toCallback.headers().firstValue("Location").orElseThrow();
		assertThat(callback).startsWith(app.callback());
		assertThat(query(callback, "state")).isEqualTo("s1");

		// the chat app's server: code + verifier → token
		final HttpResponse<String> token = exchange(app.id(), app.secret(), Map.of("grant_type", "authorization_code",
				"code", query(callback, "code"), "redirect_uri", app.callback(), "code_verifier", verifier));
		assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
		return token.body();
	}

	private HttpResponse<String> exchange(final String client, final String secret, final Map<String, String> fields)
			throws Exception {
		return app.send(HttpRequest.newBuilder(URI.create(url("/oauth2/token")))
				.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
						(client + ":" + secret).getBytes(StandardCharsets.UTF_8)))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(), HttpResponse.BodyHandlers.ofString());
	}

	private McpSyncClient mcp(final String token) {
		return TestMcp.connect(url(""), "Bearer " + token);
	}

	private static String read(final McpSyncClient client, final String path) {
		return TestMcp.text(TestMcp.call(client, "read_file", Map.of("repository", "tree", "path", path)));
	}

	@Test
	void theTokenActsAsTheAgentUserWithItsGrantsOnly() throws Exception {
		try (McpSyncClient client = mcp(connect(GUEST))) {
			assertThat(read(client, "knowledge/ideas.md")).isEqualTo("# Ideen");
			assertThat(read(client, "private/secret.md")).startsWith("error: Not found");
		}
	}

	/** Access tokens live an hour; chat apps renew them with the refresh token, without a new login. */
	@Test
	void theRefreshTokenGivesANewAccessToken() throws Exception {
		final HttpResponse<String> renewed = exchange(CLAUDE.id(), CLAUDE.secret(), Map.of("grant_type", "refresh_token",
				"refresh_token", field(tokens(GUEST, CLAUDE), "refresh_token")));

		assertThat(renewed.statusCode()).as(renewed.body()).isEqualTo(200);
		try (McpSyncClient client = mcp(field(renewed.body(), "access_token"))) {
			assertThat(read(client, "knowledge/ideas.md")).isEqualTo("# Ideen");
		}
	}

	// several chat apps (option 1): each with its own client, secret and callback

	@Test
	void eachConfiguredAppConnectsWithItsOwnClient() throws Exception {
		try (McpSyncClient client = mcp(field(tokens(GUEST, OTHER), "access_token"))) {
			assertThat(read(client, "knowledge/ideas.md")).isEqualTo("# Ideen");
		}
	}

	@Test
	void oneAppsSecretDoesNotWorkForAnother() throws Exception {
		assertThat(exchange(OTHER.id(), CLAUDE.secret(), Map.of("grant_type", "authorization_code", "code", "x",
				"redirect_uri", OTHER.callback())).statusCode()).isEqualTo(401);
	}

	@Test
	void oneAppsCallbackIsNotAllowedForAnother() throws Exception {
		final HttpResponse<String> response = get(browser, "/oauth2/authorize?" + form(Map.of("response_type", "code",
				"client_id", CLAUDE.id(), "redirect_uri", OTHER.callback(), "code_challenge", "abc",
				"code_challenge_method", "S256")));

		assertThat(response.statusCode()).isEqualTo(400);
	}

	// refusals

	@Test
	void wrongClientSecretGetsNoToken() throws Exception {
		assertThat(exchange(CLAUDE.id(), "wrong", Map.of("grant_type", "authorization_code", "code", "x",
				"redirect_uri", CLAUDE.callback()))
				.statusCode()).isEqualTo(401);
	}

	@Test
	void unregisteredRedirectUriIsRefused() throws Exception {
		final HttpResponse<String> response = get(browser, "/oauth2/authorize?" + form(Map.of("response_type", "code",
				"client_id", CLAUDE.id(), "redirect_uri", "https://evil.example/callback", "code_challenge", "abc",
				"code_challenge_method", "S256")));

		assertThat(response.statusCode()).isEqualTo(400);
	}

	@Test
	void codeWithoutPkceVerifierGetsNoToken() throws Exception {
		assertThat(exchange(CLAUDE.id(), CLAUDE.secret(), Map.of("grant_type", "authorization_code", "code", "made-up",
				"redirect_uri", CLAUDE.callback()))
				.statusCode()).isEqualTo(400);
	}

	@Test
	void forgedTokenIsRefused() {
		assertThatThrownBy(() -> mcp("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhcnRlbSJ9.forged")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void tokensOfARemovedUserStopWorking() throws Exception {
		final String token = connect(GUEST);
		final String withoutGuest = Files.readString(USERS).replaceAll("(?s)  - username: guest.*", "");
		final String original = Files.readString(USERS);
		try {
			Files.writeString(USERS, withoutGuest);

			assertThatThrownBy(() -> mcp(token)).isInstanceOf(RuntimeException.class);
		} finally {
			Files.writeString(USERS, original);
		}
	}
}
