# Data Model — Spring AI Alibaba Admin

Generated: 2026-05-16
Source schemas: [admin-schema.sql](../spring-ai-alibaba-admin/docker/middleware/init/mysql/admin-schema.sql), [agentscope-schema.sql](../spring-ai-alibaba-admin/docker/middleware/init/mysql/agentscope-schema.sql)
Total tables: 27 (core: 22, supporting: 5; plus 1 in-code value object)

Scope: only the persistent data model owned by `spring-ai-alibaba-admin`. The graph-core checkpoint savers store opaque JSON checkpoints keyed by `thread_id` / `checkpoint_id` and are not modeled here.

The admin module is split across two MySQL schemas that share the same database server:

- **agentscope-schema** — the agent platform domain (tenancy, apps, agents, plugins, tools, knowledge bases, MCP, models).
- **admin-schema** — the evaluation and prompt-management domain (datasets, evaluators, experiments, prompts).

Most cross-entity links are by business identifier (`workspace_id`, `account_id`, `app_id`, `kb_id`, `plugin_id`, `prompt_key`) rather than by enforced foreign key. The only declared FK constraints live inside the `dataset_*` and `evaluator_version` tables. Logical references are marked with `LOGICAL FK -> …` to distinguish them from declared constraints.

## Core Models

### Tenancy & Identity

#### `account`

User account that owns workspaces and API keys.

| Field          | Type         | Markers          | Description                                                        |
| -------------- | ------------ | ---------------- | ------------------------------------------------------------------ |
| id             | BIGINT       | PK               | Surrogate ID                                                       |
| account_id     | VARCHAR(64)  | UNIQUE, NOT NULL | Business account identifier                                        |
| username       | VARCHAR(255) | NOT NULL         | Login username (indexed)                                           |
| email          | VARCHAR(255) | NULL             | Account email                                                      |
| mobile         | VARCHAR(255) | NULL             | Account mobile number                                              |
| password       | VARCHAR(255) | NOT NULL         | Hashed password (argon2id in seed data)                            |
| nickname       | VARCHAR(255) | NULL             | Display nickname                                                   |
| icon           | VARCHAR(255) | NULL             | Avatar URL                                                         |
| type           | VARCHAR(64)  | NOT NULL         | Account type: `basic`, `admin`                                     |
| status         | TINYINT      | NOT NULL, DEFAULT 1 | Status: `0`-deleted, `1`-normal                                 |
| gmt_create     | DATETIME     | NOT NULL         | Create time                                                        |
| gmt_modified   | DATETIME     | NOT NULL         | Last modified time                                                 |
| gmt_last_login | DATETIME     | NULL             | Last successful login time                                         |
| creator        | VARCHAR(64)  | NOT NULL         | Creator account id                                                 |
| modifier       | VARCHAR(64)  | NOT NULL         | Modifier account id                                                |

#### `workspace`

Top-level isolation boundary. Every business entity in agentscope-schema is scoped by `workspace_id`.

| Field        | Type          | Markers                                   | Description                          |
| ------------ | ------------- | ----------------------------------------- | ------------------------------------ |
| id           | BIGINT        | PK                                        | Surrogate ID                         |
| workspace_id | VARCHAR(64)   | UNIQUE, NOT NULL                          | Business workspace identifier        |
| account_id   | VARCHAR(64)   | NOT NULL, LOGICAL FK -> account.account_id | Owning account                      |
| status       | TINYINT       | NOT NULL, DEFAULT 1                       | Status: `0`-deleted, `1`-normal      |
| name         | VARCHAR(255)  | NOT NULL                                  | Workspace name                       |
| description  | VARCHAR(4096) | NULL                                      | Workspace description                |
| config       | TEXT          | NULL                                      | Workspace configuration              |
| gmt_create   | DATETIME      | NOT NULL                                  | Create time                          |
| gmt_modified | DATETIME      | NOT NULL                                  | Last modified time                   |
| creator      | VARCHAR(64)   | NOT NULL                                  | Creator account id                   |
| modifier     | VARCHAR(64)   | NOT NULL                                  | Modifier account id                  |

### Applications & Agents

#### `application`

A deployable app inside a workspace. May be an `agent` or a `workflow`.

| Field        | Type          | Markers                                       | Description                                                                  |
| ------------ | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                            | Surrogate ID                                                                 |
| workspace_id | VARCHAR(64)   | NOT NULL, LOGICAL FK -> workspace.workspace_id | Owning workspace                                                            |
| app_id       | VARCHAR(64)   | UNIQUE, NOT NULL                              | Business app identifier                                                      |
| name         | VARCHAR(255)  | NOT NULL                                      | App name                                                                     |
| description  | VARCHAR(4096) | NULL                                          | App description                                                              |
| icon         | VARCHAR(255)  | NULL                                          | App icon URL                                                                 |
| source       | VARCHAR(64)   | NOT NULL                                      | App source channel                                                           |
| type         | VARCHAR(64)   | NOT NULL                                      | App type: `agent`, `workflow`                                                |
| status       | TINYINT       | NOT NULL, DEFAULT 1                           | Status: `0`-deleted, `1`-draft, `2`-published, `3`-publishedEditing          |
| gmt_create   | DATETIME      | NOT NULL                                      | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                      | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

#### `application_version`

Versioned snapshot of an application's configuration.

