# Mizoine 1.0 — Development Plan

**Status** (updated: 2026-11-23): steps 0–32 and 37–39 done (582 Java and 51 JavaScript tests green; file API, Markdown page, editor with syntax highlighting). Steps 33–36 planned (MCP access for agents; browser UI). Decided: T1 REST (implemented), T2 remote MCP, T3 small library without build, T3.1 CodeMirror 6 (step 39, done), T3.2 (commonmark-java), D10–D15, D20–D25, D27, D28. Open: D16–D19, D26, see [docs/technology-decisions.md](docs/technology-decisions.md).

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
| R5 | Repositories are configured in YAML files, one file per repository; the file name (without extension) is the repository name |
| R6 | A repository is a folder on disk plus per-user permissions: which user may access which subfolders |
| R7 | `/{repository}/` answers with success (a small "ok" page) when the repository exists and the user has access — *since step 21: the listing of the root folder* |
| R8 | Folders under `/{repository}/…/` list their subfolders: JSON when the client accepts JSON, HTML with clickable links when it accepts HTML |
| R9 | The start page `/` lists the repositories the logged-in user can access, as links; if there are none, it says so |
| R10 | No repositories configured is not an error: the application starts |
| R11 | The repositories folder is the current directory unless `mizoine.repositories-dir` says otherwise |

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
| HTTPS | **nginx** in front (TLS), the application speaks plain HTTP on `127.0.0.1:12013` (step 12) | Certificates and TLS handled by a standard tool |
| Repositories | One **YAML** file per repository in `mizoine.repositories-dir` (default: current directory), read lazily on request (steps 14–16) | Same hand-editable format as users; no restart needed |
| API documentation | OpenAPI annotations (`swagger-annotations-jakarta`) on endpoints; springdoc **test-only** generates `docs/openapi.yaml`; not served by the application, no Swagger UI (step 15, D9) | Description lives in the repository, nothing extra exposed |

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
- `username` must be non-empty and unique. Duplicate keys (e.g. `users:` twice) are rejected too (step 14).
- `password` must be a BCrypt hash with the `{bcrypt}` prefix (step 5). Plain-text values and `{noop}` are rejected at load time.
- The real `users.yaml` is **not committed** (git-ignored). A `users.example.yaml` is committed.

### Repository file format (steps 14–22)

One file per repository in the folder set by `mizoine.repositories-dir` — **default: the current directory** (decided 2026-11-18). The file name is the repository name: `notes.yaml` → `/notes/`. Every `*.yaml` in that folder counts, except the users file (never a repository). A dedicated folder (e.g. `--mizoine.repositories-dir=repositories`) keeps other YAML files out.

```yaml
# repositories/notes.yaml
path: /home/artem/notes        # root folder of the repository; relative paths are resolved against this file's folder
access:                        # username → folders the user may access (including everything below them)
  artem: ["/"]                 # "/" = the whole repository
  guest: [public, projects/shared]
```

Rules (implemented in step 14; decisions D1–D8 below):
- Repository name: from the file name `<name>.yaml`; letters, digits, `.`, `_`, `-`; not starting with `.`; case-sensitive. Names used by the application itself (`login`, `logout`, `error`, `default-ui.css`, `favicon.ico`, `ui`) are rejected.
- `path` should be an existing folder. If it is missing or wrong, the file is still readable and the repository has a *problem* (D8).
- `access` keys are usernames (case-sensitive, like `users.yaml`); each a list of folders. Missing or empty `access` → nobody has access (warning). A user not in `users.yaml` gives a warning, not an error: users can be added later (step 16).
- Folders in `access` are relative to the repository root, written with `/`; a leading `/` also means the root (`/public` = `public`). `.`, `..`, hidden names (D3), backslashes and NUL are rejected.
- Duplicate keys (e.g. a username twice) are rejected; unknown fields are ignored with a warning (same as `users.yaml`).

### Error behaviour

Users file:
- File missing or invalid **at startup** → application fails to start with a clear message.
- File becomes invalid **while running** (step 9) → keep the last valid users, log an error.
- Zero users in a valid file → allowed, but nobody can log in (logged as a warning).

Repository files (read on request, never at startup — step 16):
- No repositories (folder missing or without repository files) → application starts; warning if the folder is missing; the start page says so.
- File unreadable (permissions unknown) → repository unknown, 404 for everyone, error logged once; an older valid version is not kept (D7).
- File readable with problems (e.g. wrong root folder) → 500 with details for users with grant `/`, 500 without details for partial grants, 404 without grant (D8).

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
  - Timing: for an unknown user Spring Security still checks a dummy BCrypt hash, so timing is similar. It uses the encoder's default cost (10); with cost-12 user hashes a small difference remains. Not tested (timing tests are flaky). *Closed in step 11 (encoder hashes with cost 12).*
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
- Manual smoke test: start the jar, open `http://127.0.0.1:12013` (port 8080 at the time), log in with a configured user, get rejected with a wrong one.
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
browser ──HTTPS──▶ nginx (TLS, certificates) ──HTTP──▶ 127.0.0.1:12013 mizoine
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
- Add `docs/nginx.conf.example`: TLS, `proxy_pass http://127.0.0.1:12013`, `X-Forwarded-Proto/Host/For` headers, HTTP→HTTPS redirect, and `limit_req` on `/login` against password guessing.
- Add a "Behind nginx" section to the README.
- Open question for this step: test 4 needs a request from a non-local address; if that is not practical in a test, rely on Tomcat's documented default and say so here.
- *Done, with findings:*
  - Red showed the real problem: without the setting, a browser behind nginx was redirected to `http://127.0.0.1:<port>/login`.
  - Test 4 solved in `UntrustedProxyTest`: trusted proxies narrowed to `192.0.2.1/32`, so the test client on 127.0.0.1 is untrusted → forwarded `https` is ignored (no HSTS, cookie not `Secure`).
  - 12.1 accepted both `/login` and `https://example.org/login` (it was absolute in practice; relative since the bug fix below).
  - Boot 4 writes `server.tomcat.remoteip.internal-proxies` as CIDR (default: loopback and private networks).
  - "Secure" on the session cookie needs no setting: Tomcat adds it for HTTPS requests.
  - `docs/nginx.conf.example` tested live: nginx 1.28 unpacked without root (`apt-get download`), self-signed certificate, the example with only ports, paths and name replaced. `nginx -t` ok; HTTP→HTTPS 301; anonymous → `https://…/login`; HSTS; cookie `Secure; HttpOnly; SameSite=Lax`; form login with CSRF and HTTP Basic work; app not reachable on the LAN address.
  - Live test found the first rate limit too strict (it also counted loading the login page). Now only `POST /login` counts: 10/min per address, burst 5. Retested: 15 page loads pass; 6 quick wrong logins pass, then 429.
  - `X-Forwarded-For` is overwritten with `$remote_addr`, not appended: otherwise a client could put a fake private address in it, which Tomcat would skip as a "proxy".
  - Not limited by nginx: password guessing over HTTP Basic (any path). *Done in step 13 (in the application).*
- *Bug fix 2026-11-19:* behind Cloudflare → nginx with a custom domain, users were redirected to `https://localhost/login`. Tomcat makes every `sendRedirect` absolute from the `Host` header, and nginx with a minimal config (`proxy_pass http://localhost:12013;`) sends `Host: localhost:12013`; `X-Forwarded-Proto: https` came from Cloudflare.
  - First attempt `server.tomcat.use-relative-redirects: true` did not help: Tomcat ignores it for HTTP/1.0, and nginx talks HTTP/1.0 to the application by default. Found by running the user's nginx config locally; removed again.
  - Fix: Spring's `RelativeRedirectFilter` (first filter, status 302) sets `Location` itself, so Tomcat cannot rewrite it. After login Spring Security returns to the saved page with its full URL; `RelativeRedirectStrategy` keeps only path and query (also no redirect to another host).
  - Tests: `ReverseProxyTest` over HTTP/1.0 with a raw socket (`HttpClient` cannot do HTTP/1.0) and without `X-Forwarded-Host`; `SecurityIntegrationTest.formLoginReturnsToSavedPageWithRelativeUrl`; `RelativeRedirectStrategyTest`. 12.1 now requires exactly `/login`.
  - Live with the user's nginx config: `/login`, `/?continue` (since then `/`, see below), `/login?logout`, `/login?error` — all relative.
  - Clean URL after login (requested): Spring Security appended `?continue` to the saved page, so that it looks into the session only for such requests. Turned off (`HttpSessionRequestCache.setMatchingRequestParameterName(null)`): every page needs a login, so the session is loaded anyway.

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

### Repositories: overview of steps 14–22
*Planned 2026-11-18.* Covers request 1 ("ok" page per repository: steps 14, 16–18), the start page with the user's repositories (step 19), request 2 (subfolder listing: steps 20–21), plus docs (step 22). Step 15 (OpenAPI) was inserted on request and applies to all endpoints from then on. Repository files are read lazily on request, which also gives reload without restart (step 16).
Read-only for now: nothing is written to the repository. Writing and the AI-agent role (BACKLOG §7) come later.

### 14. Repository file model and parser  ☑
Pure unit tests, `@TempDir`, like step 3.
- **Red** — `RepositoryFileParserTest`:
  1. valid file → `RepositoryConfig(name, root, grants)`; name taken from the file name without `.yaml`
  2. relative `path` is resolved against the folder of the file; absolute path kept
  3. `path` missing, not existing, or a file instead of a folder → the file is still **readable**: the config is returned with a *problem* ("root folder /x does not exist"), not an exception (D8)
  4. invalid repository names (`.hidden.yaml`, `my repo.yaml`, reserved `login.yaml`) → exception
  5. folder grants: `"/"` = root; `public`, `public/`, `/public` all mean the same (a leading `/` is the repository root, not the disk root); `..`, `a/../../b` and hidden names are rejected
  6. empty `access` → valid, nobody has access (warning logged)
  7. malformed YAML → exception with line/column, not the content (same as step 3)
  8. unknown fields → ignored, warning names each field (same as step 3.8)
  9. **two levels of failure (D8):** *unreadable* — malformed YAML, invalid repository name, invalid `access` section (e.g. a grant with `..`) → `RepositoryFileException`, permissions unknown; *readable with problems* — everything else (root folder) → config with a list of problems
