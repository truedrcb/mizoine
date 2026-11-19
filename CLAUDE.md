# Working agreement for this project

## Role
Act as an expert Java developer. Build with Java 25, Maven 3.9+ (`mvn`, no Maven Wrapper), Spring Boot (current GA).

## Way of working: strict TDD
1. **Red** — write the test first and run it; confirm it fails for the expected reason.
2. **Green** — write the smallest production change that makes it pass.
3. **Refactor** — clean up with all tests green.
4. A step is done only when `mvn verify` passes. Never mark a step done with failing or skipped tests.
- No production code without a failing test that requires it.
- Unit tests for plain logic (no Spring context); `@WebMvcTest` / `@SpringBootTest` only where the framework is part of what is tested.
- Tests: JUnit 5, AssertJ, MockMvc, `spring-security-test`. Use `@TempDir` for file tests.

## Code style
- `final` everywhere it is possible: fields, local variables, parameters, `catch` parameters, enhanced-for variables. Exception: lambda parameters.
- Prefer constructor injection so fields can be final, in tests too (`MyTest(@Autowired final MockMvc mockMvc)`, `MyTest(@TempDir final Path dir)`).
- Checkstyle ([checkstyle.xml](checkstyle.xml)) enforces locals and parameters in `mvn verify`. Final fields are not checked by the tool; take care of them in code.

## API documentation (OpenAPI)
- Every HTTP endpoint (HTML pages and JSON alike) is annotated for OpenAPI (springdoc): `@Tag` on the controller; `@Operation` with summary; `@Parameter` for path/query parameters; `@ApiResponse` for every status it can return (incl. 401, 404, 500) with media types.
- Write the test first: `OpenApiDocumentationTest` checks only that each endpoint appears in `/v3/api-docs` with a summary (smoke test, no detailed checks; the details are reviewed in `docs/openapi.yaml`).
- `mvn verify` writes the description to `docs/openapi.yaml` (committed). The application itself does not serve it; springdoc is a test dependency only, no Swagger UI (DEV.md step 15, D9).

## Plan and status
- [DEV.md](DEV.md) holds the numbered development steps. The user refers to them by number ("do step 4").
- Work one step at a time. After a step: tick its checkbox (☐ → ☑) and update the status date in DEV.md.
- Record decisions made during a step under that step or in "Open decisions" in DEV.md.
- [BACKLOG.md](BACKLOG.md) holds long-term goals and principles (plain files are the source of truth, thin independent layers). Do not violate them.

## Repo notes
- JDK 25 and Maven are in `~/.local/opt/` and not on `PATH`. Before `mvn`, run `export JAVA_HOME=$(ls -d ~/.local/opt/jdk-25*); export PATH=$JAVA_HOME/bin:$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH`.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.*` packages), not Jackson 2 (`com.fasterxml.jackson.*`).
- Never commit `users.yaml` or any real password hashes; only `users.example.yaml`.
- Commit only when the user asks.
