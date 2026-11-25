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

## Quality gates (ub-mode)
- All development follows the `ub-mode` skill (from github.com/unclebob/ub-mode, MIT): First Make it Work, Then Make it Right, Practice. Load it before working on a step.
- The skill lives in `.claude/skills/ub-mode/`, which is git-ignored (`/.claude/`). In a fresh clone, copy `SKILL.md` and `LICENSE` from github.com/unclebob/ub-mode (commit 3da62bf, 2026-11-02) into that folder; `SOURCE.md` there records the commit.
- Where it differs, this file wins:
  - "Commit and Push" in the skill does not apply: commit only when the user asks (see Repo notes), and never push without being asked.
  - "Keep a Log": report the actions and their reasons at the end of a step.
  - "If an honest test is impractical, do not write a bad one" does not lift "No production code without a failing test": it covers only branches inside code that a failing test already requires (e.g. a `catch` for disk errors, a race). Name such untested branches and the reason in the step notes in DEV.md.
- Its tools: crapper (CRAP < 10 per method), dryer (duplication ≥ 0.8 removed), mutator (survivors killed), Python, from github.com/unclebob, Java supported.
  - Installed 2026-11-25 in `~/.local/opt/ub-tools/{crapper,dryer,mutator}` (git clones; mutator needs crapper next to it). Each has its own `.venv`. This Ubuntu has no `pip`/`ensurepip`, so it was installed with PyPA's `pip.pyz` (`~/.local/opt/ub-tools/pip/`): `python3 -m venv --without-pip <tool>/.venv && <tool>/.venv/bin/python -I pip/pip.pyz install -e ./<tool>`.
  - Run from the project root, with coverage from `mvn verify` (`target/site/jacoco/jacoco.xml`, JaCoCo 0.8.16 for Java 25): `crapper --use-existing-coverage --changed`, `dryer --threshold 0.8 --changed`, `mutator --use-existing-coverage --changed`.
  - Mutation testing runs `mvn test` once per mutant: only on changed code (`--changed`).
    - The whole suite per mutant takes hours. Use `--test-command "mvn -q test -Dtest=<the test classes of these files> -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true"` per group of files, and `--max-workers 4`. This is conservative: a mutant that only another test class would catch shows up as a survivor, never as falsely killed.
    - `--changed` misses some modified files: name the changed files explicitly.
    - After editing code, run `mvn clean verify` again before `--use-existing-coverage`: stale line numbers show up as "uncovered".
  - Their snapshots go to `.metrics/` (git-ignored).
- Uncovered new lines get behaviour tests or a stated reason (JaCoCo's line and branch coverage, besides the tools).

## API documentation (OpenAPI)
- Every HTTP endpoint (HTML pages and JSON alike) is annotated for OpenAPI (springdoc): `@Tag` on the controller; `@Operation` with summary; `@Parameter` for path/query parameters; `@ApiResponse` for every status it can return (incl. 401, 404, 500) with media types.
- Descriptions are for API users: what the endpoint does. No dev steps, decision ids (D…, T…), DEV.md, setting names or framework names; `OpenApiDocumentationTest.describesTheApiNotItsDevelopment` checks it. The links to steps and decisions belong in Javadoc and code comments.
- Write the test first: `OpenApiDocumentationTest` checks only that each endpoint appears in `/v3/api-docs` with a summary (smoke test, no detailed checks; the details are reviewed in `docs/openapi.yaml`).
- `mvn verify` writes the description to `docs/openapi.yaml` (committed). The application itself does not serve it; springdoc is a test dependency only, no Swagger UI (DEV.md step 15, D9).

## Plan and status
- [DEV.md](DEV.md) holds the numbered development steps. The user refers to them by number ("do step 4").
- Work one step at a time. After a step: tick its checkbox (☐ → ☑) and update the status date in DEV.md.
- Record decisions made during a step under that step or in "Open decisions" in DEV.md.
- [BACKLOG.md](BACKLOG.md) holds long-term goals and principles (plain files are the source of truth, thin independent layers). Do not violate them.

## Repo notes
- JDK 25 and Maven are in `~/.local/opt/` and not on `PATH`. Before `mvn`, run `export JAVA_HOME=$(ls -d ~/.local/opt/jdk-25*); export PATH=$JAVA_HOME/bin:$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH`.
- Node.js (from nvm, on the PATH) runs the JavaScript tests in `src/test/js/` (`node --test`, also in `mvn verify`). No npm packages for tests; vendor libraries go to `src/main/resources/ui/vendor/`.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.*` packages), not Jackson 2 (`com.fasterxml.jackson.*`).
- Never commit `users.yaml` or any real password hashes; only `users.example.yaml`.
- Commit only when the user asks.