| Field        | Type          | Markers                                  | Description                                                                  |
| ------------ | ------------- | ---------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                       | Surrogate ID                                                                 |
| app_id       | VARCHAR(64)   | NOT NULL, LOGICAL FK -> application.app_id | Owning app                                                                  |
| workspace_id | VARCHAR(64)   | NOT NULL                                 | Owning workspace                                                             |
| config       | LONGTEXT      | NULL                                     | App configuration payload                                                    |
| status       | TINYINT       | NOT NULL                                 | Same enum as `application.status`                                            |
| version      | VARCHAR(32)   | NOT NULL, DEFAULT '0.0.1'                | Version label                                                                |
| description  | VARCHAR(4096) | NULL                                     | Version description                                                          |
| gmt_create   | DATETIME      | NOT NULL                                 | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                 | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                 | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                 | Modifier account id                                                          |

#### `application_component`

Reusable component (sub-agent or sub-workflow) extracted from or attached to an application.

| Field        | Type          | Markers                                  | Description                                                                  |
| ------------ | ------------- | ---------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                       | Surrogate ID                                                                 |
| code         | VARCHAR(64)   | NOT NULL                                 | Component code                                                               |
| name         | VARCHAR(128)  | NOT NULL                                 | Component name                                                               |
| workspace_id | VARCHAR(64)   | NOT NULL                                 | Owning workspace                                                             |
| type         | VARCHAR(64)   | NOT NULL                                 | Component type: `agent`, `workflow`                                          |
| app_id       | VARCHAR(64)   | NULL, LOGICAL FK -> application.app_id   | Source app, if extracted from one                                            |
| config       | LONGTEXT      | NULL                                     | Component config                                                             |
| description  | VARCHAR(4096) | NULL                                     | Component description                                                        |
| status       | TINYINT       | NULL                                     | Status: `0`-deleted, `1`-normal, `2`-published                               |
| need_update  | TINYINT       | NULL                                     | Update flag: `0`-no update needed, `1`-update needed                         |
| creator      | VARCHAR(64)   | NULL                                     | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NULL                                     | Modifier account id                                                          |
| gmt_create   | DATETIME      | NOT NULL                                 | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                 | Last modified time                                                           |

#### `agent_schema`

Visual agent definition produced by the studio agent builder. Distinct from `application` — `agent_schema` rows are the structured representation of an agent's graph, instructions, sub-agents, and generated YAML.

| Field        | Type          | Markers                                       | Description                                                                  |
| ------------ | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                            | Surrogate ID                                                                 |
| agent_id     | VARCHAR(64)   | UNIQUE                                        | Business agent identifier                                                    |
| workspace_id | VARCHAR(64)   | NOT NULL, LOGICAL FK -> workspace.workspace_id | Owning workspace                                                            |
| name         | VARCHAR(255)  | NOT NULL                                      | Agent name                                                                   |
| description  | VARCHAR(4096) | NULL                                          | Agent description                                                            |
| type         | VARCHAR(64)   | NOT NULL                                      | Agent type: `ReactAgent`, `ParallelAgent`, `SequentialAgent`, `LLMRoutingAgent`, `LoopAgent` |
| instruction  | TEXT          | NULL                                          | System instruction prompt                                                    |
| input_keys   | TEXT          | NULL                                          | Input keys (JSON array)                                                      |
| output_key   | VARCHAR(255)  | NULL                                          | Output key                                                                   |
| handle       | LONGTEXT      | NULL                                          | Handle configuration (JSON)                                                  |
| sub_agents   | LONGTEXT      | NULL                                          | Sub-agent configuration (JSON)                                               |
| yaml_schema  | LONGTEXT      | NULL                                          | Generated YAML schema                                                        |
| status       | VARCHAR(64)   | NOT NULL, DEFAULT 'DRAFT'                     | Status: `DRAFT`, `PUBLISHED`, `ARCHIVED`                                     |
| enabled      | TINYINT       | NOT NULL, DEFAULT 1                           | Enabled flag: `0`-disabled, `1`-enabled                                      |
| gmt_create   | DATETIME      | NOT NULL                                      | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                      | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

### Plugins & Tools

#### `plugin`

Plugin registered in a workspace. Container for `tool` rows.

| Field        | Type          | Markers                                       | Description                                                                  |
| ------------ | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                            | Surrogate ID                                                                 |
| plugin_id    | VARCHAR(64)   | UNIQUE, NOT NULL                              | Business plugin identifier                                                   |
| workspace_id | VARCHAR(64)   | NOT NULL, LOGICAL FK -> workspace.workspace_id | Owning workspace                                                            |
| type         | VARCHAR(64)   | NOT NULL                                      | Plugin type: `1` official, `2` custom                                        |
| status       | TINYINT       | NOT NULL, DEFAULT 1                           | Status: `0`-deleted, `1`-normal                                              |
| name         | VARCHAR(255)  | NOT NULL                                      | Plugin name                                                                  |
| description  | VARCHAR(4096) | NULL                                          | Plugin description                                                           |
| config       | TEXT          | NULL                                          | Plugin configuration                                                         |
| source       | VARCHAR(64)   | NOT NULL                                      | Plugin source                                                                |
| gmt_create   | DATETIME      | NOT NULL                                      | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                      | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

#### `tool`

Individual callable tool exposed by a plugin.

