# MASTER-PLAN — P0 test backlog

## Phase ordering

1. **Pom prep** — add `spring-boot-starter-test` dep to start + openapi modules (core already has it). One commit per module if needed.
2. **Inspect source** — read each impl + controller to capture exact signatures, dependencies, and DTO shapes before writing tests.
3. **Write tests** — group by critical path, write 1–3 test files per batch.
4. **Run + fix** — `mvn -pl … -am test` after each batch; fix compile errors before moving on.
5. **Final validation** — full reactor `mvn test` runs green.
6. **Commit + PR** — one squash-merge PR off the feature branch.

## Branch strategy

Single feature branch: `claude/great-bell-eb031b` (already checked out). All work commits to this branch directly. No sub-branches.

## Workflow rules

- Write license header on every new `.java` file (Apache 2.0; mirror the format used in `PasswordCryptTest.java`).
- All mocks via `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks` — no Spring context unless a controller needs MockMvc.
- Controllers use `MockMvcBuilders.standaloneSetup(controller).build()`. Avoid `@SpringBootTest` — too heavy and pulls in middleware.
- Tests in the same package as the production class (`src/test/java/<same-package>`).
- Name pattern: `<ClassUnderTest>Test.java` (singular).
- Each `@Test` covers exactly one scenario; AAA structure (Arrange/Act/Assert).
- No sleep, no `Thread.sleep`; if streaming is involved, complete the SseEmitter synchronously in the test.
