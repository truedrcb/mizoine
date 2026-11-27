# Mizoine 1.0 — Development Plan

**Status** (updated: 2026-11-27): steps 0–34, 37–48, 55–78 done (1169 Java and 118 JavaScript tests green; file API, MCP for agents with OAuth, Markdown page, editor, search with index state and reindexing on request, folder tags and statuses, uploads and editable files, upload links). Step 35: works from the phone; first Connect fixed at the root (the login ends on its own page, CSP form-action 'self' only, 2026-11-27), live re-check open; planned: 36 (browser UI: one frame for all pages, 36a–36i; D90–D97 to decide), 49–54 (stable URLs, postponed). Decided: T1 REST (implemented), T2 remote MCP, T3 small library without build, T3.1 CodeMirror 6 (step 39, done), T3.2 (commonmark-java), D10–D15, D20–D25, D27, D28. Open: D26, see [docs/technology-decisions.md](docs/technology-decisions.md).

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
- *2026-11-24 (requested):* `docs/mizoine.sh` starts Mizoine **in the background** (`nohup`, log `mizoine.log`, process ID `mizoine.pid`). It refuses a second start, replaces a stale PID file, and waits until Spring logs "Started MizoineApplication" or Java ends (then the end of the log is shown). Stop: `kill $(cat mizoine.pid)` (graceful shutdown). README: section "Start and stop on a server".
  - Tested by hand: start → 200; second start refused; `kill` → graceful shutdown; stale PID file plus a port in use → "did not start" with Spring's reason, no PID file left.
  - A first version waited a fixed 3 s and reported "started" before the server answered, or even when the port was in use; now it waits for the log line.
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

### 33. MCP endpoint for agents  ☑
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
- *Done 2026-11-23 (ub-mode), with findings:*
  - **D16 decided:** the official MCP Java SDK 2.2.0 (`mcp-core` + `mcp-json-jackson3`, fits Spring Boot 4 and Jackson 3), without Spring AI.
    - Stateless Streamable HTTP: `HttpServletStatelessServerTransport` as a servlet at `/mcp`, no MCP sessions.
    - Spring AI 2.1 would also work, but brings a framework for one endpoint.
  - **Redesign first** ("do not bolt it on"): the file rules lived in the HTTP controllers. New `RepositoryFiles` is the one place for what a user may do: list repositories and folders, read text, write, create folder, delete. It works with usernames; "not visible" is its own exception (`NotVisibleException`: 404 for HTTP, a tool error for MCP).
    - `FileWriteController` uses it; `GrantedRepository.find` returns an `Optional`.
    - Deleting the top of a grant is a result of its own (`DeleteResult.IsGrantedFolder`), so MCP gets the same rule.
    - `TextFile` (valid UTF-8, up to 2 MB, D21) moved out of `MarkdownPage`; both the Markdown page and `read_file` use it.
  - **Tools** (`McpTools`): `list_repositories`, `list_folder`, `read_file` (raw, never rendered), `write_file` (optional `if_match`), `create_folder`, `delete`. They are adapters over `RepositoryFiles`; descriptions tell the agent that a human reviews with `git diff`; error texts say what to do ("create it with create_folder first").
  - **Login and CSRF:**
    - The user comes from the request that Spring Security's filters already checked (`contextExtractor`).
    - `POST /mcp` with `Content-Type: application/json` needs no CSRF token: cross-site forms cannot send JSON, a text/plain form post gets 403 (tested).
    - Without credentials the answer is **401, not 403**: found in the live check; chat apps need the 401 to find the OAuth login (step 34).
  - **Other changes:** `mcp` is a reserved repository name. The common API description mentions `/mcp` (not a controller, so not listed).
  - **Tests:** `McpEndpointTest`, 19 tests with the SDK's own client against the real server. The agent (test user guest, grants `knowledge` and `inbox`) lists, reads byte for byte and writes; it sees nothing else; the same "Not found" for missing and not allowed; an old version is not overwritten.
  - **Live** (jar + curl, JSON-RPC): initialize, `tools/list`, `write_file`, `read_file` outside the grant → "Not found"; no warnings in the log.
  - **Make it right (2026-11-24):**
    - CRAP from JaCoCo showed `McpTools.createFolder` at 10.7: only the success cases were tested.
    - `McpToolsTest` (8 tests, no server: the real `RepositoryFiles` on a temporary folder) now checks every answer of every tool: parent missing, file in the way, folder exists, not empty, granted folder, too large, not text, missing argument.
    - It found two different "Not found" texts (the folder's '/' lost in one): now the tool always repeats the agent's path.
    - `OAuthConfigTest`: OAuth without `mizoine.public-url` stops the start with a clear message.
    - None at CRAP 10 or more.


### 34. OAuth for chat apps  ☑
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
- *Done 2026-11-23 (ub-mode), with findings:*
  - **Spring's authorization server and resource server** (Boot 4.1 starters with the `security-` prefix), configured by Mizoine in `OAuthConfig`, only when `mizoine.oauth.client-id` is set.
    - **D17:** one client from the settings, secret only as bcrypt hash; code + PKCE required, no consent page, no client registration endpoint.
    - **D18:** signing key and authorizations in memory; access tokens 1 h, refresh tokens 30 days.
    - **D19:** Mizoine's own login form, the agent user logs in.
  - **`mizoine.public-url` is required with OAuth:** issuer, resource and metadata must name the public address; behind the user's nginx the request's host is `localhost`.
  - **Tokens:** a JWT is valid at Mizoine only if all of these hold:
    - signature, time and issuer are right;
    - the audience is `<public-url>/mcp` (set by a token customizer);
    - its user still exists in the users file, so removing the user stops its tokens.
  - **Discovery:**
    - Spring Security 7 already serves the protected resource metadata (RFC 9728); my own controller for it was removed again ("Subtract"). The built-in endpoint gets the public address, and loses the claim `tls_client_certificate_bound_access_tokens: true` (tokens are not bound to client certificates).
    - A 401 at `/mcp` carries `WWW-Authenticate: Bearer resource_metadata="…"` (`McpOAuthEntryPoint`).
  - **Root cause found:** Spring Boot's authorization-server auto-configuration creates its own key and `JwtDecoder` whenever the starter is on the classpath. The first version took "a `JwtDecoder` exists" as "OAuth is on", which broke every context without OAuth. Now the setting decides (`MizoineProperties.oauthEnabled()`), and Boot's OAuth auto-configurations are excluded (no key generated without OAuth).
  - **Tests:** `OAuthTest`, 10 tests on a real server.
    - Discovery: both metadata documents, and the 401 with its pointer.
    - The whole flow as phone and chat app: authorize → login page → agent logs in → code → token with PKCE → MCP with the agent's grants only. Renewal with the refresh token.
    - Refused: wrong client secret, unregistered redirect URI, missing PKCE verifier, forged token, user removed.
  - **Live** (jar with the settings as command-line options): metadata with the public address, 401 with `resource_metadata`, authorize → `/login`; no warnings.
- *Decided with this step:* D17, D18, D19 as proposed.
- *Option 1, 2026-11-24 (requested): several OAuth clients.*
  - `mizoine.oauth.clients` is a list (`MizoineProperties.Client`: id, secret, redirect URIs); `OAuthConfig` registers each.
  - Checked at startup, with messages naming the setting: each client needs an id, a bcrypt hash as secret (plain text refused) and at least one callback; ids unique.
  - The single-client settings (`client-id`, …) are gone without a compatibility layer: they were never committed.
  - **Tests:** `MizoinePropertiesTest` (binding, four invalid cases, a duplicate id); `OAuthTest` with two clients: the second app connects with its own client; one app's secret does not work for the other; one app's callback is not allowed for the other.
  - **A runtime deprecation the compiler cannot see**, caught by the "starts without warning" tests: Hibernate Validator warns that `@Valid List<Client>` is deprecated; now `List<@Valid Client>`.
  - **Red not seen** for the two-client tests (written after the change). Instead a manual mutation test: registering only the first client makes `eachConfiguredAppConnectsWithItsOwnClient` fail.


### 35. Connect Claude from the smartphone  ☐
- README section "Connect an AI agent":
  - create the agent user and its grants;
  - configure the OAuth client;
  - on claude.ai: Connectors → add a custom connector with `https://<host>/mcp` and the client ID/secret → Connect → log in as the agent user;
  - it then works in the Claude mobile app.
- nginx: `/mcp` without response buffering (streaming); `/.well-known/` passed through.
- Check live with the real server behind Cloudflare: from the Claude app on the phone, store a note, read it back, and see it as a new file in `git status` on the server.
- *2026-11-23:* the README section "Connect an AI agent (MCP)" is written (agent user, Claude Code with Basic, Claude on web and phone with OAuth, restart, Cloudflare). nginx needs nothing special for `/mcp`: the stateless endpoint answers with JSON, not a stream. **Open: the live test from the Claude app on the phone** against the real server behind Cloudflare.
- *2026-11-24:*
  - README: a check before Connect (the RFC 9728 metadata and the 401 of `/mcp` must name the public address).
  - **Security review of the write operations**, asked for with this step.
    - Tests added:
      - `WritePermissionMatrixTest` (unit, through `RepositoryFiles`, the layer HTTP and MCP share). Write, create folder, delete and move in both directions are tried against every kind of forbidden place: above a grant, beside it, other repositories, the root, hidden names, `..`, absolute paths, and links out of the repository and to non-granted folders. Each case must be refused with nothing changed on disk (a snapshot of the whole temporary tree). A control test checks that everything inside the grants works.
      - `UnsafeWriteUrlTest` (real server). PUT, DELETE and move with the encoded traversal forms of `UnsafeUrlTest`, in the URL and in the `Destination` header: each gives 400 or 404 and changes nothing.
    - **Found and fixed: a symbolic link inside a granted folder that points to a non-granted folder of the same repository led around the grants.**
      - It worked for reading, listing, writing, creating folders, deleting and moving there.
      - Cause: the grants were checked on the requested path only, while the file system follows the link. `RepositoryPaths` only kept real paths inside the root and not hidden.
      - Fix, one rule in one place: the grants must hold for the requested path *and* for where it really leads. `RepositoryPaths.realSegments` follows links as far as the path exists. `RepositoryFiles.mayEnter`, `readable` and `mayWriteIn` check both, and `RepositoryController` reuses them. Listings hide such links.
      - Links to granted places keep working.
    - Untested branch: the `catch` in `RepositoryPaths.realRoot`. The root would have to vanish between resolving a path below it and resolving the root itself, a race.
    - CRAP of the changed methods ≤ 6, branch coverage 100 %.
- *2026-11-24, live test:* the phone reaches Mizoine's MCP, but the first Connect failed. It only worked after logging in to the Mizoine UI first, in the desktop browser and again in the phone's browser.
  - Cause: the pages' CSP `form-action 'self'`. Browsers apply it also to the redirects after a form is sent: login form → `/oauth2/authorize` → `https://claude.ai/api/mcp/auth_callback`, and the last step was blocked. Already logged in, the authorize step is a plain navigation without a form, which is why the workaround helped.
  - The test's HTTP client does not enforce CSP, so `OAuthTest` passed.
  - Fix: `form-action` also allows the origins of the configured `redirect-uris` (`SecurityConfig.contentSecurityPolicy`). Without OAuth the policy is unchanged.
  - Tests: `OAuthTest.theLoginFormMayLeadOnToEachAppsCallback`, `ContentSecurityPolicyTest`.
  - **Open: re-check live after deploying.** Remove the connector, add it again, and Connect without logging in to the UI first. Then tick the step.
- *2026-11-27, live: the same symptom after a restart of Mizoine.* claude.ai had to connect anew (tokens in memory, D18, as expected); the Connect only finished after logging in to Mizoine in the browser first.
  - **Cause, again `form-action`:** the 2026-11-24 fix allowed the origins of the configured callbacks. But browsers check every redirect after the form, also the ones the chat app's callback makes itself (to an address not in the list), so the chain could still be blocked. Allowing origins chases the app's own redirects.
  - **Fix at the root:** after the login form, a saved OAuth authorization is no redirect any more: `LoginSuccessHandler` answers with Mizoine's own small page ("Logged in. Continue"), which goes on with `<meta http-equiv="refresh">` (and a link) as a plain navigation; `form-action` covers only form submissions and their redirects. Every other login redirects as before (302, relative). The CSP is one constant again, `form-action 'self'`, without the callbacks' origins; `ContentSecurityPolicyTest` (which tested the list of origins) is removed.
  - Tests (red first): `OAuthTest.tokens` (the login answers 200 with the continue page and no `Location`; the page leads to `/oauth2/authorize?…`; from there to the callback), `OAuthTest.formsPostOnlyToMizoineItself` (`form-action 'self'` only).
  - Tools: crapper all below 10 (`LoginSuccessHandler.onAuthenticationSuccess` 4); dryer nothing new; mutator 25 of 26 killed in the group's tests; the survivor (the editor's script requests are no page to return to, step 37) is killed by `EditorPageTest`, checked by hand.
  - **Open: re-check live after deploying** (as above).
- *2026-11-24, review after commit 61d9852:*
  - **Two more ways around the grants through links, found and fixed.**
    - Writing to a link to a file wrote through to that file, while only the link's folder was checked: `granted/link-secret` → `private/secret.md` was overwritten.
    - A granted folder reached through a link (`granted/link-here/empty/` with `link-here` → `granted`, with `granted/empty` also granted) could be deleted or moved; only the requested path was compared with the grants.
  - **Fix: each operation checks the place it really touches.**
    - Reading and writing follow every link: `RepositoryPaths.realSegments`.
    - Deleting and moving act on the entry itself, a link stays a link: the new `RepositoryPaths.realEntry`.
    - `RepositoryAccess.canChange(entry)` is the rule "the entry's folder is writable".
    - `RepositoryFiles.holds(rule, requested, real)` applies a rule to both paths; the root and grant tops are recognized on both paths too.
    - The pre-check before an upload or creating a folder (`checkWritable`) is the same as for writing, so HTTP and MCP answer alike.
  - MCP: an `if_match` that is not text gave an internal error (`ClassCastException`); now the tool error "Not a text: if_match".
  - Simplified:
    - `RepositoryWriter.delete` uses `entry()`, as `move` does;
    - `McpTools` builds the destination text with `where()`.
  - Uncovered branches, unchanged, and why:
    - MCP switch arms for results these tools cannot get (create-only, `If-Match` on delete);
    - a move across file systems;
    - disk-error `catch` blocks.


### 36. Browser UI: one frame for all pages  ☐
*Rewritten 2026-11-27, with what steps 37–77 built.* Upload (step 72), rename and delete of files (73), Edit and New file (71) are done; no UI library (D79): server pages (Thymeleaf) and small plain JavaScript modules, tested in Node.

**What there is (each view its own URL, BACKLOG "Every object has an address"):**

| URL | View | Actions |
|---|---|---|
| `/` | your repositories | search all |
| `/{repo}/…/` | folder | subfolders and files, tags; where writable: Edit, New file, Upload, Rename, Delete; search; Up; Git (root only) |
| `/{repo}/…/note.md` | Markdown page | Folder, Raw, Edit |
| `/{repo}/…/other.ext` | the file itself | inline (pictures, PDF, text) or download (D10) |
| `/ui/editor.html?file=…` | editor | Save, Reload; Page, Folder |
| `/{repo}/.search?q=…`, `/.search?q=…` | search | tags as links, pages |
| `/{repo}/.tags/` | tags in use | links to their search |
| `/{repo}/.git/` | Git | stage, unstage, commit, pull, push |
| `/{repo}/.search/index`, `/.search/index` | index state | reindex, update |
| `/.upload/{token}/` | upload link | pick files (no login) |

**What is missing for convenient work (found by going through the pages):**
- No common frame: every page has its own few links; deep in a repository there is no way back to the start or the repository root but "Up" step by step; the file pages do not show where they are; **no logout anywhere**; no "logged in as".
- Folders cannot be created, renamed or deleted in the browser (the API can: `PUT …/`, move, `DELETE …/`). Files cannot be moved to another folder, only renamed.
- The listing shows names only: no size, no date, no kind; nothing tells a picture from a text file before clicking.
- A Markdown page does not show its folder's tags; tags are edited only as the `.tags` file.
- No "copy link" for the object on screen, though its URL is the permalink.
- Upload links (step 75) can be made only by an agent or the API; from a computer to the phone would need a QR code.
- The editor saves only with the button (no Ctrl+S); errors (404, 500) are Spring's plain pages without a way back.
- The Git page is reachable only from the repository root, and nothing shows that there are uncommitted changes.

**Rules for all of it (kept from BACKLOG and the decisions so far):**
- **Every view has its own URL** and opens directly from a bookmark, a note or a chat; after a login it leads straight there (as now). Sorting, search terms, pages: in the query (`?sort=date`, `?q=`), never only in the browser.
- **Windows are independent:** no state shared between tabs (no `localStorage` for content or navigation; BACKLOG: each window works on its own object); a dialog is no view and needs no URL; after an action the page shows the new state of its URL (reload), never an old picture.
- **Nothing is overwritten unknowingly:** the editor's `If-Match` (D20), uploads and New file create-only, rename and move never overwrite (D30).
- **Works without JavaScript for reading** (links, pages); the actions need it. On the phone first: the frame and the actions fit a narrow screen, buttons large enough to tap.
- **The same API as curl and the agents:** the browser UI adds no endpoint of its own; what it does, the API can do (principle "automation is transparent").

**Later input, not part of these parts (2026-11-27):** the display concept "a repository like a ticket system" in [BACKLOG.md](BACKLOG.md) §4: a folder shown with its `README.md` as description and its subfolders as comments or attachments, with no fixed levels. To plan on its own; the frame (36a) and the fuller listing (36e) should not stand in its way.

**The parts, each one done and checked before the next (red first, as always):**
- **36a. The frame (D90):** one Thymeleaf fragment for the server pages, the same markup in the editor and upload pages:
  - a top bar: "Mizoine" (to `/`), the place as breadcrumbs (repository / folder / folder / file, each a link to its URL), a search field for this repository (all repositories on `/`), the user's name and **Log out** (a `POST /logout` form with the CSRF token);
  - Git status where the user has Git access: a link "Git" with the number of changed files (from `GitStatus`, only for the root of the working tree, D33), on every page of the repository;
  - **Copy link** for the object on screen (its URL, as is; `navigator.clipboard`, falls back to selecting the text).
  - Tests: the frame's links on each page type (start, folder, Markdown, search, tags, Git, index, error); breadcrumbs encoded as the URLs are (non-ASCII, spaces, `#`); no Git link without Git access; logout logs out and needs the token.