| Field        | Type          | Markers                                  | Description                                                                  |
| ------------ | ------------- | ---------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                       | Surrogate ID                                                                 |
| plugin_id    | VARCHAR(64)   | NOT NULL, LOGICAL FK -> plugin.plugin_id | Owning plugin                                                                |
| tool_id      | VARCHAR(64)   | UNIQUE, NOT NULL                         | Business tool identifier                                                     |
| workspace_id | VARCHAR(64)   | NOT NULL                                 | Owning workspace                                                             |
| status       | TINYINT       | NOT NULL, DEFAULT 1                      | Status: `0`-deleted, `1`-normal                                              |
| enabled      | TINYINT       | NOT NULL, DEFAULT 1                      | Enabled: `0`-disabled, `1`-enabled                                           |
| test_status  | TINYINT       | NOT NULL, DEFAULT 1                      | Test status: `1` not tested, `2` passed, `3` failed                          |
| name         | VARCHAR(255)  | NOT NULL                                 | Tool name                                                                    |
| description  | VARCHAR(4096) | NULL                                     | Tool description                                                             |
| config       | LONGTEXT      | NOT NULL                                 | Tool configuration                                                           |
| api_schema   | LONGTEXT      | NOT NULL                                 | Tool API schema (e.g. OpenAPI/JSON Schema)                                   |
| gmt_create   | DATETIME      | NOT NULL                                 | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                 | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                 | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                 | Modifier account id                                                          |

### Knowledge

#### `knowledge_base`

RAG knowledge base in a workspace. Holds processing/index/search configuration; documents are stored in `document`.

| Field          | Type          | Markers                                       | Description                                                                  |
| -------------- | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id             | BIGINT        | PK                                            | Surrogate ID                                                                 |
| workspace_id   | VARCHAR(64)   | NOT NULL, LOGICAL FK -> workspace.workspace_id | Owning workspace                                                            |
| kb_id          | VARCHAR(64)   | UNIQUE, NOT NULL                              | Business knowledge base identifier                                           |
| type           | VARCHAR(64)   | NOT NULL                                      | KB type (e.g. `unstructured`)                                                |
| status         | TINYINT       | NOT NULL, DEFAULT 1                           | Status: `0`-deleted, `1`-normal                                              |
| name           | VARCHAR(255)  | NOT NULL                                      | KB name                                                                      |
| description    | VARCHAR(4096) | NULL                                          | KB description                                                               |
| process_config | TEXT          | NULL                                          | Document processing configuration                                            |
| index_config   | TEXT          | NULL                                          | Index configuration                                                          |
| search_config  | TEXT          | NULL                                          | Search configuration                                                         |
| total_docs     | BIGINT        | NOT NULL, DEFAULT 0                           | Total document count                                                         |
| gmt_create     | DATETIME      | NOT NULL                                      | Create time                                                                  |
| gmt_modified   | DATETIME      | NOT NULL                                      | Last modified time                                                           |
| creator        | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier       | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

#### `document`

Document ingested into a knowledge base, with its parsing/indexing state.

| Field          | Type          | Markers                                       | Description                                                                  |
| -------------- | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id             | BIGINT        | PK                                            | Surrogate ID                                                                 |
| workspace_id   | VARCHAR(64)   | NOT NULL                                      | Owning workspace                                                             |
| kb_id          | VARCHAR(64)   | NOT NULL, LOGICAL FK -> knowledge_base.kb_id  | Owning knowledge base                                                        |
| doc_id         | VARCHAR(64)   | UNIQUE, NOT NULL                              | Business document identifier                                                 |
| type           | VARCHAR(64)   | NOT NULL                                      | Document source type: `file`, `url`                                          |
| status         | TINYINT       | NOT NULL, DEFAULT 1                           | Status: `0`-deleted, `1`-normal                                              |
| enabled        | TINYINT       | NOT NULL, DEFAULT 1                           | Enabled flag: `0`-disabled, `1`-enabled                                      |
| name           | VARCHAR(255)  | NOT NULL                                      | Document name                                                                |
| format         | VARCHAR(64)   | NOT NULL                                      | Document format (pdf, md, html, …)                                           |
| size           | BIGINT        | NOT NULL, DEFAULT 0                           | Document size in bytes                                                       |
| metadata       | TEXT          | NULL                                          | Free-form metadata                                                           |
| index_status   | TINYINT       | NOT NULL, DEFAULT 1                           | Index status: `1` pending, `2` processing, `3` completed                     |
| path           | VARCHAR(512)  | NOT NULL                                      | Storage path of original document                                            |
| parsed_path    | VARCHAR(512)  | NULL                                          | Storage path of parsed output                                                |
| process_config | TEXT          | NULL                                          | Chunking/processing override                                                 |
| source         | VARCHAR(255)  | NULL                                          | Original source label                                                        |
| error          | TEXT          | NULL                                          | Last processing error                                                        |
| gmt_create     | TIMESTAMP     | NOT NULL                                      | Create time                                                                  |
| gmt_modified   | TIMESTAMP     | NOT NULL                                      | Last modified time                                                           |
| creator        | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier       | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

### Models & MCP

#### `provider`

Model provider registry (e.g. Tongyi/DashScope, OpenAI).

| Field                 | Type           | Markers                | Description                                                              |
| --------------------- | -------------- | ---------------------- | ------------------------------------------------------------------------ |
| id                    | BIGINT         | PK                     | Surrogate ID                                                             |
| workspace_id          | VARCHAR(64)    | NULL                   | Owning workspace (nullable for preset rows)                              |
| icon                  | VARCHAR(255)   | NULL                   | Provider icon                                                            |
| name                  | VARCHAR(255)   | NULL                   | Display name                                                             |
| description           | VARCHAR(1024)  | NULL                   | Provider description                                                     |
| provider              | VARCHAR(255)   | NOT NULL               | Provider key (joined with `model.provider`)                              |
| enable                | TINYINT(1)     | DEFAULT 1              | Enable flag: `0`-disabled, `1`-enabled                                   |
| source                | VARCHAR(64)    | NOT NULL, DEFAULT 'preset' | Source: `preset`, `custom`                                           |
| credential            | VARCHAR(1024)  | NULL                   | Access credential (JSON)                                                 |
| supported_model_types | VARCHAR(255)   | NULL                   | Supported model types                                                    |
| protocol              | VARCHAR(64)    | NULL                   | Wire protocol, e.g. `openai`                                             |
| gmt_create            | DATETIME       | DEFAULT CURRENT_TIMESTAMP | Create time                                                           |
| gmt_modified          | DATETIME       | DEFAULT CURRENT_TIMESTAMP | Last modified time                                                    |
| creator               | VARCHAR(64)    | NULL                   | Creator account id                                                       |
| modifier              | VARCHAR(64)    | NULL                   | Modifier account id                                                      |

