# Mizoine 1.0 — Development Plan

**Status** (updated: 2026-11-16): steps 0–13 done (109 tests green). All planned steps are done; see "Open decisions" for follow-ups.

This plan builds the first slice of the "minimal server" from [BACKLOG.md §7](BACKLOG.md):
a web server that only configured users can access. Later features (file access, Git, AI roles)
are built on top of it.

Steps are numbered so you can refer to them ("do step 4"). Every step follows TDD:
**Red** (write a failing test and run it) → **Green** (write the least code that makes it pass) →
**Refactor** (clean up while tests stay green). A step is done only when `mvn verify` passes.

---

## Requirements

| # | Requirement |
|---|-------------|
| R1 | Java + Maven application on a current, supported framework |
| R2 | Web server serving a "Hello World" page |
| R3 | Only a few configured users can get in; everyone else is rejected |
| R4 | Users are configured in a text file (YAML) that a person can edit by hand |

## Technology decisions

| Topic | Choice | Why |
|-------|--------|-----|
| Language | **Java 25** (current LTS) | Long support window, records, pattern matching |
| Framework | **Spring Boot 4.1.x** (4.1.2 is the latest GA on Maven Central as of 2026-11-16; move to 4.2.x when it is GA) | Most widely used Java web stack, security patches come fast, Spring Security built in |
| Build | **Maven 3.9+**, installed (no Maven Wrapper) | No generated `mvnw`/`mvnw.cmd` scripts in the repo; `maven-enforcer-plugin` fails the build with a clear message on Maven < 3.9 or Java < 25 |
| Web | Spring MVC (`spring-boot-starter-webmvc`, the Boot 4 name), plain HTML template (Thymeleaf) | Simple and enough for a hello page |
| Security | Spring Security: form login for browsers + HTTP Basic for scripts/curl | Both ways are standard and tested |
| Passwords | Stored as **BCrypt hashes** with `{bcrypt}` prefix (Spring `DelegatingPasswordEncoder`) | The file is editable, but plain-text passwords are never stored |
| Users file | **YAML** (`users.yaml`), path set by property `mizoine.users-file` | Easier to read and edit by hand than JSON, allows comments |
| Tests | JUnit 5, AssertJ, Spring Boot Test, MockMvc (`spring-boot-starter-webmvc-test`), `spring-security-test` | Standard Spring testing stack |
| Package | `com.gratchev.mizoine` | Same as the archived version |
| Code style | `final` on fields, locals and parameters (not lambda parameters); Checkstyle `FinalLocalVariable` + `FinalParameters` run in `validate` and fail the build | Decided 2026-11-16 |

### Users file format

```yaml
# users.yaml — edit by hand. Generate a hash with:
#   java -jar mizoine.jar hash-password
users:
  - username: artem
    password: "{bcrypt}$2a$12$...."
  - username: guest
    password: "{bcrypt}$2a$12$...."
```

Rules:
- `username` must be non-empty and unique.
- `password` must be a BCrypt hash with the `{bcrypt}` prefix (step 5). Plain-text values and `{noop}` are rejected at load time.
- The real `users.yaml` is **not committed** (git-ignored). A `users.example.yaml` is committed.

### Error behaviour

- File missing or invalid **at startup** → application fails to start with a clear message.
- File becomes invalid **while running** (step 9) → keep the last valid users, log an error.
- Zero users in a valid file → allowed, but nobody can log in (logged as a warning).

---

## Steps

### 0. Prerequisites  ☑
- Install JDK 25 (e.g. `sdk install java 25-tem` or the distro package). Java and Maven are **not** installed in the current WSL environment.
- Install Maven 3.9+.
- *Done:* Temurin JDK 25.0.5 and Maven 3.9.16 unpacked to `~/.local/opt/` (no sudo available). Not on `PATH`; before building run
  `export JAVA_HOME=$(ls -d ~/.local/opt/jdk-25*); export PATH=$JAVA_HOME/bin:$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH`.