- **36b. Error pages (D91):** 404 and 500 in the frame, saying what is known without revealing more (D2: "not found or no access"), with links to the start and, if the repository is known and visible, its root. 403 for a missing CSRF token: "the page was open too long, reload it".
- **36c. Folders in the browser:** **New folder** (as New file: a name, `PUT …/name/`, then that folder opens), **Rename** and **Delete** for subfolders (Delete only when empty, D15, with D70's `.tags` rule; a folder with content says so). In the same `folder.js`, tested in Node; end to end as step 73.
- **36d. Move (D92):** Rename takes a path too: a plain name stays in the folder, `archive/plan.md` or `../done/` moves (step 41: `POST` with `Destination`, never overwriting). The path is shown resolved before it is sent ("Move to notes/archive/plan.md?").
- **36e. A fuller listing (D93):** size, last change and kind (folder, text, picture, PDF, other) for each entry, from the listing JSON (new fields, nothing taken away); sorting by name, date or size with `?sort=`; on the phone a compact line per entry with the actions behind one "⋯" button.
- **36f. Tags in the pages (D94):** the Markdown page shows its folder's tags (links to their search); the folder page edits its tags with a small field (add, remove, statuses as `key:value`), through `PUT ?tags` (checked, D65); the vocabulary of step 65 as suggestions where search is on.
- **36g. Upload link from the browser (D95):** "Upload from phone" on the folder page makes a link (`POST ?upload-link`) and shows it with a **QR code** to scan with the phone's camera, its expiry and Copy link.
- **36h. Small editor comforts (D96):** Ctrl+S / ⌘S saves (the same Save, `If-Match`); the frame's breadcrumbs instead of the two links; the title says when there are unsaved changes ("• plan.md").
- **36i. Look (D97):** one stylesheet for all pages; light and dark by the system setting (`prefers-color-scheme`); no web fonts, no external resources (CSP `'self'`); readable on the phone first.
- **Checked live** after each part on the phone and on a computer, as for steps 39 and 72.

### 37. Text editor in the browser (plain textarea)  ☑
*Requested 2026-11-19:* edit text files in the browser; usable on the smartphone. T3.1 decided: CodeMirror 6 later (step 39). For now a plain `<textarea>`, so that everything except highlighting is built and tested here.
- Builds the editor page described in step 39 (`/ui/editor.html?file=…`, save button, links), with a textarea; step 39 then puts CodeMirror in its place.
- **The editor only uses the public file API** (requested 2026-11-19): it reads with `GET`, creates and saves with `PUT` on the file's URL (steps 28 and 30), the same requests as curl and the MCP tools use. There is no editor-specific endpoint, and the server knows nothing about the editor: the editor is a static file under `/ui/`.
- Open from the listing: text files up to a size limit (D21) open in the editor, others download.
- Save = `PUT` with `If-Match` (D20). On 412 the editor offers to reload or to keep its text; nothing is overwritten silently.
- New file: name + empty editor, saved with `If-None-Match: *`. *Changed in step 66 (D76): the editor no longer creates files; New file is a separate action on the folder page.*
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
  - a missing file opens empty and is created with `If-None-Match: *`. *Changed in step 66 (D76): a missing file is shown as missing, without Save.*
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


### 40. Review and refactoring (ub-mode)  ☑
*Requested 2026-11-23:* review UI and backend, improve with the development skills (ub-mode). Behaviour unchanged except the two UI improvements below.
- **Measured first** ("Build the Lever"): crapper is not installed, so CRAP = CC² × (1 − coverage)³ + CC was computed from JaCoCo's report. Before: 2 of 276 methods at 10 or more, highest 12.0 (`HashPasswordCommand.fromSystem`, never run in tests), then `RepositoryWriter.delete` 10.2, `FileWriteController.put` 9.0. After: none at 10 or more; highest 8.1.
- **Subtracted dead code** (JaCoCo: never run, nothing refers to it): `UsersFileException(message, cause)`, `FolderGrant.toString`.
- **One rule in one place — `files.FileVersion`:** "the version of a file is its size and modification time" was written three times:
  - in `RepositoryRegistry` and in `FileUserDetailsService`, each with its own private record (the latter with a special `UNREADABLE` value);
  - in `FileDownload.etag` with `isSameVersion`.
  Now one record gives the version (`Optional`: empty = missing or unreadable), the ETag and the `If-Match` check. Red: `FileVersionTest`; the four users were switched with the existing tests as pin.
- **Split by job:** `RepositoryWriter.delete` → `refuseFolder` (D15) and `refuseFile` (D13); `FileWriteController.put` → `writeFile`.
- **Covered:** `HashPasswordCommand.fromSystem`, its piped mode (`echo … | java -jar … hash-password`), tested with replaced standard input and output. The terminal branch cannot be reached in a test.
- **UI on the smartphone (improvement):** none of the four server pages had `<meta name="viewport">`, so phones showed them shrunk to desktop width, and each repeated its `<head>`. Now one fragment, `templates/fragments/head.html`, gives every page the viewport and the shared stylesheet `/ui/mizoine.css` (readable width, images and tables). The editor page uses it too; `editor.css` keeps only what the editor adds. Red: `PageLayoutTest`.
- **Tests: one helper instead of seven copies.** Seven test classes each wrote their own code to create a temporary repositories folder, register it and delete it; now `TestRepositories` (builder: `file`, `folder`, `link`, `repository`, `path`, `register`, `delete`). The test count did not change, so the trees are the same. While migrating, two classes lost their `max-file-size` setting; their own tests caught it.
- **Glossary** added below (ub-mode "Model the Domain").
- **Looked at and left:**
  - `RepositoryController` (folder listing, file download, Markdown page) is the largest class at about 260 lines, but each part is one `Target` case and short ("if the code is already clear and local, leave it");
  - `editor.js` and `page.js`: decisions in the tested module, the page file only shows them.
- **Result:** 22 files changed, 185 lines added, 335 removed; 593 Java and 51 JavaScript tests green.
- *Follow-up 2026-11-24 (requested): no deprecated APIs.*
  - The compiler now reports deprecated and for-removal APIs as errors (`-Xlint:deprecation,removal -Werror` in `pom.xml`, main and test code), so none creep back in.
  - **Main code:** `McpSchema.Tool.builder()` → `Tool.builder(name, inputSchema)`.
  - **Test code:**
    - `new CallToolRequest(…)` → `CallToolRequest.builder(name).arguments(…)`;
    - `httpRequestCustomizer` → `addHttpRequestCustomizer`;
    - Spring's `isPayloadTooLarge()` → `isContentTooLarge()` (HTTP 413's new name).
  - **One MCP helper:** `McpEndpointTest` and `OAuthTest` each built their MCP client and tool calls; now one helper, `TestMcp`, also used by `McpToolsTest`.
  - **After:** 633 Java tests green, highest CRAP 8.1, no unused imports.

### 41. Move and rename files and folders  ☑
*Requested 2026-11-24:* move files or folders, for the HTTP file API and as an MCP tool. Renaming is a move within the same folder.

**Behaviour (the same for HTTP and MCP, decided once in `RepositoryFiles` and `RepositoryWriter`):**
- **Where:**
  - source and destination are paths in the **same repository** (D31);
  - both must be writable for the user, i.e. inside granted folders (possibly two different grants), never hidden, never `.git`;
  - not the repository root and not the top of a grant (as for delete, step 32).
- **Folders and files alike:** a folder moves with everything in it, including hidden content such as `.gitkeep`.
  - It must not be moved into itself (`a/` → `a/b/`): 409.
  - A symbolic link moves as a link, never its target.
- **Destination:**
  - its folder must exist (D14): 409;
  - an existing destination is **never overwritten** (D30): 412;
  - the type must match the URLs (D5): a folder URL for a folder, a file URL for a file.
- **Version:** `If-Match` on a source file (D13): moved only if unchanged, else 412.
- **Atomic:** one `Files.move` with `ATOMIC_MOVE` under the writer's lock. Readers and Git see the old or the new place, never both or neither. Where the file system cannot move atomically (a mount point inside the repository): 409, nothing moved.
- **Git:** shows the move as delete + add; `git status` and `git diff` recognize it as a rename.
- **Not changed:** links in other Markdown files that point to the old place (D32).

**HTTP (D29, proposed):** `POST /{repository}/{path}` with header `Destination: /{repository}/{new path}`, the semantics of WebDAV `MOVE`.
- Answers:
  - 201 with `Location` of the new place;
  - 404 not visible (D2);
  - 409 destination folder missing, into itself, root or grant top, not atomic;
  - 412 destination exists, or `If-Match` not met.
- CSRF: cross-site forms cannot send a `Destination` header, so `POST` with it is exempt like the agents' `PUT` (D11). A form `POST` without it stays CSRF-protected and gets 400.

**MCP:** new tool `move(repository, from, to, if_match?)`:
- the same rules;
- answers that say what to do ("The destination exists. Choose another name or delete it first.");
- the description reminds of review with `git diff`.

**Red:**
1. `RepositoryWriterTest`, plain logic on a temporary folder:
   - file and folder moved, contents and hidden content along;
   - rename in place;
   - destination exists → nothing changes;
   - destination folder missing;
   - into itself;
   - a link moves as a link;
   - an old `If-Match` → nothing moved;
   - nothing left behind after a refused move.
2. `RepositoryFiles` grants (through `McpToolsTest` and the HTTP test):
   - from a grant into another grant: allowed;
   - out of the grants, from above a grant, from or to hidden names: "not found";
   - root and grant top: refused.
3. HTTP (`FileMoveTest`):
   - `POST` + `Destination` → 201 + `Location`;
   - all refusals with their status;
   - a `Destination` in another repository or on another host → 400;
   - `POST` without `Destination` → 400 (with session: CSRF 403 first);
   - the moved file downloads at the new URL, 404 at the old.
4. MCP:
   - `McpToolsTest`: every answer of `move`;
   - `McpEndpointTest`: the agent moves a note from `inbox` to `knowledge`.
5. OpenAPI smoke test: `post /{repository}/{path}` has a summary.
- *Done 2026-11-24 (ub-mode), after step 42, with findings:*
  - **Writer:** `RepositoryWriter.move` with its own result type (`Moved`, `DestinationExists`, `DestinationParentMissing`, `IntoItself`, `PreconditionFailed`, `NotAtomic`, `NotFound`, `IsGrantedFolder`).
    - One `Files.move(…, ATOMIC_MOVE)` under the writer's lock; a link moves as a link.
    - "Is the file still at the client's version" is one helper now, for delete and move.
  - **Grants:** `RepositoryFiles.move` checks the user's rights; HTTP and MCP use it alike.
    - Source and destination must both be writable (also two different grants).
    - Root and grant tops are refused.
  - **HTTP:** `POST` + `Destination` (D29). `RepositoryUrls.destination` takes only an encoded path on this server, in the same repository, of the source's kind; else 400.
    - CSRF: a `POST` with `Destination` is exempt, because a form cannot send that header.
    - Without `Destination`, a `POST` stays CSRF-protected also with HTTP Basic (403 before 400): browsers send a cached Basic login with cross-site forms. The first version of the test expected 400 there; the test was wrong, not the rule.
  - **MCP:** tool `move(repository, from, to, if_match?)` with answers that say what to do.
  - **CRAP:** `RepositoryWriter.move` was at 10.0 (complexity 10, fully covered); split (`entry`, `isInside`). None at 10 or more.
  - **Untested:**
    - `NotAtomic` needs a second file system inside the repository;
    - `FileAlreadyExistsException` at the move, and a file vanishing in `readText`, are races.
  - **Tests:** `RepositoryWriterTest` (+9), `McpToolsTest` (+3), `McpEndpointTest` (the agent files a note from its inbox), `FileMoveTest` (17), OpenAPI smoke test for `post`.
  - **Live** (jar):
    - HTTP move to an encoded name → 201 with `Location`; the same again → 404;
    - MCP `read_file` gives `{text, version}`; `move` moves;
    - no warnings in the log.


### 42. Versions for agents  ☑
*Found while planning step 41:* `write_file` returns the file's version, but `read_file` does not. An agent that reads, changes and writes back cannot pass `if_match`, so it may silently overwrite a change made in between (by the user, the editor or another agent). The same holds for `move`.
- `read_file` also returns the version, as `structuredContent` `{ "text": …, "version": … }` next to the plain text content, so older clients still get the text.
- `list_folder` stays as it is (no versions: reading every file would be needed).
- The `write_file` description: "pass the version from read_file as if_match".
- **Red:** `McpToolsTest` + `McpEndpointTest`: `read_file` gives the version; `write_file` with it writes; after a change in between, the same version → refused.
- *Done 2026-11-24 (ub-mode), before step 41 (it makes `move` safe too):*
  - `RepositoryFiles.readText` returns `FileText(content, version)`. `read_file` gives the text as plain content (every client) and `{text, version}` as structured content.
  - **The version is read before the content:** if the file changes in between, the agent holds new content with an old version, and its write back fails safely (412). The other order could pair old content with a new version and overwrite the change.
  - Tool descriptions: `read_file` names the version; `write_file` says "always pass it when changing a file you read".
  - **Tests** (Red first):
    - `McpToolsTest`: text and version; the version protects the write back, and after a change in between it is refused; the description;
    - `McpEndpointTest`: the structured content over the real protocol.


## Git in the browser (planned 2026-11-24)
*Requested 2026-11-24:* `/{repository}/.git/` is a reserved address for Git in the browser, not the real `.git` folder.
- The page shows the Git status of the repository, with stage / unstage (all or single files), a commit message, commit, pull and push.
- The same functions are an HTTP API. MCP is not needed (T4.4).
- Git access is granted like a subfolder in the repository file: `/` includes it; `.git` grants only it.

**Principles kept (BACKLOG):**
- Plain files and Git stay the truth. Mizoine runs the same `git` commands a human would (T4: "the same thing can be done by hand"), and adds no state of its own.
- An independent layer: without a Git grant, or without `git` installed, nothing else changes.
- Agents write, humans commit: the MCP tools stay without commit and push (BACKLOG §4).

**HTTP (D35):**

| Request | What |
|---|---|
| `GET /{repository}/.git/` | The status: HTML page (D6 default) or JSON |
| `POST /{repository}/.git/stage` | `paths` (one or more), or `all` |
| `POST /{repository}/.git/unstage` | the same |
| `POST /{repository}/.git/commit` | `message` |
| `POST /{repository}/.git/pull` | fast-forward only (D37) |
| `POST /{repository}/.git/push` | the current branch to its upstream, never forced (D37) |

- **Answers:**
  - 404 without Git access, the same as for anything else not visible (D2);
  - 409 when Git refuses, with Git's message (nothing to commit, not fast-forward, push rejected, no upstream);
  - 504 when the remote does not answer in time.
- **Browser:**
  - plain forms with the CSRF token;
  - after an action, 303 back to the page, which shows the result (Post/Redirect/Get).
- **API clients:** JSON bodies, answered with the new status. JSON POSTs need no CSRF token, as for `/mcp`: cross-site forms cannot send JSON.
- **Every other path under `.git`** (`/{repository}/.git/config`, …) is 404 for everyone, also with grant `/`. The real `.git` folder is never read or written as files (D3 stays).

### 43. Git access in the repository file  ☑
- A grant `.git` (only at the top, not `public/.git`) gives Git access to the repository. `/` includes it.
  - Every other hidden name in `access` is still rejected (D3).
  - Example: `access: {artem: ["/"], editor: [notes, .git]}`.
- `RepositoryAccess.canUseGit()`.
- Git access covers the whole repository (D33). The status names every changed file, and a commit takes everything staged, also outside the user's file grants. A `.git` grant is therefore given only to someone who may see the whole repository's changes.
- Routing: `/{repository}/.git/…` is matched before the file URLs and never reaches `RepositoryPaths`.
  - Without Git access: 404.
  - With access: the page (step 45).
- The repository's folder page links to `.git/` for users with Git access.
- Tests:
  - parsing the grant (`.git` accepted; `a/.git`, `.gitx`, `.github` as hidden names rejected);
  - `canUseGit`;
  - 404 for guest;
  - `/{repository}/.git/config` is 404 also for artem;
  - `UnsafeUrlTest` still green.

- *Done 2026-11-24:*
  - **Data shape:** the `.git` grant is not a folder. `RepositoryConfig.gitUsers` holds the users with an explicit `.git`; `FolderGrant.isGit` recognizes it once, in the parser (`.git`, `/.git`, `.git/`). The folder grants never contain it, so no folder rule can mistake it for a folder. `a/.git`, `.git/objects`, `.gitx` and `.github` stay rejected (D3).
  - **Access:**
    - `RepositoryAccess.canUseGit()` = `.git` or `/`.
    - A user with `.git` alone has access to the repository (start page), and may enter the root, which shows nothing: the way to Git, as above a grant (D4).
  - **Address:** `RepositoryFiles.gitRoot(user, repository)` decides, for HTTP and later for anything else. The Git page lives in its own package `git`: `GitController` serves `GET /{repository}/.git/` (HTML, for now only the heading and a link back; the status comes in step 45) and `GET /{repository}/.git` (redirect). Everything else under `.git/` still reaches the file URLs, where `.git` is hidden: 404 for everyone, also for `/`.
  - The repository's root page links to `.git/` for users with Git access.
  - Tests:
    - `RepositoryFileParserTest`, `RepositoryAccessTest`;
    - `GitPageTest`: page, redirect, 404 without Git access; the `.git` folder neither read nor written by GET, PUT or DELETE, also with `/`; the root link; Git access alone;
    - the OpenAPI smoke test;
    - `UnsafeUrlTest` unchanged and green.
  - README: the grant in the repository file section.

### 44. Running Git and reading its status  ☑
- The data shape first (D36):
  - `GitStatus(branch, upstream, ahead, behind, List<Change>)`;
  - `Change(path, renamedFrom, staged, unstaged)`, with an enum `Kind` (modified, added, deleted, renamed, untracked, conflict).
- Read from `git status --porcelain=v2 --branch -z --untracked-files=all`: the stable machine format; `-z` keeps any file name intact (spaces, UTF-8, newlines).
- `GitCommands` runs the `git` program (T4):
  - always `git -C <root> …`, with a fixed list of arguments and no shell;
  - paths after `--`, with `--literal-pathspecs`;
  - not interactive: `GIT_TERMINAL_PROMPT=0`, and SSH with `BatchMode=yes`;
  - a time limit (`mizoine.git.timeout`, default 60 s), and Git's output kept to a limit.
- Git runs only where the repository root is the top of a working tree (`git rev-parse --show-toplevel` = the real root). Otherwise the page says "not a Git repository", and actions give 409 (D34).
- One at a time with Mizoine's own writes: Git commands run under `RepositoryWriter`'s lock. A pull changes files, and a commit reads the working tree. Changes outside Mizoine are handled by Git's own `index.lock`.
- Tests:
  - the parser, plain, on recorded `-z` output, including renames, conflicts, names with spaces and UTF-8, a detached HEAD and no upstream;
  - `GitCommands` on `@TempDir` repositories, with the test's Git cut off from the machine's settings (`GIT_CONFIG_GLOBAL`, `GIT_CONFIG_NOSYSTEM`);
  - a time limit that ends a hanging command.

- *Done 2026-11-24:*
  - **Data shape:** `GitStatus(branch, hasCommits, upstream, changes)`, `Upstream(name, gone, ahead, behind)`, `Change(path, renamedFrom, staged, unstaged)`, `Kind`.
    - `hasCommits` is needed in step 46: before the first commit, unstaging is `git rm --cached`.
    - `gone`: the upstream is set, but its branch no longer exists.
  - `GitStatusParser`, pure, reads porcelain v2 with `-z` (D36). A record it does not know is an error, not skipped.
  - `GitCommands`, the only place that starts `git`:
    - `git --literal-pathspecs -C <root> …`, arguments as a list, no shell;
    - no input;
    - the environment without the variables of `git rev-parse --local-env-vars` (`GIT_DIR`, `GIT_WORK_TREE`, …, which would point Git to another repository);
    - `GIT_TERMINAL_PROMPT=0`;
    - `GIT_SSH_COMMAND=ssh -o BatchMode=yes`, unless the server sets `GIT_SSH_COMMAND` or `GIT_SSH`. That also overrides a `core.sshCommand` setting; such a server sets `GIT_SSH_COMMAND` in its start script (README in step 48).
    - Each output stream is read in its own virtual thread, so Git cannot block on a full pipe.
    - Past the time or output limit, Git and everything it started (shell, SSH) are ended: `GitTimeoutException`, or `GitException`.
  - `status(root)`:
    - empty unless the root is the top of a working tree (`rev-parse --show-toplevel` = the real root, D34);
    - leaves out Mizoine's temporary upload files (`RepositoryWriter.TEMPORARY_PREFIX`, now public).
  - **Changed from the plan:** no lock with `RepositoryWriter` for the status. An upload copies into its temporary file *outside* the writer's lock, so a lock would not keep those files out of the status; filtering does. Reading the status changes no file. Whether pull (step 48) shares the writer's lock is decided there.
  - **Moved to step 45:** the setting `mizoine.git.timeout` and the Spring bean. `GitCommands` takes the time limit, output limit and environment in its constructor; nothing uses it before the page does.
  - Tests:
    - `GitStatusParserTest` (pure, on recorded output): spaces, UTF-8, a newline in a name, rename, copy, type change, conflict, ahead / behind, gone upstream, detached HEAD, no commits, unknown records;
    - `GitCommandsTest`, on real `@TempDir` repositories with Git cut off from this machine's settings (`TestGit`: own home, `GIT_CONFIG_NOSYSTEM`): not a repository, a subfolder, no commits, staged and unstaged changes, temporary files left out, a conflict after a merge, ahead and behind a bare remote, a detached HEAD, the time limit (an alias running `sleep 30` ends after 0.5 s), the output limit, Git not installed, literal paths, the environment.
    - Proven by removing `--literal-pathspecs` and the temporary-file filter: one test failed each time.
  - Untested branches, and why:
    - in `GitCommands.run`, a failing read of Git's output and an interrupted thread;
    - `status` when `rev-parse` succeeds but `status` fails (e.g. a damaged index);
    - `isTopOfWorkingTree` when the root vanishes between the two calls.
  - CRAP ≤ 9 (`GitStatusParser.parse`).