#### `model`

Specific model exposed by a provider (e.g. `qwen-max`, `qwen-vl-plus`, `text-embedding-v3`).

| Field        | Type         | Markers                                  | Description                                              |
| ------------ | ------------ | ---------------------------------------- | -------------------------------------------------------- |
| id           | BIGINT       | PK                                       | Surrogate ID                                             |
| workspace_id | VARCHAR(64)  | NULL                                     | Owning workspace (nullable for preset rows)              |
| icon         | VARCHAR(255) | NULL                                     | Model icon                                               |
| name         | VARCHAR(100) | NULL                                     | Display name                                             |
| type         | VARCHAR(100) | DEFAULT 'LLM'                            | Model type: `LLM`, `text_embedding`, `rerank`, …         |
| mode         | VARCHAR(100) | DEFAULT 'chat'                           | Model mode                                               |
| model_id     | VARCHAR(100) | NOT NULL                                 | Model identifier on the provider                         |
| provider     | VARCHAR(100) | NOT NULL, LOGICAL FK -> provider.provider | Owning provider                                         |
| enable       | TINYINT(1)   | DEFAULT 1                                | Enable flag: `0`-disabled, `1`-enabled                   |
| tags         | VARCHAR(255) | NULL                                     | Capability tags (`function_call,reasoning,vision,…`)     |
| source       | VARCHAR(100) | NOT NULL, DEFAULT 'preset'               | Source: `preset`, `custom`                               |
| gmt_create   | DATETIME     | DEFAULT CURRENT_TIMESTAMP                | Create time                                              |
| gmt_modified | DATETIME     | DEFAULT CURRENT_TIMESTAMP                | Last modified time                                       |
| creator      | VARCHAR(64)  | NULL                                     | Creator account id                                       |
| modifier     | VARCHAR(64)  | NULL                                     | Modifier account id                                      |

#### `mcp_server`

Registered Model Context Protocol server.

| Field         | Type          | Markers                | Description                                                                  |
| ------------- | ------------- | ---------------------- | ---------------------------------------------------------------------------- |
| id            | BIGINT        | PK                     | Surrogate ID                                                                 |
| server_code   | VARCHAR(64)   | NOT NULL               | Business server identifier                                                   |
| name          | VARCHAR(64)   | NOT NULL               | Server name                                                                  |
| description   | VARCHAR(1024) | NULL                   | Server description                                                           |
| source        | VARCHAR(128)  | NULL                   | Server source                                                                |
| deploy_env    | VARCHAR(16)   | NULL                   | Deploy environment: `local`, `remote`                                        |
| type          | VARCHAR(32)   | NOT NULL               | Server type: `OFFICIAL`, `CUSTOMER`                                          |
| deploy_config | TEXT          | NOT NULL               | Deploy configuration                                                         |
| workspace_id  | VARCHAR(64)   | NULL                   | Owning workspace                                                             |
| account_id    | VARCHAR(64)   | NULL                   | Owner account id                                                             |
| status        | TINYINT       | NOT NULL               | Status: `0` unable, `1` normal, `3` deleted                                  |
| biz_type      | VARCHAR(512)  | NULL                   | Business type tags                                                           |
| detail_config | TEXT          | NULL                   | Detail configuration                                                         |
| host          | VARCHAR(1024) | NULL                   | Server host                                                                  |
| install_type  | VARCHAR(32)   | NULL                   | Install type: `npx`, `uvx`, `sse`                                            |
| gmt_create    | DATETIME      | NOT NULL               | Create time                                                                  |
| gmt_modified  | DATETIME      | NOT NULL               | Last modified time                                                           |

### Evaluation

#### `dataset`

Evaluation dataset header. Versions and items are owned children.

| Field          | Type         | Markers             | Description                              |
| -------------- | ------------ | ------------------- | ---------------------------------------- |
| id             | BIGINT       | PK                  | Surrogate ID                             |
| name           | VARCHAR(255) | NOT NULL            | Dataset name                             |
| description    | TEXT         | NULL                | Dataset description                      |
| columns_config | LONGTEXT     | NULL                | Column structure configuration (JSON)    |
| create_time    | DATETIME     | NOT NULL            | Create time                              |
| update_time    | DATETIME     | NOT NULL            | Last update time                         |
| deleted        | TINYINT(1)   | NOT NULL, DEFAULT 0 | Logical delete flag: `0`/`1`             |

#### `dataset_version`

Published / draft version of a dataset.