### 1. Project skeleton  ☑
- Create `pom.xml` at the repo root: parent `spring-boot-starter-parent` 4.1.x, Java 25,
  starters `web`, `security`, `thymeleaf`, `validation`; test deps `spring-boot-starter-test`, `spring-security-test`.
- No Maven Wrapper (decided 2026-11-16). Instead, `maven-enforcer-plugin` requires Maven `[3.9,)` and Java `[25,)`.
- Main class `com.gratchev.mizoine.MizoineApplication`.
- **Red:** `MizoineApplicationTests.contextLoads()` (`@SpringBootTest`) — fails until the app exists.
- **Green:** app class + minimal `application.yaml`.
- Done when: `mvn verify` is green.
- *Changed from the plan:* only `webmvc` and `thymeleaf` added (Boot 4 renamed `web` → `webmvc`). `security` and `spring-security-test` are added in step 5, `validation` in step 4, when a test first needs them.

### 2. Hello World page (security off for this step only)  ☑
- **Red:** `HelloControllerTest` (`@WebMvcTest`): `GET /` returns 200, view shows `Hello, World`.
  *(No `@WithMockUser` needed: security is not on the classpath yet.)* `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure` in Boot 4.
- **Green:** `HelloController` + `templates/hello.html`.
- Refactor: nothing expected.

### 3. Users file model and parser  ☑
Pure unit tests, no Spring context. Use `@TempDir` for files.
- **Red** — `UsersFileParserTest`:
  1. valid file with 2 users → 2 entries with correct usernames and hashes
  2. empty `users:` list → empty result
  3. missing file → `UsersFileException` naming the path
  4. malformed YAML → `UsersFileException`
  5. duplicate username → `UsersFileException` naming the user (exact match only: `artem` and `Artem` are two different users, see 4.3)
  6. blank username → `UsersFileException`
  7. password without `{id}` prefix (plain text) → `UsersFileException`
  8. unknown fields (top level or per user) are ignored, and a warning naming each field is logged (`OutputCaptureExtension`). *Decided 2026-11-16.*
  9. *(added)* empty file, file without `users` key → `UsersFileException`; `users:` with no value → empty result
  10. *(added)* missing password → `UsersFileException`; `{noop}` password is rejected (it is plain text)
  11. *(added)* error messages never contain the password value, also for YAML syntax errors (Jackson quotes the broken line, so only line/column is reported)
  12. *(added)* `artem` and `Artem` are two different users
- **Green:** `record UserEntry(String username, String passwordHash)`, `UsersFileParser` in package `com.gratchev.mizoine.users`. Boot 4 uses **Jackson 3**: dependency `tools.jackson.dataformat:jackson-dataformat-yaml` (version managed by Boot).
  For 3.8 the file is read as a `JsonNode` tree and checked field by field, so every unknown field can be logged.
- The parser checks only the `{id}` syntax. Checking that the id is one the password encoder knows (e.g. typo `{bcrpyt}`) moves to step 5, where the encoder exists.

### 4. `UserDetailsService` backed by the file  ☑
- **Red** — `FileUserDetailsServiceTest` (unit):
  1. known user → `UserDetails` with the hash and role `USER`
  2. unknown user → `UsernameNotFoundException`
  3. username lookup is case-sensitive: user `artem` exists → `loadUserByUsername("Artem")` throws `UsernameNotFoundException`. *Decided 2026-11-16.*
- **Green:** `FileUserDetailsService implements UserDetailsService`, reads users via `UsersFileParser`.
- Configuration property `mizoine.users-file` bound to a `@ConfigurationProperties` record `MizoineProperties`, validated (`@NotNull`).
- *Done, with additions:*
  - 4.4 *(added)* every lookup returns a new `UserDetails`: Spring Security erases the password after login, so a cached instance would break every later login.
  - `MizoinePropertiesTest` (`ApplicationContextRunner`): path is bound; missing property fails with a message naming `mizoine.users-file` (custom `@NotNull` message — the default one says `usersFile`). This already covers 6.1.
  - The file is read once, in the constructor. Step 9 changes that.
  - Only `spring-security-core` was added in this step; the full starter came in step 5.