- *2026-11-25, review and refactoring of steps 43–44 (behaviour pinned by the tests, all green before and after):*
  - `GitCommands`:
    - the stream reader with its mutable thread field and `volatile` flags is replaced by a `FutureTask` per stream (`readNBytes(limit + 1)`, run in a virtual thread);
    - one deadline covers both streams and the exit;
    - starting the process is a method of its own;
    - `run`: CRAP 6.2 → 3.1.
  - `GitStatusParser`: an iterator over the records instead of index arithmetic, so a rename takes its old path as "the next record"; one `switch` per record type. `parse` CRAP 9 → < 5. Pinned additionally: a rename without its old path is an error.
  - `FolderGrant`: `of` and `isGit` split a path in one place.
  - `RepositoryFileParser`: returns its result as records (`Access`, `UserAccess`) instead of filling a `Set` passed in.
- *2026-11-25, the ub-mode tools installed and run for the first time (crapper, dryer, mutator; CLAUDE.md says how), on the files of steps 43–44:*
  - **crapper:** worst `GitStatusParser.kind` 9.0. It is a one-to-one `switch` over Git's letters, kept as it is. Then `GitCommands.run` 7.7. crapper also counts `catch` and `switch` labels, so it is higher than the JaCoCo estimate used before.
  - **dryer:** no candidates at 0.8.
  - **mutator**, per group of files with their test classes:

    | Files | Result |
    |---|---|
    | `git` package | 50 killed, 0 survived |
    | `FolderGrant`, `RepositoryFileParser` | 61 killed, 0 survived |
    | `RepositoryAccess`, `RepositoryFiles` | 42 + 34 killed (second run: only `RepositoryAccess` again), 1 survived (equivalent) |

    - Survivors, and what was done:
      - `GitStatusParser`: `substring(1)` before `parseInt` of "+2" was dead code (`parseInt` takes the '+'). Removed.
      - `GitCommands.remaining`: `Math.max(0, …)` was not needed (`Future.get` and `waitFor` treat a negative wait as none). Removed.
      - `GitCommands` output limit: exactly the limit was untested. New test `outputUpToTheLimitIsKept`.
      - `FolderGrant.of`: a backslash or NUL *at the start* of a folder was untested. New cases in the parser test.
      - **`RepositoryFileParser`: `git = false` → `true` survived.** Every user would have got Git access unnoticed: the Git test listed only users with `.git`. Now the test has a user without it, and the plain test checks `gitUsers` is empty.
      - `RepositoryAccess.canReadFile`: a *file* whose path equals a grant (a file `public` next to the granted folder `public`) was untested. New test `aFileWhosePathIsAGrantIsNotReadable`.
      - Left: `visibleChildren` `>` → `>=` (code from step 16) is an equivalent mutant. With equal sizes, a grant starting with the folder *is* the folder, handled by the branch above.

### 45. The Git page  ☑
- `mizoine.git.timeout` (default 60 s) and the `GitCommands` bean, with the server's environment (moved from step 44).
- `GET /{repository}/.git/`:
  - the branch, ahead / behind its upstream;
  - two lists, staged and not staged, each file with its kind;
- JSON with `Accept: application/json` (D6).
- A server page like the folder page (Thymeleaf, D35): it works without JavaScript and on the phone, under the strict CSP.
- File names link to the file's page where the user may read it.
- OpenAPI: `@Tag("Git")`, every status.
- Tests: page and JSON for a temporary repository with changes; "not a Git repository".