- **Green:** `record RepositoryConfig(name, root, grants, problems)`, `record FolderGrant`, `RepositoryFileParser` (Jackson YAML tree, as in `UsersFileParser`). `access` is parsed and checked first, so permissions are known even when the rest is wrong.
- *Done, with additions:* 57 test cases (`RepositoryFileParserTest`, many parameterized).
  - `RepositoryNames.isValid` is separate, for the lookup by URL in step 16. Reserved: `login`, `logout`, `error`, `default-ui.css`, `favicon.ico` (and `ui` since step 24).
  - `FolderGrant` = path segments; `/` = whole repository. Rejected (file unreadable): empty, `.`/`..`, hidden names (D3), backslash, NUL; folders not given as a list; `access` not a mapping; blank username.
  - **Duplicate keys** (e.g. a username twice under `access`) make the file unreadable instead of silently using the last one (Jackson `STRICT_DUPLICATE_DETECTION`).
  - Missing `access` = empty: nobody has access, warning. Missing or non-text `path`, missing folder, file instead of folder → *problems* (D8).
  - Refactoring: YAML reading with position-only error messages moved to `config/YamlFiles`, shared by users and repository files. Duplicate keys are now rejected in `users.yaml` too (new test first).

### 15. OpenAPI documentation  ☑
*Requested 2026-11-18:* all API methods are annotated so OpenAPI documentation can be generated; the build generates it. *Decided 2026-11-18 (D9):* no Swagger UI, and the **application does not serve the description** — it exists only as a file in the repository.
- **Dependencies:** `io.swagger.core.v3:swagger-annotations-jakarta` 2.2.55 (compile: annotations only); `org.springdoc:springdoc-openapi-starter-webmvc-api` 3.2.0 (**test** scope: builds the description in tests, not part of the jar).
- **Output:** `docs/openapi.yaml`, committed. Written by `OpenApiDocumentationTest` during `mvn verify`; changes show up in `git diff`.
- **Red** — `OpenApiDocumentationTest` (`@SpringBootTest` + MockMvc, test user):
  1. description has title "Mizoine" and an API version
  2. `GET /` is documented: summary, `200` as `text/html`, `401`
  3. security scheme HTTP Basic declared and required for all operations
  4. `docs/openapi.yaml` is written and equals the description served in the test
- **Green:** `@Tag`, `@Operation`, `@ApiResponse` on `HelloController`. The API-wide part (`@OpenAPIDefinition`: title, version, server; `@SecurityScheme` HTTP Basic) is in **test code**, `OpenApiDocumentationTest.ApiDescription` — a configuration class in the application would suggest that it serves API docs (refactored 2026-11-18, output unchanged).
- **Check:** the built jar contains no springdoc classes; `/v3/api-docs` on the running jar → 404 for a logged-in user.
- **From now on (CLAUDE.md):** every endpoint step adds annotations, and its tests check the endpoint in the description (summary, parameters, response codes and media types — JSON and HTML).
- *Done, with findings:*
  - The springdoc Maven plugin (planned first) was dropped: it starts the application and fetches `/v3/api-docs`, which the jar no longer has (D9). The test generates the file instead.
  - *(added)* `servers` is the relative `/`: springdoc puts the test instance's `http://localhost` there by default, which is wrong for every real installation.
  - `GET /` documents `200 text/html`, `302` (browser not logged in) and `401` (HTTP Basic challenge).
  - API version in the description is `1.0` (an annotation constant): the swagger model classes are test-only, so the pom version cannot be set from main code. API version ≠ artifact version anyway.
  - The description endpoint `/v3/api-docs` (constant `OpenApiDocumentationTest.API_DOCS_PATH`) exists only in `OpenApiDocumentationTest`: springdoc is switched off for all other tests in `src/test/resources/config/application.yaml`; `ApiDocsNotServedTest` checks 404 there (added 2026-11-18).
  - Checked: the jar contains `swagger-annotations-jakarta` only, no springdoc; on the running jar `/v3/api-docs`, `/v3/api-docs.yaml` and `/swagger-ui.html` → 404 for a logged-in user.

### 16. Lazy repository lookup  ☑
*Decided 2026-11-18:* repository files are **not** loaded at startup. On each request to `/{repository}/…` the matching file `<repositories-dir>/<repository>.yaml` is looked up and read when needed.
- **Red** — `RepositoryRegistryTest` (unit, `@TempDir`, counting parser as in step 9):
  1. first request for `notes` reads `notes.yaml` and returns its config; other files in the folder are not read
  2. unchanged file → not read again (modification time + size, as in step 9)
  3. changed file → read again on the next request; new grants apply
  4. removed file → repository unknown (404)
  5. file added while running → found on the next request
  6. unreadable file → repository treated as unknown (404, D2); error logged **once** per file version, naming the file; a previously loaded valid version is **not** kept (D7)
  7. readable file with problems (e.g. root folder missing) → returned with its problems, error logged once; the controller decides what the user sees (D8, step 18)
  8. **name check before any file access:** names that are not valid repository names (`..`, `.git`, `a/b`, `a%2fb`, reserved `login`) → unknown without touching the file system
  9. lookups of names without a file do not grow the cache (random URLs cannot fill memory)
  10. a username in `access` that is not in `users.yaml` → warning when the file is read, naming user and repository
- **Red** — `RepositoriesStartupTest` (`ApplicationContextRunner`):
  1. `mizoine.repositories-dir` not set → the current directory is used, no warning *(decided 2026-11-18; first planned as "no repositories, warning")*
  2. folder missing → starts, warning; requests answer 404 until the folder exists
  3. empty folder → starts (no warning needed; the start page tells the user)
  4. *(added)* the users file in the repositories folder (e.g. `./users.yaml` with the default `.`) is never a repository
- **Red** — also in `RepositoryRegistryTest`: `all()` lists the repositories of all `*.yaml` files in the folder, reading only new or changed files (same cache as `find`); non-`.yaml` files and invalid names are skipped; unreadable files are left out (permissions unknown).
- **Green:** `RepositoryRegistry.find(name)` → `Optional<RepositoryConfig>` and `all()`; cache `name → (file version, config)`; `RepositoryFileParser` from step 14.
- *Done, with findings:*
  - 22 unit tests (`RepositoryRegistryTest`) + 5 startup tests (`RepositoriesStartupTest`) + 2 property tests.
  - Unreadable files are cached as "unreadable" with their file version, so they are neither parsed nor logged again until they change.
  - "User exists" for the warning uses Spring's `UserDetailsService` (`loadUserByUsername`), so the users code did not change. Unit tests pass a plain predicate.
  - *(added)* broken files in the folder are not even mentioned at startup (nothing is read); `all()` also drops cache entries of removed files; a folder created after startup is picked up.
  - `mizoine.repositories-dir` defaults to `.` (the current directory). `RepositoriesConfig` warns at startup only when the folder is missing; an empty folder gives no warning.
  - The users file is never a repository: compared as normalized absolute path, so `./users.yaml` and `data/../users.yaml` match.
  - **Finding:** Spring's default text → `Path` conversion first looks for the text on the **classpath**: `.` became `target/test-classes`, and `mizoine.users-file=application.yaml` would have pointed to the copy inside the application. A `@ConfigurationPropertiesBinding` converter fixed only defaults, not explicit values. So `MizoineProperties` binds both paths as text and offers `usersFilePath()` / `repositoriesDirPath()` (`Path.of`, relative to the working directory). Test: `MizoinePropertiesTest.pathsAreFileSystemPathsNotClasspathResources`.
  - Checked with the jar: starts without the setting, logs the warning, start page as before.

### 17. Access rules  ☑
Pure unit tests — the core of the security of this feature.
- **Red** — `RepositoryAccessTest`:
  1. grant `/` → user may enter every folder
  2. grant `public` → may enter `public` and `public/a/b`; not `publicity`, not `private`
  3. a folder *above* a granted one (e.g. root when only `projects/shared` is granted) may be entered, but shows only the way to the granted folders (D4)
  4. user without any grant → no access to the repository at all
  5. usernames are case-sensitive
- **Green:** `RepositoryAccess` with `canEnter(user, folder)` and `visibleChildren(user, folder, children)`.
- *Done, with additions:* 12 unit tests (`RepositoryAccessTest`).
  - API: `RepositoryAccess.of(config, username)` → `hasAccess()`, `isWholeRepository()` (decides D8 details), `canEnter(folder)`, `visibleChildren(folder, children)`. Folders are path segments, like `FolderGrant`.
  - *(added)* several grants add up; an empty folder list means no access; anonymous (`null`) has no access; folder names are case-sensitive.
  - *(added)* hidden names (D3) are filtered here too — never visible nor enterable, even with grant `/` — so no caller can forget it. Step 20 still blocks them at the path level.
  - `visibleChildren` keeps the given order; sorting is the listing's job (step 21).

### 18. `/{repository}/` — "ok" page  ☑
- **Red** — `RepositoryControllerTest` (`@SpringBootTest` + MockMvc, test repositories in `src/test/resources/repositories/`):
  1. user with access → 200, small page with `ok`
  2. unknown repository → 404
  3. existing repository, user without any grant → 404, same response as unknown (D2)
  4. anonymous → login redirect / 401, as for every page (step 5)
  5. `/notes` without trailing slash → redirect to `/notes/` (D5)
  6. `/`, `/login`, `/logout`, `/default-ui.css` still work (no clash with `/{repository}/`)
  7. readable file with problems, user with grant `/` → **500 with details**: what is wrong (e.g. "root folder /x does not exist") and which file to fix (`notes.yaml`); as JSON or HTML like step 21 (D8)
  8. same, user with a partial grant → 500 without details ("repository not available"); no paths in the response
  9. same, user without grant → 404 (D2)
  10. unreadable file → 404 for everyone, details only in the log
