# CLAUDE.md — AI Assistant Guide for Spring AI Alibaba

Concise guide for AI assistants working on this repo. Details live in `docs/`; this file only orients you.

## Project Content

Spring AI Alibaba is a production-ready framework for building **agentic, workflow, and multi-agent applications** on top of Spring AI, with deep Alibaba Cloud integrations (DashScope, Nacos).

It ships three deliverables:

1. **Framework libraries** — multi-agent runtime, graph-based workflow engine, A2A communication, MCP support.
2. **Spring Boot Starters** — drop-in starters for A2A/Nacos, built-in nodes, dynamic config, observability.
3. **Admin & Studio** — a one-stop visual platform for designing, debugging, evaluating, and observing agents.

## Key Architecture

- High-level module dependency: [docs/module-dependency-graph.html](docs/module-dependency-graph.html)
- Admin service technical architecture: [docs/spring-ai-alibaba-admin-service-technical-architecture.html](docs/spring-ai-alibaba-admin-service-technical-architecture.html)
- Admin overall architecture diagram: [docs/spring-ai-alibaba-admin-architecture-diagram.html](docs/spring-ai-alibaba-admin-architecture-diagram.html)
- Admin module dependency: [docs/spring-ai-alibaba-admin-module-dependency.html](docs/spring-ai-alibaba-admin-module-dependency.html)
- Admin external dependencies (MySQL, Redis, ES, Nacos, OSS, model providers): [docs/spring-ai-alibaba-admin-external-dependencies.html](docs/spring-ai-alibaba-admin-external-dependencies.html)
- Admin MySQL schema diagram: [docs/spring-ai-alibaba-admin-mysql-schema-diagram.html](docs/spring-ai-alibaba-admin-mysql-schema-diagram.html)

Layer summary:

- **`spring-ai-alibaba-graph-core`** — stateful graph runtime: persistence (PostgreSQL / MySQL / Oracle / MongoDB / Redis / File), checkpointing, conditional routing, parallel execution, human-in-the-loop.
- **`spring-ai-alibaba-agent-framework`** — built-in agent patterns: `SequentialAgent`, `ParallelAgent`, `RoutingAgent`, `LoopAgent`, `ReactAgent`.
- **`spring-ai-alibaba-studio`** — embedded debugging UI for agents and graphs.
- **`spring-ai-alibaba-admin`** — full platform: tenancy, apps, plugins, tools, knowledge bases, MCP servers, evaluators, prompts, observability. Two MySQL schemas (`agentscope-schema`, `admin-schema`) on one DB server.

## Key Modules

```
spring-ai-alibaba/
├── spring-ai-alibaba-agent-framework/        # Multi-agent patterns
├── spring-ai-alibaba-graph-core/             # Graph runtime, persistence, state
├── spring-ai-alibaba-studio/                 # Embedded debug UI
├── spring-ai-alibaba-admin/                  # Platform (visual dev + ops)
│   ├── spring-ai-alibaba-admin-server-start  # Boot entrypoint (builder, generator, platform)
│   ├── spring-ai-alibaba-admin-server-core   # Domain services
│   ├── spring-ai-alibaba-admin-server-runtime # Runtime domain types
│   └── spring-ai-alibaba-admin-server-openapi # External chat/workflow OpenAPI
├── spring-ai-alibaba-bom/                    # Dependency BOM
├── spring-boot-starters/
│   ├── spring-ai-alibaba-starter-a2a-nacos/
│   ├── spring-ai-alibaba-starter-builtin-nodes/
│   ├── spring-ai-alibaba-starter-config-nacos/
│   └── spring-ai-alibaba-starter-graph-observation/
├── examples/                                 # chatbot, deepresearch, documentation, multimodal
├── tools/                                    # build/lint tooling
└── docs/                                     # API list, data model, diagrams
```

## Key Contracts

- **REST API list (233 endpoints across 42 controllers)** — [docs/api-list.md](docs/api-list.md). Major surfaces:
  - `/console/v1/**` — admin builder (auth, accounts, apps, plugins, tools, knowledge-bases, mcp-servers, providers, models, workspaces, workflows).
  - `/api/v1/apps/**` — admin OpenAPI for chat/workflow completions (streaming + async).
  - `/api/**` — admin platform (datasets, evaluators, experiments, prompts, observability).
  - `/graph-studio/api/**` — DSL import/export + app generator (Spring Initializr based).
  - `/run_sse`, `/resume_sse`, `/graph_run_sse` — studio SSE execution endpoints.
- **Persistent data model (27 MySQL tables across 2 schemas)** — [docs/data-model.md](docs/data-model.md) + ER diagram [docs/data-model.svg](docs/data-model.svg). Cross-entity links are mostly **by business id** (`workspace_id`, `app_id`, `kb_id`, `plugin_id`, `prompt_key`) — only `dataset_*` and `evaluator_version` use declared SQL FKs.
- **Non-MySQL entities**: `DocumentChunk` lives in the vector store (Elasticsearch by default); `ChatSession` is an in-process `ConcurrentHashMap` with 30-minute idle expiry; graph-studio `App` is also in-memory. See [docs/data-model.md](docs/data-model.md#non-mysql-api-entities).

## How to Run

**Prerequisites:** JDK 17, Maven 3.6+, Git. Spring Boot 3.5.x, Spring AI 1.1.x (`jakarta.*` namespace).

```shell
# Build everything (skip tests)
./mvnw -B package -DskipTests=true

# Build one module
./mvnw -pl :spring-ai-alibaba-agent-framework -B package -DskipTests=true

# Tests
./mvnw test
./mvnw -pl :<module> -Dtest=<TestClass> test

# Lint / license / spelling
make lint
make codespell
make yaml-lint
make licenses-check
```

**Admin platform (requires external middleware):** MySQL, Redis, Elasticsearch, Nacos, OSS-compatible storage, and at least one model provider (e.g. DashScope). Bootstrap SQL: `spring-ai-alibaba-admin/docker/middleware/init/mysql/{admin-schema,agentscope-schema}.sql`. Boot entrypoint: `spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start`.

**Examples:** see `examples/chatbot`, `examples/deepresearch`, `examples/multimodal`, `examples/documentation` — each is a self-contained Spring Boot app.

## Conventions

- Apache 2.0 license header on every Java file (template in [CONTRIBUTING.md](CONTRIBUTING.md) / existing files).
- Java 17 features welcome (records, switch expressions, text blocks).
- SLF4J only — no `System.out.println`.
- Lombok (`@Data`, `@Slf4j`, …) is used throughout to cut boilerplate.
- JUnit 5 + Mockito for tests.
- Use the BOM (`spring-ai-alibaba-bom`) for version management; do not pin versions ad hoc.

## Caveat



## Historical Baggage



## Important Links

- Issues: https://github.com/alibaba/spring-ai-alibaba/issues
- Source: https://github.com/alibaba/spring-ai-alibaba
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
