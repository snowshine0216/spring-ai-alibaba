# AUTODEV-LOOP — P0 test backlog

_Input: `docs/test-analysis/04-gap-report.md`. Generated 2026-05-17._

## Goal

Add the 20 P0 tests listed in the gap report. Each must compile against current source and pass under `mvn test` without external middleware (no live Nacos / MySQL / RocketMQ / ES). Tests follow the in-tree convention: JUnit 5 + Mockito, in `src/test/java/`.

## Bundling decision

**Single PR**, single feature branch (`claude/great-bell-eb031b`). Bundle chosen on user confirmation — 20 test files for one cohesive coverage push is easier to review as a whole than as 20 micro-PRs.

## IN scope (17 items → ~12 test files after consolidating interface/impl pairs)

The gap report splits some items into separate "interface" and "impl" rows (e.g. P0-3 `PromptService.*` vs P0-4 `PromptServiceImpl.*`). For pure-Mockito tests these collapse to one file — mocking the mapper exercises both the interface contract and the impl wiring. Pairs flagged below are collapsed.

| Gap ID(s) | Test file | Module | Approach | CP |
|---|---|---|---|---|
| P0-1 | `PromptControllerTest` | start | MockMvc standalone, service mocked | CP-1 |
| P0-3 + P0-4 | `PromptServiceImplTest` | start | Mockito (`PromptMapper` mocked) | CP-1 |
| P0-5 + P0-6 | `PromptVersionServiceImplTest` | start | Mockito (`PromptVersionMapper`, `PromptService`, `NacosClientService` mocked) | CP-1 |
| P0-7 | `ChatControllerTest` | openapi | MockMvc standalone, services mocked | CP-2 / CP-3 |
| P0-8 | `AppControllerTest` | start | MockMvc standalone, service mocked | CP-4 |
| P0-9 + P0-10 | `AppServiceImplTest` | core | Mockito (`AppMapper`, `AppVersionMapper` mocked) | CP-4 |
| P0-13 | `WorkflowControllerTest` | start | MockMvc standalone, service mocked | CP-5 |
| P0-14 + P0-15 | `WorkflowServiceImplTest` | core | Mockito (runtime + mappers mocked) | CP-5 |
| P0-16 | `AuthControllerTest` | start | MockMvc standalone, service mocked | CP-6 |
| P0-17 + P0-18 | `AccountServiceImplTest` | core | Mockito (`AccountMapper`, `WorkspaceMapper` mocked) | CP-6 |
| P0-19 | `DocumentControllerTest` | start | MockMvc standalone, service mocked | CP-7 |
| P0-20 | `DocumentServiceImplTest` | core | Mockito (mapper + RocketMQ producer + VectorStore mocked) | CP-7 |

## OUT of scope (3 items → see SKIPPED.md)

- **P0-2 NacosClientService.*** — the constructor calls `NacosFactory.createConfigService` and `createNamingService` directly against a real Nacos endpoint, with no seam. Cannot test without either (a) a running Nacos, (b) refactoring `NacosClientService` to accept injected factories, or (c) PowerMock/Mockito-inline static mocking. All three are out of scope for "add tests without changing source".
- **P0-11 AppMapper.*** — MyBatis-Plus mapper with annotation-based queries; meaningful testing requires a real or Testcontainers MySQL. H2 does not support MyBatis-Plus dialect sufficiently for our annotations.
- **P0-12 AppVersionMapper.*** — same reasoning as P0-11.

## Acceptance criteria

1. `mvn -pl spring-ai-alibaba-admin-server-core,spring-ai-alibaba-admin-server-start,spring-ai-alibaba-admin-server-openapi -am test` exits 0 under JDK 17.
2. All new test classes have at least the assertions called out in the gap report's "What to assert" lines.
3. No new external middleware required to run the suite.
4. Apache 2.0 license header on every new Java file (per repo convention).
5. Tests use the existing convention: JUnit 5 + Mockito + AssertJ (already on `spring-boot-starter-test`).