### 5. Security configuration  ☑
- **Red** — `SecurityIntegrationTest` (`@SpringBootTest` + MockMvc, test users file in `src/test/resources/users-test.yaml` with known hashes):
  1. anonymous `GET /` → redirect to `/login`
  2. anonymous `GET /` with `Accept: application/json` or Basic header missing → 401
  3. HTTP Basic with valid user → 200 and "Hello"
  4. HTTP Basic with wrong password → 401
  5. HTTP Basic with user not in the file → 401
  6. form login (`formLogin()` request post-processor) valid → redirect to `/`; invalid → `/login?error`
  7. logout works and session is gone
  8. CSRF is active for form posts (post without token → 403)
  9. *(from step 3)* user whose hash has an unknown `{id}` (e.g. `{bcrpyt}`) → rejected when the file is loaded, not only at login
- **Green:** `SecurityConfig` with `SecurityFilterChain`: everything authenticated except `/login`, form login, HTTP Basic, `DelegatingPasswordEncoder`.
- Update the step 2 test if needed so it runs with real security.
- *Done, with additions and findings:*
  - Test users in `src/test/resources/users-test.yaml`: `artem`/`artem-pass`, `guest`/`guest-pass`, BCrypt cost 4 (fast tests; cost only matters when hashing).
  - Test 9 is a unit test: `PasswordHashing` (in `users` package) supports **only `{bcrypt}`** and checks the hash format, so a typo in the id or a truncated hash fails at load. `FileUserDetailsService` checks every user on load; the message names the user, never the hash.
  - *(added)* login page is public; second user works; case-sensitive username over HTTP; same user can log in twice; POST `/login` with CSRF token works.
  - Logout depends on the client (Spring Security default when HTTP Basic is on): browser (`Accept: text/html`) → 302 to `/login?logout`, session invalidated; other clients → 204, no redirect. Both are tested.
  - Spring Security 7 redirects to the relative URL `/login`.
  - Boot 4 needs `spring-boot-starter-security-test` for `spring-security-test`.
  - `HelloControllerTest` (`@WebMvcTest`) does not load `SecurityConfig`; it uses `@WithMockUser` against Boot's default security. The real chain is covered by `SecurityIntegrationTest`.

### 6. Startup validation (fail fast)  ☑
- *Note:* 6.1 is already covered by `MizoinePropertiesTest` (step 4). Since the users file is read when the `UserDetailsService` bean is created, 6.2 and 6.3 probably pass straight away; still write them first and check that the messages are clear.
- **Red** — tests using `ApplicationContextRunner`:
  1. property not set → context fails, message mentions `mizoine.users-file`
  2. file missing → context fails, message mentions the path
  3. invalid file → context fails
  4. valid file with zero users → context starts, warning logged (`OutputCaptureExtension`)
- **Green:** load and validate the file once at startup.
- *Done, with additions:*
  - Refactoring first: users beans moved from `SecurityConfig` to `users/UsersConfig`, so `UsersStartupTest` can start them without web security.
  - 6.2 and 6.3 passed right away, as expected. The test searches the cause chain for `UsersFileException` (the root cause of a missing file is `NoSuchFileException`).
  - 6.4: the warning is logged by `FileUserDetailsService` on load.
  - *(added)* `UsersFileFailureAnalyzer` (registered in `META-INF/spring.factories`): a users file problem prints a short "APPLICATION FAILED TO START" with description and action instead of a bean creation stack trace. Checked by running the jar too.
  - The analyzer's action text points to `users.example.yaml`, which step 10 creates.

### 7. Show the logged-in user on the page  ☑
- **Red:** `GET /` as `artem` → page contains `Hello, artem`.
- **Green:** pass `Principal` to the view. Small step, confirms the end-to-end flow.
- *Done, with additions:* the page heading is now `Hello, <username>` (no longer `Hello, World`). *(added)* a username with HTML is escaped (`th:text`); integration test checks `Hello, guest` over HTTP Basic. Smoke-tested with the jar and curl: valid user → `Hello, artem`, wrong password → 401, anonymous browser → 302 to `/login`.

