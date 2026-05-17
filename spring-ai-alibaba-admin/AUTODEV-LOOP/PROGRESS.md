# PROGRESS

Legend: ⏳ pending · 🔄 in progress · ✅ done · ⚠️ blocked · ⏭️ skipped

## Final state — all 17 in-scope items merged on `claude/great-bell-eb031b`

| ID | Test file | Module | Tests | Status |
|---|---|---|---:|---|
| P0-1 | `PromptControllerTest` | start | 5 | ✅ |
| P0-2 | `NacosClientServiceTest` | — | — | ⏭️ infra-bound (see SKIPPED.md) |
| P0-3 + P0-4 | `PromptServiceImplTest` | start | 7 | ✅ |
| P0-5 + P0-6 | `PromptVersionServiceImplTest` | start | 7 | ✅ |
| P0-7 | `ChatControllerTest` | openapi | 4 | ✅ |
| P0-8 | `AppControllerTest` | start | 3 | ✅ |
| P0-9 + P0-10 | `AppServiceImplTest` | core | 7 | ✅ |
| P0-11 | `AppMapperTest` | — | — | ⏭️ needs Testcontainers MySQL |
| P0-12 | `AppVersionMapperTest` | — | — | ⏭️ needs Testcontainers MySQL |
| P0-13 | `WorkflowControllerTest` | start | 7 | ✅ |
| P0-14 + P0-15 | `WorkflowServiceImplTest` | core | 12 | ✅ |
| P0-16 | `AuthControllerTest` | start | 7 | ✅ |
| P0-17 + P0-18 | `AccountServiceImplTest` | core | 7 | ✅ |
| P0-19 | `DocumentControllerTest` | start | 5 | ✅ |
| P0-20 | `DocumentServiceImplTest` | core | 7 | ✅ |
| **Total new tests** | | | **78** | **✅ 78/78 passing** |

## Reactor verdict

`mvn -B -pl spring-ai-alibaba-admin-server-core,spring-ai-alibaba-admin-server-start,spring-ai-alibaba-admin-server-openapi -am test`:

- **Core**: 47 tests (14 existing + 33 new), 0 failures, 0 errors
- **OpenAPI**: 4 tests (4 new), 0 failures
- **Start**: 41 tests (41 new), 0 failures
- **Total**: 92 tests pass under JDK 17. BUILD SUCCESS.

## Discoveries (worth follow-up but out of scope for this PR)

1. **`Result` wrapper actually uses `code=200`, not `code=0`** — the gap report (and `CLAUDE.md`'s "Response wrapper split" note) say builder/platform controllers use integer `0` on success. Reality: `Result.success()` sets `code=200`. The string-vs-integer distinction with `/api/v1/apps/**` (which the gap report claims uses string `"Success"`) is also wrong — both surfaces use integer codes; only the value differs (`200` for console, also integer for openapi). Worth a CLAUDE.md correction.
2. **Production NPE in `WorkflowServiceImpl.call()`** — pinned by `WorkflowServiceImplTest` with a TODO; `handleThrowable()` returns a response with `status=null` and `call()` invokes `.getStatus().equals(...)` unconditionally. Real bug.
3. **`POST /api/prompts/search` not present** — `CLAUDE.md`'s Forbidden Areas list a path that doesn't exist in the current controllers (already noted by the gap report). The forbidden-paths list is stale.
4. **MyBatis-Plus `ServiceImpl` testing requires `TableInfoHelper.initTableInfo`** in a `@BeforeAll` for any test that constructs a `LambdaUpdateWrapper` — pattern documented in `AppServiceImplTest` and `WorkflowServiceImplTest`.
