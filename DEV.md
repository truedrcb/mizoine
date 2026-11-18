# Mizoine 1.0 — Development Plan

**Status** (updated: 2026-11-18): steps 0–19 done (250 tests green). Next: step 20 (safe paths inside a repository), then 21–22.

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
| R7 | `/{repository}/` answers with success (a small "ok" page) when the repository exists and the user has access |
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
| HTTPS | **nginx** in front (TLS), the application speaks plain HTTP on `127.0.0.1` (step 12) | Certificates and TLS handled by a standard tool |
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
- Repository name: from the file name `<name>.yaml`; letters, digits, `.`, `_`, `-`; not starting with `.`; case-sensitive. Names used by the application itself (`login`, `logout`, `error`, `default-ui.css`, `favicon.ico`) are rejected.
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
  - Not limited by nginx: password guessing over HTTP Basic (any path). *Done in step 13 (in the application).*

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
  - `RepositoryNames.isValid` is separate, for the lookup by URL in step 16. Reserved: `login`, `logout`, `error`, `default-ui.css`, `favicon.ico`.
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
  - `/{repository}` redirects to `/{repository}/` for every valid name (reveals nothing); invalid names (e.g. `/favicon.ico`) → 404 without redirect.
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

### 20. Safe paths inside a repository  ☐
Before anything below the root is exposed.
- **Red** — `RepositoryPathsTest` (unit, `@TempDir`) and a few MockMvc cases:
  1. `a/b` resolves inside the root
  2. `..`, `a/../../x`, URL-encoded `%2e%2e`, `%2f`, backslashes, NUL → rejected (404 over HTTP; Tomcat or Spring Security's firewall may already answer 400 for some — the test accepts 400 or 404, never 200)
  3. a symbolic link pointing outside the root → not followed, treated as not existing
  4. `.git` and other names starting with `.` → not visible, not reachable (D3)
  5. not existing folder → 404; a file instead of a folder → 404 for now (files come later)
- **Green:** `RepositoryPaths.resolve(root, relative)` using `normalize()` + `toRealPath()` and a check that the result is still under the root's real path.

### 21. Subfolder listing: JSON and HTML  ☐
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

### 22. Documentation  ☐
- `repositories.example/notes.yaml` (committed, checked by a test like step 10); real repository folder git-ignored if kept in the project.
- README: already describes `mizoine.repositories-dir` and the file format (2026-11-18); add URLs and a JSON example (`curl -H 'Accept: application/json' -u artem …/notes/`) once they work.

---

## Open decisions

- ~~Step 3.8: ignore or reject unknown fields in `users.yaml`?~~ **Decided:** ignore them and log a warning.
- ~~Step 4.3: case-sensitive usernames?~~ **Decided:** yes, case-sensitive.
- Roles: everyone is `USER` for now. AI-agent role from BACKLOG §7 comes later as a new field `role` in `users.yaml`.
- Sessions of users removed from `users.yaml` stay valid until they expire (step 9): add invalidation if needed.
- Run the dependency check once an NVD API key is available (step 8).
- ~~Rate-limit HTTP Basic login attempts~~ **Done:** step 13.

### Repositories and API documentation (steps 14–22) — decided 2026-11-18
- ~~Load all repository files at startup?~~ **Decided 2026-11-18:** no — lazy lookup per request (step 16).
- **D1 — file format:** `access` as a map `username → [folders]` (shown above). **Decided:** yes — short to write, duplicates impossible. Alternative: a list of `{user, folders}` entries.
- **D2 — no access vs. unknown:** **Decided:** both **404**, so a user cannot find out which repositories or folders exist. Alternative: 403 for "exists, but not for you".
- **D3 — hidden folders:** **Decided:** all names starting with `.` (`.git`, `.mizoine`, …) are never listed nor reachable. Alternative: hide only `.git`.
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
