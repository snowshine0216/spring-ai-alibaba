# SKIPPED items

These P0 entries from `docs/test-analysis/04-gap-report.md` cannot be implemented as passing tests under the current constraint set ("no external middleware", "do not modify source"). They are surfaced here so a follow-up audit can reopen them once the unblock path is taken.

## P0-2 — `NacosClientService.*`

**Blocker**: `NacosClientService` calls `NacosFactory.createConfigService(properties)` and `NacosFactory.createNamingService(properties)` directly in its constructor. Both methods immediately attempt to contact the configured Nacos server. There is no injected factory or seam to redirect.

**Unblock paths**:
1. **Refactor**: introduce a `NacosFactory` adapter interface, accept a mock in tests. Touches production source.
2. **Testcontainers Nacos**: spin up `nacos/nacos-server:v3.0.3` (matches the `${nacos-client.version}`) in CI. Adds ~30s container startup per test class.
3. **Mockito-inline `mockStatic`**: stub `NacosFactory.createConfigService` and `createNamingService`. Requires `mockito-inline` dep + JVM agent.

Recommend path (1) plus an integration test under `@Tag("integration")` running on (2) in CI only.

## P0-11 — `AppMapper.*`

**Blocker**: MyBatis-Plus mapper relying on MySQL-dialect annotations and operator behaviour. H2 in MySQL compatibility mode is insufficient (mismatches around `INSERT … ON DUPLICATE KEY`, JSON columns, and `IFNULL` semantics used elsewhere in the codebase).

**Unblock path**: Testcontainers MySQL 8.0.33 (matches `${mysql.version}`). Add to CI with `@Testcontainers` + `@MybatisPlusTest` slice. Out of scope for this PR because it introduces new test infra.

## P0-12 — `AppVersionMapper.*`

Same reasoning as P0-11. The two mappers share the publish state-machine — a single Testcontainers test class can cover both at once.