| Field         | Type        | Markers                                   | Description                                                |
| ------------- | ----------- | ----------------------------------------- | ---------------------------------------------------------- |
| id            | BIGINT      | PK                                        | Surrogate ID                                               |
| dataset_id    | BIGINT      | NOT NULL, FK -> dataset.id ON DELETE CASCADE | Parent dataset                                          |
| version       | VARCHAR(32) | NOT NULL, UNIQUE(dataset_id, version)     | Version label                                              |
| description   | TEXT        | NULL                                      | Version description                                        |
| data_count    | INT         | NOT NULL, DEFAULT 0                       | Number of items in this version                            |
| status        | VARCHAR(32) | NOT NULL, DEFAULT 'DRAFT'                 | Status: `DRAFT`, `PUBLISHED`, `ARCHIVED`                   |
| experiments   | TEXT        | NULL                                      | Linked experiments (denormalized JSON list)                |
| dataset_items | TEXT        | NULL                                      | Items in this version (denormalized JSON list)             |
| create_time   | DATETIME    | NOT NULL                                  | Create time                                                |
| update_time   | DATETIME    | NOT NULL                                  | Last update time                                           |

#### `dataset_item`

Single row of evaluation data, scoped to a dataset.

| Field          | Type       | Markers                                      | Description                                       |
| -------------- | ---------- | -------------------------------------------- | ------------------------------------------------- |
| id             | BIGINT     | PK                                           | Surrogate ID                                      |
| dataset_id     | BIGINT     | NOT NULL, FK -> dataset.id ON DELETE CASCADE | Parent dataset                                    |
| columns_config | LONGTEXT   | NULL                                         | Per-item column override (JSON)                   |
| data_content   | LONGTEXT   | NOT NULL                                     | Item payload (JSON)                               |
| create_time    | DATETIME   | NOT NULL                                     | Create time                                       |
| update_time    | DATETIME   | NOT NULL                                     | Last update time                                  |
| deleted        | TINYINT(1) | NOT NULL, DEFAULT 0                          | Logical delete flag: `0`/`1`                      |

#### `evaluator`

Evaluator header. Versions hold the actual prompt and model configuration.

| Field       | Type         | Markers             | Description                              |
| ----------- | ------------ | ------------------- | ---------------------------------------- |
| id          | BIGINT       | PK                  | Surrogate ID                             |
| name        | VARCHAR(255) | NOT NULL            | Evaluator name                           |
| description | TEXT         | NULL                | Evaluator description                    |
| create_time | DATETIME     | NOT NULL            | Create time                              |
| update_time | DATETIME     | NOT NULL            | Last update time                         |
| deleted     | TINYINT(1)   | NOT NULL, DEFAULT 0 | Logical delete flag: `0`/`1`             |

#### `evaluator_version`

Versioned evaluator: prompt, variables, and model configuration that scores experiment outputs.

| Field        | Type        | Markers                                            | Description                                                |
| ------------ | ----------- | -------------------------------------------------- | ---------------------------------------------------------- |
| id           | BIGINT      | PK                                                 | Surrogate ID                                               |
| evaluator_id | BIGINT      | NOT NULL, FK -> evaluator.id ON DELETE CASCADE     | Parent evaluator                                           |
| description  | TEXT        | NULL                                               | Version description                                        |
| version      | VARCHAR(32) | NOT NULL, UNIQUE(evaluator_id, version)            | Version label                                              |
| model_config | TEXT        | NOT NULL                                           | Model configuration (JSON)                                 |
| prompt       | LONGTEXT    | NULL                                               | Prompt configuration (JSON)                                |
| variables    | LONGTEXT    | NULL                                               | Prompt variable list                                       |
| status       | VARCHAR(32) | NULL                                               | Status: `DRAFT`, `PUBLISHED`, `ARCHIVED`                   |
| experiments  | TEXT        | NULL                                               | Linked experiments (denormalized JSON list)                |
| create_time  | DATETIME    | NOT NULL                                           | Create time                                                |
| update_time  | DATETIME    | NOT NULL                                           | Last update time                                           |

#### `experiment`

Evaluation run pairing a dataset version with one or more evaluator versions.

| Field                    | Type         | Markers                                            | Description                                                                  |
| ------------------------ | ------------ | -------------------------------------------------- | ---------------------------------------------------------------------------- |
| id                       | BIGINT       | PK                                                 | Surrogate ID                                                                 |
| name                     | VARCHAR(255) | NOT NULL                                           | Experiment name                                                              |
| description              | TEXT         | NULL                                               | Experiment description                                                       |
| dataset_id               | BIGINT       | NOT NULL, LOGICAL FK -> dataset.id                 | Source dataset                                                               |
| dataset_version_id       | BIGINT       | NOT NULL, LOGICAL FK -> dataset_version.id         | Specific dataset version                                                     |
| dataset_version          | VARCHAR(32)  | NOT NULL                                           | Cached dataset version label                                                 |
| evaluation_object_config | LONGTEXT     | NULL                                               | Configuration of the system being evaluated (JSON)                           |
| evaluator_config         | TEXT         | NOT NULL                                           | Evaluator configuration including referenced evaluator versions (JSON)       |
| status                   | VARCHAR(32)  | NOT NULL, DEFAULT 'DRAFT'                          | Status: `DRAFT`, `RUNNING`, `COMPLETED`, `FAILED`, `STOPPED`                 |
| progress                 | INT          | NOT NULL, DEFAULT 0                                | Progress percentage 0-100                                                    |
| complete_time            | DATETIME     | NULL                                               | Completion time                                                              |
| create_time              | DATETIME     | NOT NULL                                           | Create time                                                                  |
| update_time              | DATETIME     | NOT NULL                                           | Last update time                                                             |

#### `experiment_result`

One row per (experiment × dataset_item × evaluator_version) outcome.