- **Green:** `RepositoryController` with `GET /{repository}/`, Thymeleaf template `repository.html`.
- **OpenAPI** (step 15): annotations; `OpenApiDocumentationTest` checks the path, parameter `repository` and responses 200/302/401/404/500.
- *Done, with findings:* 15 tests (`RepositoryControllerTest`) + 2 OpenAPI tests.
  - Test repositories in `src/test/resources/repositories/` (`notes`, `private`, `moved`, `broken`) use `path: .` (their own folder), so no empty folders are needed in Git.
  - **HTML only for now:** the page and the 500 details are `text/html`; `Accept: application/json` → 406 (test `jsonIsNotAvailableYet`). JSON comes with step 21.
  - 404 for unknown, unreadable and "no grant" is one `ResponseStatusException(NOT_FOUND)`: identical status, headers and body (tested).
  - `/{repository}` redirects to `/{repository}/` for every valid name (reveals nothing); invalid names (e.g. `/.hidden`) → 404 without redirect.
  - **springdoc leaves out methods that return `ModelAndView`.** First taken for "springdoc drops trailing slashes" — wrong; with a view name (`String`) and the 500 status set on the response, both `/{repository}` and `/{repository}/` are documented under their real paths.
  - Live-tested with the jar: all cases as in the tests; the problem file and the unreadable file are logged once each.

### 19. Start page: the user's repositories  ☑
*Requested 2026-11-18.* Extends the start page of step 7: the greeting stays as heading, the list of repositories comes below it.
- **Red** — `StartPageTest` (`@SpringBootTest` + MockMvc, test repositories folder):
  1. user with grants in two of three repositories → page lists exactly those two, as links `<a href="notes/">notes</a>`, sorted by name
  2. user without any grant → page says "No accessible repositories configured for you." (HTML-escaped username in heading as before)
  3. no repositories at all (folder missing or without repository files) → same message; status 200
  4. repository whose file is readable with problems (D8): listed with a "not available" note; the note shows the problem only to users with grant `/`
  5. unreadable repository files are not listed (permissions unknown, D7)
  6. `Accept: application/json` → `{"repositories":[{"name":"notes","available":true}, …]}` (same content negotiation as step 21, D6)
  7. anonymous → login (unchanged)
- **Green:** `HelloController` → `StartPageController`, using `RepositoryRegistry.all()` and `RepositoryAccess`; template `hello.html` → `start.html`. Update `HelloControllerTest` and the greeting tests from step 7.
- **OpenAPI** (step 15): `GET /` gets the JSON response (`application/json`) next to `text/html`; `docs/openapi.yaml` changes accordingly.
- *Done, with findings:* 16 tests (`StartPageTest`) + 1 (`StartPageWithoutRepositoriesTest`); `HelloControllerTest` was folded into `StartPageTest` (it would have needed a mocked registry); `HelloController` → `StartPageController`, `hello.html` → `start.html`.
  - JSON: `{"user":"guest","repositories":[{"name":"moved","available":false},{"name":"notes","available":true}]}`; `problems` only for users with grant `/` (D8), otherwise left out.
  - **D6 needed a setting:** with an HTML and a JSON method for one URL, Spring chose **JSON** for no `Accept` header and for `*/*` (plain curl). `WebConfig` sets HTML as default content type for requests without preference. App-wide: error responses without `Accept` are HTML pages now too (JSON when asked for) — consistent with D6. Browsers send `text/html` explicitly and were not affected.
  - Side effect in tests: `OpenApiDocumentationTest` now asks for `application/json` / `application/vnd.oai.openapi` explicitly.
  - springdoc merges the HTML and JSON methods into one operation and takes the texts from one of them: both methods use the same summary, description and response texts.
  - Live-tested with the jar: plain curl → HTML list; JSON for guest without and for artem with problems; 404 as HTML by default, as JSON when asked for.

