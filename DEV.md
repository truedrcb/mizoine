# Mizoine 1.0 — Development Plan

**Status** (updated: 2026-11-16): steps 0–7 done (59 tests green). Next: step 8.

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
#   htpasswd -bnBC 12 "" 'secret' | tr -d ':\n' | sed 's/$2y/$2a/'
users:
  - username: artem
    password: "{bcrypt}$2a$12$...."
  - username: guest
    password: "{bcrypt}$2a$12$...."
```

Rules:
- `username` must be non-empty and unique.
- `password` must be a hash with an `{id}` prefix. Plain-text values and `{noop}` are rejected at load time.
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

### 8. Security hardening  ☐
- **Red** — tests for:
  1. security headers present (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Content-Security-Policy`)
  2. error responses do not leak stack traces
  3. same response/timing path for unknown user and wrong password (Spring default — assert 401 for both, no different message)
- **Green:** header config, `server.error.include-stacktrace=never`.
- Add OWASP `dependency-check-maven` (or `mvn versions:display-dependency-updates`) to a separate profile `-Psecurity-check`. Not part of default build (slow).

### 9. Reload users file without restart  ☐
Lets a person edit `users.yaml` and have it take effect.
- **Red** — `FileUserDetailsServiceReloadTest` (unit, `@TempDir`, injectable `Clock`/check):
  1. file changed (new user added) → new user can be loaded
  2. user removed → `UsernameNotFoundException`
  3. file changed to invalid content → old users kept, error logged
  4. file not changed → file is not read again (check via counter on a test parser)
- **Green:** re-read when file's last-modified time changes (checked on lookup; no background thread needed).
- Note: already logged-in sessions of a removed user stay valid until they expire — document this, or add a test + session invalidation later.

### 10. Run as a jar + documentation  ☐
- **Red:** none new; `mvn verify` builds an executable jar.
- Add `users.example.yaml`, add `users.yaml` to `.gitignore`.
- Update `README.md`: how to install JDK, generate a password hash, create `users.yaml`, run
  `java -jar target/mizoine-*.jar --mizoine.users-file=./users.yaml`.
- Manual smoke test: start the jar, open `http://localhost:8080`, log in with a configured user, get rejected with a wrong one.

### 11. (Optional) Password hash helper  ☐
- **Red:** `HashPasswordCommandTest`: given a password → output starts with `{bcrypt}` and matches via the encoder.
- **Green:** `java -jar mizoine.jar hash-password` reads password from stdin (no password in shell history) and prints the hash, then exits without starting the web server.

### 12. (Optional, later) HTTPS  ☐
- Either a reverse proxy (Caddy/nginx, recommended) or Spring Boot SSL bundles with a test that the HTTP port redirects to HTTPS.

---

## Open decisions

- ~~Step 3.8: ignore or reject unknown fields in `users.yaml`?~~ **Decided:** ignore them and log a warning.
- ~~Step 4.3: case-sensitive usernames?~~ **Decided:** yes, case-sensitive.
- Roles: everyone is `USER` for now. AI-agent role from BACKLOG §7 comes later as a new field `role` in `users.yaml`.