| Field                | Type          | Markers                                          | Description                                              |
| -------------------- | ------------- | ------------------------------------------------ | -------------------------------------------------------- |
| id                   | BIGINT        | PK                                               | Surrogate ID                                             |
| experiment_id        | BIGINT        | NOT NULL, LOGICAL FK -> experiment.id            | Parent experiment                                        |
| input                | LONGTEXT      | NOT NULL                                         | Input content fed to the evaluation object               |
| actual_output        | LONGTEXT      | NOT NULL                                         | Output produced by the evaluation object                 |
| reference_output     | LONGTEXT      | NULL                                             | Reference output (ground truth)                          |
| score                | DECIMAL(3, 2) | NULL                                             | Evaluation score 0.00-1.00                               |
| reason               | TEXT          | NULL                                             | Reason text from the evaluator                           |
| evaluation_time      | DATETIME      | NULL                                             | Evaluation execution time                                |
| evaluator_version_id | BIGINT        | NOT NULL, LOGICAL FK -> evaluator_version.id     | Evaluator version that produced this score               |
| create_time          | DATETIME      | NOT NULL                                         | Create time                                              |
| update_time          | DATETIME      | NOT NULL                                         | Last update time                                         |

### Prompts

#### `prompt`

Prompt header. Versions hold actual templates.

| Field          | Type         | Markers          | Description                                                            |
| -------------- | ------------ | ---------------- | ---------------------------------------------------------------------- |
| id             | BIGINT       | PK               | Surrogate ID                                                           |
| prompt_key     | VARCHAR(255) | UNIQUE, NOT NULL | Stable business key joined by `prompt_version.prompt_key`              |
| prompt_desc    | VARCHAR(255) | NULL             | Description                                                            |
| latest_version | VARCHAR(32)  | NULL             | Pointer to the most recent version label                               |
| tags           | VARCHAR(255) | NULL             | Tags (comma-separated)                                                 |
| create_time    | DATETIME(3)  | DEFAULT CURRENT_TIMESTAMP(3) | Create time                                                |
| update_time    | DATETIME(3)  | DEFAULT CURRENT_TIMESTAMP(3) | Last update time                                           |

#### `prompt_version`

Versioned prompt template content.

| Field            | Type         | Markers                                       | Description                                                                  |
| ---------------- | ------------ | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id               | BIGINT       | PK                                            | Surrogate ID                                                                 |
| version          | VARCHAR(32)  | NOT NULL, UNIQUE(prompt_key, version)         | Version label                                                                |
| prompt_key       | VARCHAR(255) | NOT NULL, LOGICAL FK -> prompt.prompt_key     | Owning prompt                                                                |
| version_desc     | VARCHAR(255) | NULL                                          | Version description                                                          |
| template         | LONGTEXT     | NULL                                          | Prompt template body                                                         |
| variables        | LONGTEXT     | NULL                                          | Template variable definitions                                                |
| model_config     | LONGTEXT     | NULL                                          | Model parameters for debugging (JSON)                                        |
| status           | VARCHAR(32)  | NOT NULL, DEFAULT 'pre'                       | Version status: `pre` (prerelease), `release`                                |
| create_time      | DATETIME(3)  | DEFAULT CURRENT_TIMESTAMP(3)                  | Create time                                                                  |
| previous_version | VARCHAR(32)  | NULL                                          | Earlier version label (for diff comparisons)                                 |

## Supporting Models

The following tables exist in the schema but are excluded from the ER diagram. Each is either operational, polymorphic, a pure template library, or a redundant legacy form of a core model.

### `api_key`

API key issued for an account. Operational table — no business relationships beyond `account_id`. Excluded from the diagram.

| Field        | Type          | Markers                                       | Description                                                                  |
| ------------ | ------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| id           | BIGINT        | PK                                            | Surrogate ID                                                                 |
| account_id   | VARCHAR(64)   | NOT NULL, LOGICAL FK -> account.account_id    | Owning account                                                               |
| api_key      | VARCHAR(512)  | UNIQUE, NOT NULL                              | API key value                                                                |
| status       | TINYINT       | NOT NULL, DEFAULT 1                           | Status: `0`-deleted, `1`-normal                                              |
| description  | VARCHAR(4096) | NULL                                          | Description                                                                  |
| gmt_create   | DATETIME      | NOT NULL                                      | Create time                                                                  |
| gmt_modified | DATETIME      | NOT NULL                                      | Last modified time                                                           |
| creator      | VARCHAR(64)   | NOT NULL                                      | Creator account id                                                           |
| modifier     | VARCHAR(64)   | NOT NULL                                      | Modifier account id                                                          |

### `reference`

Polymorphic edge table linking any "main entity" to any "refer entity" by `main_code` / `refer_code` and integer type discriminators. The `main_type` / `refer_type` enums are defined in application code rather than SQL. Excluded from the diagram because it dangles off every other entity without a typed schema.

| Field        | Type        | Markers                | Description                                          |
| ------------ | ----------- | ---------------------- | ---------------------------------------------------- |
| id           | BIGINT      | PK                     | Surrogate ID                                         |
| main_code    | VARCHAR(64) | NOT NULL               | Code of the referring entity                         |
| main_type    | TINYINT     | NOT NULL               | Type discriminator for the referring entity         |
| refer_code   | VARCHAR(64) | NOT NULL               | Code of the referenced entity                        |
| refer_type   | TINYINT     | NOT NULL               | Type discriminator for the referenced entity        |
| workspace_id | VARCHAR(64) | NOT NULL, DEFAULT '1'  | Workspace scope                                      |
| gmt_create   | DATETIME    | NOT NULL               | Create time                                          |
| gmt_modified | DATETIME    | NOT NULL               | Last modified time                                   |