### 20. Safe paths inside a repository  ☑
Before anything below the root is exposed.
- **Red** — `RepositoryPathsTest` (unit, `@TempDir`) and a few MockMvc cases:
  1. `a/b` resolves inside the root
  2. `..`, `a/../../x`, URL-encoded `%2e%2e`, `%2f`, backslashes, NUL → rejected (404 over HTTP; Tomcat or Spring Security's firewall may already answer 400 for some — the test accepts 400 or 404, never 200)
  3. a symbolic link pointing outside the root → not followed, treated as not existing
  4. `.git` and other names starting with `.` → not visible, not reachable (D3)
  5. not existing folder → 404; a file instead of a folder → 404 for now (files come later)
- **Green:** `RepositoryPaths.resolve(root, relative)` using `normalize()` + `toRealPath()` and a check that the result is still under the root's real path.
- *Done, with findings:* 21 unit tests (`RepositoryPathsTest`) + 31 HTTP tests (`UnsafeUrlTest`: 14 unsafe URLs in-process and on a real server, plus session tests).
  - API: `RepositoryPaths.resolveFolder(root, segments)` → real path of an existing folder inside the root, or empty; `childFolders(root, segments)` → visible subfolder names, sorted (for step 21).
  - Rejected: `.`, `..`, empty, `/`, `\`, NUL in a segment; hidden names (D3); symbolic links whose real target is outside the root **or hidden** (*added:* a link `git` → `.git` would otherwise make `.git` reachable). Links staying inside the root work; a root that is itself a link works.
  - **Finding — rejected URLs became login redirects with the session id in the URL.** On a real server, URLs like `/notes/../private/` or `/notes/%2e%2e/` are rejected by Spring Security's firewall with 400; Tomcat then renders the error via `/error`, which required a login, so the client got `302 /login;jsessionid=…`. Fixed:
    - error dispatch needs no login (`dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`); a direct `GET /error` still needs login (tested). Error pages show no internals (step 8).
    - `server.servlet.session.tracking-modes: cookie`: the session id is never put into URLs (tested via the effective tracking modes).
  - MockMvc did not show the problem (no error dispatch, no URL normalization): only the real-server tests did.
  - Live-tested with the jar: all unsafe URLs → 400; first anonymous visit → `302 /login` without session id.

### 21. Subfolder listing: JSON and HTML  ☑
Replaces the "ok" page of step 18 for folders. Also: replace the temporary test `RepositoryControllerTest.jsonIsNotAvailableYet` and give the 500 problem page (D8) a JSON variant.
- **Red** — `RepositoryListingTest` (MockMvc):
  1. `GET /notes/` with `Accept: application/json` → 200, `{"repository":"notes","path":"/","folders":["a","b"]}`; sorted by name; only folders, no files
  2. same with `Accept: text/html` → HTML page with a link per subfolder (`<a href="a/">a</a>`) and a link to the parent folder below the root
  3. deeper folder `GET /notes/projects/shared/` → its subfolders; `path` is `/projects/shared/`
  4. only folders the user may see (D4); hidden folders never listed (D3)
  5. no `Accept` header or `*/*` → HTML (D6); `Accept: application/xml` only → 406
  6. folder names with spaces, `#`, `%`, non-ASCII → correctly encoded in links and JSON, HTML-escaped in the page
  7. `/notes/projects` (no trailing slash) → redirect to `/notes/projects/` so relative links work
- **Green:** controller method with `GET /{repository}/**`, content negotiation by `produces` (`application/json` and `text/html` handler methods; HTML default already set by `WebConfig`, step 19), `record FolderListing(repository, path, folders)`, template `folder.html`.
- **OpenAPI** (step 15): both media types, the path parameter, 302/404/406/500.
- *Done, with findings:* 14 tests (`RepositoryListingTest`, tree with special names created in a temporary folder) + step 18 tests updated (JSON listing, JSON problem page, problems below the root) + 4 double-encoded attack URLs in `UnsafeUrlTest`.
  - One pattern `/{repository}/{*path}` for all folders (HTML and JSON method); `/{repository}` (no slash) keeps its own redirect from step 18. `{*path}` is already URL-decoded.
  - Below the root, the trailing-slash redirect is given **only for folders the user may open** — otherwise 404 (a redirect would tell that the folder exists; also keeps e.g. `/v3/api-docs` at 404).
  - Links: `UriUtils.encodePathSegment` per name (`with%20space/`, `hash%23/`, `%C3%9Cn%C3%AFcode/`, `a%20%3Cb%3E/`), text HTML-escaped by Thymeleaf. The redirect URL is fully encoded, so names with `{` are not taken for URI template variables. Root: link to the start page; below: link `../`.
  - JSON problem page (D8): `{"repository","error":"repository not available","file","problems"}`, `file`/`problems` only for grant `/`.
  - **Finding — Spring Security's firewall rejects an encoded `%` (`%25`):** folders with `%` in their name were listed but not reachable (400). *Decided 2026-11-18:* keep the firewall unchanged (a first fix allowed `%25`; reverted) and **hide folders with `%` in the name**, like hidden folders (`RepositoryPaths`): not listed, not reachable. Very rare in practice. Double-encoded attacks (`%252e%252e`, `%252egit`, …) stay in `UnsafeUrlTest` (rejected with 400).
  - **Finding — springdoc's merging of an HTML and a JSON method for one URL depends on method order:** here it dropped the HTML variant (on the start page it happened to work). Now each such URL is documented on the HTML method with both media types, and the JSON method is `@Hidden` (also changed on the start page). springdoc writes `{*path}` as `{path}`.
  - Folder names with `;`, `\` or NUL stay unreachable (firewall) — on Linux only `;` is realistic.
  - Live-tested with the jar: links and JSON for special names, guest sees only `public`, `.git` → 404, redirect without slash.

### 22. Documentation  ☑
- `repositories.example/notes.yaml` (committed, checked by a test like step 10); real repository folder git-ignored if kept in the project.
- README: already describes `mizoine.repositories-dir`, the file format and browsing (2026-11-18, after step 21); check that it is complete, add an example repository folder.
- *Done:*
  - `repositories.example/`: `notes.yaml` (grant `/` for `alice`, the example user) and an example tree `notes/public`, `notes/projects/shared` (each folder with a README: Git keeps no empty folders).
  - `RepositoriesExampleTest`: the example is valid together with `users.example.yaml` — no problems, no warnings (so `alice` exists), expected folders listed.
  - `.gitignore`: `/repositories/` (real repositories folder in the project). Checked with `git check-ignore`.
  - README checked against the application and completed: run steps with `repositories` (not the default `.`, which would read `users.example.yaml` as a repository); all reserved names; JSON example matching the example repository; 404 for no access; new section "Development" (TDD, `mvn verify` with Checkstyle and `docs/openapi.yaml`, dependency check).
  - Followed the README literally with the jar: own user sees `notes`; the curl example returns exactly the documented JSON; no warnings in the log.

### 23. Favicon  ☑
*Requested 2026-11-19:* the favicon of the old version, also without login.
- **Red** — `FaviconTest`: anonymous and logged-in `GET /favicon.ico` → 200, `image/x-icon`, the archived file (ICO header checked); `Cache-Control: max-age=604800, public`; `POST` stays forbidden; `/favicon.png` still needs login. `OpenApiDocumentationTest`: entry with `image/x-icon` and `security: []`.
- **Green:** `src/main/resources/favicon.ico` (copied unchanged from the old version, 48×48 and 32×32), `FaviconController` (replaced by a resource handler in step 25), and `GET /favicon.ico` → `permitAll` in `SecurityConfig`.
- *Done, with findings:*
  - A static file would not work: `/{repository}` matches `/favicon.ico` before the static resources and answers 404 (reserved name). Hence a controller; `favicon.ico` stays a reserved repository name.
  - No `produces` on the mapping: without an `Accept` header D6 assumes `text/html`, then `/{repository}` would win again. The content type is set on the response.
  - Spring Security keeps the controller's `Cache-Control` (it adds its no-cache headers only when none are set).
  - `RepositoryControllerTest` used `/favicon.ico` as an example of an invalid name; now `/.hidden`.
  - Checked on the real server with a browser's `Accept` header: 200, the same bytes as the old version.

### 24. Public files under `/ui/`  ☑
*Requested 2026-11-19:* `/ui/**` without login, for the user interface later; logos and other public files go there.
- **Red** — `UiResourcesTest` (real server): anonymous and logged-in `GET /ui/…` → 200 with the file's content type, also in subfolders (`ui/test/hello.txt` exists only in the test resources); missing files and folders (`/ui/`, `/ui/test/`, `/ui`) → 404; `POST` → 403; ten traversal variants (`..`, `%2e%2e`, `%2f`, `%5c`, `%252e`, `//`, `;x=1`) serve nothing from outside `ui/`; `/notes/` still works. `RepositoryFileParserTest`: `ui` is no repository name.
- **Green:**
  - `WebConfig`: resource handler `/ui/**` → `classpath:/ui/`, ordered before the controllers.
  - `spring.web.resources.add-mappings: false`.
  - `SecurityConfig`: `GET /ui/**` → `permitAll`.
  - `ui` reserved in `RepositoryNames`.
  - `favicon.ico` moved to `src/main/resources/ui/`, so it is also at `/ui/favicon.ico`.
- *Done, with findings:*
  - Same clash as in step 23: `/{repository}/{*path}` matches `/ui/…` and wins over static resources. Ordering the resource handlers first works only without Spring Boot's own `/**` mapping, which would then answer every URL. Checked: with it on, the start page and repositories give 404 (tests fail).
  - The traversal tests passed before the fix too (firewall, Tomcat and the resource handler reject them already). They stay as a guard.
  - Not an operation in the OpenAPI description: springdoc lists controllers, not resource handlers. Mentioned in the common description (`info.description`) instead, together with `/favicon.ico` (requested).
  - Caching: Spring Security's default `no-cache` applies to `/ui/**` for now; set caching when the user interface has real files.

### 25. Consistency and simplification  ☑
*Requested 2026-11-19:* check the docs against the code; remove duplication in code and tests. Behaviour unchanged, except that grants of `%` folders are now rejected (they were never reachable).
- **Docs:** DEV.md (old port in the smoke test of step 9, `ui` in the reserved names, `/favicon.ico` no longer an example of an invalid name); OpenAPI (folder listing: `%` names are hidden too; `GET /{repository}`: login redirect and the same 401 text as elsewhere).
- **Code:**
  - `ApiTexts`: the texts several endpoints share (login redirect, 401, 406) and the note on HTML + JSON on one URL, instead of copies in both controllers.
  - `FolderNames`: one rule for hidden (`.` or `%`) and plain names, used by `FolderGrant`, `RepositoryAccess` and `RepositoryPaths` (before: three checks, two of them without `%`). Red: `FolderNamesTest`, `%` in `RepositoryAccessTest` and in the grants of `RepositoryFileParserTest`.
  - `FaviconController` removed: `/favicon.ico` is a resource handler for `ui/favicon.ico` in `WebConfig` (7 days cache), possible since step 24. `FaviconTest` stayed green throughout.
- **Tests:**
  - `src/test/resources/config/application.yaml` sets `mizoine.users-file` and `mizoine.repositories-dir` for all Spring Boot tests (was repeated in 18 classes; tests without `repositories-dir` read the project root before).
  - `TestUsers` (names, `password(user)`, `loggedInAs(user)` for MockMvc, `basicAuthHeader(user)`) and `TestHttp` (requests to the real test server) instead of credentials and `HttpClient`/Base64 code in many classes; `ProxyHttp` builds on `TestHttp`.
  - `OpenApiDocumentationTest` reduced to smoke tests (requested): title and common description, every endpoint has a summary, the file is written. Details are reviewed in `docs/openapi.yaml`; CLAUDE.md updated.
  - `FaviconTest` reduced to `/favicon.ico` itself; the general rules for `/ui/**` are in `UiResourcesTest`.

### 26. Files in the folder listing  ☑
*Requested 2026-11-19:* show files too, not only folders.
- **Red:**
  - `RepositoryPathsTest`: `childFiles` lists only regular files, sorted, without hidden names (`.` or `%`), without links leading outside the root or to hidden files; links inside work; nothing for missing or unsafe folders.
  - `RepositoryAccessTest`: `visibleFiles` — all files inside a granted folder, none above one (D4: only the way down), none outside the grants, never hidden ones.
  - `RepositoryListingTest`: JSON has `files` next to `folders`; for `guest` no files at `/` and `projects/`, the files of `projects/shared/`; HTML shows file names as text without links (escaped); "Empty folder." only when there are neither.
  - `RepositoriesExampleTest`: the README's curl example (`projects/` has `README.md`).
- **Green:** `RepositoryPaths.childFiles` (same checks as folders: `children(...)` with the kind of entry), `RepositoryAccess.visibleFiles`, `FolderListing.files`, `folder.html`.
- *Decided:* files are names only, no links: they cannot be opened yet, and a link would lead to 404. JSON gets a new field `files`; `folders` is unchanged, so existing clients keep working.

### 27. File names with non-ASCII characters  ☑
*Reported 2026-11-19:* the browser showed `Ru��cksendeportal - Statusverfolgung.pdf` instead of `Rücksendeportal - Statusverfolgung.pdf`.
- **Cause** (reproduced with the jar): Java decodes file names with the charset of the process locale (`sun.jnu.encoding`), not `file.encoding` (UTF-8 by default since Java 18). Started without a UTF-8 locale (`LANG=POSIX` or none, typical for services), it is `ANSI_X3.4-1968`: every non-ASCII byte becomes `�`. The name was decomposed (NFD, `u` + combining diaeresis, as macOS writes it), hence `Ru` + two `�`. `-Dsun.jnu.encoding=UTF-8` is ignored. HTML and JSON were correct.
- **Fix:** run with a UTF-8 locale (`LANG=C.UTF-8`; systemd: `Environment=LANG=C.UTF-8`); the application itself cannot change it. Documented in the README; start script `docs/mizoine.sh` sets `LANG=C.UTF-8` and removes `LC_ALL`/`LC_CTYPE`, which would override it.
- A startup warning (`FileNameEncoding` with its test) was added first and removed again on request: the documentation and the start script are enough.
- Guard: `RepositoryListingTest.nonAsciiFileNamesStayAsTheyAreOnDisk` (an NFD name comes out unchanged in JSON and HTML).
- **Checked live:** with `LANG=POSIX` the names are broken; with `LANG=C.UTF-8` both NFD and NFC names are correct. `docs/mizoine.sh` from an empty environment with `LC_ALL=POSIX`: names correct; without a jar it stops with a message; of several jars it takes the newest (`sort -V`: 1.0.10 after 1.0.9).
- *Not changed:* names are not normalized (NFD stays NFD): plain files are the source of truth, and browsers show both forms the same.


## Files: read and write (planned 2026-11-19)

Requested 2026-11-19:
- read, create, replace and delete files with standard HTTP;
- logged-in users only, with the existing folder grants (a grant allows reading and writing);
- usable by AI agents and by a future JavaScript UI.

Options and recommendations: [docs/technology-decisions.md](docs/technology-decisions.md) (T1 file API, T2 AI connection, T3 UI). Steps 28–32 follow T1 (decided: REST on the file URLs) and need D10–D15 below. Steps 33–35 implement T2 (decided: remote MCP with OAuth, for agents on the smartphone). Steps 36–37 build the browser UI and a plain text editor (T3, T3.1 decided); step 39 adds CodeMirror 6 later.

Rules for all of them:
- same 404 as today for unknown, hidden or not granted paths (D2, D3);
- folders above a grant (D4) stay read-only;
- every endpoint annotated for OpenAPI (smoke test in `OpenApiDocumentationTest`);
- writes never touch `.git` or any hidden name.

### 28. Download a file  ☑
`GET /{repository}/{path}` without trailing slash: a file → its content, a folder → redirect to `…/` (as now).
- **Red:**
  1. `GET` of a granted file → 200, the exact bytes, `Content-Type` from the file name (unknown → `application/octet-stream`).
  2. File responses are safe in the browser: `Content-Security-Policy: sandbox`, `X-Content-Type-Options: nosniff`; HTML, SVG, XML, JavaScript and unknown types with `Content-Disposition: attachment` (D10).
  3. `ETag` and `Last-Modified`; `If-None-Match` → 304; `Range` → 206; `HEAD` without body.
  4. Hidden files, files above a grant, files outside the grants, missing files, links leading outside the root → 404, the same for all; file URL with trailing slash → 404.
  5. Non-ASCII and special names (`a <b>.md`, `Rücksendeportal.pdf` in NFD) download by their encoded URL.
- **Green:** in `RepositoryController`: return a `Resource` (Spring gives `Range` and `HEAD`); `RepositoryPaths.resolveFile` with the same checks as folders.
- The HTML listing links file names (removes the "names only" decision of step 26).
- *Done 2026-11-23 (ub-mode), with findings:*
  - **Redesign of the controller:** `Target` is now a sealed type (`Problem`, `Redirect`, `Folder`, `File`), so each request has exactly one answer; before, a record carried an optional redirect next to the listing data.
  - **Routing by `Accept`:** the HTML and JSON methods (`produces`) answer for files too: a browser navigates with `text/html`, a JSON client gets the raw `.json`. A third method without `produces` answers every other `Accept` (`image/*`, `text/markdown`, …), for files only; a folder there → 406.
  - **Types:** Spring's type table has no `.md` and no `.yaml`, so Markdown would always be downloaded. `FileDownload` has its own small table (`md`, `markdown` → `text/markdown`; `yaml`, `yml` → `application/yaml`), then Spring's.
  - **Inline is an allow-list** (images except SVG, audio, video, PDF, plain text, Markdown, CSV, JSON, YAML); everything else is `attachment`. The file CSP is the application's CSP plus `sandbox` (`SecurityConfig.FILE_CONTENT_SECURITY_POLICY`). Spring Security keeps the header that the controller set (checked live).
  - **ETag:** strong, from size and modification time in nanoseconds. 304, 206 and `HEAD` come from Spring.
  - **`HEAD`:** MockMvc does not drop the body (Tomcat does), so the test checks only the headers; the empty body was checked live with `curl -I`.
  - **Coverage:** JaCoCo 0.8.16 added to the build (`target/site/jacoco/jacoco.xml`, needed for Java 25). Uncovered new lines got behaviour tests. Left uncovered: the `catch` for a file removed between the check and the read (a race, no honest test).
  - **ub-mode tools** (crapper, dryer, mutator) could not be installed: running them was blocked by the permission system. See "Quality gates (ub-mode)" in CLAUDE.md.
  - **Live:** the jar with a test repository, `curl -I` and `curl`: `text/markdown`, `inline`, CSP with `sandbox`, `nosniff`, ETag, exact bytes.
  - *Bug fix 2026-11-23:* Russian text in `.md` showed as `Ð¼Ð¾Ð¹ Ð´Ð¾Ð¼` instead of `мой дом`. Text was sent without charset, and browsers then read it as Windows-1252, two characters per UTF-8 byte pair. Now every `text/*` type is sent with `charset=UTF-8` (`FileDownload.withCharset`); other types unchanged. Red: `FileDownloadTest.textIsDeclaredAsUtf8`. Files in another encoding than UTF-8 would now show wrongly; for a Markdown knowledge base UTF-8 is the norm.

### 29. Writing prerequisites: CSRF for API clients  ☑
- **Red:**
  1. `PUT` / `DELETE` with HTTP Basic and without CSRF token are not rejected by CSRF (before: 403).
  2. With a session cookie and without token → 403; with the token in the `X-CSRF-TOKEN` header → passes (D11).
  3. `POST` with HTTP Basic and without token → still 403.
- **Green:** `SecurityConfig.isApiClientWrite`: CSRF is not required for `PUT` / `DELETE` with an `Authorization` header.
- *Done 2026-11-23 (ub-mode), with findings:*
  - **`POST` is not exempt:** browsers also send a cached Basic login on cross-site form posts. Only `PUT` and `DELETE` are, which cross-site forms cannot send (and there is no CORS).
  - **A `PUT` without any credentials** has no `Authorization` header, so CSRF answers first with 403. An API client always sends credentials; with a wrong password it gets 401 (`FileWriteTest`).
  - **The size limit (planned item 3) moved to step 30:** it needs the `PUT` handler.
  - `ApiClientCsrfTest` writes to a folder that does not exist (`/notes/no-such-folder/`): requests that pass CSRF get 409 and never write into `src/test/resources`.

### 30. Create or replace a file  ☑
`PUT /{repository}/{path}`; the body is the new content.
- **Red:**
  1. New file → 201 with `Location` and `ETag`; existing file → 204 with the new `ETag`; content exactly the body bytes.
  2. `If-Match` with the current `ETag` → written; with an old one → 412, file unchanged. `If-None-Match: *` on an existing file → 412 (D13).
  3. Atomic: a reader during the write sees the old or the new content, never a part; the temporary file is hidden and removed after errors.
  4. Missing parent folder → 409 (D14).
  5. Not writable: hidden names, folders above a grant, outside the grants, `.git/…`, names with `%`, links leading outside the root → 404; a folder at that path → 409.
  6. Works with HTTP Basic (AI agents, curl); HTML page unchanged.
- *Done 2026-11-23 (ub-mode), with findings:*
  - **`RepositoryWriter`** does all writes and answers with a sealed result (`Created`, `Replaced`, `PreconditionFailed`, `ParentMissing`, `IsFolder`, `NotFound`, `TooLarge`); `FileWriteController` only maps results to status codes.
  - **Atomic:** the body is streamed into a hidden temporary file in the target folder (`.mizoine-upload-…`), then moved over the target with `ATOMIC_MOVE`; the temporary file is removed after every outcome, also after a broken upload (tested with a failing stream).
  - **One writer at a time:** the version check and the move run under one lock, so two clients with the same `If-Match` cannot both win. Editors outside Mizoine cannot be locked; the ETag still sees their changes.
  - **Size (D12, was item 3 of step 29):** `mizoine.max-file-size`, default 100 MB. A known `Content-Length` over the limit → 413 before reading; otherwise counted while streaming (chunked uploads, checked on a real server in `FileApiServerTest`).
  - **Permissions:** a replaced file keeps its POSIX permissions; a new one gets the usual ones (umask). A plain temporary file would be readable only by the owner.
  - **Symbolic links:** a link to a visible file inside the root is written through to that file; any other link → 404.
  - **Shared pieces** (one decision in one place): `GrantedRepository` (repository + access, or the one 404) and `RepositoryUrls` (path ↔ segments, encoded URLs), used by both controllers.
  - **Writable** = inside a granted folder and not hidden (`RepositoryAccess.canWriteIn`); folders above a grant stay read-only (D4).
  - The planned "a reader during the write sees old or new" test was not written: `ATOMIC_MOVE` gives it, and a timing test would be flaky. The no-leftover tests cover what can be checked.

### 31. Create a folder  ☑
`PUT /{repository}/{path}/` (trailing slash = folder, as in the Solid Protocol; empty body). Folders are created only explicitly (D14).
- **Red:**
  1. → 201 with `Location`.
  2. Existing folder → 204, nothing changes (PUT is idempotent). With `If-None-Match: *` → 412, as for files.
  3. Missing parent → 409 (D14, as WebDAV `MKCOL`). A file at that path → 409.
  4. A body → 415 (as WebDAV `MKCOL`: a folder has no content).
  5. Same "not writable" cases as step 30.
- *Done 2026-11-23 (ub-mode):* `RepositoryWriter.createFolder` with its own result type (`Created`, `Exists`, `PreconditionFailed`, `ParentMissing`, `FileInTheWay`, `NotFound`). A body, also a chunked one, → 415 (real server). The repository root → 409.

### 32. Delete a file or an empty folder  ☑
`DELETE /{repository}/{path}`, or `…/` for a folder. Folders are removed only explicitly and only when empty (D15).
- **Red:**
  1. File → 204; `If-Match` → 412 if it changed.
  2. Empty folder → 204. Folder with content → 409 with a short reason (D15, as the Solid Protocol), nothing deleted.
  3. A folder whose only content is hidden (`.git`, `.gitkeep`, …) counts as not empty → 409: the user cannot see what would be lost.
  4. The repository root and the top of a grant (the user's own granted folder) → 409.
  5. `DELETE` of a file URL with trailing slash, or of a folder URL without → 404 (the type must match the URL, D5).
  6. Same "not writable" cases as step 30; missing → 404.
- *Done 2026-11-23 (ub-mode):* `RepositoryWriter.delete` with its own result type (`Deleted`, `NotEmpty`, `PreconditionFailed`, `NotFound`). A symbolic link is removed, never its target (file and folder links tested). The top of a grant is recognized by `RepositoryAccess.isGrantedFolder` → 409, before the write check (which would give 404 there).

### 33. MCP endpoint for agents  ☐
T2 decided 2026-11-19: remote MCP in Mizoine, so that Claude (also on the smartphone) and other agents store and retrieve knowledge.
- Tools return files raw, exactly as stored: `read_file` gives the Markdown text, never rendered HTML (step 38 renders only for browsers).
- `/mcp` over Streamable HTTP, with tools built on the same file service as steps 28–32 and the same grants: `list_repositories`, `list_folder`, `read_file`, `write_file`, `create_folder`, `delete`.
- Tool descriptions tell the agent that writes are plain files that a human reviews with `git diff` and commits.
- Login in this step: HTTP Basic, for Claude Code (`--header`), the MCP Inspector and tests. OAuth follows in step 34.
- The agent is a normal user in `users.yaml` with grants only to its folders (e.g. `agent: [knowledge, inbox]`), so hiding and limits come from the existing rules.
- **Red** — an MCP client in the test (Java MCP SDK) against the real server:
  1. `tools/list` shows the tools with descriptions.
  2. The agent user lists, reads and writes in its folders; content round-trips byte for byte (Markdown, non-ASCII).
  3. Paths outside its grants, hidden names, `.git`, `..`, links leading outside: an error without details, the same for "missing" and "not allowed" (D2).
  4. Without login → 401.
- Library (D16): Spring AI's MCP server, or the Java MCP SDK directly; decided by what supports Spring Boot 4 at the time.

### 34. OAuth for chat apps  ☐
What Claude's "custom connector" (and other chat apps) need to log in.
- Mizoine becomes an OAuth 2.1 authorization server (Spring Security's authorization server) and protects `/mcp` with its tokens (resource server).
- Discovery:
  - `/.well-known/oauth-protected-resource` (RFC 9728);
  - authorization server metadata (RFC 8414);
  - `401` on `/mcp` with `WWW-Authenticate: Bearer resource_metadata=…`.
- One client from the configuration (D17): client ID, secret (only a hash stored), allowed redirect URIs (Claude's callback). It is entered in Claude as "your own OAuth client". Dynamic client registration later, if other agents need it.
- Authorization code with PKCE. The login page is the existing form login; the agent user logs in (D19).
- The token stands for that user: the same grants as the user. Tokens kept in memory (D18).
- **Red:**
  1. Metadata endpoints and the 401 challenge.
  2. The full code + PKCE flow in a test gives a token; with it, `/mcp` works as the agent user, with only its grants.
  3. A wrong secret, a wrong redirect URI, a missing PKCE verifier, an expired or foreign token → rejected.
  4. User removed from `users.yaml` → its tokens stop working.

### 35. Connect Claude from the smartphone  ☐
- README section "Connect an AI agent":
  - create the agent user and its grants;
  - configure the OAuth client;
  - on claude.ai: Connectors → add a custom connector with `https://<host>/mcp` and the client ID/secret → Connect → log in as the agent user;
  - it then works in the Claude mobile app.
- nginx: `/mcp` without response buffering (streaming); `/.well-known/` passed through.
- Check live with the real server behind Cloudflare: from the Claude app on the phone, store a note, read it back, and see it as a new file in `git status` on the server.

### 36. Browser UI for files  ☐
T3 decided: a small library without build, Preact + htm or Lit (D26), copied into `src/main/resources/ui/vendor/` with a note of its version and source; the application's code as plain JavaScript modules in `src/main/resources/ui/`.
- upload (with progress), delete and create folder in the listing;
- the CSRF token from the page;
- JavaScript tests with Node's built-in test runner (needs Node.js for development only).

### 37. Text editor in the browser (plain textarea)  ☑
*Requested 2026-11-19:* edit text files in the browser; usable on the smartphone. T3.1 decided: CodeMirror 6 later (step 39). For now a plain `<textarea>`, so that everything except highlighting is built and tested here.
- Builds the editor page described in step 39 (`/ui/editor.html?file=…`, save button, links), with a textarea; step 39 then puts CodeMirror in its place.
- **The editor only uses the public file API** (requested 2026-11-19): it reads with `GET`, creates and saves with `PUT` on the file's URL (steps 28 and 30), the same requests as curl and the MCP tools use. There is no editor-specific endpoint, and the server knows nothing about the editor: the editor is a static file under `/ui/`.
- Open from the listing: text files up to a size limit (D21) open in the editor, others download.
- Save = `PUT` with `If-Match` (D20). On 412 the editor offers to reload or to keep its text; nothing is overwritten silently.
- New file: name + empty editor, saved with `If-None-Match: *`.
- Content stays exactly UTF-8, line endings unchanged.
- **Red:**
  1. JavaScript tests (Node test runner, with a fake `fetch`): loading is a `GET` of the file URL; saving is a `PUT` of the same URL with `If-Match` and the text as body; a new file is a `PUT` with `If-None-Match: *`; 412 leads to the conflict choice; text round-trips without changes to line endings or non-ASCII.
  2. Java: the editor page loads under the strict CSP.
- The editing part (get and set the text) sits behind a small interface, so that step 39 only replaces the textarea.
- *Done 2026-11-23 (ub-mode), with findings:*
  - **The page:**
    - `ui/editor.html` and `ui/editor/editor.css`;
    - `ui/editor/editor.js`: all decisions, without the DOM;
    - `ui/editor/page.js`: only connects them to the page, through the field interface `{getText, setText, onChange}` that step 39 replaces.
  - **No UI library needed** for one page (D26 stays open for step 36).
  - **JavaScript tests** (`src/test/js/editor.test.mjs`, Node's runner with a fake `fetch`, 29 tests) are part of `mvn verify` through exec-maven-plugin: **Node.js must be on the PATH** to build. `-DskipTests` skips them too. `page.js` has no unit tests (no DOM in Node without npm packages); it holds no decisions and was checked live.
  - **Line endings:** a textarea turns CRLF into LF. `restoreLineEndings` gives a file that used CRLF throughout its CRLF back on save, otherwise every line would change in `git diff`.
  - **Login:**
    - A 401 shows "Log in", which opens a new tab, so the text stays.
    - The script's requests carry `X-Requested-With: XMLHttpRequest`, so Spring Security sends no Basic challenge (no browser dialog).
    - Root cause found by a test: the own request cache (clean URLs, step 12) also remembered script requests as "the page to return to after login". Now it skips them, as Spring's default does.
  - **D27 in practice:**
    - `csrf.spa()` (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`);
    - a `CsrfCookieFilter`, because Spring creates the token only when asked and a static page never asks;
    - the cookie gets `SameSite=Lax` (and `Secure` behind HTTPS).
  - **Consequence: `GET /login` no longer creates a session** (the token was what needed one). `ReverseProxyTest` and `UntrustedProxyTest` checked the session cookie of the login page; they now log in for real through the proxy.
  - **Test tooling:** spring-security-test's `csrf()` replaces the token repository of the whole test context with a session-based one. `EditorPageTest` therefore runs in its own context and logs in like a browser (login page → cookie + form token → POST). One assertion of `ApiClientCsrfTest` that relied on `csrf().asHeader()` was removed; `EditorPageTest` covers it.
  - **Bug found live, fixed at the root:** `curl --data-binary` sends `application/x-www-form-urlencoded` by default, and Spring Boot's `FormContentFilter` read such `PUT` bodies as form fields, so the file was saved **empty**. The filter is off (`spring.mvc.formcontent.filter.enabled: false`); Mizoine has no form `PUT`s. Red: `FileWriteTest.contentIsStoredWhateverTheContentType`.
  - **Live** (jar, curl with a cookie jar, the editor's requests):
    - the page and its modules load without login;
    - load before login → 401;
    - form login, then load as the script;
    - save with the cookie's token → 204; with a stale ETag → 412; without token → 403;
    - the bytes on disk are exact, with CRLF and `ü`.


### 38. Markdown as a readable page  ☑
*Requested 2026-11-19.* Needs only step 28 (reading files), so it can be done right after it.
- `GET /{repository}/…/document.md` from a browser shows the rendered document on the same URL, chosen by `Accept` (D22).
- Pictures and links are relative Markdown links, resolved by the browser against the document's folder: `![](images/a.png)`, `[x](other.md#part)`, `[y](sub/)`.
- Bookmarks: every heading has an `id`, GitHub style (D23), so `document.md#topic` works.
- Options and reasons: [docs/technology-decisions.md](docs/technology-decisions.md), T3.2.
- **Agents always get raw files** (requested 2026-11-23): access through the MCP API or with an OAuth token (steps 33–34) never renders a page, whatever `Accept` says. Rendering is only for people in a browser.
- **Red:**
  1. Which version is returned:
     - `Accept: text/html` (as browsers send it) → 200 `text/html`, rendered;
     - `Accept: text/markdown`, `*/*` or none → the exact bytes as `text/markdown; charset=UTF-8`;
     - `?raw` → raw;
     - all answers have `Vary: Accept`;
     - with an OAuth token (`Authorization: Bearer …`, step 34) → raw even with `Accept: text/html` (written when step 34 exists, or step 38 adds the rule with a fake bearer request if it comes first).
  2. Relative references stay as written in the HTML (`<img src="images/a.png">`, `<a href="other.md#part">`), and the picture itself downloads at that URL. A test fetches both.
  3. Heading IDs:
     - `## Costs and Risks` → `id="costs-and-risks"`;
     - a repeated heading → `-1`;
     - `## Über uns` → `id="über-uns"`;
     - a link to `#costs-and-risks` in the same page matches.
  4. GitHub's Markdown features: tables, strikethrough, task lists, autolinks, fenced code blocks (with a `language-…` class for highlighting later).
  5. Safety:
     - HTML inside the Markdown (`<script>`, `<img onerror=…>`) appears as text;
     - `[x](javascript:…)` gives no such link;
     - the strict CSP header is unchanged.
  6. The page:
     - its title is the first heading, else the file name;
     - links to the folder (`./`) and to the raw file (`?raw`);
     - an **Edit** link to the editor page, `/ui/editor.html?file=<the file's path, encoded>` (step 39). Only shown when the user may write the file (`canWriteIn` of its folder); read-only users see no edit link.
  7. The same 404 cases as step 28. A file over the size limit (D21), or not UTF-8, is returned raw, not rendered.
  8. OpenAPI: the file `GET` lists both media types (smoke test only).
- **Green:** commonmark-java with its GFM extensions and heading anchors (D23); a Thymeleaf page around the rendered HTML; the `Accept` choice in `RepositoryController`.
- The folder listing links `.md` files to this page; other files download (step 28).
- *Done 2026-11-23 (ub-mode), with findings:*
  - **Two pure pieces:**
    - `MarkdownRenderer` (Markdown → title + HTML) with commonmark-java 0.33.0 and its GFM extensions;
    - `MarkdownPage` (whether the request gets the page; whether the file can be one).
    The controller only joins them.
  - **D23 needs no own generator:** commonmark's heading-anchor extension gives GitHub's IDs, also for non-ASCII (`über-uns`, `мой-дом`) and punctuation (`whats-new-2026`).
  - **D22 in practice:** with an HTML and a JSON method on one URL, Spring sends `*/*` and requests without `Accept` to the HTML method (D6). So the method itself checks whether `text/html` is named explicitly (and not refused with `q=0`). A `Bearer` token or `?raw` gives the raw file; the token is not checked yet (step 34), but the header already decides.
  - **`?raw` has no value:** MockMvc passes it as `null`, Tomcat as `""`. The controller checks whether the parameter is present, and documents it on the method for OpenAPI.
  - **`rel="nofollow"`:** commonmark adds it to every link when it sanitizes URLs. It is harmless, so the tests check `href` and link text, not whole tags.
  - **No `sandbox` on the page:** a sandboxed page has an opaque origin, so its pictures would be loaded cross-site, without the `SameSite=Lax` session cookie, and would end at the login page. The page keeps the application's strict CSP; HTML from the file is escaped (D24).
  - **`Vary: Accept`** on raw files too (`FileDownload`), so caches keep page and file apart.
  - **Edit link for everyone who can read:** every grant allows reading and writing, so the planned "only for writers" check would always be true, and dead code. Add it when read-only grants come.
  - **The Edit link leads to `/ui/editor.html`, which does not exist yet** (steps 37, 39): 404 until then.
  - **Changed test:** `FileDownloadTest.contentIsTheExactBytesWhateverTheBrowserAccepts` now uses a `.txt` file; for a browser, a `.md` is a page since this step.
  - **Untested:** the `catch` for a file removed between the check and the read (a race).
  - **Live** (jar, `curl` with a browser's `Accept`): Russian title, heading ID `расходы`, links, `Vary: Accept`; raw for curl's default and `?raw`.


### 39. Syntax highlighting with CodeMirror 6  ☑
*When highlighting is wanted* (T3.1). Replaces the textarea of step 37; loading, saving and conflicts stay as they are.

**The editor page** (requested 2026-11-23; first built in step 37 with a textarea):
- A static page under `/ui/`: `/ui/editor.html?file=/notes/projects/plan.md`. The URL parameter `file` is the file's path on the server, URL-encoded (D28).
- It shows the editor with the file's content, and a **Save** button.
- It uses only the existing file API:
  - load with `GET` of that path (`Accept: text/markdown` or `*/*`, so never the rendered page);
  - save with `PUT` and `If-Match`;
  - a missing file opens empty and is created with `If-None-Match: *`.
- It sends `X-Requested-With: XMLHttpRequest`. Without login, Spring Security then answers 401 without the Basic challenge, so the browser shows no login dialog of its own. The page shows a "Log in" link instead and keeps the text.
- CSRF: the page reads the token from Spring Security's CSRF cookie and sends it as a header (D27).
- `file` must be a path on this server: starting with `/`, not with `//`, no scheme. Anything else is rejected before any request.
- Links back to the rendered page (`…/plan.md`) and to the folder (`…/`).
- Save is disabled while nothing changed; leaving the page with unsaved changes asks first.
- The language comes from the file name: `.md`, `.json`, `.yaml`/`.yml`, `.html`, `.css`, `.js`, `.xml`, `.java`, `.properties`, `.sh`, …; plain text otherwise.
- **Red:**
  1. JavaScript: choice of language by file name; the step 37 tests pass unchanged with CodeMirror in place of the textarea.
  2. JavaScript (with a fake `fetch`):
     - `?file=` decides the URL of `GET` and `PUT`;
     - an absolute URL, `//host/…` or `javascript:` in `file` makes no request at all;
     - 401 shows the login link and keeps the text;
     - the CSRF header is sent with `PUT`;
     - the links point to the page and to the folder.
  3. Java: the editor page and `ui/vendor/` load under the strict CSP without login. If CodeMirror needs a style nonce, a nonce per response in the CSP header.
  4. Java: with the CSRF cookie (D27), a `PUT` with the cookie's token in the header passes; without the header → 403.
- The vendor bundle is made by a small script in the repository (`ui/vendor/README` says how and which versions). It runs only when the editor is updated, not in `mvn verify`.
- *Done 2026-11-23 (ub-mode), with findings:*
  - **Bundle:** CodeMirror 6.0.2 with the language packages for Markdown, JSON, YAML, HTML, CSS, JavaScript/TypeScript, XML, Java, and `legacy-modes` for properties and shell.
    - Built with npm in `tools/codemirror/` (`npm ci`, `npm run build`: esbuild; exact versions in `package-lock.json`; `.npmrc` pins exact versions and turns install scripts off) into `ui/vendor/codemirror.js`, about 700 kB, with `codemirror.LICENSE.txt` (MIT).
    - Not part of `mvn verify`; `tools/codemirror/node_modules/` is git-ignored.
  - **Upgrading the libraries** (added on request): plain npm, described in `tools/codemirror/README.md` (`npm outdated`, `npm install <package>@latest …`, `npm run build`). No shell scripts (requested). Checked: `npm run build` gives the same bundle byte for byte.
  - **Language choice** is plain logic in `editor.js` (`languageFor`, 20 tests); the bundle only maps the name to the package.
  - **Same interface as the textarea:** `codeMirrorField(host, language)` → `{getText, setText, onChange}`. `page.js` loads the bundle with a dynamic `import`; if it fails, the textarea stays (logged in the console). The step 37 tests pass unchanged.
  - **CSP:** CodeMirror's style library inserts a `<style>` element into a normal document, which the strict CSP blocks. In a shadow root it uses a constructed style sheet (`adoptedStyleSheets`) instead, so the editor is mounted in one. No nonce and no CSP change needed. The bundle has no `eval` or `new Function`.
  - **Checked in a real browser** (headless Chrome on the Windows side, against the jar):
    - CodeMirror is styled: gutter, active line, frame;
    - `?file=/ui/editor/editor.js` (a public file, so no login needed) shows JavaScript highlighting, folding markers and wrapped lines;
    - no CSP errors in the console;
    - not logged in: the page says so.
  - **Gap found by that check, fixed test-first:** when `fetch` itself fails (no answer: network down, server stopped), the editor showed nothing; the error stayed in the console. Now every request goes through one place in `FileSession`, which turns "no answer" into status 0. The page says "no answer from the server" and keeps the text (2 JavaScript tests).
  - **Not checked in the browser:** highlighting of a logged-in Markdown file. Headless Chrome with a session needs a scripted login; the styles come from the same mechanism as the JavaScript highlighting that was checked.
- *Bug fix 2026-11-23: every save was "Changed elsewhere"* (reported from the real setup, behind Cloudflare):
  - **Reproduced** by a new end-to-end test, `EditorEndToEndTest`:
    - the real `editor.js` runs in Node (`src/test/js/e2e/editor-flow.mjs`) against the real server, with a browser's session (form login, cookies, CSRF header): load, save, save again;
    - once direct (passed), once through `WeakeningProxy`, which does to ETags what compressing proxies and CDNs do: `"x"` → `W/"x"`. That run gave "conflict" on every save.
  - **Cause:** `If-Match` was compared as exact text, and the editor sends back the weak tag it received.
  - **Fix:** `FileDownload.isSameVersion` compares the tag values without `W/`, for save and delete. Mizoine's tags are strong (size and modification time); `W/` only shows that the response was compressed on the way. An old tag, weak or not, still fails (`RepositoryWriterTest`).
  - The end-to-end test stays as the harness for such problems: it runs in `mvn verify` (needs Node.js).


---

## Open decisions

- ~~Step 3.8: ignore or reject unknown fields in `users.yaml`?~~ **Decided:** ignore them and log a warning.
- ~~Step 4.3: case-sensitive usernames?~~ **Decided:** yes, case-sensitive.
- Roles: everyone is `USER` for now. AI-agent role from BACKLOG §7 comes later as a new field `role` in `users.yaml`.
- Sessions of users removed from `users.yaml` stay valid until they expire (step 9): add invalidation if needed.
- Run the dependency check once an NVD API key is available (step 8).
- ~~Default port~~ **Decided 2026-11-18:** `12013` instead of Spring Boot's 8080 — "MIZOINE" in digits (M and N skipped, I=1, Z=2, O=0, I=1, E=3). Test: `MizoineApplicationTests.defaultPortIsMizoineInDigits`. Older step notes mentioning 8080 describe the state at that time.
- ~~Rate-limit HTTP Basic login attempts~~ **Done:** step 13.

### Repositories and API documentation (steps 14–22) — decided 2026-11-18
- ~~Load all repository files at startup?~~ **Decided 2026-11-18:** no — lazy lookup per request (step 16).
- **D1 — file format:** `access` as a map `username → [folders]` (shown above). **Decided:** yes — short to write, duplicates impossible. Alternative: a list of `{user, folders}` entries.
- **D2 — no access vs. unknown:** **Decided:** both **404**, so a user cannot find out which repositories or folders exist. Alternative: 403 for "exists, but not for you".
- **D3 — hidden folders:** **Decided:** all names starting with `.` (`.git`, `.mizoine`, …) are never listed nor reachable. Alternative: hide only `.git`. *Added 2026-11-18:* names containing `%` are hidden the same way (the firewall rejects them in URLs, step 21).
- **D4 — folders above a granted folder:** with only `projects/shared` granted, **Decided:** the user may open `/notes/` and `/notes/projects/`, but sees only `projects` and `shared` on the way. Alternative: the user must open `/notes/projects/shared/` directly.
- **D5 — trailing slash:** **Decided:** folder URLs end with `/`; without it → redirect. Needed for relative links in the HTML.
- **D6 — default format:** **Decided:** HTML when `Accept` is missing or `*/*` (browsers, plain curl); JSON only when asked for. Anything else → 406.
- **D7 — invalid repository file while running:** **Decided:** the repository becomes unknown (404) until the file is fixed, error logged once. Different from `users.yaml` (step 9 keeps the last valid users): keeping old permissions after the file was changed could keep access the editor wanted to remove. Alternative: keep the last valid version, like users.
- **D8 — error details for entitled users** *(added 2026-11-18 by request):* if the repository file can be read far enough to know the permissions, a user with a grant for the **whole repository** (`/`) gets the details of what is wrong (e.g. a wrong root path), with HTTP 500. Users with partial grants get 500 without details; users without a grant get 404 (D2). If the permissions cannot be read (malformed YAML, broken `access` section), everyone gets 404 and the details go only to the log (D7).
- **D9 — Swagger UI:** **Decided 2026-11-18:** no Swagger UI, and the application does not serve the API description at all; it is generated during the build into `docs/openapi.yaml` in the repository (step 15).
- Repository names share the URL space with the application (`/login`, `/logout`, …): reserved names are rejected (step 14). Alternative: a prefix like `/r/{repository}/` — not chosen: `/{repository}/` was requested.
- ~~The start page `/` could list the user's repositories~~ **Decided 2026-11-18:** yes, step 19.
- ~~Fail to start without repositories?~~ **Decided 2026-11-18:** no; folder missing or empty → start (warning in the log if missing), message on the start page.
- ~~Repositories folder when not set?~~ **Decided 2026-11-18:** the current directory (`.`); the users file in it is never a repository.

### Files: read and write (steps 28–36) — to decide
Proposals; details in [docs/technology-decisions.md](docs/technology-decisions.md).
- **T1 — file API style:** **Decided 2026-11-19:** REST on the file URLs (GET / PUT / DELETE), a subset of WebDAV, so that WebDAV (`MOVE`, `PROPFIND`, …) can be added later on the same URLs. Not chosen: WebDAV now; own JSON operations.
- **T2 — AI connection:** **Decided 2026-11-19:** remote MCP server in Mizoine (`/mcp`) with OAuth (steps 33–35). Requirement: Claude or another agent on the smartphone stores and retrieves knowledge as a technical agent user limited to a few folders, connected in the most standard and easy way. Only a remote MCP connector reaches smartphone chat apps, and the same connection serves Claude Code. Not chosen: REST + Skill, local MCP program, WebDAV mount.
- **T3 — UI technology:** **Decided 2026-11-19:** a small library without build step, Preact + htm or Lit, copied into `ui/vendor/` (D26). Not chosen: plain JavaScript modules only; server pages only; htmx; SPA with a Node build.
- **D26 — Preact + htm or Lit:** *To decide in step 36.* Preact + htm: React-style components and state, ~5 kB. Lit: web components, a browser standard, ~6 kB. Both run without build and under the strict CSP.
- **D10 — showing files in the browser:** **Implemented 2026-11-23 as proposed (step 28):** inline only for types a browser does not execute (images, PDF, plain text, …), always with `CSP: sandbox` and `nosniff`; `attachment` for HTML, SVG, XML, JavaScript and unknown types. Alternative: a separate domain for downloads.
- **D11 — CSRF for writes:** **Implemented 2026-11-23 as proposed (step 29), limited to PUT and DELETE:** token required for session-cookie requests (the UI), not for requests with an `Authorization` header (agents, curl). Cross-site forms cannot send PUT or DELETE, and there is no CORS. Alternative: a token endpoint that API clients must call first.
- **D12 — maximum file size:** **Implemented 2026-11-23 as proposed (step 30):** `mizoine.max-file-size`, default 100 MB (nginx at the server has `client_max_body_size 100M`).
- **D13 — concurrent changes:** **Implemented 2026-11-23 as proposed (steps 30, 32):** `ETag` from size and modification time; `If-Match` / `If-None-Match: *` honoured, not required. Alternative: require `If-Match` for every replace (safer, but more work for every client).
- **D14 — missing parent folders:** **Decided 2026-11-19:** 409; folders are created only explicitly (`PUT …/`, step 31). The same rule as WebDAV (`PUT` and `MKCOL` answer 409 when the parent is missing). Not chosen: create parents automatically, as the Solid Protocol does (handier for agents, but a typo creates a new folder tree).
- **D15 — deleting folders:** **Decided 2026-11-19:** only empty folders; with content → 409, as the Solid Protocol. Not chosen: recursive delete, as WebDAV `DELETE` (one request removes a whole tree; Git keeps the history only of what was committed). If WebDAV is added later, its recursive `DELETE` needs a decision of its own.
- References for the folder API: WebDAV ([RFC 4918](https://www.rfc-editor.org/rfc/rfc4918)) and the [Solid Protocol](https://solidproject.org/TR/protocol). Mizoine takes from each the part that matches D14 and D15. `PUT …/` instead of WebDAV's own `MKCOL` method: Spring MVC and Spring Security's firewall accept only the standard HTTP methods, and OpenAPI 3.1 cannot describe others. `MKCOL` can be added as an alias when WebDAV comes.
- **D16 — MCP library:** Spring AI's MCP server (with the Spring AI community `mcp-security` module) or the Java MCP SDK directly. *To decide in step 33* by Spring Boot 4 support at that time.
- **D17 — OAuth clients:** *Proposed:* one client configured in Mizoine ("Use your own OAuth client" in Claude): least code, no open registration endpoint. Alternatives: dynamic client registration (any agent can register itself); "Claude's published identity" (client ID metadata documents).
- **D18 — token storage:** *Proposed:* in memory, so no database (BACKLOG principle 1); after a restart the connector logs in again. Alternative: a token file.
- **D19 — who logs in during "Connect":** *Proposed:* the agent user itself (its password is entered once in the phone's browser). Alternative: the human logs in and picks which agent user the connector acts as (more code: a consent page with a choice).
- **T3.1 — text editor:** **Decided 2026-11-19:** CodeMirror 6, bundled once into `ui/vendor/` (best on the phone; Markdown, JSON, YAML packages). For the moment it is more than needed: the editor starts as a plain `<textarea>` (step 37), and CodeMirror 6 replaces it when highlighting is wanted (step 39). Not chosen: Ace; Monaco (not supported on mobile browsers).
- **D20 — saving from the editor:** **Decided 2026-11-23 as proposed:** always with `If-Match`; on 412 offer reload or keep. (D13 makes `If-Match` optional for API clients; the UI always uses it.)
- **D21 — what opens in the editor:** **Decided 2026-11-23 as proposed:** text files (valid UTF-8, no NUL bytes) up to 2 MB; others are downloaded.
- **T3.2 — Markdown page:** **Decided 2026-11-23 as proposed:** rendered on the server with commonmark-java, on the file's own URL (step 38). Not chosen: flexmark-java (more dialects, heavy); rendering in the browser (needs JavaScript; agents and curl get no page).
- **D22 — rendered or raw:** **Decided 2026-11-23 as proposed:** rendered when the request asks for `text/html` (browser navigation); raw for `text/markdown`, `*/*` or no `Accept` (curl, downloads, agents); `?raw` forces raw; `Vary: Accept`. This is the opposite default of folders (D6), because a file has bytes to give. Not chosen: a separate URL prefix for rendered pages (e.g. `/view/…`); rendered also for `*/*`. **Added 2026-11-23:** never rendered for agents: MCP tools and requests with an OAuth token (`Bearer`) always get the raw file.
- **D23 — heading IDs:** **Decided 2026-11-23 as proposed:** GitHub's rules (lower case, spaces → `-`, punctuation removed, non-ASCII letters kept, repeats `-1`, `-2`), so that links written for GitHub or VS Code keep working. To check in step 38: whether commonmark-java's heading-anchor extension follows these rules exactly, else a small own ID generator.
- **D24 — HTML inside Markdown:** **Decided 2026-11-23 as proposed:** shown as text, not rendered; unsafe link schemes dropped. Not chosen: allow a safe subset with an HTML sanitizer (e.g. OWASP Java HTML Sanitizer).
- **D25 — external pictures:** **Decided 2026-11-23 as proposed:** not shown (CSP allows only Mizoine's own files): no tracking, no mixed content. Not chosen: allow `https:` pictures (`img-src 'self' https:`).
- **D27 — CSRF token for the static UI pages:** **Decided 2026-11-23 as proposed:** Spring Security's cookie token (`CookieCsrfTokenRepository`, readable by JavaScript), sent back in the `X-XSRF-TOKEN` header: the standard setup for pages without a server template. The session-bound default cannot reach a static file under `/ui/`. Not chosen: a small endpoint that returns the token (one more endpoint).
- **D28 — the editor's URL:** **Decided 2026-11-23 as proposed:** `/ui/editor.html?file=/repository/path/file.md`, the file's path on the server as one parameter, so that the page needs no knowledge of repositories. Not chosen: two parameters (`repository`, `path`).
- **Later, not planned yet:** rename and move (`MOVE`), copy, live Markdown preview in the editor, Obsidian `[[wiki links]]`, links relative to the repository root (`/…`), syntax highlighting of code blocks in the page, per-grant read-only permissions.