- *Done 2026-11-25:*
  - `mizoine.git.timeout` (default 60 s). `GitConfig` makes the `GitCommands` bean: `git` from the `PATH`, the server's environment, 16 MB output limit.
  - **Domain:** `GitStatus.staged()`, `unstaged()`, `conflicts()`, `isClean()`. A file changed again after staging is in both of the first two lists; a conflict is only in its own list.
  - **View:** `GitStatusView` (pure) gives the page's text.
    - the branch, or "detached HEAD";
    - the upstream: "origin/main: 2 ahead, 3 behind", "… up to date", "… gone", "no upstream";
    - lines of kind, path, old name and link.
    - The old name belongs only to the side that is the rename or copy: changed again after staging, the not-staged line has none.
  - **Links:** `RepositoryFiles.fileLinks(user, repository)` gives the file's URL by the same rule as reading (`readable`). None for a user with `.git` alone, for hidden names, for deleted files.
  - **Controller:** `GET /{repository}/.git/` as HTML (`git.html`, server page, no JavaScript) or JSON (`GitStatus`). A folder that is not the top of a working tree: the page says so, JSON gives 409 (D34).
    - Git failing (not installed, time limit) gives the default error page, 500, no details. Steps 46–48 add Git's messages for the actions.
  - **Moved to steps 46–48:** the forms for the actions, each with its own step.
  - README: "Git in the browser", with the "dubious ownership" case (`safe.directory`).
  - Tests:
    - `GitStatusTest`, `GitStatusViewTest` (pure);
    - `GitStatusPageTest`: real repositories with staged and not staged changes, rename, deletion, new file, UTF-8 and spaces; links for artem, none for Git access alone; JSON; clean; not a Git repository;
    - `MizoinePropertiesTest`.
  - Tools:
    - crapper: nothing new above 7;
    - dryer: no candidates;
    - mutator: 33 killed, 1 survived. The survivor: `ahead == 0 && behind == 0` → `||` wrote "up to date" for "0 ahead, 1 behind". Killed with the one-sided cases.
    - "Uncovered" only the text concatenations in the OpenAPI annotations, which are not executed.
  - Untested: the 500 when Git fails on the page (Spring's default error handling; Git missing is tested in `GitCommandsTest`).

- *2026-11-25, OpenAPI and README reviews:*
  - OpenAPI: the descriptions say what an endpoint does, with no dev steps, decision ids, setting or framework names. `OpenApiDocumentationTest.describesTheApiNotItsDevelopment` checks it.
    - Operation ids `gitStatus`, `gitRedirect`, `repositoryRedirect`.
    - Field descriptions on all JSON schemas.
    - The browser's CSRF header corrected to `X-XSRF-TOKEN`.
  - README:
    - the settings table complete (`public-url`, `oauth.clients`, `git.timeout`);
    - the reserved names complete;
    - Git needed for the build;
    - the intro describes today's Mizoine.
  - **Found by the README check:** `oauth2` was not a reserved repository name. With OAuth on, such a repository would have shared its URLs with `/oauth2/authorize` and `/oauth2/token`. Now reserved. The parser's message lists all reserved names from the one set (`RepositoryNames.reserved()`) instead of a hand-written part of them.

### 46. Stage and unstage  ☑
- Stage: `git add -- <paths>`, or `git add --all`. Unstage: `git restore --staged -- <paths>`, or everything. Before the first commit, `git rm --cached`.
- Paths must be in the current status. Others give 409, so the request cannot name any path in the working tree.
- Tests:
  - single files, all files;
  - names with spaces and UTF-8;
  - a path not in the status;
  - a path with `..`;
  - CSRF (a form without token: 403; JSON without token: allowed).

- *Done 2026-11-25:*
  - **Domain:** `GitActions`, one place for every action. Each starts from the current status, and a repository that is no working tree (D34) is refused before Git runs. The outcome is `GitOutcome.Done(message)` or `Refused(message)`, the latter often in Git's own words.
  - **Input checked where it comes in:** `Selection` is all, or a non-empty list of paths, never blank. JSON `{"all": true}` and the page's form both become one.
  - **Stage:** `git add -- <paths>`.
    - Only paths of the not-staged list; anything else is refused naming the path (`../outside.md`, `.git/config`, `*.md`, unchanged files).
    - "All" is that list, not `git add --all`, so Mizoine's temporary upload files (left out of the status) are never staged.
    - Conflicts are in neither list, so they are not staged (D37).
  - **Unstage:** `git restore --staged -- <paths>`; before the first commit `git rm --cached`. A rename is unstaged with its old name.
  - **HTTP:**
    - `POST /{repository}/.git/stage` and `/unstage` with JSON: 200 with the new status, 400 for a selection of nothing, 409 `{"message": …}`, 404 without Git access.
    - The page's forms (checkbox per file, "… selected", "… all") post to the same URLs as forms. A hidden handler answers 303 back to the page, which shows the result (flash attribute).
  - **CSRF:** the rule for `/mcp` became "a JSON POST" (`SecurityConfig.isJsonPost`): a cross-site form cannot send JSON, wherever it posts. Form posts keep the token, also with HTTP Basic (D11).
  - **Found while testing:**
    - (a) With `produces = JSON`, a POST without `Accept` was routed to the file move on the same pattern (400). The mappings have no `produces`.
    - (b) Without `Accept`, the pages' default type (HTML, D6) gave 406 for the JSON answer. The answers set `Content-Type: application/json` themselves.
    - (c) Jackson 3 refuses a missing primitive `boolean`. The controller reads `SelectionRequest(paths, Boolean all)` and turns it into a `Selection` once.
  - Tests: `GitActionsTest` (real repositories) and `GitActionsPageTest` (JSON, forms with token and cookie as a browser sends them, 403 without token).

### 47. Commit  ☑
- `git commit -m <message>`; the message is not empty (else 400).
- Nothing staged: 409.
- Author: the Mizoine user (D38). Committer: the server's Git settings.
- Hooks in the repository run as with a commit by hand. They are the owner's: users cannot write into `.git`.
- Tests:
  - the commit is in `git log` with the user as author;
  - empty message;
  - nothing staged;
  - a failing `pre-commit` hook gives 409 with its message.

- *Done 2026-11-25:*
  - `git commit -q -m <message> --author=<user>`. `CommitMessage` is never blank. Nothing staged is refused before Git runs. Hooks run; a refusing hook gives its own message.
  - **Author (D38):** `Author.of(username, email)`; `<`, `>` and line breaks are removed, as they would end the name or the email in Git's form.
    - `users.yaml` has an optional `email` per user (`UserEntry.email`), parsed and checked to be text.
    - `UserEmails` (since the review: `KnownUsers`; implemented by `FileUserDetailsService`, reloaded like the users) is all the Git page knows of the users.
    - The committer is the server's own Git identity.
  - `POST /{repository}/.git/commit` with `{"message": …}`; the page shows a message field and "Commit the staged changes" when something is staged.
  - Tests:
    - the commit in `git log` with author `artem <artem@example.org>` and the server as committer;
    - nothing staged;
    - a `pre-commit` hook that refuses;
    - an empty message (400);
    - the form.

### 48. Pull and push  ☑
- Pull: `git pull --ff-only`. Diverged or conflicting: 409 with Git's message; resolved by hand on the server (D37).
- Push: `git push` of the current branch to its upstream, never `--force`. Without upstream: 409.
- Access to the remote: the server user's SSH key or credential helper. Mizoine stores no Git passwords.
- Tests:
  - with a local bare repository as remote: pull fast-forward, pull diverged, push, push rejected, no upstream;
  - a remote that does not answer gives 504 (time limit).
- README section "Git in the browser":
  - the grants;
  - what each button runs;
  - credentials on the server;
  - what stays by hand: merge, conflicts, branches.

- *Done 2026-11-25:*
  - **Pull:** `git pull --ff-only --no-rebase -q`. Never a merge or a rebase, also where the server sets `pull.rebase`. A diverged branch is refused with Git's message.
  - **Push:** `git -c push.default=upstream push -q`: the current branch to its upstream only, never forced. Rejected when behind.
  - Without upstream, both are refused before Git runs.
  - **One at a time with Mizoine's writes (decided here, open since step 44):** a pull runs under the writer's lock (`RepositoryWriter.exclusively`). An upload keeps reading its body meanwhile; only its final replace waits. Push and staging change no file of the working tree and run without the lock.
    - Tested with a "remote" whose upload-pack writes a marker and sleeps: a write started during the pull waits (thread state WAITING) and completes after it.
    - Proven by removing the lock: the test failed.
  - **Time limit:** a remote that does not answer (upload-pack `sleep 30; true`; Git appends the remote's path to the command) ends after the limit. JSON gives 504 `{"message": "Git did not finish within …"}`, the page shows it.
  - `POST /{repository}/.git/pull` and `/push` (JSON content type, body may be empty); Pull and Push buttons on the page.
  - README "Git in the browser": what each button runs, author and committer, credentials (the server user's SSH key or credential helper, SSH in batch mode), what stays by hand, the JSON API with curl. `users.example.yaml` mentions `email`.
- *Tools for steps 46–48:*
  - crapper: `UsersFileParser.toUser` rose to 11.1 with the email check. The check is its own method now: 8.1.
  - dryer:
    - the two exception handlers were one shape: now one handler, its status taken from the exception;
    - the five JSON endpoints share `act(…)`.
    - Left: `stage` and `unstage` at 0.81. Their signature and the annotation structure OpenAPI needs per endpoint are alike by design; merging them into one `{action}` endpoint would make the API description worse.
  - mutator, per group of files with their test classes:

    | Files | Result |
    |---|---|
    | `GitActions`, `Selection`, `CommitMessage`, `Author` | 25 killed |
    | `GitController`, `GitConfig` | 4 killed; "uncovered" only the annotation texts |
    | `SecurityConfig` | 16 killed, with the CSRF tests (`ApiClientCsrfTest`, `FileWriteTest`, …) |
    | `UsersFileParser`, `FileUserDetailsService`, `RepositoryWriter` (first run on the whole files) | survivors in code of earlier steps, below |

    - Killed with new tests (gaps from earlier steps):
      - a replaced file keeps its permissions (`RepositoryWriter.keepPermissions`, step 30);
      - the users are counted from 1 in messages ("User #2").
    - Left, equivalent, and why:
      - `UsersFileParser` `||` → `&&` on the root check: a scalar root is refused by the next check anyway;
      - `RepositoryWriter.isInside` `>` → `>=`: a folder moved onto itself is refused as "destination exists" anyway;
      - `isVersion` `orElse(false)` → `true`: only for a file that vanishes between two checks, a race;
      - `copy` `read >= 0` → `> 0` and `0` → `1`: `read` returns 0 only for an empty buffer.
- *Review of the Git feature (steps 43–48), with the tools and for security, 2026-11-25:*
  - **Fixed: the JSON exemption from CSRF was wider than JSON.** `isCompatibleWith` also let `*/*` and `application/*` through, and a malformed `Content-Type` threw out of the security filter. Now only `application/json` itself (with parameters such as `charset`) is exempt; anything else, or a type that cannot be read, needs the token (`ApiClientCsrfTest`). Browsers could not send these types cross-site anyway, so it was no open hole: the rule now says exactly what its reason covers.
  - **dryer over all production code (not only `--changed`)** found `exists(UserDetailsService, …)` twice, in `RepositoriesConfig` and `OAuthConfig`. The question "does the users file name this user" is now asked once, `KnownUsers.exists` (the interface was `UserEmails`; `FileUserDetailsService` implements it). Left by design: `pull`/`push` at 0.87 and `stage`/`unstage` at 0.81, the annotations of separate endpoints around one-line bodies.
  - crapper: all methods of the feature below 10 (the highest: `GitStatusParser.kind`, a switch, 9.0). mutator on `SecurityConfig` and `FileUserDetailsService`: 24 killed, none left.
  - Checked, nothing to change:
    - Git gets only paths from its own status, after `--`, with `--literal-pathspecs`; no shell. The message is one argument after `-m`; `--author` is always `Name <email>` with `<`, `>` and line breaks removed.
    - Hooks, `.git/config`, `.gitattributes`, `.gitmodules` and nested `.git` folders cannot be written through Mizoine: hidden names are refused for every write (D3), also behind links. Git config and hooks stay the server administrator's.
    - Git's environment has no variable that points it at another repository; the root must be the top of the working tree.
    - No CORS anywhere, which the JSON and `Destination` exemptions rely on; cookies are `SameSite=Lax`.
    - The page shows Git's messages and paths escaped (`th:text`); redirects use the repository name only after the access check.
    - Git access reveals every changed path and Git's messages for the whole repository: as decided (D33).

## Stable URLs (planned 2026-11-25)
**Postponed 2026-11-25:** steps 49–54 and D39–D46 wait; to be taken up later. The plan below stays as a draft.
*Requested 2026-11-25* (BACKLOG §3, "Every object has an address"): a URL saved elsewhere must lead to the object, like a permalink, also after the object was moved or renamed.

**Today:** the URL is the object's path. It stays valid until the file or folder is moved or renamed, by Mizoine or by hand. Already true and kept:
- every file, folder and view has its own URL;
- after a login the browser lands on the URL asked for;
- several windows work independently; a save over someone else's change is refused (D13, D20).

**The idea: the ID is part of the name** (D39). Names may start with a short ID, as in the old Mizoine: `ab3k9 Meeting notes.md`, `ab3k9-budget/`. The ID moves with the object, also when it is moved by hand or in another tool. A permalink names only the ID; Mizoine finds the object by it.

```
/notes/.id/ab3k9          → 302 → /notes/projects/2026/ab3k9 Meeting notes.md
/notes/.id/ab3k9?raw      → 302 → /notes/projects/2026/ab3k9 Meeting notes.md?raw
/notes/.id/ab3k9#costs    → the browser keeps #costs after the redirect
```

**Principles kept (BACKLOG):**
- Plain files stay the truth: the ID is visible in the name, readable and typeable by hand. No index file, no database (D45).
- Changes without the tool: moving a file by hand keeps its permalink, as long as the ID stays in the name.
- An independent layer: names without an ID work as before; nothing has to be renamed.
- Access as everywhere: an ID outside the user's grants is "not found" (D2).

### 49. IDs in names  ☐
- A value type `ShortId`: 5 characters `0-9a-z`, minutes since 2017-01-01 00:00 UTC in base 36, sortable as text (D40, the old Mizoine's `ShortIdGenerator`, compatible with its data).
- `ShortId.of(name)`: the ID of a file or folder name, if it starts with one followed by the end of the name, `.`, a space, `-` or `_` (D41). `ab3k9 Notes.md` and `ab3k9.md` have one; `ab3k9x.md` and `Notes ab3k9.md` have none.
- `ShortId.next(time, used)`: the ID of this minute, else the next free one.
- Plain unit tests: format and parse, sort order, the name rule, the edge of the range, clashes.

### 50. Permalink: `/{repository}/.id/{id}`  ☐
- `GET` (and `HEAD`) finds the files and folders in the repository whose name has this ID, among those the user may read: granted, not hidden, symbolic links by the same real-path rules as reading (D42).
  - one → 302 to its current URL (folders with `/`), the query string kept (D44);
  - none, or only outside the user's grants → 404, the same as anything not visible (D2);
  - several → 300 with the list of their URLs, HTML or JSON (D43).
- `.id` is a hidden name, so no real file or folder can take this address (as `.git`, D3). Every other path under `.id` is 404.
- Tests:
  - found at the top, deep in folders, a folder with an ID;
  - after a move by Mizoine and after a move by hand (`Files.move` in the test): the same permalink;
  - outside the grants, in a hidden folder, behind a link out of the grants: 404;
  - query kept; not logged in → login → the object.
- OpenAPI: `@Operation` with summary, 302, 300, 401, 404, 500.
- README: a section "Links that last".

### 51. Showing the permalink  ☐
- The document page and the folder page show "Permalink" for a name with an ID, linking to `/{repository}/.id/{id}`.
- The folder listing in JSON gives each entry with an ID its permalink.
- Nothing is shown for names without an ID.

### 52. The editor with a permalink  ☐
- `/ui/editor.html?file=/notes/.id/ab3k9` loads through the redirect. It saves to the path the redirect led to; the address bar keeps the permalink, so a bookmark of the editor lasts too.
- If the file was moved while open: Save says so (412 or 404 at the old path); Reload finds it again by its ID.
- JavaScript tests (`node --test`) for the URL handling.

### 53. New IDs  ☐
- `GET /{repository}/.id/` (JSON or text): a new ID, the current minute or the next one not used in the repository. Not reserved: the name made with it is the reservation (D45).
- MCP tool `new_id`, so that agents name new notes with IDs. Its description says how a name with an ID looks.
- README: how to name a file with an ID by hand: take a new ID from Mizoine, put it at the start of the name.

### 54. Old paths after a move (Git history)  ☐
- For a path that is not found, in a repository with Git (D34): look in the committed history for a rename of that path (`git log -M --diff-filter=R --name-status`), follow the chain to today's path. If it exists and the user may read it: 302 there; else 404 as before (D46).
- So even links to names without an ID survive a move, once the move is committed.
- Only renames Git detects (similar content); uncommitted moves are not known yet.
- Tests on real repositories (as `GitCommandsTest`): one rename, a chain of two, a rename into a folder without grant (404), a deleted file (404).

## Search (planned 2026-11-25)
*Requested 2026-11-25:* search in the documents, for the browser and for agents (MCP).
- As simple as possible.
- Language-aware: synonyms (*postponed 2026-11-25, D52: planned later*); spellings that mean the same letter, e.g. German `Ä`, `A`, `AE`.
- Ranked: the most similar results first.
- *Added 2026-11-25:* email addresses and file names with their extension are found as written, and by their parts (D63).
- *Added 2026-11-25:* only text and names are indexed. PDFs, pictures and emails get a Markdown file beside them from the import layer (BACKLOG §4), which is then indexed as text (D50).
- Looser (fuzzy) when the strict search finds nothing.
- Indexing may take time; searching must be quick.
- Secure: only documents the searching user may read, also over MCP.
- ~~A permission like Git access, by its technical path: `.search`.~~ *Changed 2026-11-25 (D47):* no permission of its own. Whoever may read a file may find it.

**Engine (T5, decided 2026-11-25):** Apache Lucene, embedded in Mizoine (10.5.1, 2026-08), not a search server next to it: the installation stays one jar. It covers:
- German normalization: `ä`/`ae`/`a` → `a`, `ß` → `ss` (`GermanNormalizationFilter`);
- accents folded (`é` → `e`);
- stemming for English, German, French and Russian (others optional);
- synonyms, for later (D52);
- BM25 ranking, fuzzy queries, highlighted snippets.

**Principles kept (BACKLOG):**
- Plain files stay the truth. The index is derived only: it lives outside the repositories, can be deleted at any time, and is rebuilt from the files.
- Changes without the tool are seen: the index state shows the dates of the newest file and the last commit beside the index's own, and the user decides when to update the index (D49).
- An independent layer: with search switched off, nothing else changes.
- Import of PDF, email and the like stays a separate layer (BACKLOG §4): its Markdown output is indexed like any file.

**HTTP and MCP:**

| Request | What |
|---|---|
| `GET /{repository}/.search?q=…` | Results in one repository: HTML page (D6 default) or JSON |
| `GET /.search?q=…` | Results in every repository the user may read in (search box on the start page) |
| MCP tool `search` | `query`, optional `repository` and `limit`; the agent's own grants |
| `GET /{repository}/.search/index` | The index's state: `not-indexed`, `needs-reindexing`, `reindexing`, `indexed`, `failed`, with its dates (step 60) |
| `GET /.search/index` | The states of all repositories the user may search |
| `POST /{repository}/.search/reindex` | Build the repository's index anew from its files, in the background (step 60) |

- A result: repository, path and URL (later also the permalink, once stable URLs exist: steps 49–54, postponed), title, a snippet with the found words marked, and whether it was found strictly or by similar spelling.
- Every user who may read something in a repository may search it; the results are only what that user may read (D47). A repository the user cannot see is 404, as everywhere (D2). Every other path under `.search` is 404.
- `.search` is a technical address only, never a grant: like `.id`, a dot name cannot be a real file or folder (D3).

### 55. Text analysis  ☑
- Lucene `core`, `analysis-common`, `queryparser`, `highlighter` (10.5.x).
- One analyzer per field (D51):
  - `exact`: words, lower case, and every spelling of a letter folded to one:
    - German: `ä`/`ae` → `a`, `ö`/`oe` → `o`, `ü`/`ue` → `u`, `ß` → `ss`;
    - French and other Latin letters: accents folded (`é`, `è`, `ê` → `e`, `ç` → `c`, `œ` → `oe`); elision removed (`l'école` → `école`, `qu'il` → `il`);
    - Russian: `ё` → `е`;
  - one stemmed field per language (D51), each on top of `exact`: `en` (English), `de` (German light), `fr` (French light), `ru` (Russian light). Each stemmer changes only words of its own alphabet and endings, so a mixed note is found in each of its languages;
  - `tokens` (D63): every word as written between spaces, with punctuation at its ends removed (`(siehe bericht-2026.pdf).` → `bericht-2026.pdf`), lower case, German normalization: email addresses, URLs and file names stay whole. Their parts come from the fields above (`artem@example.org` → `artem`, `example.org`);
  - `name`: the file or folder name, whole and in parts: `Bericht 2026-11.pdf` → `bericht 2026-11.pdf`, `bericht`, `2026`, `11`, `pdf`; also split at `-`, `_`, `.` and camelCase (`MeetingNotes` → `meeting`, `notes`), with the same German normalization.

  A query searches all of them; a whole match (`tokens`, whole name) ranks above parts, `exact` above stemmed.
- Languages: `mizoine.search.languages` (default `en,de,fr,ru`). Others that Lucene has a stemmer for (e.g. `es`, `it`, `nl`, `uk`, `pl`) can be added; a changed list makes the indexes `needs-reindexing`, as a changed analysis does (D58).
- Synonyms: not in these steps, postponed (D52). Lucene can apply them at search time later (`SynonymGraphFilter`), without changing the index.
- `ANALYSIS_VERSION`, a constant: raised with every change of fields or analyzers, so that existing indexes say `needs-reindexing` (D58). A test compares the tokens of fixed sample texts (English, German, French, Russian, mixed) with expected tokens kept in the test: it fails when the analysis changes, as a reminder to raise the version and update the expected tokens together.
- Plain unit tests, no Spring:
  - `Äpfel` = `Aepfel` = `Apfel` = `apfel`; `Straße` = `Strasse`; `Café` = `Cafe`;
  - `Häuser` finds `Haus` (stemming);
  - `ue` after a vowel stays (`Feuer` is not `Fer`);
  - French: `l'école` is found by `ecole` and `écoles`; `Noël` by `noel`; `cœur` by `coeur`;
  - Russian: `ёлка` = `елка`; `книги` finds `книга`; capital Cyrillic letters (`Москва` = `москва`);
  - `mizoine.search.languages=en,de` leaves Russian words unstemmed, and the index of another language list says `needs-reindexing`;
  - `artem@example.org` is found by the whole address, by `example.org` and by `artem`; a URL by itself and by its host;
  - `bericht-2026.pdf` is found by `bericht-2026.pdf`, `bericht` and `pdf`; `Übersicht.md` by `uebersicht.md`;
  - `MeetingNotes.md` by `meeting notes`.

### 56. The index  ☑
- **What (D50):**
  - text files (UTF-8, no NUL bytes, up to 2 MB, as D21): file name, path, title (a Markdown file's first heading, else the name) and content;
  - all other files (PDF, pictures, email files, …): name and path only. Their text comes from the import layer (BACKLOG §4) as a Markdown file beside them, indexed like any text file; the search result is that Markdown file, which links to its source;
  - folders: name and path, so that a folder can be found by its name.
  - Hidden names never; links are not followed (D54).
- **Where (D48, D60):** one index per repository, in `mizoine.search.index-dir` (default `search-index` in the directory Mizoine starts from), e.g. `search-index/notes/lucene10-a1/`; never inside a repository.
- **Nothing heavy on its own (D61):**
  - at startup, Mizoine only opens each repository's index and checks its format: milliseconds, no file is read;
  - Mizoine's own writes, deletes and moves update an indexed index at once;
  - changes by hand, by Git or by another tool: no timer, no rescan of its own (D49). The index state shows the dates to compare (step 60); an update or a reindexing runs when the user asks for it;
  - an update (only the changed files) or a full reindexing runs only when asked for (step 60).
- `mizoine.search.enabled` (default `true`): `false` builds no index and answers 404 for search.
- **The state of an index (D59),** per repository:

  | State | Meaning | Search |
  |---|---|---|
  | `not-indexed` | No index yet: first start, a new repository | 503 with the state |
  | `needs-reindexing` | Made by another Lucene major or analysis version (`reason: format`), or cannot be read (`reason: damaged`) | On the old index if it can still be read, the answer says so; else 503 |
  | `reindexing` | A reindexing runs: files done and total | On the old index if there is one, the answer says so; else 503 with `Retry-After` |
  | `indexed` | Current format. Whether it covers the latest changes, the dates tell (step 60) | Normal |
  | `failed` | The last reindexing failed (e.g. disk full), with the reason | On the old index if there is one; else 503 |

  - Every search answer carries the state, so a client can tell that results may be incomplete.
  - At startup, one log line per repository whose state is not `indexed`, naming the request that fixes it.
- **Versions and upgrades (D58):** an index is used only where Mizoine can be sure to read it. Nothing has to be deleted by hand after an upgrade.
  - Each index lives in a folder named after its format, e.g. `lucene10-a1`:
    - `lucene10`: the Lucene major version;
    - `a1`: Mizoine's own analysis version, a constant raised whenever the fields or analyzers change (step 55).
  - The commit data inside the index records the same two values, and Lucene's own version, as a check.
  - **`needs-reindexing`** when:
    - only a folder of another format exists (an upgrade of Lucene's major version, a changed analysis);
    - Lucene cannot open it (`IndexFormatTooOldException`, `IndexFormatTooNewException`, `CorruptIndexException`, missing files);
    - its commit data does not match its folder name.
  - An old index of the same Lucene major (only the analysis changed) still answers until the reindexing is done (D57). One of another major is never opened.
  - A reindexing builds the new format's folder beside the old one. When it is complete, search switches to it and the other folders are deleted. Going back to an older Mizoine finds no index of its format: `needs-reindexing`; it never reads a newer one.
  - A minor Lucene upgrade (10.5 → 10.6) keeps the index: Lucene reads indexes of its own major version, and the analysis version says whether Mizoine's fields changed.
  - The `lucene-backward-codecs` jar is not used: reindexing is simpler than converting, and the files are the truth.
- State and upgrade tests (no real old Lucene index needed):
  - no index → `not-indexed`, search 503;
  - a folder of another analysis version → `needs-reindexing` (`format`), search on it says so;
  - an index whose commit data says another version → `needs-reindexing`;
  - garbage files in the index folder (a damaged or foreign index) → Mizoine starts, logs it, `needs-reindexing` (`damaged`);
  - startup reads no file of the repository (a test counts the files opened);
  - nothing changes the index on its own: a file changed by hand is not found until an update; the state's newest-file date shows the change.
- Tests with `@TempDir`:
  - a change by hand is found after an update request;
  - Mizoine's write is found at once;
  - delete and move;
  - a deleted index becomes `not-indexed`;
  - a binary file is found by its name only, a folder by its name.

### 57. Searching, ranked and filtered  ☑
- **Query (D53):** words, `"a phrase"`, `-without`, `prefix*` (Lucene's simple query syntax, no field or regex syntax). At most 200 characters.
- **Order:** BM25. A match in the name or title ranks above one in the content; `exact` above stemmed.
- **Strict, then loose (D53):**
  1. all words;
  2. if none: any of the words;
  3. if still none: similar spelling (edit distance 1 for words of 4–7 letters, 2 from 8 letters).

  The result says which.
- **Permissions (D54):**
  - the query itself is limited to the folders the user may read, so neither results nor their number reveal anything else;
  - each result is checked again with the same rules as reading (`RepositoryFiles`) before it is shown;
  - grants that change apply at once: the filter is built per search, not stored in the index.
- **Snippets:** up to 3 passages per result with the found words marked; only from results the user may read.
- Paging: `limit` (default 20, at most 100) and `offset`.
- Tests: a security matrix:
  - another user's folder, a repository without any grant, a hidden folder, a grant removed while running, a link out of the grants: never in the results, nor in their number;
  - ranking (title above content); the three levels of strictness; the query length limit.

### 58. Search page and API  ☑
- `GET /{repository}/.search?q=…` and `GET /.search?q=…`: HTML (a search box, the results with title, path and snippet, links to the documents) or JSON.
- A search box on the start page (all repositories) and on each folder page (this repository).
- The search terms are part of the URL, so a search can be bookmarked and opened in several windows (BACKLOG §3).
- OpenAPI: `@Operation`, parameters `q`, `limit`, `offset`; 200, 400 (empty or too long query), 401, 404, 500.
- README: a section "Search": who finds what, the query syntax, German spellings, the languages, the settings, and the index state: after installing or upgrading, reindex where the state says so; deleting `search-index/` is always safe.

- *Done 2026-11-25 (steps 55–58):*
  - **Lucene 10.5.1** (`lucene-core`, `lucene-analysis-common`, `lucene-highlighter`); no query parser: `SearchQuery` reads the query once, `SearchQueries` builds Lucene's queries with its `QueryBuilder`.
  - **Analysis (step 55), `SearchAnalysis`:**
    - one analyzer per field (`exact`, `title`, one per language, `tokens`, `name`, `nameparts`);
    - folding: `ё` → `е` as a char filter; lower case, accents (`ASCIIFoldingFilter`), German spellings (`GermanNormalizationFilter`); French elision;
    - stemmers per `Language`: Porter (en), light (de, fr, ru, es, it, pt, sv). Unknown codes stop the startup with the known ones named.
    - `ANALYSIS_VERSION` 1; the format names the sorted languages: `a1-de-en-fr-ru`. Some stems look odd (German `artem` → `art`, French `meetings` → `meting`) but do no harm: queries are analyzed the same way.
  - **Found on the way:** `TextFile` read a valid UTF-8 file with a NUL byte as text, against D21. Fixed at the cause (`TextFileTest.aNulByteIsNotText`): binary files are no longer text for the Markdown page and `read_file` either.
  - **Index (step 56):**
    - `RepositoryIndex` per repository in `<index-dir>/<repository>/<format>/`, format `lucene10-a1-…` (`IndexFormat`); commit data records the format and Lucene's version.
    - `open` looks at the index folder only; it has no access to the repository's root at all (structural, D61).
    - `reindex` builds `<format>.building` beside the old index, which answers meanwhile; under the lock it applies the changes made during the walk, switches, deletes the other folders. A failure keeps the old index: `failed` with the reason.
    - `update` compares the stored versions (the ETag of each file) with the files; `changed` re-reads one path with everything below it (a write, a new folder, a delete, either side of a move).
    - `IndexDocuments`: files and folders by name (`name` whole, `nameparts`), title (first Markdown heading, else the name), text files' content in the text fields and `tokens`. The index keeps no content: snippets read the file after the hit was checked.
    - Hidden names and links are never indexed; a linked file is found at its own place.
    - Mizoine's own changes: `RepositoryFiles` publishes a `RepositoryChange` (the real path) after each write, new folder, delete and move; the search layer listens (`SearchConfig.IndexUpdates`). A failing index update is logged, never a failed write. The files layer does not know search.
    - `mizoine.search.enabled=false`: no beans, no index folder, the addresses are 404 (`@SearchEnabled`, one condition for configuration and controller; `SearchDisabledTest` found the controller still being scanned).
    - At startup, one warning per repository whose index is not `indexed`.
  - **Searching (step 57), `Search`:**
    - one `MultiReader` over the indexes of all searched repositories: one ranking, one count;
    - field weights: `name` 8, `nameparts` and `title` 4, `tokens` 3, `exact` 2, languages 1;
    - levels: all words, any word, similar spelling (`FuzzyQuery` on `exact` and `nameparts`, words of 4+ letters, prefix of one letter kept);
    - permissions: `RepositoryFiles.searchArea(s)` gives the granted folders; the query filters on repository and path prefixes; `RepositoryFiles.findable` checks each hit with the rules of reading (links followed) before it is shown. A file deleted by hand since the indexing is dropped (`aFileGoneSinceTheIndexingIsNotShown`);
    - folders only on the way to a grant (D4) are not searched;
    - snippets: Lucene's highlighter on the words as written, else on a language's forms, else on `tokens`; returned as plain-text parts, so the page never needs unescaped HTML.
  - **Page and API (step 58), `SearchController`:** `GET /{repository}/.search` (`searchRepository`) and `GET /.search` (`searchAllRepositories`), HTML (D6 default; without `q` the search box) or JSON; 400 with the message, 404 without a grant, 503 with the states when no index can answer. Search boxes on the start page and on every folder page.
  - Enum values in the API (`not-indexed`, `all-words`) come from one interface, `ApiCode` (dryer found the two copies).
  - ~~Not yet usable on a live server~~ (until step 60, done 2026-11-26): there was no request that built an index.
  - **Found by the mutator:** a repository root that is itself a symbolic link was indexed as empty: `walkFileTree` follows no link, also not its start. The walk now starts at the root's real place (`aRootThatIsALinkIsIndexedWhereItLeads`).
  - **Untested branches** (disk errors and races; no honest test):
    - `RepositoryIndex`: `IOException` in `update`, `changed`, `known`, `closeQuietly`, `deleteQuietly`, listing the index folder; `lease` failing to acquire.
    - `RepositoryIndex.reindex` while another reindexing of the same index runs, and changes arriving during its walk (`pending`): a race between two requests; step 60 runs reindexings in the background and will test the "not twice".
    - `IndexDocuments.walk`: a repository root or start path that vanishes between the check and the walk.
    - `SearchAnalysis.terms`, `Snippets`: `IOException` and invalid offsets while analyzing a `String` (cannot happen).
    - `Search.release`: closing a lease fails.
    - `SearchConfig.IndexUpdates.changed`: an index that fails to take a change (a disk error after the write); the startup warning's reason part (only states with a reason: an index of another format at startup).
- *Tools for steps 55–58:*
  - crapper: every new or changed method below 10 (highest `SearchQuery.parse`, 8.0).
  - dryer, over all production code: `IndexStatus` and `Strictness` named their values the same way → one interface `ApiCode` with the rule. Left: the two `GitController` pairs, as before (step 48).
  - mutator, per group with its test classes:

    | Files | First run | After |
    |---|---|---|
    | search core (analysis, index, queries, snippets, search) | 88 killed, 24 survived, 3 uncovered | all killed but 4 equivalent (below) |
    | `SearchController`, `SearchConfig` | 7 killed; uncovered only annotation texts | the same |
    | `TextFile`, `RepositoryAccess` | 35 killed, 3 survived | 37 killed, 1 equivalent (below) |
    | `RepositoryFiles` | 11 killed, 3 survived | 2 were Git code, killed by the Git page tests; 1 dead check removed |

    - Killed with new tests: a 4-letter word spelled loosely, an 8-letter word with two letters wrong, the first letter kept; a grant `public` is no grant for `publicity/`; a changed folder leaves `projects-old/` alone; the prefix of a whole name (`bericht-20*`); a lone `*` asks nothing (the check was not dead: as an empty prefix of the whole-name field it found everything); snippet parts at the start and end of a passage; neighbouring passages stay apart; a NUL byte as the first byte; an update after a whole folder is gone; an unreadable older index; a failed first reindexing has nothing to search.
    - Removed as dead: the `asks` flag of `SearchQueries.query` (a query that asks nothing finds nothing); the root check in `RepositoryFiles.findable` (search never asks for the root); the link check of a path's last part (the walk does not follow it).
    - Left, equivalent, and why:
      - `IndexDocuments.indexable`, `i = 1 → 0` and `< → <=`: the root is already its real path, and the walk does not follow a last part that is a link;
      - `RepositoryIndex.reindex`, the "searchable" of the state while a reindexing runs: only visible during the walk (the race above);
      - `RepositoryAccess.visibleChildren` `> → >=` (step 21 code): a grant as long as the folder is the folder itself, which the branch above already answered.

### 59. Search for agents (MCP)  ☑
- MCP tool `search(query, repository?, limit?)`: the same search, as the agent user. Results: path, title, snippet; then `read_file` reads the document.
- Its description tells the agent the query syntax and that results are limited to its folders.
- Tests: an agent with `[knowledge, inbox]` finds only in those folders; a repository it has no grant in answers as unknown.
- *Done 2026-11-26:*
  - `McpSearchTool`, given to `McpTools` only when search is on (`ObjectProvider<Search>`): without search, agents have no `search` tool.
  - Arguments: `query`, optional `repository` and `limit` (an integer; text is refused). The same `Search` as the page, as the agent user.
  - The answer: a text for every client (one line per result, its first passage below it with the found words in `**bold**`, line breaks as spaces; the level of strictness; repositories without an index), and the results as structured content, as the JSON API.
  - Errors as tool errors: "Not found: other" for a repository without a grant; "Not searched: no search index yet for tree. A person with access to the whole repository can build it."; the query's own messages.
  - The text and error helpers of `McpTools` are shared (dryer).

### 60. Index state and reindexing on request  ☑
- `GET /{repository}/.search/index` (JSON or HTML): the state (D59): `not-indexed`, `needs-reindexing` (with `reason`), `reindexing` (files done and total), `indexed`, `failed` (with the reason). With the dates the user needs to judge whether the index is current (D49):
  - `indexedAt`: when the last full reindexing finished;
  - `updatedAt`: the last change of the index (Mizoine's own write, or an update request);
  - `newestFile`: the newest modification time of a file in the folders the user may read, from the file system, read when asked (names and times only, no content);
  - `lastCommit`: the date of the last Git commit, for users with Git access (D33), where the repository is a Git working tree.

  So "the newest file is younger than `updatedAt`" means: changes are not in the index yet.
  - For everyone who may search the repository: the state and whether a reindexing is running.
  - Only for users with the whole repository (`/`): the number of indexed files and the files that could not be read (names only), since these speak of the whole repository.
- `GET /.search/index`: the states and dates of all repositories the user may search, e.g. for a script after an upgrade.
- `POST /{repository}/.search/update`: indexes only what changed since the index's own record: new, changed (by version: size + modification time) and deleted files. Quicker than a reindexing; for an `indexed` index after changes by hand or a pull. Same rules as the reindexing below (who, background, 202, not twice); for any other state 409 "needs reindexing".
- `POST /{repository}/.search/reindex`: the index is built anew from all the repository's files, beside the old one (D58). For a first index, after an upgrade, for a damaged index, or after many changes by hand.
  - Only for users with the whole repository (`/`, D56); others 404, as for anything not visible (D2).
  - Runs in the background: 202 Accepted at once, with the state `reindexing`. Requested again while it runs: 202, the running one counts; it is not started twice.
  - Searching keeps working on the old index meanwhile, if there is one (D57).
  - JSON POST, no CSRF token needed (as the Git actions); the body may be empty.
- No MCP tool: agents do not update or reindex.
- OpenAPI: 200, 401, 404, 500 for the state; 202, 401, 404, 500 for the reindexing; 202, 401, 404, 409, 500 for the update.
- Tests:
  - the states in order: `not-indexed` → `reindexing` → `indexed`; a failing reindexing (index folder not writable) → `failed`, the old index still answers;
  - a file changed by hand: `newestFile` is later than `updatedAt`; after an update it is found and `updatedAt` moves on;
  - an update removes a deleted file and finds a new one, without reading unchanged files;
  - `lastCommit` only for users with Git access;
  - `newestFile` of a user with folder grants only counts those folders;
  - a second request while running starts nothing new;
  - search during reindexing answers from the old index and says so;
  - a user with folder grants only: the state without counts; reindexing 404;
  - a form POST without token is refused (CSRF).
- README: the state, update and reindexing with curl; after installing and after an upgrade: check the state, reindex where it says so; after changes by hand or a pull: compare the dates, update.
- *Done 2026-11-26:*
  - `RepositoryIndex`:
    - `begin()` marks a reindexing at once (so a second request starts nothing), `build(root)` does it; `reindex` is both.
    - `beginUpdate()` gives `STARTED`, `RUNNING` or `NEEDS_REINDEXING`; `runUpdate`.
    - An update now reads the files without the lock and writes under it, so searches are not held up; unchanged files are not read (`anUpdateReadsOnlyChangedFiles`).
    - `details()`: progress (counted in a first walk, names and times only), `indexedAt` and `updatedAt` in the commit data (kept across a restart; one moment for both after a reindexing), the number of entries, what could not be read.
    - Any failure of a reindexing in the background ends it as `failed`, not only I/O.
  - `SearchIndexes` runs reindexing and updates in the background (virtual threads); closing waits up to 10 s.
  - `IndexReports`: the state for a user. `newestFile` from the user's own folders (names and times, the index's rules: no hidden names, no links); `lastCommit` with Git access (`GitCommands.lastCommit`: `git log -1 --format=%cI`; a failing Git gives no date); `files` and `unreadable` only with the whole repository.
  - `RepositoryFiles.wholeSearchArea`: who may reindex (D56) is decided in the files layer, which alone makes "not found".
  - `SearchIndexController`: `GET /{repository}/.search/index` (`searchIndexState`), `GET /.search/index` (`searchIndexStates`), HTML and JSON; `POST …/reindex` (`searchReindex`) and `POST …/update` (`searchUpdate`), JSON, 202, 409 for an update that needs a reindexing, 404 without the whole repository.
  - The startup warning names the request: `Search index of notes is not-indexed: reindex it (POST /notes/.search/reindex)`.
  - `TestGit` is public now, for tests of other packages with a real Git repository.
  - *Added 2026-11-26 on request:* navigation and buttons in the browser.
    - Links: "Search indexes" on the start page and on the search page of all repositories, "Search index" on a repository's search page; the state pages link back to the files and the search.
    - The state pages have **Update** and **Reindex** buttons where `mayReindex` (new in the state: the whole repository). The buttons are forms (`POST /{repository}/.search/{reindex|update}`, form-encoded, with the CSRF token) beside the JSON endpoints, as on the Git page: 303 back to the page (`back=all`: to the page of all repositories), which says what happened ("Reindexing started.", "The index needs a reindexing first."). While one runs, the page says so, with a link to reload it.
    - Tests as a browser sends them: the browser helpers of `GitActionsPageTest` became the shared test class `TestBrowser` (login form, session, CSRF cookie, the page's token, a cross-site form without it). `formLogin()` of spring-security-test is not used for this: it replaces the context's CSRF token store and broke later browsers in the same context.
  - *Review 2026-11-26 (tools and reading):*
    - **Fixed: a write during an update was undone by it.** The update reads the changed files without the lock and writes them under it; a Mizoine write in between was overwritten by the update's older reading. Now the update has two phases (`readChanges`, `writeChanges`), notes the paths Mizoine changes meanwhile (`touched`, as `pending` of the reindexing) and takes them anew after writing. Tests for a write, a new file and a delete between the phases.
    - **Fixed: a second Mizoine with the same index folder.** Lucene's write lock made its index look `damaged`, and a reindexing there would have deleted the folder the first one uses. Now `needs-reindexing` with reason `in use`, an error in the log, and a reindexing never starts there.
    - Checked, left as it is: an index of a repository removed while running stays open until the restart (a few file handles); the state page walks the user's folders for `newestFile` on each request (D49: read when asked); a page of results may be one short of `limit` when a hit fails the check again (honest, and reveals nothing).
    - crapper: all below 10. dryer: nothing new at 0.8. mutator on `RepositoryIndex`: 48 of 49 killed; the survivor (a Mizoine write counted as a reindexing) was hidden by an update before the restart: killed by `aWriteThroughMizoineIsNoReindexingAlsoAfterARestart`.
  - **Untested branches:** a form of a user without the whole repository (404, the same check as the JSON endpoints: no page gives that user a token for it); `SearchIndexes.close` interrupted while waiting; an update that fails in the background (logged); `IndexReports`' Git failure (logged, no date): a missing or broken `git` program.
- *Tools for steps 59–60:*
  - crapper: all below 10 (highest of the new code `RepositoryIndex.update`, 8.0).
  - dryer: the search tool read its arguments and wrote its errors as `McpTools` does: now it uses `McpTools`' helpers. Left: the two `GitController` pairs (step 48).
  - mutator: first run 186 killed, 12 survived. Killed with new tests: no index has 0 files; a new reindexing counts from 0 again; an update keeps `indexedAt`. Removed as dead: the blank-output check of `GitCommands.lastCommit` (Git exits with an error without a commit). The `RepositoryFiles` survivors (access rules of step 35) were only due to the narrow test selection: killed by the file API's own tests. Left, equivalent: `IndexDocuments.indexable` (step 57, see there); `McpTools.createFolder`'s create-only `false` (step 31: "exists" either way, as its comment says).

## Tags and statuses (planned 2026-11-25)
*Requested 2026-11-25:* folders and files can be tagged; the tags are shown with their attributes and in search results, can be searched, and can be read and edited over the HTTP API and MCP.
*Changed 2026-11-25:* **folders only for now**, in a technical file `.tags` inside the folder. Tags of files are postponed; favourite for later: a sidecar file beside the file, `my_doc.md.tags`, maybe a hybrid (D72).

**The `.tags` file (D64, D65):** the folder's own tags, in the folder itself, so that the tags move with the folder, also when it is moved by hand.

```
# projects/mizoine/.tags
project  status:active
prio:high
```

- Plain text: tags separated by spaces, commas or line breaks; a leading `#` is allowed and ignored (`#project` = `project`). Mizoine writes one tag per line. The same format can serve the sidecar files later (`my_doc.md.tags`).
- A tag (D66): letters of any alphabet, digits, `-`, `_`, `/` (e.g. `project/mizoine`), at most one `:`; no spaces; at most 50 characters and 30 tags per folder; case kept, compared without case.
- **Statuses are tags `key:value` (D67, decided):** `status:open`, `prio:high`; at most one value per key, so setting `status:done` replaces `status:open`. No fixed values.
- `.tags` is a hidden name (D3): never listed, read or written as a file over the API; Mizoine reads and writes it for the tags only. Git versions it like any file; edited by hand, it counts (BACKLOG principle 4).

**Principles kept (BACKLOG):**
- Plain files: a few words in a text file inside the folder; no database.
- An independent layer: without `.tags` nothing changes; a `.tags` that cannot be read is logged and counts as no tags, never an error page.
- Access as everywhere (D68, decided):
  - whoever may read in the folder sees its tags; whoever may write in it may change them;
  - a folder shown only on the way to a grant (D4) shows no tags; the root's tags need the whole repository (`/`) to change.

**HTTP and MCP (D69, decided):**

| Request | What |
|---|---|
| `GET /{repository}/{folder}/?tags` | The folder's tags, JSON, with an `ETag` |
| `PUT /{repository}/{folder}/?tags` | Replace them: a JSON list; `If-Match` honoured (D13) |
| Folder listing (JSON and page) | The folder's own tags, and each visible subfolder's |
| `GET /{repository}/.tags/` | The vocabulary: the tags in use among the folders the user may read, with counts, from the search index (step 65) |
| MCP | `list_folder` with the tags; new tools `set_tags`, `list_tags` |

- `?tags` on a file: 400 "Tags are for folders" until file tags come (D72).

### 61. Tags: the model and the `.tags` file  ☑
- `Tag` (a value type, checked once where it comes in; case kept, compared without case) and `Tags` (a set; one value per key for `key:value`).
- `TagsFile`: reads and writes a folder's `.tags` in the plain format above; written atomically under the writer's lock, like any write (step 30). Comment lines are not part of the format: Mizoine writes only the tags.
- Plain unit tests: spaces, commas, line breaks and `#` read alike; invalid tags (too long, two `:`, empty) refused with a message; `status:done` replaces `status:open`; `Projekt` = `projekt`; an unreadable file counts as no tags and is logged.

### 62. Reading folder tags  ☑
- The folder listing (JSON and page) gives the folder's own tags and each visible subfolder's.
- `GET /{repository}/{folder}/?tags`: `{"tags": [...]}` with an `ETag` (the version of the `.tags` file).
- Tests: the permission matrix (read grant: visible; folder on the way to a grant (D4): no tags; not visible: 404); `GET …/.tags` stays 404 as a file; `?tags` on a file is 400.
- OpenAPI for the new answers.

### 63. Editing folder tags  ☑
- `PUT /{repository}/{folder}/?tags` with a JSON list replaces the tags; an empty list deletes the `.tags` file. With `If-Match` only if the file is unchanged (412 otherwise).
  - Only where the user may write in the folder; otherwise the same answer as a file write there.
  - CSRF as for file writes: the browser's session needs the token, HTTP Basic clients do not (D11).
  - Invalid tags: 400 with the reason.
- Moving a folder needs nothing: `.tags` is inside it.
- **Deleting (D70):** a folder that holds only `.tags` counts as empty: deleting it removes the `.tags` too. Any other content, also hidden, still blocks the delete (D15).
- Tests: replace, remove, `If-Match`, a read-only place (above a grant) refused, the root needs `/`, a folder with only `.tags` deleted, a folder with `.tags` and a file not, a moved folder keeps its tags, two concurrent edits (one 412).
- README: a section "Tags".

### 64. Tags for agents (MCP)  ☑
- `list_folder` returns the folder's and its subfolders' tags.
- `set_tags(repository, path, tags, if_match?)`: replaces a folder's tags.
- `list_tags(repository)`: the vocabulary of step 65, so that an agent keeps to your tags instead of inventing new ones; the descriptions say so.
- Tests: an agent sees and sets tags only in its folders.

### 65. Searching by tags, and the vocabulary  ☑
Builds on steps 55–60: the search index knows the tags, so the vocabulary is one of its answers (D71).
- A field `tag` for folders in the index (normalized like `exact`, step 55). Mizoine's own tag edits update the index at once; a `.tags` edited by hand is taken up by the next update (D49): the update compares the versions of `.tags` files too.
- Query: `#project` or `#status:open` finds folders with that tag (hashtag form; D53 stays: no other field syntax). A plain word also matches tags, ranked like a match in the name. API parameter `tag=` (repeatable) to filter; MCP `search` gets `tags`.
- Results show the folders' tags.
- `GET /{repository}/.tags/` (JSON or page): every tag in use among the folders the user may read, with counts, `key:value` tags grouped by key (all statuses together); each tag links to its search. The same in `list_tags`. With no usable index: 503 with the index state, as search (D59).
- Tests: `#status:open` finds only open folders; tags of folders a user may not read are neither found nor counted; a tag edit is found at once; a hand edit after an update.

- *Done 2026-11-26 (steps 61–65):*
  - **Model (61), package `tags`, no dependencies:** `Tag` (checked once; case kept, compared without case; `#` is the hashtag form), `Tags` (each once, the first spelling kept; one value per key, the later wins; at most 30), `TagsFile` (reads `.tags`: missing, unreadable or not UTF-8 is no tags, logged; words that are no tags are left out and logged, the rest counts). `Tags.of` (API) is strict, `Tags.read` (a hand-edited file) lenient.
  - **Reading (62):** `RepositoryFiles.listing` gives `tags` and `folderTags`, where the user may read inside (one rule, `readableFolder`, also used by the search's second check). `GET …/?tags` (always JSON, also without `Accept`), ETag of the `.tags` file. The folder page shows them.
  - **Editing (63):** `PUT …/?tags` with a JSON list; `RepositoryFiles.setFolderTags` checks access as for a file write in the folder. The writer got two narrow methods, `writeTags` and `deleteTags`: the only ones that write a hidden name, so `write` still refuses every hidden name. A folder with only `.tags` counts as empty for a delete (D70). A tags change publishes a `RepositoryChange` for the `.tags` path.
  - **OpenAPI:** one operation per path and method, so `?tags` would have replaced the folder's `GET` and the `PUT` (found by reading `docs/openapi.yaml`; now pinned in `OpenApiDocumentationTest`). The `?tags` handlers are `@Hidden`; the folder `GET` and the `PUT` describe `?tags`.
  - **Search (65):** analysis version 2: field `tag` (whole, folded like a name), stored `tags`. A folder's entry has the version of its `.tags`, so an update sees hand edits; a change of `.tags` takes only the folder's own entry anew. `#tag` in a query (exact, never loosened to a similar spelling), plain words match tags too (weight 4), `tag=` filter at every level; hits show tags. `Search.vocabulary` from the index, lower case, sorted; `GET /{repository}/.tags/` (page grouped by key, links to the search). `search` and `vocabulary` share one way to lease the indexes (`withIndexes`).
  - **MCP (64):** `list_folder` shows the tags (the listing); `set_tags`; with search on `list_tags` and a `tags` filter for `search`; folders' tags in the search text.
  - **Left out, to decide:** the repository root has no entry in the index, so its tags show in the listing but are not searched or counted. A tag filter finds the tagged folders, not the files inside them (no inheritance).
  - *Tools for steps 61–65:*
    - crapper: all below 10. dryer: `Tag.equals`/`hashCode` and `Tags.equals`/`hashCode` are one line each on other fields: left, no shared decision.
    - mutator: first run 292 killed, 32 survived (most in older code outside the group's tests). Killed with new tests: a `.tags` that is a folder or a link is never written (a link put there by hand would have been written through); the tags of a linked folder are not indexed; a tag changed twice leaves one entry; the vocabulary of an empty index; a lone `#` is a word. Removed as dead: a link check in `IndexDocuments.folder` (a link is no folder without following links). Changed: the tags page groups by `Tag.key()` instead of its own `indexOf(':')`. Left, equivalent: `Tag.key` and the old `indexOf` for a colon at index 0 (no tag starts with `:`); `IndexDocuments.indexable` (step 57); `RepositoryWriter` `isInside`, `isVersion`, `copy` (step 48); `McpTools.createFolder` (step 59). The survivors of `RepositoryFiles` were killed by the Git and index page tests.
  - *Review with the tools (2026-11-26):*
    - **Fixed:** the tags in use counted every tagged folder of the index in the user's folders, without the second check of a search hit; after a tagged folder was moved by hand out of a user's folder, the user still saw its tags until an update. `Search.vocabulary` now counts only folders the user may read now (`findable`), as the hits (test `theTagsInUseOnlyOfFoldersTheUserMayReadNow`).
    - **Refactored:** `Tags.texts()` is the one way from tags to their written form (it was written out five times, once in a test). The result of a tags change is `FolderTags`, as for reading them: `TagsResult.Written` had the same shape, so both handlers of `FolderTagsController` answer with one `tagList`. Imports in order, unused ones removed.
    - **Mutator:** the survivor `IndexDocuments.folder` `&& → ||` is killed: a hidden folder is not indexed also when the index is told that its `.tags` changed (`theTagsOfAHiddenFolderAreNotIndexed`). All changed files: no survivors. "Uncovered": the `required = false` of an annotation (no code), and in `McpTools` the cases the exhaustive switch needs that MCP cannot reach (no version sent, no create-only).
    - crapper: all below 10. dryer: nothing new (the `GitController` pairs, step 48; `Tag`/`Tags` `equals`/`hashCode`, above).

## Uploads and editable files (planned 2026-11-26)
*Requested 2026-11-26:*
- MCP and the HTTP API upload pictures, documents and other files of any kind.
- The browser UI uploads files to the current folder.
- *Added 2026-11-26:* the browser UI renames and deletes files; uploading works from the phone; agents use MCP's standard way for binary content.
- Every text file can be edited in the browser (XML, HTML, SVG, Java, sh, bat, YAML, properties, …).
- The folder page has an **Edit** button for such files. It opens the editor, with syntax highlighting where there is a highlighter for the file's type.

**What there is already (checked 2026-11-26):**
- **HTTP:** `PUT /{repository}/{path}` already stores any bytes (step 30): streamed, atomic, up to `mizoine.max-file-size` (100 MB), whatever the `Content-Type`. Uploading over the API works; it is only not described as such.
- **MCP:** `write_file` takes text only (a JSON string); `read_file` gives text only.
- **Editor:** it opens whatever `?file=` names and never checks that it is text. **A picture opened in the editor and saved is destroyed** (decoded as UTF-8, written back with replacement characters).
- **Folder page:** no Edit anywhere. Only the rendered Markdown page has one. A click on a text file's name downloads it (D10: HTML, SVG, XML, JavaScript, unknown types such as `.java`, `.sh`) or shows it as plain text.
- **Highlighting:** Markdown, JSON, YAML, HTML, CSS, JavaScript/TypeScript, XML/SVG, Java, properties and shell (step 39). The installed `@codemirror/legacy-modes` also has PowerShell, Python, TOML, SQL, Dockerfile, diff, C/C++/C#/Kotlin/Scala (`clike`), Groovy, Go, Rust, Ruby, Perl, Lua, nginx and LaTeX. **No mode for Windows batch** (`.bat`, `.cmd`).

**Principles kept (BACKLOG):** plain files, nothing besides them; one way to write (`PUT` on the file's URL) for curl, the editor, the upload and the MCP tools; the same access, names and size rules for every way in.

### 66. The editor never creates a file (security)  ☑
*Requested 2026-11-26 (D76):* the editor must not be able to create a file that does not exist; creating a file is a separate, explicit action (step 71).
- **Why:** checked 2026-11-26, today the editor opens a missing file empty ("New file: not saved yet") and its Save creates it (`PUT` with `If-None-Match: *`; pinned by `editor.test.mjs`, "missing file opens empty, as new" and "a new file is created only if it still does not exist"). So a link from anywhere, e.g. `/ui/editor.html?file=/notes/projects/run.sh`, opens a logged-in user's editor at a path someone else chose, and one Save puts a file there.
- **Change:**
  - a missing file (404) shows "This file does not exist", with a link to its folder, and no Save; the text field stays read-only;
  - the editor has no request that creates: every Save is a `PUT` with `If-Match` of the version it loaded. The code path with `If-None-Match: *` is removed;
  - the page's texts and the "new file" state are removed.
- **Red first:**
  1. JavaScript (fake `fetch`): a 404 on load → "does not exist", Save disabled, and `save()` sends no request at all; the two tests above are replaced by these.
  2. JavaScript: no request the editor sends has `If-None-Match` (checked over all tests' requests).
  3. **End to end** (`EditorEndToEndTest`, the real `editor.js` in Node against the real server, logged in with a browser's session and CSRF header):
     - open the editor on a path that does not exist and try to save → no `PUT` is sent, **the file does not exist on disk afterwards**;
     - open an existing file, delete it on the server, then Save → 412 and **the file is not created again** (an `If-Match` on a missing file never creates; checked against `RepositoryWriter`, with a Java test if it is not already pinned).
  4. Java: a `PUT` with `If-Match` on a missing path → 412, nothing written.
- Docs: steps 37 and 39 get a note "changed in step 66"; README (editor section) says how files are created.
- *Done 2026-11-26 (ub-mode):*
  - **Reproduced end to end first** (red, against the real server with a browser's session): a link to a missing `run.sh` created it on Save (`load new`, `save-1 saved`), and a file deleted by another client while open was created again on the next Save after Reload (`reload new`, `save-2 saved`).
  - **A second way to the same hole, found while designing the fix:** after a load that failed (401, server error, no answer), the session had no file either, and Save sent `If-None-Match: *`. Logging in and pressing Save, as the page told the user to, would have created a missing file.
  - **The cause, removed:** `FileSession` treated "nothing loaded" as "a new file". Now "nothing loaded" means nothing to save: `save()` returns `not-loaded` without a request, `isChanged` is false, and every `PUT` carries the `If-Match` of the loaded version. The `If-None-Match: *` path is gone.
  - **The page:** a missing file says "This file does not exist. The editor does not create files.", the text field is hidden, Save stays disabled. Not logged in while loading → Reload is offered as well (before, the page said only "press Save again").
  - **Tests:**
    - JavaScript: missing → `missing`, and Save sends nothing; the same after a load with 401, 500 or no answer; a file deleted elsewhere gives `conflict` on Save, then `missing`, then nothing; an `after` check over every request of the file: none has `If-None-Match`. The two tests that pinned creating are removed.
    - End to end (`EditorEndToEndTest`, `editor-flow.mjs` with scenarios `edit`, `missing`, `deleted`, counting the `PUT`s): `aLinkToAMissingFileCreatesNothing` (no `PUT`, no file on disk), `aFileDeletedWhileOpenIsNotCreatedAgain` (one `PUT`, 412, no file on disk).
    - Java, already pinned: `RepositoryWriterTest.ifMatchOnAMissingFileFails` (an `If-Match` on a missing file never creates).
  - **Tools:**
    - crapper (complexity only: JaCoCo does not cover JavaScript): highest CC 6 (`parseFileParam`).
    - dryer over `ui/` and `src/test/js/`: nothing.
    - mutator on `editor.js` with `node --test src/test/js/`: first 24 of 27 killed; the 3 survivors were in `languageFor` (step 39), two killed with the names `a.md` and `.md`; left, equivalent: `?? → ||` (no language name is empty). `page.js` has no unit tests by design (step 37: no decisions, only shows them).
  - Until step 71 there is no way to create a file in the browser; the API (`PUT`) and MCP (`write_file`) still create files.

### 67. Uploads over HTTP: pinned and described  ☑
- Tests pin what step 30 gives for binary content:
  - all 256 byte values round-trip exactly;
  - a picture (PNG bytes) is stored as sent and downloaded with `image/png`;
  - the request's `Content-Type` changes nothing (`image/png`, `multipart/form-data`, none);
  - over the limit → 413; a new file with `If-None-Match: *` over an existing one → 412.
- OpenAPI and README: the `PUT` creates or replaces "any file, text or binary"; a curl example for a picture (`curl -T photo.jpg …`).
- No production change is expected. If a test turns red, that is a bug found, fixed test-first.
- *Done 2026-11-26 (ub-mode):* nothing to fix: step 30 already stores any bytes. Pinned by `FileWriteTest.binaryContentIsStoredAndReadExactly` (512 bytes, every value twice) and `aPictureIsServedByItsNamesType` (PNG with `image/png`, `multipart/form-data` or no `Content-Type`: stored as sent, served as `image/png`). 413 and `If-None-Match: *` were pinned since step 30. The `PUT` description (OpenAPI) and README say "any file, text or binary".

### 68. Text or not: one rule  ☑
- **By name only (D73, decided):** a file is editable when its name is a known text format; all others are not, and are never read to decide.
- **One list, in one place:** `ui/editor/text-formats.json`, file extension or whole name (`Makefile`, `Dockerfile`) → editor language (`text` for plain text without highlighting). The server reads it from the classpath for the listing; the editor imports it for its language and for what it opens. Adding a format is one line there.
- The folder listing (JSON and page) gains:
  - `editable`: the visible files with a known text format;
  - `writable`: whether the user may write in this folder (D74).
  - Both are new fields; `files` stays as it is.
- Plain unit tests for the rule:
  - known extensions, in any case (`Plan.MD`), and known whole names (`Makefile`);
  - unknown or missing extensions are not editable (`photo.png`, `data.bin`, `LICENSE` unless listed);
  - the last extension counts (`archive.tar.gz` is not, `config.yaml.example` only if `example` is listed);
  - no file is read: the rule is a function of the name.
- Permission tests: `writable` false above a grant (D4) and for read-only places.
- *Done 2026-11-26 (ub-mode):*
  - `ui/editor/text-formats.json`: `maxBytes` (2 MB, D21), `extensions` and whole `names` → highlighting (`text` for none). Read by `files.TextFormats` (Java, from the classpath, once: `TextFormats.STANDARD`) and by the editor (fetched from `/ui/editor/text-formats.json`, public). `TextFile.MAX_BYTES` comes from it too: one number for both.
  - The rule: a whole name first (`Makefile`), else the last extension in any case; a name starting with its only dot (`.md`) has none. Unit tests: 66 names.
  - Listing: `editable` (files of a text format) and `writable` (`RepositoryAccess.canWriteIn`); `files` unchanged; also in MCP `list_folder`. The listing test was written first but not run red on its own before the code (the fields could not have matched).

### 69. The editor: only text formats  ☑
- **The editor opens only known text formats** (red first, JavaScript tests with a fake `fetch`):
  - a name not in `text-formats.json` makes no request: "not a text format: download it", with a link;
  - **a guard, not a second rule:** a file with a text name that does not hold text (not strict UTF-8 by `TextDecoder` with `fatal`, a NUL, over 2 MB) is shown as "cannot be edited" without Save, so a misnamed picture is never destroyed;
  - a Java test checks that the listing and the editor read the same list (both from `text-formats.json`).
- **The formats and their highlighting** (in `text-formats.json`, D75, decided: simple):
  - **highlighted**, with the highlighters the editor already has (step 39), no new ones and no rebuild of the bundle:
    - `md`, `json`, `yaml`/`yml`, `html`/`htm`, `css`, `js`/`mjs`/`ts`, `xml`, `svg`, `java`, `properties`, `sh`;
    - the same highlighters for close relatives: `xsd`, `xsl`, `xslt`, `xhtml` → XML; `cjs` → JavaScript; `bash` → shell;
  - **plain text** (editable, not highlighted): `txt`, `csv`, `tsv`, `log`, `adoc`, `rst`, `bat`, `cmd`, `ps1`, `py`, `toml`, `ini`, `cfg`, `conf`, `sql`, `c`, `h`, `cpp`, `cs`, `kt`, `gradle`, `go`, `rs`, `rb`, `tex`; by whole name `Dockerfile`, `Makefile`.
- The editor still works for a file without a mode: plain text.
- *Done 2026-11-26 (ub-mode):*
  - `textFormat(formats, name)` replaces `languageFor`; the page fetches the list and refuses a name not on it before any request ("Not a text format … Open it from its folder").
  - The guard: `FileSession` reads bytes (`arrayBuffer`) and decodes them strictly (`TextDecoder` `fatal`); not UTF-8, a NUL or more than `maxBytes` → `not-text`, nothing loaded, so no Save. A byte order mark is now kept (`ignoreBOM`): before, `response.text()` dropped it, so a save removed it.
  - No new highlighters, no rebuild of the bundle (D75); only comments in `tools/codemirror/entry.js` changed (esbuild drops them).

### 70. `.tags` as a visible text file  ☑
*Requested 2026-11-26:* `.tags` is text and must be editable in the browser like any text file. D81 (decided, the user's proposal): one exception where hidden names are decided.
- **The one exception, in `FolderNames`:** `.tags` (`TagsFile.NAME`, exactly, in this case) is not a hidden name. Everything that asks "hidden?" then treats it as a plain name: listing, reading, writing, deleting, moving, the editor, MCP `list_folder`/`read_file`/`write_file`. No other place gets its own rule. All other names starting with `.` stay hidden (D3).
- **What stays special about it** (each one line where it already is):
  - **Only as a file:** a folder may not be named `.tags` (creating or moving to one → refused as a hidden name would be), so it cannot block a folder's tags. A `.tags` folder made by hand stays as it is: its parent shows no tags (as now, "cannot be read").
  - **The index** keeps `.tags` out as a file of its own: its words are already the folder's tags (`IndexDocuments.visitFile`, one condition). A write of `.tags` through any API refreshes the folder's entry, as now.
  - **Viewing as is:** `*.tags` is served as `text/plain`, inline (`FileDownload`'s own types), and later also fits the sidecar files of D72 (`plan.md.tags`).
  - **Editing:** `.tags` is in the list of text formats (step 68), as plain text; the folder page gets its Edit link with the other files (step 71).
  - **Checked or not:** `?tags` and MCP `set_tags` stay the checked way (each tag checked, 400 with the reason). An edit of the file itself is an edit by hand: the file is stored as written, words that are no tags are skipped and logged when read (D65), as for an edit outside Mizoine. Deleting `.tags` removes the folder's tags; a folder that holds only `.tags` still counts as empty (D70).
  - The narrow writer methods `writeTags`/`deleteTags` stay for `?tags`: they refuse a `.tags` that is a link, where a normal file write writes through a link to a visible file (step 30).
- **Access:** as for any file in that folder (D68 unchanged): read in a granted folder, write where the user may write; above a grant (D4) no files are shown, so no `.tags` either; the root's `.tags` needs `/`.
- **Tests, red first:**
  - `FolderNames`: `.tags` is not hidden; `.Tags`, `.tags2`, `.tagsx`, `.git`, `.env` stay hidden.
  - Listing (JSON, page, MCP): `.tags` is among the files where there is one; not above a grant.
  - `GET …/.tags` → 200, `text/plain`, inline, with ETag; `PUT …/.tags` writes it, `If-Match` honoured, and `GET …/?tags` shows the new tags at once; search finds the folder by them at once (`RepositoryChange`).
  - A word that is no tag, written into the file → stored as written, `?tags` leaves it out.
  - `PUT …/.tags/` (a folder) and a move to `…/.tags/` → refused.
  - The index has no entry for the `.tags` file itself.
  - The tests that pinned `.tags` as hidden (`TagsSearchPageTest.withoutAnIndexOrAGrant`: `GET …/.tags` → 404, and the like) change with it: each one named in the step notes.
- Docs: README "Tags" (the file can be edited like any text file; `?tags` checks), the glossary entry "Hidden name", D3.
- *Done 2026-11-26 (ub-mode):*
  - `FolderNames.isHidden`: exactly `.tags` is not hidden; `isPlainFolder`: a folder may not be named `.tags` (the writer's `folderParentOf`, for creating and moving folders; HTTP 404, MCP "Not found").
  - Index: `visitFile` skips `.tags` (its words are the folder's tags); a write or delete of the file refreshes the folder at once, as before (`RepositoryIndex.apply` already took paths ending with `.tags`).
  - `FileDownload`: `*.tags` is `text/plain`, inline. `text-formats.json`: `.tags` as plain text.
  - Tests that pinned `.tags` as hidden and changed with D81: `FolderTagsTest.notVisibleFilesAndTheTagsFileItself` (now `notVisibleFoldersAndFiles`: the file is 200), `TagsSearchPageTest.withoutAnIndexOrAGrant` (the file is 200). New: `FolderNamesTest.theTagsFileIsNotHidden` (and `.Tags`, `.tags2`, `.env`, … stay hidden), `FolderTagsWriteTest` (edited like any text file, `If-Match`, delete; not above a grant; no folder of that name), `TagsSearchPageTest.aTagsFileWrittenAsAFileIsFoundAtOnce` (found at once, no hit of its own, delete takes effect at once), `FileDownloadTest` (type), `McpToolsTest` (no folder `.tags`).
  - `writeTags`/`deleteTags` stay for `?tags` (they refuse a link).

### 71. Edit on the folder page  ☑
- Each file in `editable` gets an **Edit** link next to its name, to `/ui/editor.html?file=…`. The same URL as the Markdown page's Edit, built in one place (`RepositoryController.EDITOR`).
- A click on the name stays as it is: rendered Markdown, inline or download (D10).
- Only where `writable` (D74): a file that is not editable, or in a place the user may not write, gets no editor link. Viewing is opening the file itself, as is (the click on its name).
- **New file, an explicit action (D76):** in a writable folder, a field for the name and a **Create** button.
  - It sends one `PUT` of the new file's URL with an empty body and `If-None-Match: *` (an existing name → "exists already", nothing changed); then it opens the editor on the file, which now exists.
  - Only names of a known text format (D73); others are refused before any request, with the reason. Hidden names, `%`, `/` as for uploads.
  - The CSRF token is required, as for every browser write (D27); it is on Mizoine's own page, so no link from elsewhere can create a file.
  - Tests: a file is created only by Create; an existing name is not overwritten; a name of an unknown format and a hidden name are refused; no Create above a grant.
- Tests: the page has Edit for text files only, with the right encoded URL (non-ASCII and spaces in names); none for pictures; none above a grant.
- *Done 2026-11-26 (ub-mode):*
  - The page: `Link` has `edit` (the editor's URL, built once in `RepositoryController.editor`, also for the document page's Edit) for editable files where `writable`. The document page needs no check: every readable file lies inside a grant, and every grant may write.
  - New file: a form `#new-file` with `data-folder`, hidden until the script shows it; `ui/folder/folder.js` (`checkNewFileName`: NFC, the server's name rules, a known text format; `FolderActions.createFile`: one `PUT` with `If-None-Match: *` and an empty body), then the editor opens on the file. `ui/folder/page.js` only shows.
  - `editor.js` now exports `send` (the one request wrapper: no answer → status 0) and `SCRIPT`, used by the editor and the folder page.

### 72. Upload in the browser  ☑
Replaces the upload part of step 36.
- **As simple as possible, and from the phone (D77):** on the folder page, where `writable`, one **Upload** button: a plain `<input type="file" multiple>`. On the phone it offers the camera, the gallery and files by itself. No drag and drop, no progress bar.
- Each file is a `PUT` of its URL in this folder with `fetch`, the same request as curl, one after the other:
  - `If-None-Match: *`: nothing is overwritten silently; a name that exists → "Replace?"; yes → `PUT` with the `If-Match` of the current version;
  - the CSRF header from the cookie (D27);
  - a line per file: "uploading", "done", or the error; when all are done, the listing reloads.
- **Names (D78):** normalised to NFC, then checked before any request, with the reason shown: hidden (`.…`), `%`, `/`, empty. The server checks them again anyway.
- Errors per file: 413 (too large, with the limit), 409 (folder gone), 404 (no access), 0 (no answer); the other files go on.
- Plain JavaScript modules, no library (D79): `ui/folder/folder.js` holds the decisions (names, requests, answers) and is tested in Node with a fake `fetch`; `ui/folder/page.js` only connects it to the page. The folder page keeps working without JavaScript (links only).
- Java: the folder page loads its module under the strict CSP; the cookie token works for the page's `PUT`.
- Checked in a real browser, and on the phone: a camera picture into a folder.
- *Done 2026-11-26 (ub-mode):*
  - `#upload` with `<input type="file" multiple>`; `uploadOne`: name checked (NFC), `PUT` with `If-None-Match: *`; "exists" → `confirm` → `PUT` without a condition (simplified from the plan: the user has just said yes; an `If-Match` from a request right before would protect almost nothing). A line per file; the listing reloads after a change (`reloads`).

### 73. Rename and delete in the browser  ☑
*Requested 2026-11-26 (with D78).* Takes the delete of step 36 here.
- On the folder page, where `writable`, each file gets **Rename** and **Delete** (in the same module as step 72).
- **Rename:** asks for the new name (NFC, the same name checks as step 72); a move in the same folder with the existing API (step 41: `POST` with `Destination`, `If-Match` of the version shown). An existing name is never overwritten (412, D30): "exists already".
- **Delete:** asks first ("Delete plan.md?"); `DELETE` with `If-Match` (step 32). Changed since the page was shown → 412: "changed meanwhile, reload".
- After either, the listing reloads.
- Tests, JavaScript with a fake `fetch`: the requests and their headers; a refused name makes no request; 412 and 404 answers are shown. Java: no Rename or Delete above a grant or in a read-only place (`writable`).
- Folders: renaming and deleting empty folders use the same requests; they stay in step 36 unless wanted here.
- *Done 2026-11-26 (ub-mode):*
  - `li[data-file]` where `writable`; `page.js` adds Rename (`prompt`, `renameTo`: cancelled or the same name after NFC → nothing; else checked) and Delete (`confirm`). Requests: `POST` with `Destination` (never overwrites, D30: 412 → "exists") and `DELETE`, both without `If-Match` (simplified, as for replace).
  - End to end (`FolderEndToEndTest`, `e2e/folder-flow.mjs`, the browser's session from the shared `e2e/browser.mjs`, which the editor flow uses too): the folder page gives the CSRF cookie; New file, again (exists), upload, again (exists), replace, rename, rename onto an existing name (exists), delete; on disk only the replaced picture remains.

### 74. Binary files for agents (MCP)  ☑
In the standard MCP way (D80):
- **Reading:** `read_file` returns what MCP defines for content of a tool result:
  - text: `text` content, as now;
  - pictures: `image` content, base64 `data` with its `mimeType`, so that the model can look at them;
  - any other file: an embedded `resource` with a base64 `blob`, its `uri` (the file's URL) and `mimeType`;
  - the structured result keeps the version (`if_match`) and the size, as now.
- **Writing:** a new tool `upload_file(repository, path, content, mime_type?, if_match?)`. MCP has no binary type for a tool's input, so `content` is a base64 string, declared in the input schema as `"contentEncoding": "base64"` (JSON Schema).
  - The same rules as `write_file`: folder must exist, `if_match`, review note.
  - At most 10 MB decoded, for reading and writing (D80); more → an error with the limit, and the HTTP URL to use instead. Checked where it comes in (valid base64, size), before anything is written.
  - The description says when to use it ("pictures, PDFs, office documents; for text use write_file").
- `write_file` stays text only; its description points to `upload_file`.
- Tests: bytes round-trip exactly (upload, then HTTP `GET`, then `read_file`); a PNG comes back as `image` content, a PDF as a `blob` resource; invalid base64 and too large are refused, nothing written; only in the agent's folders; `McpEndpointTest` tool list.
- README: the MCP tool list.
- *Done 2026-11-26 (ub-mode):*
  - `RepositoryFiles.readContent` (sealed `FileContent`: `Text`, `Bytes` with the type from the name as for a download, `TooLarge`) replaces `readText`; `FileText` and `NotTextException` are gone.
  - `read_file`: text as before; otherwise a first text line (name, type, size, version) and `image` content for `image/*`, else an embedded resource with a base64 `blob`, `uri` the file's URL path; structured: `mimeType`, `size`, `version`. More than 10 MB → an error with the HTTP URL.
  - `upload_file(repository, path, content, if_match?)`: `content` declared `contentEncoding: base64`; its length is checked before decoding, then strict base64, then the size; the write answers are shared with `write_file` (`written`). No `mime_type` argument (simplified from the plan: the type always comes from the name).
  - SDK builders (`ImageContent.builder`, `BlobResourceContents.builder`), the constructors are deprecated (`-Werror`).
  - `McpEndpointTest.theLargestUploadGoesThroughTheEndpointAndBack`: 10 MB through the real endpoint and back (about 13 MB of JSON): the endpoint sets no lower limit.

- *Tools for steps 67–74:*
  - crapper: all below 10. `RepositoryWriter.createFolder` and `move` were at 10 with the `.tags` folder check: moved into `folderParentOf`, the lookup both already did. JavaScript (no coverage tool): the folder page's dialogs made decisions (`renameAndDelete` CC 8, `upload` CC 7): moved into `folder.js` (`renameTo`, `uploadOne`, `reloads`, tested); `page.js` only shows them (highest CC 5), as the editor's page (step 37).
  - dryer: nothing new (the `GitController` pairs, step 48; `Tag`/`Tags` `equals`/`hashCode`, step 65).
  - mutator: no survivors in any group (`folder.js` 17, `editor.js` 30, `TextFormats`/`TextFile` 8, `FolderNames`/`RepositoryWriter` 64, `McpTools` 107, `RepositoryFiles` 17, `RepositoryController`/`FileDownload` 21, `IndexDocuments` 19). "Uncovered": annotation texts, and `McpTools`' create-only and delete-version cases MCP never sends (step 59); the one newly reachable case, `create_folder` "Not found" for a folder named `.tags`, got a test.

- *Review with the tools (2026-11-26, after the commit):*
  - **Fixed:** `read_file` gave every `image/*` as MCP image content. Clients take only the types models read (Claude: PNG, JPEG, GIF, WebP); a BMP, TIFF or icon there can make the client refuse the whole answer. Now only those four are images, every other picture a base64 resource like any file (`McpToolsTest.onlyCommonPictureTypesAreImages`, red first at `a.bmp`). Tool description and README say which.
  - crapper all below 10; dryer nothing new; mutator on `McpTools` 107 of 108 killed, the survivor is the known equivalent of step 59 (`create_folder`'s create-only `false`).
  - **Found in use (2026-11-26):** an agent in a chat app cannot upload a photo through `upload_file`: it would have to write the photo's base64 itself, millions of characters, and the chat app does not give it the bytes of an attached photo anyway. Planned: upload links (steps 75–78).
## Upload links (planned 2026-11-26)
*Requested 2026-11-26:* agents in a chat app (Claude on the phone) cannot upload photos or documents over MCP (base64 is no way for a model, and it never has the bytes of a photo attached in the chat). MCP is not for binary uploads; the workaround: Mizoine gives a temporary HTTP upload address.

**How it is used:**
1. In the chat: "save this photo of the receipt in receipts/". The agent calls `create_upload_link(repository, folder, name?)`.
2. Mizoine answers with a link, e.g. `https://mizoine.example.org/.upload/k3J9…/`, valid 15 minutes. The agent shows it in the chat.
3. The person taps it on the phone: a small page with one button (the phone's picker: camera, gallery, files), no login. The photo goes straight from the phone to Mizoine, never through the chat.
4. The agent sees the file with `list_folder` and works with it (`read_file` shows a picture to the model, step 74).

The same link takes `curl -T photo.jpg <link>photo.jpg`, for any program that has the file (e.g. a code sandbox of the chat app, where its network allows Mizoine).

**Principles kept (BACKLOG):** plain files (the upload is an ordinary file in the folder, uncommitted, for `git diff`); one write path (`RepositoryFiles.write`, with its checks); the link only adds who may write, for a short time.

### 75. Upload links: the model  ☑
- `UploadLink`: a token (256 random bits, base64url), the user who made it, repository, folder, an optional fixed file name, the expiry, files left. In memory (D85).
- `UploadLinks`: create (limits of D84), find a valid one, count a use, forget expired ones. Time from a `Clock`, so tests need no waiting.
- Plain unit tests: valid until the expiry, not after; used up after its files; a fixed name takes one file; at most 20 open links per user; an unknown token and an expired one are the same "no link"; tokens differ and are never logged (only a short id).
- *Done 2026-11-27 (ub-mode):* package `uploads`: `UploadLink` (record; `toString` shows the id, never the token) and `UploadLinks` (in memory, every method synchronized: one writer). Token 32 random bytes in base64url (43 characters), a separate 8-hex id for logs. `reserve` takes a file before the write, `release` gives it back if the file is not written. **Found by its test:** a link was forgotten as soon as its last file was reserved, so a refused last upload could not give it back; now a used-up link stays until it expires, but is not found and does not count as open. 7 unit tests with a test clock.

### 76. The upload page and endpoint  ☑
- `GET /.upload/{token}/`: a page with the target ("Upload to notes/receipts/"), time left, the file picker (several files, or one with a fixed name), a line per file; plain JavaScript as the folder page (`folder.js`'s name check and NFC, D78).
- `PUT /.upload/{token}/{name}`: the body is the file, stored through `RepositoryFiles.write` **as the link's user**, so the grants are checked at upload time (D86); never over an existing file (412, as `If-None-Match: *`); `mizoine.max-file-size` as for every write (413).
- No login; the token is the permission. CSRF is not needed there (nothing a session allows; a cross-site page would need the token): only `/.upload/**` is exempt.
- Headers: `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, `X-Robots-Tag: noindex`; the strict CSP as everywhere.
- An unknown, expired or used-up token, a name refused, a grant gone meanwhile: 404 with a page "This upload link is not valid (any more). Ask for a new one." Nothing is read or listed through a link: only the files uploaded with it are named on its page.
- Tests: upload as the link's user; a folder outside the user's grants refused when the grant was removed after the link was made; never overwrite; fixed name; files left; expiry (with the test clock); 413; curl-style `PUT` without the page; CSRF exempt only here; headers; the page under the strict CSP; MockMvc and an end-to-end flow in Node as for the folder page.
- OpenAPI: both endpoints.
- *Done 2026-11-27 (ub-mode):*
  - `UploadLinkController`: `GET /.upload/{token}/` (page `upload.html`, script `ui/upload/page.js` with `folder.js`'s `FolderActions` and `uploadOne`, never replacing) and `PUT /.upload/{token}/{name}` (201, 404, 412, 413). `UploadLinkService.upload` writes through `RepositoryFiles.write` as the link's user with `Precondition.CREATE_ONLY`; a refused file gives its use back.
  - Security: `/.upload/**` permitted without login, CSRF not asked there (`PathPatternRequestMatcher`; the first try with `getServletPath()` did not match under MockMvc, where the servlet path is empty).
  - **Gap found by a test:** without login, every other request below `/.upload/` (a `GET` of a file, `DELETE`, a move) fell through to the repository handlers with no user and failed with 500. A catch-all now answers 404: nothing is read, listed or changed through a link.
  - Headers `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, `X-Robots-Tag: noindex` on every answer below `/.upload/`.
  - Tests: `UploadLinkPageTest` (8: no login, as its user; never overwrite or read; one name, a refused file does not count; a folder in the way, the folder gone; 15 minutes with a test clock; the grant checked again; only where the user may write; 20 per user) and `UploadLinkEndToEndTest` (`e2e/upload-link-flow.mjs`: link over HTTP Basic, then the real `folder.js` without cookie or token; asked to replace, still 412).

### 77. Creating links: MCP and HTTP  ☑
- MCP tool `create_upload_link(repository, folder, name?)`: only where the agent may write (as `write_file`); the answer is the link, its expiry and a sentence to pass on ("Open this link on your phone and choose the photo"). The description says to use it for photos, scans and documents the person has, instead of `upload_file`.
- HTTP `POST /{repository}/{folder}/?upload-link` (JSON `{"name": …}` optional) → `{"url", "expires"}`: the same rule, for API clients and tests (D87).
- The absolute URL from `mizoine.public-url` (as for OAuth); without it, the tool says it cannot make links (D89).
- Tests: as the agent only in its folders; no link above a grant; the URL from `public-url`; the limits of D84 answered with a message.
- README: a section "Upload links"; the MCP tool list.
- *Done 2026-11-27 (ub-mode):*
  - `POST /{repository}/{folder}/?upload-link` (hidden: one operation per path and method, described on the move, pinned in `OpenApiDocumentationTest`). As every `POST` without a `Destination`, it needs `Content-Type: application/json` to be free of CSRF (a cross-site form cannot send it); the body may be empty.
  - MCP `create_upload_link(repository, path, name?)`: the answer is the link and what to tell the person. `McpTools` gets the `UploadLinkService`.
  - `RepositoryFiles.checkWritableFolder` (the folder exists and the user may write in it, also through a link) and `isFileName`.
  - The URL from `mizoine.public-url` (trailing `/` removed); without it 409 / an error saying so.
  - The tool was written test first together with its code (not run red on its own).

### 78. Live check on the phone  ☑
- Claude on the phone, connected to Mizoine (step 35): "save this receipt in …", tap the link, take a photo, the agent finds it with `list_folder` and reads it. Notes in this step.
- *Done 2026-11-27 (by the user, live):* files uploaded from the phone through a temporary link. The reconnect after a restart showed the Connect problem of step 35 again: fixed there (2026-11-27).
- *Before (2026-11-27): to be done on the phone by the user.* Covered without the phone: the end-to-end flow on the real server (link as the agent's tool makes it, upload without login, never overwrite). To check: `mizoine.public-url` set on the server; reconnect the connector if Claude does not list `create_upload_link`; the camera picker on the phone; the photo found and shown by the agent.
- *Tools for steps 75–77:*
  - crapper: all below 10. `UploadLinkService.write` was 13.7 (CC 11, 72 %): the impossible results under one `default`, the name rule in `takes`, two tests (a folder in the way, the folder gone): now 8, 100 %.
  - dryer: nothing new.
  - mutator: no survivors (`UploadLinks`/`UploadLink` 26, the service and controller 16, `RepositoryFiles` 19, `McpTools` 124, `SecurityConfig` 17). Uncovered: annotation texts; `McpTools`' "too many" answer got a test, and so did the HTTP answer without a public address (409, no mutant there).

## Glossary

| Term | Meaning |
|---|---|
| Repository | A folder on disk described by `<name>.yaml` in the repositories folder; the name is the first URL segment. |
| Root | The repository's folder (`path` in its file). Nothing outside it is ever reachable. |
| Grant | A folder a user may read and write, with everything below it (`access` in the repository file). `/` = whole repository. |
| Above a grant | Folders on the way down to a grant: may be entered, show only the way down, no files, read-only (D4). |
| Hidden name | Starts with `.` or contains `%`: never listed, read or written (D3, `FolderNames`). The one exception is a folder's tags file `.tags`, a text file like any other; a folder may not have that name (D81). |
| Text format | A file name the editor opens: a known extension or whole name in `ui/editor/text-formats.json`, the one list for the server and the editor (D73). Everything else is not editable. |
| Editable / writable | In a folder listing: the files of a text format, and whether the user may write in the folder (steps 68, 71). |
| Upload link | `/.upload/<token>/`: takes files into one folder without login, as the user who made it, for 15 minutes and at most 10 files; never reads or overwrites (D82–D89). |
| Plain name | A single visible name: not hidden, not empty, no separator, no NUL (`FolderNames.isPlain`). |
| Folder URL / file URL | Ends with `/` / does not (D5). |
| Version | Size + modification time of a file (`FileVersion`); changes with every write, also outside Mizoine. |
| ETag | The version as HTTP header; `If-Match` with it protects against lost updates (D13). |
| Target | What a URL below a repository leads to: problem page, redirect, folder or file (`RepositoryController.Target`). |
| Agent | A user in `users.yaml` with grants to a few folders, used by an AI (BACKLOG §7). |
| Move | A file or folder to a new path in the same repository, in one atomic step; never overwrites (D29–D31). |
| Git access | The grant `.git` (or `/`): the Git page and API of a repository, for the whole repository (D33). Never the `.git` folder as files. |
| Git page | `/{repository}/.git/`: status, stage / unstage, commit, pull, push (steps 43–48). |
| Git action | Stage, unstage, commit, pull or push from the Git page or its JSON API (`GitActions`): starts from the status, so it names only files of the status; done, or refused with the reason. |
| Short ID | 5 characters `0-9a-z` at the start of a file or folder name, followed by the end, `.`, a space, `-` or `_` (steps 49–54, D39–D41). |
| Permalink | `/{repository}/.id/{id}`: leads to the object with this short ID, wherever it is now (step 50). |
| Search index | Lucene's index of one repository's files, outside the repositories; derived from the files, may be deleted, built anew on request (D48, D60). |
| Index state | `not-indexed`, `needs-reindexing`, `reindexing`, `indexed` or `failed`, with the dates of the index, the newest file and the last commit (D49, D59). |
| Search area | Where a user may search in a repository: the granted folders, inside which everything may be read (`RepositoryFiles.SearchArea`, D54). |
| Strictness | How a search found its results: `all-words`, else `any-word`, else `similar-spelling`, else `none` (D53). |
| Tag | A word given to a folder, kept in its `.tags` file (D64–D66); `key:value` tags such as `status:open` hold one value per key (D67). Tags of files: later (D72). |
| `.tags` | The hidden plain-text file of a folder with the folder's own tags (D64, D65). |

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
- **D26 — Preact + htm or Lit:** **Closed 2026-11-26 by D79: no library.** (Was: to decide in step 36.) Preact + htm: React-style components and state, ~5 kB. Lit: web components, a browser standard, ~6 kB. Both run without build and under the strict CSP.
- **D10 — showing files in the browser:** **Implemented 2026-11-23 as proposed (step 28):** inline only for types a browser does not execute (images, PDF, plain text, …), always with `CSP: sandbox` and `nosniff`; `attachment` for HTML, SVG, XML, JavaScript and unknown types. Alternative: a separate domain for downloads.
- **D11 — CSRF for writes:** **Implemented 2026-11-23 as proposed (step 29), limited to PUT and DELETE:** token required for session-cookie requests (the UI), not for requests with an `Authorization` header (agents, curl). Cross-site forms cannot send PUT or DELETE, and there is no CORS. Alternative: a token endpoint that API clients must call first.
- **D12 — maximum file size:** **Implemented 2026-11-23 as proposed (step 30):** `mizoine.max-file-size`, default 100 MB (nginx at the server has `client_max_body_size 100M`).
- **D13 — concurrent changes:** **Implemented 2026-11-23 as proposed (steps 30, 32):** `ETag` from size and modification time; `If-Match` / `If-None-Match: *` honoured, not required. Alternative: require `If-Match` for every replace (safer, but more work for every client).
- **D14 — missing parent folders:** **Decided 2026-11-19:** 409; folders are created only explicitly (`PUT …/`, step 31). The same rule as WebDAV (`PUT` and `MKCOL` answer 409 when the parent is missing). Not chosen: create parents automatically, as the Solid Protocol does (handier for agents, but a typo creates a new folder tree).
- **D15 — deleting folders:** **Decided 2026-11-19:** only empty folders; with content → 409, as the Solid Protocol. Not chosen: recursive delete, as WebDAV `DELETE` (one request removes a whole tree; Git keeps the history only of what was committed). If WebDAV is added later, its recursive `DELETE` needs a decision of its own.
- References for the folder API: WebDAV ([RFC 4918](https://www.rfc-editor.org/rfc/rfc4918)) and the [Solid Protocol](https://solidproject.org/TR/protocol). Mizoine takes from each the part that matches D14 and D15. `PUT …/` instead of WebDAV's own `MKCOL` method: Spring MVC and Spring Security's firewall accept only the standard HTTP methods, and OpenAPI 3.1 cannot describe others. `MKCOL` can be added as an alias when WebDAV comes.
- **D16 — MCP library:** **Decided 2026-11-23 (step 33):** the official MCP Java SDK 2.2.0 directly (`mcp-core`, `mcp-json-jackson3`), stateless servlet transport. Not chosen: Spring AI 2.1 (a framework for one endpoint).
- **D17 — OAuth clients:** **Decided 2026-11-23, changed 2026-11-24 (option 1, requested):** one client per chat app, configured as a list `mizoine.oauth.clients` (id, secret hash, redirect URIs): each app can be shut out on its own, no secret is shared between vendors. No open registration. Before: exactly one client. Not chosen: dynamic client registration (any app registers itself; an open endpoint to protect).
- **D18 — token storage:** **Decided 2026-11-23 as proposed (step 34):** in memory, so no database (BACKLOG principle 1); after a restart the connector logs in again. Not chosen: a token file. **Confirmed 2026-11-27:** the login on reconnect after a restart is wanted, not a gap: each new Connect is evidence that the person still has the agent user's access (D19). Only one login, on Mizoine's page that Connect opens (step 35, 2026-11-27).
- **D19 — who logs in during "Connect":** **Decided 2026-11-23 as proposed (step 34):** the agent user itself (its password is entered once in the phone's browser). Not chosen: the human logs in and picks which agent user the connector acts as (more code: a consent page with a choice).
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
- **D29 — HTTP form of a move:** **Decided 2026-11-24 as proposed:** `POST` on the source URL with a `Destination` header (WebDAV `MOVE` semantics, standard methods only: Spring MVC, the firewall and OpenAPI 3.1 accept it; a `Destination` header cannot come from a cross-site form). Not chosen: WebDAV `MOVE` itself (needs firewall and Spring changes, not describable in OpenAPI 3.1; can be an alias later, like `MKCOL`); `POST` with a JSON body `{"to": …}` (own format).
- **D30 — overwriting at the destination:** **Decided 2026-11-24 as proposed:** never; an existing destination gives 412, the client deletes it first if wanted. Not chosen: WebDAV's `Overwrite: T` (one request replaces a file or a whole folder tree).
- **D31 — between repositories:** **Decided 2026-11-24 as proposed:** no, same repository only (roots may be on different disks, then no atomic move; different access rules). Not chosen: copy + delete across repositories, not atomic.
- **D32 — links pointing to a moved file:** **Decided 2026-11-24 as proposed:** not changed (plain files: Mizoine does not rewrite content it was not asked to write). Later, maybe a separate "find links to …" tool for agents.
### Git in the browser (steps 43–48) — decided 2026-11-24
Proposals; T4 in [docs/technology-decisions.md](docs/technology-decisions.md).
- **T4 — how Mizoine runs Git:** **Decided 2026-11-24 as proposed:** the `git` program on the server (as a human would), with a fixed argument list, no shell, a time limit. Not chosen: JGit, a Java library (no `git` needed, but its own behaviour, and SSH or credential setup separate from the server's).
- **T4.4 — Git for agents (MCP):** **Decided 2026-11-24 as proposed:** none for now (BACKLOG §4: a human reviews and commits). Possible later: a read-only `git_status`. Not chosen: commit and push as MCP tools.
- **D33 — what Git access covers:** **Decided 2026-11-24 as proposed:** the whole repository. The status shows all changed files, and a commit or push takes everything; filtering by file grants would look safe without being safe, since a push publishes all commits. Not chosen: status and staging limited to the user's file grants.
- **D34 — which repositories:** **Decided 2026-11-24 as proposed:** only those whose root is the top of a Git working tree; others say "not a Git repository". Not chosen: a repository in a subfolder of a Git working tree (its commit would take files outside the repository).
- **D35 — the page:** **Decided 2026-11-24 as proposed:** a server page with plain forms (like the folder page), JSON at the same URLs for API clients. Not chosen: a static page under `/ui/` with JavaScript, like the editor (needs D26 first).
- **D36 — status format:** **Decided 2026-11-24 as proposed:** `git status --porcelain=v2 --branch -z`, Git's stable machine format. Not chosen: the human-readable `git status` output (changes between Git versions and languages).
- **D37 — pull and push:** **Decided 2026-11-24 as proposed:** pull only fast-forward, push only the current branch to its upstream, never forced; everything else by hand on the server. Not chosen: merge or rebase from the browser (conflicts need a UI of their own).
- **D38 — the commit's author:** **Decided 2026-11-24 as proposed:** the Mizoine username, with an optional `email` per user in `users.yaml` (else `<username>@mizoine`); committer from the server's Git settings. Not chosen: always the server's identity (the history would not say who committed).
- **Later, not planned yet:** a diff of a file (only for users who may read it), discard changes, branches, history.

### Stable URLs (steps 49–54) — postponed 2026-11-25 (proposals kept as a draft)
- **D39 — where the identity lives:** *Proposed:* in the name, as a short ID at its start: visible, moves with the file in every tool, works for every file type. Not chosen: an `id:` in Markdown front matter (only Markdown, every file must be read to find one); an index file of IDs (a second truth that goes stale when files are moved by hand); Git history alone (only committed moves, see D46).
- **D40 — the ID's form:** *Proposed:* the old Mizoine's short ID (5 characters `0-9a-z`, minutes since 2017-01-01 UTC, sortable): short to type, IDs of old data stay valid. Not chosen: a readable date-time `2026-11-25-1432` (15 characters, also sortable); a random ID (not sortable).
- **D41 — which names have an ID:** *Proposed:* 5 ID characters at the start, followed by the end of the name, `.`, a space, `-` or `_`. Not chosen: an ID anywhere in the name (more false matches); a fixed separator only.
- **D42 — the address:** *Proposed:* `/{repository}/.id/{id}`: a dot name is never a real file or folder (D3), so it cannot clash, like `.git`. Not chosen: `/{repository}/@{id}` (a real folder could be named `@…`); one address for all repositories, `/.id/{id}` (IDs are unique per repository only, and the repository name tells where to search).
- **D43 — several objects with one ID:** *Proposed:* 300 with the list (HTML or JSON); the user chooses. Not chosen: the first found (which one depends on the file system's order); an error.
- **D44 — the redirect:** *Proposed:* 302, the query string kept. Not chosen: 301 (browsers keep it forever, but the target moves); answering with the object itself on the permalink (relative links in a Markdown page and in folder pages need the real path).
- **D45 — finding an ID:** *Proposed:* search the folders on each request, no index (plain files, nothing to go stale); an index only if a real repository proves too slow, and then rebuilt from the files. New IDs are not reserved.
- **D46 — old paths from Git history:** *Proposed:* yes, as its own step (54) after the ID steps, independent of them: it serves links to names without an ID. Only committed renames.
- **Not planned:** stable URLs across a renamed repository (its name is the first part of every URL: keep repository names stable); rewriting links in other files after a move (D32 stays).

### Search (steps 55–60) — decided 2026-11-25
Details of T5 in [docs/technology-decisions.md](docs/technology-decisions.md).
- **T5 — search engine:** **Decided 2026-11-25:** Apache Lucene 10.5, embedded (the installation is complicated enough; no second server): one jar, no second program to run and keep in sync. German normalization, stemming, synonyms, BM25, fuzzy queries and snippets are built in. Not chosen:
  - Meilisearch or Typesense: very good typo tolerance, but a second server, and each file change must be sent to it;
  - OpenSearch or Elasticsearch: Lucene inside a large server;
  - SQLite FTS5: simple, but no German normalization or stemming, no fuzzy search.
- **D47 — who may search:** **Decided 2026-11-25:** everyone, with no grant of its own; the results are only what the user may read. Searching reveals nothing that reading does not. Not chosen: a grant `.search`, like `.git` (first requested, then dropped: no protection, one more thing to configure).
- **D48 — where the index lives:** **Decided 2026-11-25 as proposed:** outside the repositories, `mizoine.search.index-dir` (default `search-index` in the start directory). It is derived, may be deleted, and is built anew on request (D61). Not chosen: `.mizoine/` inside each repository (would show up in Git status, and in backups of the repository).
- **D49 — noticing changes by hand:** **Decided 2026-11-25:** nothing runs by timer. The index state shows its own dates (`indexedAt`, `updatedAt`) beside the newest file's date (file system) and the last commit's (Git); the user decides, and asks for an update (changed files only) or a reindexing. Mizoine's own writes still update the index at once. Not chosen: a rescan every 5 minutes (work on its own, also when nothing changed); Java's `WatchService` (misses events on some file systems, e.g. network drives, WSL).
- **D50 — what is indexed:** **Decided 2026-11-25:** the content of text files up to 2 MB, and the names and paths of all files and folders. PDF, JPEG, EML and the like are extracted by the import layer into a Markdown file beside them, which is then indexed as text. Not chosen: text extraction inside the index (Apache Tika: large, and the extracted text would be visible only to search, not as a file).
- **D51 — languages:** **Decided 2026-11-25:** English, German, French and Russian (`mizoine.search.languages`, default `en,de,fr,ru`; others optional, from Lucene's stemmers). One shared normalized field (German spellings, accents, French elision, `ё` = `е`) and one stemmed field per language, all searched at once. Not chosen: detecting the language of each document (more complex, often wrong for short or mixed notes); a language setting per repository; transliteration between alphabets (`Москва` = `Moskva`: needs ICU, a large library; can come later).
- **D52 — synonyms:** **Postponed 2026-11-25:** not in steps 55–60; to be planned later. Ideas kept for then: an optional plain file of your own (Solr format, e.g. from OpenThesaurus), applied at search time, so editing it needs no reindexing.
- **D53 — strict and loose:** **Decided 2026-11-25 as proposed:** all words, then any word, then similar spelling; only when the stricter level finds nothing; the result says which level. Simple query syntax only. Not chosen: always fuzzy (too many weak results); full Lucene syntax (fields, regex: heavy queries).
- **D54 — permissions:** **Decided 2026-11-25 as proposed:** filtered inside the query (the user's readable folders) and every result checked again before it is shown. The index stores files at their path in the repository and does not follow symbolic links. Not chosen: one index per user (grants change while running).
- **D55 — semantic search (by meaning, with embeddings):** **Postponed 2026-11-25:** not in steps 55–60; to be planned later. Notes for then: Lucene can do it (vector fields) without changing the engine, but it needs an embedding model: in the jar (large) or an external AI service (documents would leave the server).
- **D56 — who may reindex:** **Decided 2026-11-25 as proposed:** users with the whole repository (`/`), as for the details of a broken repository file (D8): a rebuild costs the server time, and only they see the whole repository anyway. Not chosen: everyone who may read (anyone could keep the server busy); a separate admin role (none exists yet).
- **D57 — searching during a reindexing:** **Decided 2026-11-25 as proposed:** on the old index, if there is one that can be read, until the new one is complete, then switched at once; the answer says the index is being rebuilt. Not chosen: no results while it runs.
- **D58 — index after an upgrade:** **Decided 2026-11-25 as proposed:** one folder per index format (Lucene major + Mizoine's analysis version). A missing, unreadable or mismatching index is `needs-reindexing`, not rebuilt on its own; reindexing builds the new folder beside the old one, the others are deleted after success. Not chosen: `lucene-backward-codecs` and Lucene's `IndexUpgrader` (only one major version back, and no help when Mizoine's own analysis changed); asking the administrator to delete the index by hand.
- **D59 — the index state:** **Decided 2026-11-25 as proposed:** `not-indexed`, `needs-reindexing` (reason `format` or `damaged`), `reindexing`, `indexed`, `failed`, with the dates of D49; in its own request and in every search answer. Mizoine does not claim "up to date": the dates tell (D49). Search without a usable index: 503 with the state, not an empty result (which would read as "nothing found").
- **D60 — one index per repository:** **Decided 2026-11-25 as proposed:** yes: the state, the format and the reindexing belong to one repository; reindexing one leaves the others alone; a removed repository's index is simply its folder. Not chosen: one index for all repositories (one format, one reindexing for everything).
- **D61 — what runs on its own:** **Decided 2026-11-25 as proposed:** only cheap things: the format check at startup and Mizoine's own writes. An update or a full reindexing only on request (requested 2026-11-25: no big startup, no timer). Not chosen: reindexing automatically at startup or after an upgrade; a setting to switch that on (can be added later).
- **D62 — update and reindexing:** **Decided 2026-11-25 as proposed:** both on request: an update indexes only new, changed and deleted files (by version), a reindexing builds everything anew beside the old index. Not chosen: reindexing only (slow after a small change by hand); an update after each pull through Mizoine (couples the Git and search layers).
- **D63 — email addresses and file names:** **Decided 2026-11-25 as proposed:** two more fields: `tokens` keeps every word whole as written between spaces, punctuation at its ends removed, so email addresses, URLs and file names stay whole (simpler and more predictable than Lucene's `UAX29URLEmailTokenizer`, which knows emails and URLs but not file names); `name` holds each file and folder name whole and in its parts (stem, extension, `-` `_` `.`, camelCase). A whole match ranks first. A query word with `@` or `.` is searched whole and in its parts. Not chosen: a field syntax such as `ext:pdf` (D53: no fields); can come later as a filter parameter (`type=pdf`).
- **Later, needs its own plan — dates:** finding dates written in different ways (`25.11.2026`, `2026-11-25`, `25. November 2026`, `Nov 25`), ranges ("November 2026"), and the dates a file has: its modification time, the date in its short ID (steps 49–54), the date of its last commit. Needs: which formats and languages, where a date comes from, how a range is asked for.

### Tags and statuses (steps 61–65) — decided 2026-11-25
- **D64 — where tags live:** **Decided 2026-11-25:** folders only for now, in the folder's own `.tags` file; it moves with the folder, also by hand. Tags of files: postponed (D72). Not chosen: one tags file per repository; tags in Markdown front matter (D71).
- **D65 — the file's format:** **Decided 2026-11-25 as proposed:** plain text, tags separated by spaces, commas or line breaks, an optional leading `#`; Mizoine writes one per line. The simplest a human can type, and fit for the sidecar files later. Not chosen: YAML (a list needs brackets or dashes; nothing else to say in it now that it holds only the folder's tags).
- **D66 — what a tag may be:** **Decided 2026-11-25 as proposed:** letters of any alphabet, digits, `-`, `_`, `/`, one `:` (D67); no spaces; at most 50 characters and 30 tags per folder; case kept as written, compared without case. Not chosen: tags with spaces (need quoting in queries and in `#…`).
- **D67 — statuses:** **Decided 2026-11-25 as proposed:** tags `key:value`, at most one value per key: `status:done` replaces `status:open`. No fixed values.
- **D68 — who sees and changes tags:** **Decided 2026-11-25 as proposed:** see with read access in the folder, change with write access in it; folders shown only on the way to a grant (D4) show none.
- **D69 — the API's form:** **Decided 2026-11-25 as proposed:** `?tags` on the folder's URL (`GET`, `PUT`, `ETag` / `If-Match`); `/{repository}/.tags/` for the vocabulary, from the search index (D71).
- **D70 — deleting a tagged folder:** **Decided 2026-11-25 as proposed:** a folder that holds only `.tags` counts as empty, and its `.tags` is deleted with it; otherwise tagging a folder would make it undeletable through Mizoine. Any other content still blocks the delete (D15). Moving needs nothing: `.tags` is inside.
- **D71 — the vocabulary:** **Decided 2026-11-25 as proposed:** from the search index (step 65): it has every tag anyway, counted per user's readable folders in milliseconds. Without a usable index, 503 with the state, as search. Not chosen: reading all `.tags` files on each request (a walk of the whole repository); a list of allowed tags (a configuration of its own). Markdown front matter `tags:` (Obsidian) is not read; possible later as a read-only source.
- **D72 — tags of files:** **Postponed 2026-11-25:** to plan later. Favourite: a sidecar file beside the file, `my_doc.md.tags`, in the same format as `.tags` (D65); maybe a hybrid. To think through then: how a sidecar moves and is deleted with its file (by Mizoine, and by hand), how it shows in folder listings and Git, and whether front matter counts too.

### Uploads and editable files (steps 66–74) — decided 2026-11-26
- **D73 — what counts as a text file for the editor:** **Decided 2026-11-26 (simplified on request):** by name only. Only known text formats are editable: a list of extensions and whole names (`Makefile`), in one file that the server and the editor share (step 68); all other files are not, and no file is read to decide. A text file with an unknown name is downloaded; adding its format is one line in the list. The editor's check on load (step 69) only guards against a misnamed file being destroyed. Not chosen: by name, else by the first 8 KB of content (finds unknown text files, but reads files for a listing and is a second rule).
- **D74 — what the listing says, and read-only places:** **Decided 2026-11-26:** new fields `editable` (file names) and `writable` (this folder); `files` unchanged, so API clients keep working. An editor link only for editable files where the user may write; no link otherwise. Viewing needs no editor: the file opens as is from its name. Not chosen: a **View** link to the editor without Save.
- **D75 — highlighting:** **Decided 2026-11-26 (simplified on request):** the editor's highlighters of step 39 for a limited list of types, also for their close relatives (`xsd`, `xslt` as XML, `bash` as shell, …); every other text format is edited as plain text. No new highlighters, no own mode for `.bat`, no rebuild of the bundle. Not chosen: more CodeMirror modes (legacy modes for Python, SQL, PowerShell, …), an own mode for `.bat`.
- **D76 — creating files:** **Decided 2026-11-26 (on request, a security fix):** the editor never creates a file: a missing file is shown as missing, without Save, and every save needs the loaded version (`If-Match`), so a link from elsewhere cannot make a user create a file at a chosen path (step 66, tested end to end). A new file is created only by an explicit **New file** action on the folder page (name + Create, step 71), then edited. Not chosen: the editor creating a missing file on Save (step 37's behaviour until now).
- **D77 — how the browser uploads:** **Decided 2026-11-26:** the simplest that works from the phone: one file picker (`<input type="file" multiple>`, which offers the camera and the gallery there), one `PUT` per file on its URL with `fetch`, never overwriting without asking ("Replace?"). No drag and drop, no progress bar. Not chosen: a `multipart/form-data` `POST` endpoint (a second way to write; `POST` on a URL is already the move, D29).
- **D78 — file names from the browser:** **Decided 2026-11-26:** normalised to NFC before an upload or a rename; macOS may give decomposed names (`u` + `¨`), which on Linux would be another name than the same word typed. The server stores names as given, as now. **Also requested:** rename and delete files in the browser (step 73), with the existing move and delete requests.
- **D79 — UI library (was D26):** **Decided 2026-11-26 as proposed:** none. Plain JavaScript modules, as the editor (step 37 showed that a page needs no library). This closes D26. Preact + htm stays the choice if a page ever needs components with state.
- **D80 — binary files for agents:** **Decided 2026-11-26: the standard MCP way** (step 74). Reading: `read_file` returns MCP's own content types: `image` (base64 with `mimeType`) for pictures, an embedded resource with a base64 `blob` for other files. Writing: a tool `upload_file` with base64 `content`, declared `contentEncoding: base64` in its input schema; MCP has no binary input type, so base64 in the arguments is what MCP servers do. At most 10 MB decoded either way (base64 adds a third; chat apps limit a tool call's size anyway; HTTP stays 100 MB). Not chosen: an `encoding` parameter on `write_file` (one tool doing two things); Mizoine downloading a URL the agent names (it would reach addresses on the server's own network for whoever controls the agent); MCP resources (`resources/read`) for files (fewer clients support them than tools).
  - Not chosen: an `encoding` parameter on `write_file` (one tool doing two things is easier to get wrong); Mizoine downloading a URL the agent names (Mizoine would reach addresses on the server's own network for whoever controls the agent).

- **D81 — `.tags` visible and editable:** **Decided 2026-11-26 (proposed by the user, with these details):** `FolderNames` treats exactly `.tags` as not hidden, the one exception; so it is listed, read, written, edited and moved like any text file. It stays special only where it already is: not as a folder name; not a file of its own in the search index (its words are the folder's tags); served as `text/plain`; `?tags`/`set_tags` remain the checked way, an edit of the file counts as an edit by hand (D65). Not chosen: a separate "show hidden files" switch (shows `.git`, `.env`, …: D3); an editor page of its own for tags (a second editor).
### Upload links (steps 75–78) — decided 2026-11-26
- **D82 — how a file from a chat gets into Mizoine:** **Decided 2026-11-26 as proposed:** a temporary upload link that the person opens in the browser (or a program sends the file to with `PUT`). Not chosen: base64 through MCP (`upload_file`, step 74: stays for agents that have the bytes, e.g. Claude Code; impossible from a chat); Mizoine downloading a URL the agent names (D80: it would reach the server's own network); email to Mizoine (a mail server of its own).
- **D83 — what a link allows:** **Decided 2026-11-26 as proposed:** uploading into one folder, nothing else: no reading, no listing, no overwriting (an existing name → 412). Files keep their own names (NFC, D78), or the one name the agent gave (then exactly one file). Alternative: a link per single file always.
- **D84 — lifetime and limits:** **Decided 2026-11-26 as proposed:** 15 minutes, at most 10 files per link (1 with a fixed name), each up to `mizoine.max-file-size`; at most 20 open links per user. Used up or expired, it is gone. Alternatives: single use always (a second photo needs a new link); longer (the link stays in the chat history).
- **D85 — where links are kept:** **Decided 2026-11-26 as proposed:** in memory, as the OAuth tokens (D18): no database; a restart ends all links (the agent makes a new one). Not chosen: signed links without state (they need a secret key file, and cannot count uses or be ended early).
- **D86 — as whom the file is written:** **Decided 2026-11-26 as proposed:** as the user who made the link (usually the agent), with that user's grants checked again at upload time; the person who taps the link needs no login. In Git the file is an uncommitted change like any agent write.
- **D87 — who may make links:** **Decided 2026-11-26 as proposed:** any user, where that user may write; through MCP (`create_upload_link`) and HTTP (`POST …/?upload-link`). Alternative: MCP only. Note: a person can also hand a link to someone else (a drop box for 15 minutes); the limits of D84 hold.
- **D88 — the token:** **Decided 2026-11-26 as proposed:** 256 random bits in base64url in the path (`/.upload/{token}/`); never written to logs (a short id instead); `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. The link is in the chat's history: the short lifetime is what protects it.
- **D89 — the link's address:** **Decided 2026-11-26 as proposed:** from `mizoine.public-url` (set already where OAuth is used); without it, no links (the tool says so). Not chosen: the request's host (behind a proxy it may be wrong, as for OAuth).

### Browser UI (step 36) — to decide
- **D90 — the frame:** *Proposal:* a top bar on every page: Mizoine, breadcrumbs, search, the user and Log out, the Git link with the number of changes, Copy link. Server-rendered (a Thymeleaf fragment), so it is there without JavaScript; only Copy link needs it. Alternative: no bar, links per page as now.
- **D91 — error pages:** *Proposal:* own 404/500/403 pages in the frame, revealing nothing more than now (D2). Alternative: Spring's default pages.
- **D92 — moving in the browser:** *Proposal:* Rename takes a path relative to the folder (or `/…` from the repository root), shown resolved before it is sent. Alternative: a folder picker (a tree to click through: more code, more requests).
- **D93 — the listing's details:** *Proposal:* size, date, kind as new JSON fields and on the page; `?sort=name|date|size` (default name); folders first. On the phone the actions behind "⋯". Alternative: names only, as now.
- **D94 — tags in the UI:** *Proposal:* the folder's tags editable on its page (checked through `?tags`), shown on its Markdown pages; suggestions from the vocabulary. Alternative: tags only through the `.tags` file (as now).
- **D95 — QR code for upload links:** *Proposal:* yes, drawn in the browser by a small vendored library (MIT, no build, e.g. `qrcode-generator`, about 20 kB, in `ui/vendor/` with its license and version, as CodeMirror). Alternative: drawn on the server (a Java library, e.g. ZXing); no QR, only the link to copy.
- **D96 — editor shortcuts:** *Proposal:* Ctrl+S / ⌘S only, nothing else (CodeMirror's own keys stay). Alternative: none.
- **D97 — look:** *Proposal:* system fonts, light/dark by the system setting, no theme switch. Alternative: a switch kept per browser (would need `localStorage`; allowed only because the look is no content and no navigation).

- **Later, not planned yet:** copy, live Markdown preview in the editor, Obsidian `[[wiki links]]`, links relative to the repository root (`/…`), syntax highlighting of code blocks in the page, per-grant read-only permissions.