### `prompt_build_template`

Static library of prompt-construction templates seeded for the UI builder. Has no relationships to other entities. Excluded from the diagram.

| Field               | Type         | Markers          | Description                                  |
| ------------------- | ------------ | ---------------- | -------------------------------------------- |
| id                  | BIGINT       | PK               | Surrogate ID                                 |
| prompt_template_key | VARCHAR(255) | UNIQUE, NOT NULL | Template key                                 |
| tags                | VARCHAR(255) | NULL             | Tags (comma-separated)                       |
| template_desc       | VARCHAR(255) | NULL             | Description                                  |
| template            | LONGTEXT     | NULL             | Template body                                |
| variables           | LONGTEXT     | NULL             | Variable list                                |
| model_config        | LONGTEXT     | NULL             | Recommended model parameters (JSON)          |

### `evaluator_template`

Static library of evaluator-prompt templates (text similarity, code quality, sentiment, …). Has no relationships to other entities. Excluded from the diagram.

| Field                  | Type         | Markers          | Description                                  |
| ---------------------- | ------------ | ---------------- | -------------------------------------------- |
| id                     | BIGINT       | PK               | Surrogate ID                                 |
| evaluator_template_key | VARCHAR(255) | UNIQUE, NOT NULL | Template key                                 |
| template_desc          | VARCHAR(255) | NULL             | Description                                  |
| template               | LONGTEXT     | NULL             | Template body                                |
| variables              | LONGTEXT     | NULL             | Variable list                                |
| model_config           | LONGTEXT     | NULL             | Recommended model parameters (JSON)          |

### `model_config`

Self-contained model configuration table living in the admin-schema. Conceptually overlaps with the `provider` + `model` pair in agentscope-schema (it bundles `provider`, `model_name`, `base_url`, `api_key`, parameters in one row). Excluded from the diagram to avoid drawing a parallel/duplicate model registry.

| Field                | Type         | Markers                | Description                                                                  |
| -------------------- | ------------ | ---------------------- | ---------------------------------------------------------------------------- |
| id                   | BIGINT       | PK                     | Surrogate ID                                                                 |
| name                 | VARCHAR(100) | UNIQUE, NOT NULL       | Model display name                                                           |
| provider             | VARCHAR(50)  | NOT NULL               | Provider key                                                                 |
| model_name           | VARCHAR(100) | NOT NULL               | Model identifier                                                             |
| base_url             | VARCHAR(500) | NOT NULL               | Provider service endpoint                                                    |
| api_key              | VARCHAR(500) | NOT NULL               | API credential                                                               |
| default_parameters   | JSON         | NULL                   | Default parameters                                                           |
| supported_parameters | JSON         | NULL                   | Supported parameter definitions                                              |
| status               | TINYINT      | NOT NULL, DEFAULT 1    | Status: `0`-disabled, `1`-enabled                                            |
| create_time          | DATETIME     | NOT NULL               | Create time                                                                  |
| update_time          | DATETIME     | NOT NULL               | Last update time                                                             |
| deleted              | TINYINT(1)   | NOT NULL, DEFAULT 0    | Logical delete flag                                                          |

### `LimitEntity` (in-code only)

Not a SQL table. Lives at [LimitEntity.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/base/entity/LimitEntity.java) as a value object holding a `count` and `time` window for rate limiting. Excluded.

## Non-MySQL API Entities

The admin REST API exposes a few entity types that are referenced by the controller layer ([docs/api-list.md](api-list.md)) but are **not** persisted in MySQL. They are listed here so a reader cross-referencing the API and the data model can find them.

### `DocumentChunk` — stored in the vector store

API: `DocumentChunkController` (`/console/v1/documents/{docId}/chunks`, 7 endpoints — create, update, delete, batch-delete, list, preview, update-status). Returned by `KnowledgeBaseController#retrieve`.

Source: [DocumentChunk.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-runtime/src/main/java/com/alibaba/cloud/ai/studio/runtime/domain/knowledgebase/DocumentChunk.java).

Fields: `chunkId`, `docId`, `docName`, `title`, `text`, `score`, `pageNumber`, `enabled`, `workspaceId`.

Storage: Spring AI `VectorStore` abstraction (Elasticsearch in the default deployment), not MySQL. Round-tripped through [DocumentChunkConverter](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/rag/DocumentChunkConverter.java) which maps `DocumentChunk` ↔ Spring AI `Document`. The MySQL `document` table tracks the parent document (metadata, source path, indexing state); chunks live in the vector index.

### `ChatSession` — in-memory, transient

API: `PromptController` (`GET /api/prompt/session`, `DELETE /api/prompt/session`). Indirectly created by `POST /api/prompt/run` and by evaluator debug flows.

Source: [ChatSession.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/dto/ChatSession.java).

Fields: `sessionId`, `promptKey`, `version`, `template`, `variables`, `modelConfig`, `messages`, `mockTools`, `createTime`, `lastUpdateTime`.

Storage: in-process `ConcurrentHashMap<String, ChatSession>` inside [ChatSessionServiceImpl](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/service/impl/ChatSessionServiceImpl.java), with a 30-minute idle expiry and a `@Scheduled` cleanup every 10 minutes. The source code itself notes that production deployments should swap this for Redis. Sessions do not survive a restart.

### `App` (graph-studio generator) — in-memory, transient

API: `ApplicationController` under `/graph-studio/api/app/*` and the `DSLController` under `/graph-studio/api/dsl/*` (returning `R<App>`).

Source: [App.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/builder/generator/model/App.java) — a wrapper of `AppMetadata` plus a polymorphic `Object spec` (e.g. `Workflow`).