### 8. Security hardening  ☑
- **Red** — tests for:
  1. security headers present (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Content-Security-Policy`)
  2. error responses do not leak stack traces
  3. same response/timing path for unknown user and wrong password (Spring default — assert 401 for both, no different message)
- **Green:** header config, `server.error.include-stacktrace=never`.
- Add OWASP `dependency-check-maven` (or `mvn versions:display-dependency-updates`) to a separate profile `-Psecurity-check`. Not part of default build (slow).
- *Done, with additions and findings:*
  - Spring Security already sent `nosniff`, `X-Frame-Options: DENY` and no-cache headers. Added: `Content-Security-Policy: default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` and `Referrer-Policy: same-origin`. Tested on the page, the login page and a 401 response (`SecurityHeadersTest`).
  - The default login page loads `/default-ui.css` from the same server and has no inline styles or scripts, so the strict CSP does not break it (tested that the stylesheet is public).
  - 8.2 and 8.3 passed straight away (Boot and Spring Security defaults); the tests now guard them. 8.2 needs a real server (`ErrorResponseTest`, random port, `java.net.http.HttpClient`): MockMvc does not render error responses. A test-only endpoint throws an exception with a secret message; neither the message nor a stack trace appears in JSON or HTML errors.
  - The error defaults are pinned in `application.yaml` (`server.error.include-*`).
  - Timing: for an unknown user Spring Security still checks a dummy BCrypt hash, so timing is similar. It uses the encoder's default cost (10); with cost-12 user hashes a small difference remains. Not tested (timing tests are flaky).
  - Profile `security-check` uses `dependency-check-maven` 13.0.0, failing on CVSS ≥ 7. **It needs an NVD API key** (`NVD_API_KEY`); without one, version 13 fails at once ("Invalid API Key"). Not run yet.
  - Spring Framework 7 removed `ResultMatcher.matchAll`; use `andExpectAll`. `@LocalServerPort` is in `org.springframework.boot.test.web.server`.

### 9. Reload users file without restart  ☑
Lets a person edit `users.yaml` and have it take effect.
- **Red** — `FileUserDetailsServiceReloadTest` (unit, `@TempDir`, injectable `Clock`/check):
  1. file changed (new user added) → new user can be loaded
  2. user removed → `UsernameNotFoundException`
  3. file changed to invalid content → old users kept, error logged
  4. file not changed → file is not read again (check via counter on a test parser)
- **Green:** re-read when file's last-modified time changes (checked on lookup; no background thread needed).
- Note: already logged-in sessions of a removed user stay valid until they expire — document this, or add a test + session invalidation later.
- *Done, with additions:*
  - No `Clock` needed: a file version is (modification time, size); the tests set the modification time explicitly, so they do not depend on file system time resolution.
  - *(added)* invalid change is logged once, not on every login; fixing the file afterwards takes effect; deleted file keeps the old users; unsupported hash in a change is rejected like at startup.
  - Startup stays strict (step 6): only changes *after* startup fall back to the old users.
  - Live-tested with the jar: user added while running could log in; a broken edit kept both users and logged one error.
  - **Known limit:** a removed user who logged in with the form keeps the session until it expires (default 30 min idle). HTTP Basic checks the user on every request, so there the removal works at once. Possible later step: invalidate sessions of removed users.

### 10. Run as a jar + documentation  ☑
- **Red:** none new; `mvn verify` builds an executable jar.
- Add `users.example.yaml`, add `users.yaml` to `.gitignore`.
- Update `README.md`: how to install JDK, generate a password hash, create `users.yaml`, run
  `java -jar target/mizoine-*.jar --mizoine.users-file=./users.yaml`.
- Manual smoke test: start the jar, open `http://localhost:8080`, log in with a configured user, get rejected with a wrong one.
- *Done, with additions:*
  - Done after step 11, so the README uses `hash-password` instead of the `htpasswd` pipeline.
  - *(added)* `UsersExampleFileTest`: `users.example.yaml` is loaded by the real `FileUserDetailsService` without warnings, so the example cannot drift from the format.
  - The example user `alice` has the hash of a random password that was never stored: copying the example unchanged gives no working login.
  - `.gitignore`: `/users.yaml`. Checked with `git check-ignore`.
  - Smoke test followed the README literally: own user → `Hello, artem`; wrong password → 401; `alice` → 401; browser → 302 to `/login`.

### 11. Password hash helper  ☑
- **Red:** `HashPasswordCommandTest`: given a password → output starts with `{bcrypt}` and matches via the encoder.
- **Green:** `java -jar mizoine.jar hash-password` reads password from stdin (no password in shell history) and prints the hash, then exits without starting the web server.
- *Done, with additions:*
  - In a terminal (`Console.isTerminal()`, JDK 22+) the password is asked twice and not shown; piped input is read once. Only the hash goes to stdout (one line, for `$(...)`), messages to stderr. Exit code 1 for empty password, no input or mismatch.
  - Input comes through a small `PasswordReader` interface, so all cases are unit tests (9 tests). Leading/trailing spaces are part of the password.
  - `main` checks the first argument before Spring starts: runs in about 0.6 s.
  - *(added)* new hashes use **BCrypt cost 12**. Spring Security uses the same encoder for its dummy check of unknown users, so that check now takes as long as a real login (closes the timing note in step 8).
  - Smoke-tested with the jar: piped, empty input, and in a pseudo terminal (`script`).

### 12. HTTPS behind nginx  ☑
*Decided 2026-11-16:* **nginx** as reverse proxy terminates TLS; the app itself speaks plain HTTP. (Replaces "proxy or Spring Boot SSL bundles".)

```
browser ──HTTPS──▶ nginx (TLS, certificates) ──HTTP──▶ 127.0.0.1:8080 mizoine
```

- **Red** — `ReverseProxyTest` (real server, random port, `java.net.http.HttpClient`, because Tomcat handles the forwarded headers):
  1. request with `X-Forwarded-Proto: https` and `X-Forwarded-Host: example.org` → anonymous browser is redirected to `https://example.org/login`, not `http://…`
  2. such a request gets `Strict-Transport-Security` (Spring Security sends HSTS only for secure requests)
  3. the session cookie after form login is `Secure`, `HttpOnly`, `SameSite=Lax`
  4. a forwarded header from a non-local address is not trusted (no https redirect)
- **Green:**
  - `server.forward-headers-strategy: native` (Tomcat `RemoteIpValve`: trusts `X-Forwarded-*` only from internal addresses such as 127.0.0.1)
  - `server.address: 127.0.0.1` by default, so only nginx on the same machine can reach the app; can be overridden for other setups
  - session cookie: `same-site: lax`, `http-only: true`
- Add `docs/nginx.conf.example`: TLS, `proxy_pass http://127.0.0.1:8080`, `X-Forwarded-Proto/Host/For` headers, HTTP→HTTPS redirect, and `limit_req` on `/login` against password guessing.
- Add a "Behind nginx" section to the README.
- Open question for this step: test 4 needs a request from a non-local address; if that is not practical in a test, rely on Tomcat's documented default and say so here.
- *Done, with findings:*
  - Red showed the real problem: without the setting, a browser behind nginx was redirected to `http://127.0.0.1:<port>/login`.
  - Test 4 solved in `UntrustedProxyTest`: trusted proxies narrowed to `192.0.2.1/32`, so the test client on 127.0.0.1 is untrusted → forwarded `https` is ignored (no HSTS, cookie not `Secure`).
  - 12.1 accepts both `/login` and `https://example.org/login` (it is absolute in practice).
  - Boot 4 writes `server.tomcat.remoteip.internal-proxies` as CIDR (default: loopback and private networks).
  - "Secure" on the session cookie needs no setting: Tomcat adds it for HTTPS requests.
  - `docs/nginx.conf.example` tested live: nginx 1.28 unpacked without root (`apt-get download`), self-signed certificate, the example with only ports, paths and name replaced. `nginx -t` ok; HTTP→HTTPS 301; anonymous → `https://…/login`; HSTS; cookie `Secure; HttpOnly; SameSite=Lax`; form login with CSRF and HTTP Basic work; app not reachable on the LAN address.
  - Live test found the first rate limit too strict (it also counted loading the login page). Now only `POST /login` counts: 10/min per address, burst 5. Retested: 15 page loads pass; 6 quick wrong logins pass, then 429.
  - `X-Forwarded-For` is overwritten with `$remote_addr`, not appended: otherwise a client could put a fake private address in it, which Tomcat would skip as a "proxy".
  - Not limited by nginx: password guessing over HTTP Basic (any path). Possible follow-up.

### 13. Limit failed logins (HTTP Basic and form)  ☑
*Requested 2026-11-16, follow-up of step 12.* nginx cannot do it for HTTP Basic: a valid client sends credentials with every request, and nginx does not know which attempts failed.
- **Rule:** per client address, after `mizoine.login-limit.max-failures` (default 5) failures, each less than `mizoine.login-limit.window` (default 5m) after the previous one, login attempts from that address get **429** with `Retry-After` until one window after the last failure. A successful login resets the count. Requests without credentials (e.g. the login page) are not blocked.
- **Per address, not per username:** otherwise anyone could lock out a known user. The address is the real client address from nginx (`X-Forwarded-For`, step 12).
- **Red/Green:**
  - `LoginAttemptLimiterTest` (unit, test clock, 8 tests): threshold, end of block, old failures forgotten, success resets, addresses independent, `retryAfter`, expired addresses removed (memory stays bounded).
  - `LoginRateLimitTest` (MockMvc, own address per test, limit 3): Basic blocked even with the right password, unknown usernames count, other addresses unaffected, form login blocked, form and Basic failures add up, login page still works, success resets.
  - `MizoinePropertiesTest`: defaults and overrides of `mizoine.login-limit.*`.
- **Design:** `LoginAttemptEvents` listens to Spring Security's `AuthenticationFailureBadCredentialsEvent` / `AuthenticationSuccessEvent` (unknown users also arrive as bad credentials). `LoginLimitFilter` runs in the security chain before the login filters and rejects blocked attempts before any password check. It is not a bean, so Boot does not register it a second time as a servlet filter.
- **Findings:**
  - MockMvc leaves `getServletPath()` empty: matching `POST /login` uses `PathPatternRequestMatcher` (works in MockMvc and Tomcat).
  - `formLogin()` from `spring-security-test` cannot set the remote address; the test builds the POST itself.
  - Other tests log in wrongly many times from the same address in one shared Spring context, so `src/test/resources/config/application.yaml` raises the limit for tests (Boot reads `classpath:/config/` after `classpath:/`, without replacing the main `application.yaml`).
  - Live-tested with the jar and `X-Forwarded-For` as nginx sends it: 5 wrong → 429 with `Retry-After: 300` even with the right password; another address → 200; login page → 200; one warning in the log.
- State is in memory: a restart forgets it. Fine for one instance.

---

## Open decisions

- ~~Step 3.8: ignore or reject unknown fields in `users.yaml`?~~ **Decided:** ignore them and log a warning.
- ~~Step 4.3: case-sensitive usernames?~~ **Decided:** yes, case-sensitive.
- Roles: everyone is `USER` for now. AI-agent role from BACKLOG §7 comes later as a new field `role` in `users.yaml`.
- Sessions of users removed from `users.yaml` stay valid until they expire (step 9): add invalidation if needed.
- Run the dependency check once an NVD API key is available (step 8).
- ~~Rate-limit HTTP Basic login attempts~~ **Done:** step 13.
