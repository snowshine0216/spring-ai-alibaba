# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring AI Alibaba Admin (a.k.a. **Agent Studio**) is a Spring Boot + React platform for the full AI agent lifecycle: prompt engineering, datasets, evaluators, experiments, observability, plus apps / plugins / tools / knowledge-bases / MCP servers / model providers. It runs alongside Nacos to push prompts and config to downstream Spring AI Alibaba agent apps. See [README.md](README.md) for the user-facing overview.

## Common Commands

### Local development (Makefile is the canonical entry point — see `make help`)

```bash
make env-start MODE=dev          # Start MySQL / Redis / ES / Nacos / RocketMQ via docker compose
make backend-start               # cd spring-ai-alibaba-admin-server-start && mvn spring-boot:run
make frontend-install            # First-time only: installs node_modules, builds spark-flow, fixes binary perms
make frontend-start              # cd frontend/packages/main && npm run dev   (auto-runs frontend-install if needed)
make env-stop  MODE=dev          # Stop middleware
make env-clean MODE=dev          # Stop + delete volumes (destructive — prompts to confirm)
```

`make local-all-start` prints the three-terminal flow. Backend serves at `:8080`, frontend at `:8000`.

### Backend Maven

Run from this directory (multi-module reactor):

```bash
mvn -DskipTests package                        # Build all four modules
mvn -pl spring-ai-alibaba-admin-server-core -am test                    # Test one module (with deps)
mvn -pl spring-ai-alibaba-admin-server-core -Dtest=DateUtilsTests test  # Run one test class
mvn -pl spring-ai-alibaba-admin-server-core -Dtest=DateUtilsTests#methodName test   # Single method
cd spring-ai-alibaba-admin-server-start && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

There is no aggregate test target — tests live only in `spring-ai-alibaba-admin-server-core/src/test/`.

### Frontend (monorepo at `frontend/`, Umi 4 + React)

```bash
cd frontend && npm install --ignore-scripts    # NEVER run plain `npm install` — husky postinstall fails
cd frontend/packages/spark-flow && npm run build   # Must build BEFORE starting main (it is a workspace dep)
cd frontend/packages/main && npm run dev           # Dev server on :8000 (proxies to backend :8080)
cd frontend/packages/main && npm run build         # Production build
```

Quirks the Makefile handles for you (see `frontend-install` target if doing this by hand):
- `frontend/node_modules/tailwindcss/lib/cli.js` and `frontend/node_modules/cross-env/dist/bin/cross-env.js` need `chmod +x` after install.
- `cross-env` often needs `npm install cross-env --force` to materialize its `dist/`.

### Docker / K8s deployment

```bash
make build-image                                          # Build frontend + backend images
make deploy-compose BUILD_LOCAL=true                      # docker-compose up using freshly-built local images
make deploy-compose REGISTRY=ghcr.io/your-org             # ...or pull from a registry
make deploy-k8s     BUILD_LOCAL=true                      # bash deploy/kubernetes/deploy.sh with locally-built images
make undeploy-compose / make undeploy-k8s                 # Tear down
```

### Configuration

- `spring-ai-alibaba-admin-server-start/model-config.yml` — model provider keys (templates: `model-config-{dashscope,deepseek,openai}.yaml`).
- `application.yml` reads everything via env vars (`SPRING_DATASOURCE_URL`, `NACOS_SERVER_ADDR`, …) — used by docker / k8s.
- `application-local.yml` (activated by `-Dspring.profiles.active=local`) supplies localhost defaults for env-var-free local dev.
- Full env-var matrix: [spring-ai-alibaba-admin-server-start/CONFIGURATION.md](spring-ai-alibaba-admin-server-start/CONFIGURATION.md).

## Big-picture Architecture

### Maven modules (defined in [pom.xml](pom.xml))

```
spring-ai-alibaba-admin/
├── spring-ai-alibaba-admin-server-start    # Spring Boot entrypoint + ALL controllers
├── spring-ai-alibaba-admin-server-core     # Domain services, RAG, agent/workflow runtime, mappers
├── spring-ai-alibaba-admin-server-runtime  # DTOs, enums, constants, exception types (no Spring deps)
└── spring-ai-alibaba-admin-server-openapi  # External /api/v1/apps/** chat/workflow surface
```

Dependency direction: `start → openapi → core → runtime`. The `runtime` module is intentionally framework-free.

**Naming gotcha:** Maven artifacts say `admin`, but Java packages live under `com.alibaba.cloud.ai.studio.*` (legacy "studio" name). When grepping, use the package, not the artifact id. The MyBatis mapper scan in [SaaStudioAdmin.java](spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/SaaStudioAdmin.java) is `com.alibaba.cloud.ai.studio.admin.mapper`.

### Two Spring Boot applications in one JAR

[`SaaStudioAdmin`](spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/SaaStudioAdmin.java) is the main entrypoint. It component-scans all of `com.alibaba.cloud.ai.studio` but **explicitly excludes** `GeneratorApplication`, `GraphProjectGenerationConfiguration`, and `GeneratorApplication.MockLoginController` via `@ComponentScan` filters. The generator is a Spring Initializr–based code-generation app reused as a library — never enable it via component scan; if you add new beans to `builder/generator/`, decide whether they should also be excluded.

### Controller surfaces (32 controllers / 205 endpoints — full inventory in [docs/api-list.md](docs/api-list.md))

Three controller roots in `spring-ai-alibaba-admin-server-start`, plus one in `-openapi`:

| Path prefix | Location | Purpose |
| --- | --- | --- |
| `/console/v1/**` | `studio.admin.builder.controller.*` | Builder console: auth, accounts, apps, agent-schemas, plugins, tools, knowledge-bases, MCP servers, providers/models, workspaces, workflows |
| `/api/{dataset,evaluator,experiment,prompt,observability,model}/**` | `studio.admin.controller.*` | Evaluation & prompt platform |
| `/graph-studio/api/**`, `/run_sse`, `/resume_sse` | `studio.admin.builder.generator.controller.*` | DSL import/export and Spring Initializr–based app generator |
| `/api/v1/apps/**` | `spring-ai-alibaba-admin-server-openapi` | External OpenAPI for chat/workflow completions (sync + SSE + async) |

**Response wrapper split** (subtle — easy to get wrong):
- `/api/v1/apps/**` (openapi) uses `Result<T>` with **string** `code` (`"Success"` on success).
- `/console/v1/**` and the platform `/api/...` controllers use `Result<T>` with **integer** `code` (`0` on success).
- SSE endpoints (`/console/v1/apps/{appId}/chat`, `/api/v1/apps/chat/completions`, `/run_sse`, `/resume_sse`) bypass the wrapper and emit `text/event-stream` frames.

### Persistence (full schema in [docs/data-model.md](docs/data-model.md))

Two MySQL schemas on one DB instance, bootstrapped by `docker/middleware/init/mysql/{admin-schema,agentscope-schema}.sql`:

- **`agentscope-schema`** — agent-platform domain (workspace, account, application, agent_schema, plugin, tool, knowledge_base, document, mcp_server, provider, model, …). Tenancy is `account → workspace`; almost every business entity carries `workspace_id`.
- **`admin-schema`** — evaluation domain (dataset, dataset_version, dataset_item, evaluator, evaluator_version, experiment, experiment_result, prompt, prompt_version, prompt_template, evaluator_template).

Cross-entity references are mostly **by business id** (`workspace_id`, `app_id`, `kb_id`, `plugin_id`, `prompt_key`) — only `dataset_*` and `evaluator_version` have declared SQL FKs. Don't assume cascade behavior; check `data-model.md`.

**Non-MySQL state** (easy to miss):
- `DocumentChunk` lives in the Spring AI `VectorStore` (Elasticsearch by default — see `application.yml` and `elasticsearch.yml`), not MySQL.
- `ChatSession` is an in-process `ConcurrentHashMap` with 30-minute idle expiry — does **not** survive restart.
- `App` (graph-studio generator) is an in-memory DSL/spec model, distinct from the MySQL `application` table.

MyBatis mappers live in two places: Java interfaces under `studio.admin.mapper` (mapper-scan target), XML statements under `spring-ai-alibaba-admin-server-start/src/main/resources/mapper/*.xml` — but only the eval-platform mappers are XML; the builder-side uses MyBatis-Plus annotations.

### Frontend monorepo (`frontend/`)

Umi 4 + React + Antd + TailwindCSS. Three workspace packages:

- `packages/main` — the workbench SPA (the one you `npm run dev`).
- `packages/spark-flow` — visual workflow editor (XYFlow / React Flow + ELK.js + Zustand). **Workspace dep of `main`** — must be built before `main` starts (the Makefile does this).
- `packages/spark-i18n` — i18n runtime + tooling.

Dev server proxies API calls to backend `:8080`; access the app at `http://localhost:8000`.

### External middleware (full list in [docs/external-dependency.html](docs/external-dependency.html))

MySQL (mandatory), Redis (sessions / caches), Elasticsearch (vector store + Kibana), Nacos (config push to downstream agents + service discovery), RocketMQ (`document_index_topic` for async doc indexing), OSS-compatible storage (file uploads), and at least one model provider (DashScope / OpenAI / DeepSeek). Local dev wires all of these up via `docker/middleware/docker-compose-dev.yaml`.

## Key References

- [docs/api-list.md](docs/api-list.md) — every endpoint, method, params, return type
- [docs/data-model.md](docs/data-model.md) / [docs/data-model.html](docs/data-model.html) — full ER model
- [docs/architecture-diagram.html](docs/architecture-diagram.html) — overall service architecture
- [docs/module-dependency.html](docs/module-dependency.html) — Maven module DAG
- [docs/external-dependency.html](docs/external-dependency.html) — middleware + model-provider deps
- [spring-ai-alibaba-admin-server-start/CONFIGURATION.md](spring-ai-alibaba-admin-server-start/CONFIGURATION.md) — env-var matrix for prod/k8s deployments
- Parent project guide: [../CLAUDE.md](../CLAUDE.md) — broader Spring AI Alibaba context


## Forbidden Areas
- `external_key` field in s`erver-core/PromptEntity`: A specific SDK client relies on this field as a cache key. Deleting or renaming it will cause the SDK to throw immediate errors. Do not refactor.
- Default value of `nacos.server-addr` in `application.yml`: Some enterprise users rely on this default value for canary/gray releases. Any modifications require an official announcement.
- `POST /api/prompts/search API` path: This path was previously exposed to the community. Changing it will break external calls. Adding a synonymous/alias interface is acceptable, but deleting the original interface is strictly forbidden.


## Legacy Overhead
- Table structures for `Dataset` and `DatasetItem`: The schema looks redundant because it was left over from an experimental feature in 2024. While the feature has been deprecated/deactivated, the data must be retained. Do not delete.
- Frontend `PromptTemplate.vue` using Vue instead of React: This is a legacy leftover from the early days. The rest of the entire admin panel uses React, with this file being the sole exception. Do not "conveniently refactor" it for the sake of uniformity.
- `LegacyEvaluatorAdapter` class: This class serves as a compatibility layer from the v0.x era. It looks messy because it must simultaneously support three legacy APIs. For all new code following v1.0, route exclusively through `EvaluatorV1`.