Storage: [AppMemorySaver.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/builder/generator/saver/AppMemorySaver.java) wraps a `ConcurrentHashMap<String, App>`. **Distinct from the MySQL `application` table** — the generator's `App` is the runtime DSL/spec model used by the Spring Initializr-based code generator, not an application registered in the agent platform. The two namespaces happen to share the word "App" but live in different controllers (`/graph-studio/api/app/*` vs. `/console/v1/apps/*`) and different storage.

### DTO projections of `experiment_result`

API: `ExperimentController#getExperimentResults` returns `List<ExperimentEvaluatorResult>` (per-evaluator overview) and `ExperimentController#getExperimentResultDetail` returns `PageResult<ExperimentEvaluatorResultDetail>`.

Source: [ExperimentEvaluatorResult.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/dto/ExperimentEvaluatorResult.java), [ExperimentEvaluatorResultDetail.java](../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/dto/ExperimentEvaluatorResultDetail.java).

Storage: derived projections, not their own tables. Both are aggregations / row-level views over the `experiment_result` table (the per-evaluator overview groups by `evaluator_version_id`; the detail row maps one-to-one to an `experiment_result` row).

## Enums

The schema encodes enums as `VARCHAR` or `TINYINT` columns. Valid values are recovered from column comments and from seed data.

### `account.type`

- `basic` — regular user
- `admin` — administrator

### `account.status` / `workspace.status` / `plugin.status` / `tool.status` / `knowledge_base.status` / `document.status`

- `0` — deleted
- `1` — normal

### `application.status` / `application_version.status`

- `0` — deleted
- `1` — draft
- `2` — published
- `3` — publishedEditing

### `application.type` / `application_component.type`

- `agent`
- `workflow`

### `application_component.status`

- `0` — deleted
- `1` — normal
- `2` — published

### `agent_schema.type`

- `ReactAgent`
- `ParallelAgent`
- `SequentialAgent`
- `LLMRoutingAgent`
- `LoopAgent`

### `agent_schema.status` / `dataset_version.status` / `evaluator_version.status`

- `DRAFT`
- `PUBLISHED`
- `ARCHIVED`

### `plugin.type`

- `1` — official
- `2` — custom

### `tool.test_status`

- `1` — not tested
- `2` — passed
- `3` — failed

### `document.type`

- `file`
- `url`

### `document.index_status`

- `1` — pending
- `2` — processing
- `3` — completed

### `mcp_server.deploy_env`

- `local`
- `remote`

### `mcp_server.type`

- `OFFICIAL`
- `CUSTOMER`

### `mcp_server.install_type`

- `npx`
- `uvx`
- `sse`

### `mcp_server.status`

- `0` — unable
- `1` — normal
- `3` — deleted

### `model.type`

- `LLM`
- `text_embedding`
- `rerank`
- (seed data also uses `llm` lower-case)

### `provider.source` / `model.source`

- `preset`
- `custom`

### `experiment.status`

- `DRAFT`
- `RUNNING`
- `COMPLETED`
- `FAILED`
- `STOPPED`

### `prompt_version.status`

- `pre` — prerelease
- `release` — released

## Relationships (Summary)

Declared SQL foreign keys (with `ON DELETE CASCADE`):

- `dataset_version.dataset_id` → `dataset.id` (N:1, required)
- `dataset_item.dataset_id` → `dataset.id` (N:1, required)
- `evaluator_version.evaluator_id` → `evaluator.id` (N:1, required)

Logical references (joined by business id, no SQL constraint):

- `workspace.account_id` → `account.account_id` (N:1, required)
- `application.workspace_id` → `workspace.workspace_id` (N:1, required)
- `application_version.app_id` → `application.app_id` (N:1, required)
- `application_component.app_id` → `application.app_id` (N:1, optional)
- `application_component.workspace_id` → `workspace.workspace_id` (N:1, required)
- `agent_schema.workspace_id` → `workspace.workspace_id` (N:1, required)
- `plugin.workspace_id` → `workspace.workspace_id` (N:1, required)
- `tool.plugin_id` → `plugin.plugin_id` (N:1, required)
- `knowledge_base.workspace_id` → `workspace.workspace_id` (N:1, required)
- `document.kb_id` → `knowledge_base.kb_id` (N:1, required)
- `mcp_server.workspace_id` → `workspace.workspace_id` (N:1, optional)
- `mcp_server.account_id` → `account.account_id` (N:1, optional)
- `provider.workspace_id` → `workspace.workspace_id` (N:1, optional)
- `model.workspace_id` → `workspace.workspace_id` (N:1, optional)
- `model.provider` → `provider.provider` (N:1, required)
- `prompt_version.prompt_key` → `prompt.prompt_key` (N:1, required)
- `experiment.dataset_id` → `dataset.id` (N:1, required)
- `experiment.dataset_version_id` → `dataset_version.id` (N:1, required)
- `experiment_result.experiment_id` → `experiment.id` (N:1, required)
- `experiment_result.evaluator_version_id` → `evaluator_version.id` (N:1, required)
- `api_key.account_id` → `account.account_id` (N:1, required) — supporting

`experiment.evaluator_config` and `evaluator_version.experiments` denormalize a logical many-to-many between experiments and evaluator versions; the authoritative join is provided per-result by `experiment_result.evaluator_version_id`.

## ER Diagram

See [data-model.html](data-model.html) (self-contained HTML page with the ER diagram embedded as SVG; open directly in a browser). The standalone [data-model.svg](data-model.svg) is also kept for embedding in other docs.
