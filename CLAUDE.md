# Working agreement for this project

## Role
Act as an expert Java developer. Build with Java 25, Maven (use `./mvnw`), Spring Boot (current GA).

## Way of working: strict TDD
1. **Red** — write the test first and run it; confirm it fails for the expected reason.
2. **Green** — write the smallest production change that makes it pass.
3. **Refactor** — clean up with all tests green.
4. A step is done only when `./mvnw verify` passes. Never mark a step done with failing or skipped tests.
- No production code without a failing test that requires it.
- Unit tests for plain logic (no Spring context); `@WebMvcTest` / `@SpringBootTest` only where the framework is part of what is tested.
- Tests: JUnit 5, AssertJ, MockMvc, `spring-security-test`. Use `@TempDir` for file tests.

## Plan and status
- [DEV.md](DEV.md) holds the numbered development steps. The user refers to them by number ("do step 4").
- Work one step at a time. After a step: tick its checkbox (☐ → ☑) and update the status date in DEV.md.
- Record decisions made during a step under that step or in "Open decisions" in DEV.md.
- [BACKLOG.md](BACKLOG.md) holds long-term goals and principles (plain files are the source of truth, thin independent layers). Do not violate them.

## Repo notes
- Never commit `users.yaml` or any real password hashes; only `users.example.yaml`.
- Commit only when the user asks.
