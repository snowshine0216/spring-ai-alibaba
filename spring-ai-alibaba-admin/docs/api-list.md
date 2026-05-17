# REST API Inventory — Spring AI Alibaba Admin

Generated: 2026-05-16
Scope: every HTTP endpoint exposed by Spring `@RestController` / `@Controller` classes inside the `spring-ai-alibaba-admin` module.

- **Scanned**: 32 controller files (205 endpoints) across `spring-ai-alibaba-admin-server-openapi/` and `spring-ai-alibaba-admin-server-start/` (the `server-runtime` and `server-core` submodules do not expose REST endpoints).
- **Per endpoint**: HTTP method, full path (class base + method path), one-line description, key parameters with binding source, and declared return type.
- **Omitted parameters**: `HttpServletRequest` / `HttpServletResponse`, `Principal`, `BindingResult`, and similar Spring infrastructure types.
- **Visibility**: all 205 endpoints are external (no path is mounted under an `/internal/` prefix and no `@InternalApi`-style annotation is present in the admin codebase).

## Response Wrappers

Two response wrappers cover virtually every admin endpoint:

`Result<T>` — used by the `server-openapi` chat / workflow surface (`/api/v1/apps/**`) and a handful of generator endpoints:

    {
      "code":    string,   // "Success" on success, otherwise an error code
      "message": string,
      "data":    T,
      "requestId": string
    }

`Result<T>` (builder) — used by every `/console/v1/**` builder controller; same shape as above but the `code` is an integer (`0` on success):

    {
      "code":      int,    // 0 on success, non-zero error code on failure
      "message":   string,
      "data":      T,
      "requestId": string
    }

Endpoints under the platform group (`/api/datasets/**`, `/api/evaluator/**`, `/api/experiment/**`, `/api/prompt/**`, `/api/observability/**`) often return their domain type directly (e.g. `Result<DatasetDTO>` / `PageResult<EvaluatorDTO>`); these are noted in-line below.

SSE / streaming endpoints (`/console/v1/apps/{appId}/chat`, `/api/v1/apps/chat/completions`, generator `/run_sse`, `/resume_sse`) bypass the JSON wrapper and emit `text/event-stream` frames whose payload is the same domain object the matching synchronous endpoint would return.

---

## Module Index

| # | Group | Controllers | Endpoints | Path Prefix |
|---|---|---|---|---|
| 1 | [server-openapi](#1-server-openapi) | 1 | 5 | `/api/v1/apps/**` |
| 2 | [server-start (builder)](#2-server-start-builder) | 21 | 134 | `/console/v1/**` |
| 3 | [server-start (generator)](#3-server-start-generator) | 4 | 11 | `/graph-studio/api/**`, `/run_sse`, `/resume_sse` |
| 4 | [server-start (platform)](#4-server-start-platform) | 6 | 55 | `/api/datasets/**`, `/api/evaluator/**`, `/api/experiment/**`, `/api/prompt/**`, `/api/observability/**`, `/api/model-config/**` |
| | **Total** | **32** | **205** | |

---

## 1. server-openapi

### ChatController
- **Base path**: `/api/v1/apps`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/api/v1/apps/chat/completions` | Handles chat completion requests (streaming + non-streaming) | `request: AgentRequest (@RequestBody)` | `Object` |
| POST | `/api/v1/apps/workflow/completions` | Handles workflow completion requests (streaming + non-streaming) | `request: WorkflowRequest (@RequestBody)` | `Object` |
| POST | `/api/v1/apps/workflow/async-completions` | Initiate asynchronous workflow completion | `request: WorkflowRequest (@RequestBody)` | `Result<TaskRunResponse>` |
| POST | `/api/v1/apps/workflow/stop-completions` | Stop a running workflow task | `request: TaskStopRequest (@RequestBody)` | `Result<Boolean>` |
| POST | `/api/v1/apps/workflow/async-results` | Retrieve results of asynchronous workflow execution | `request: AsyncResultRequest (@RequestBody)` | `Result<AsyncResultResponse>` |

---

## 2. server-start (builder)

### AccountController
- **Base path**: `/console/v1/accounts`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/accounts` | Create a new user account | `account: Account (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/accounts/{accountId}` | Update an existing account's information | `accountId: String (@PathVariable), account: Account (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/accounts/{accountId}` | Delete an account by its ID | `accountId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/accounts/{accountId}` | Retrieve account information by ID | `accountId: String (@PathVariable)` | `Result<Account>` |
| GET | `/console/v1/accounts` | List accounts with pagination | `query: BaseQuery (@ApiModelAttribute)` | `Result<PagingList<Account>>` |
| PUT | `/console/v1/accounts/change-password` | Change the password for an account | `request: ChangePasswordRequest (@RequestBody)` | `Result<String>` |
| GET | `/console/v1/accounts/profile` | Retrieve the current user's account profile | — | `Result<Account>` |

### AgentSchemaController
- **Base path**: `/console/v1/agent-schemas`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/agent-schemas` | Create a new agent schema | `agentSchemaEntity: AgentSchemaEntity (@RequestBody)` | `Result<AgentSchemaEntity>` |
| PUT | `/console/v1/agent-schemas/{id}` | Update an existing agent schema | `id: Long (@PathVariable), agentSchemaEntity: AgentSchemaEntity (@RequestBody)` | `Result<AgentSchemaEntity>` |
| DELETE | `/console/v1/agent-schemas/{id}` | Delete an agent schema | `id: Long (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/agent-schemas/{id}` | Get an agent schema by ID | `id: Long (@PathVariable)` | `Result<AgentSchemaEntity>` |
| GET | `/console/v1/agent-schemas` | Get all agent schemas for the current workspace | — | `Result<List<AgentSchemaEntity>>` |
| GET | `/console/v1/agent-schemas/page` | Get agent schemas with pagination | `current: long (@RequestParam), size: long (@RequestParam)` | `Result<PagingList<AgentSchemaEntity>>` |
| GET | `/console/v1/agent-schemas/search` | Search agent schemas by name | `name: String (@RequestParam)` | `Result<List<AgentSchemaEntity>>` |
| PATCH | `/console/v1/agent-schemas/{id}/enabled` | Enable or disable an agent schema | `id: Long (@PathVariable), enabled: Boolean (@RequestParam)` | `Result<Void>` |

### ApiExampleController
- **Base path**: `/test/api/example`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/test/api/example/getOrder` | Retrieve order information via GET request | `headers: Map<String, String> (@RequestHeader)` | `Map<String, Object>` |
| POST | `/test/api/example/getOrder` | Retrieve order information via POST request with orderId in body | `headers: Map<String, String> (@RequestHeader), body: Map<String, Object> (@RequestBody)` | `Map<String, Object>` |
| POST | `/test/api/example/getOrder/{orderId}` | Retrieve order information with orderId in path and request body | `headers: Map<String, String> (@RequestHeader), orderId: String (@PathVariable), body: Map<String, Object> (@RequestBody)` | `Map<String, Object>` |

### ApiKeyController
- **Base path**: `/console/v1/api-keys`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/api-keys` | Create a new API key | `apiKey: ApiKey (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/api-keys/{id}` | Update an existing API key | `id: Long (@PathVariable), apiKey: ApiKey (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/api-keys/{id}` | Delete an API key | `id: Long (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/api-keys/{id}` | Retrieve a specific API key | `id: Long (@PathVariable)` | `Result<ApiKey>` |
| GET | `/console/v1/api-keys` | List API keys with pagination | `query: BaseQuery (@ModelAttribute)` | `Result<PagingList<ApiKey>>` |

### AppChatController
- **Base path**: `/console/v1/apps`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/apps/chat/completions` | Handles chat completion requests (streaming + non-streaming) | `request: AgentRequest (@RequestBody)` | `Object` |

### AppComponentController
- **Base path**: `/console/v1/component-servers`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/console/v1/component-servers` | Retrieve paginated list of application components | `request: AppComponentQuery (@ApiModelAttribute)` | `Result<PagingList<AppComponent>>` |
| GET | `/console/v1/component-servers/app-publishable` | Retrieve paginated list of applications publishable as components | `request: AppComponentQuery (@ApiModelAttribute)` | `Result<PagingList<Application>>` |
| POST | `/console/v1/component-servers` | Publish new application component | `request: AppComponentQuery (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/component-servers/{code}` | Update existing application component | `code: String (@PathVariable), request: AppComponentQuery (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/component-servers/{code}` | Delete application component | `code: String (@PathVariable)` | `Result<Boolean>` |
| GET | `/console/v1/component-servers/{code}/detail-by-code` | Retrieve detailed component information by code | `code: String (@PathVariable)` | `Result<AppComponent>` |
| GET | `/console/v1/component-servers/{appId}/detail-by-appid` | Retrieve detailed component information by application ID | `appId: String (@PathVariable)` | `Result<AppComponent>` |
| GET | `/console/v1/component-servers/{code}/query-refer` | Query components that reference specified component | `code: String (@PathVariable)` | `Result<List<AppComponent>>` |
| GET | `/console/v1/component-servers/{appId}/query-config` | Query component configuration by application ID | `appId: String (@PathVariable)` | `Result<AppComponent>` |
| POST | `/console/v1/component-servers/query-by-codes` | Retrieve list of components by unique codes | `request: AppComponentQuery (@RequestBody)` | `Result<List<AppComponent>>` |
| GET | `/console/v1/component-servers/{code}/query-schema` | Retrieve schema of component by code | `code: String (@PathVariable)` | `Result<Map<String, Object>>` |
| POST | `/console/v1/component-servers/schema-by-codes` | Retrieve schemas for multiple components | `request: AppComponentQuery (@RequestBody)` | `Result<Map<String, Object>>` |

### AppController
- **Base path**: `/console/v1/apps`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/apps` | Create a new application | `app: Application (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/apps/{appId}` | Update an existing application | `appId: String (@PathVariable), app: Application (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/apps/{appId}` | Delete an application | `appId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/apps/{appId}` | Retrieve an application by ID | `appId: String (@PathVariable)` | `Result<Application>` |
| GET | `/console/v1/apps` | List applications with pagination | `query: AppQuery (@ApiModelAttribute)` | `Result<PagingList<Application>>` |
| POST | `/console/v1/apps/{appId}/publish` | Publish an application | `appId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/apps/{appId}/versions` | List application versions | `appId: String (@PathVariable), query: AppQuery (@ApiModelAttribute)` | `Result<PagingList<ApplicationVersion>>` |
| GET | `/console/v1/apps/{appId}/versions/{version}` | Get a specific application version | `appId: String (@PathVariable), version: String (@PathVariable)` | `Result<ApplicationVersion>` |
| POST | `/console/v1/apps/{appId}/copy` | Create a copy of an application | `appId: String (@PathVariable)` | `Result<String>` |

### AuthController
- **Base path**: `/console/v1/auth`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/auth/login` | Authenticate user and return access tokens | `loginRequest: LoginRequest (@RequestBody)` | `Result<TokenResponse>` |
| POST | `/console/v1/auth/refresh-token` | Refresh access token using refresh token | `request: RefreshTokenRequest (@RequestBody)` | `Result<TokenResponse>` |
| POST | `/console/v1/auth/logout` | Invalidate user's access token | — | `Result<Void>` |

### DocumentChunkController
- **Base path**: `/console/v1/documents`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/documents/{docId}/chunks` | Create a new document chunk | `docId: String (@PathVariable), chunk: DocumentChunk (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/documents/{docId}/chunks/{chunkId}` | Update an existing document chunk | `docId: String (@PathVariable), chunkId: String (@PathVariable), chunk: DocumentChunk (@RequestBody)` | `Result<Void>` |
| DELETE | `/console/v1/documents/{docId}/chunks/{chunkId}` | Delete a single document chunk | `docId: String (@PathVariable), chunkId: String (@PathVariable)` | `Result<Void>` |
| DELETE | `/console/v1/documents/{docId}/chunks/batch-delete` | Batch delete multiple document chunks | `docId: String (@PathVariable), request: DeleteChunkRequest (@RequestBody)` | `Result<Void>` |
| GET | `/console/v1/documents/{docId}/chunks` | List document chunks with pagination | `docId: String (@PathVariable), query: BaseQuery (@ModelAttribute)` | `Result<PagingList<DocumentChunk>>` |
| POST | `/console/v1/documents/{docId}/chunks/preview` | Preview document chunks before indexing | `docId: String (@PathVariable), request: IndexDocumentRequest (@RequestBody)` | `Result<List<DocumentChunk>>` |
| PUT | `/console/v1/documents/{docId}/chunks/update-status` | Update the enabled status of document chunks | `docId: String (@PathVariable), request: UpdateChunkRequest (@RequestBody)` | `Result<Void>` |

### DocumentController
- **Base path**: `/console/v1/knowledge-bases`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/knowledge-bases/{kbId}/documents` | Create new documents in the knowledge base | `kbId: String (@PathVariable), request: CreateDocumentRequest (@RequestBody)` | `Result<List<String>>` |
| PUT | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | Update an existing document | `kbId: String (@PathVariable), docId: String (@PathVariable), document: Document (@RequestBody)` | `Result<Void>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | Delete a single document | `kbId: String (@PathVariable), docId: String (@PathVariable)` | `Result<Void>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}/documents/batch-delete` | Delete multiple documents in batch | `kbId: String (@PathVariable), request: DeleteDocumentRequest (@RequestBody)` | `Result<Void>` |
| GET | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | Retrieve a single document by ID | `kbId: String (@PathVariable), docId: String (@PathVariable)` | `Result<Document>` |
| GET | `/console/v1/knowledge-bases/{kbId}/documents` | List documents with pagination | `kbId: String (@PathVariable), query: DocumentQuery (@ApiModelAttribute)` | `Result<PagingList<Document>>` |
| PUT | `/console/v1/knowledge-bases/{kbId}/documents/{docId}/re-index` | Re-index document with process and chunking configuration | `kbId: String (@PathVariable), docId: String (@PathVariable), request: IndexDocumentRequest (@RequestBody)` | `Result<Void>` |

### FileController
- **Base path**: `/console/v1/files`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/files/upload` | Upload multiple files and return their upload policies | `files: MultipartFile[] (@RequestPart), category: String (@RequestPart)` | `Result<List<UploadPolicy>>` |
| GET | `/console/v1/files/download` | Download a file from the server | `path: String (@RequestParam), preview: boolean (@RequestParam)` | `void` |
| POST | `/console/v1/files/upload-policies` | Get multiple file upload policies for OSS | `request: WebUploadRequest (@RequestBody)` | `Result<List<WebUploadPolicy>>` |
| GET | `/console/v1/files/get-preview-url` | Get preview URL for a file | `path: String (@RequestParam)` | `Result<String>` |

### KnowledgeBaseController
- **Base path**: `/console/v1/knowledge-bases`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/knowledge-bases` | Create a new knowledge base | `kb: KnowledgeBase (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/knowledge-bases/{kbId}` | Update an existing knowledge base | `kbId: String (@PathVariable), kb: KnowledgeBase (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}` | Delete a knowledge base | `kbId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/knowledge-bases/{kbId}` | Retrieve a knowledge base by ID | `kbId: String (@PathVariable)` | `Result<KnowledgeBase>` |
| GET | `/console/v1/knowledge-bases` | List knowledge bases with pagination | `query: BaseQuery (@ApiModelAttribute)` | `Result<PagingList<KnowledgeBase>>` |
| POST | `/console/v1/knowledge-bases/query-by-codes` | Retrieve knowledge bases by their IDs | `query: KnowledgeBaseQuery (@RequestBody)` | `Result<List<KnowledgeBase>>` |
| POST | `/console/v1/knowledge-bases/retrieve` | Retrieve relevant document chunks based on query | `query: DocumentRetrieverQuery (@RequestBody)` | `Result<List<DocumentChunk>>` |

### McpServerController
- **Base path**: `/console/v1/mcp-servers`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/mcp-servers` | Create a new MCP server with the provided configuration | `detail: McpServerDetail (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/mcp-servers` | Update an existing MCP server with new configuration | `detail: McpServerDetail (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/mcp-servers/{serverCode}` | Delete an MCP server | `serverCode: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/mcp-servers/{serverCode}` | Get detailed information about a specific MCP server | `serverCode: String (@PathVariable), needTools: Boolean (@RequestParam)` | `Result<McpServerDetail>` |
| GET | `/console/v1/mcp-servers` | Get a paginated list of MCP servers based on the provided query criteria | `query: McpQuery (@ApiModelAttribute)` | `Result<PagingList<McpServerDetail>>` |
| POST | `/console/v1/mcp-servers/query-by-codes` | Get a list of MCP servers based on a list of server codes | `query: McpQuery (@RequestBody)` | `Result<List<McpServerDetail>>` |
| POST | `/console/v1/mcp-servers/debug-tools` | Debug a tool on a specific MCP server | `request: McpServerCallToolRequest (@RequestBody)` | `Result<McpServerCallToolResponse>` |

### ModelController
- **Base path**: `/console/v1/models`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/console/v1/models/{modelType}/selector` | Model selector API — retrieve models grouped by provider for a model type | `modelType: String (@PathVariable)` | `Result<List<ModelProviderGroup>>` |
| GET | `/console/v1/models/enabled` | Get all enabled models for prompt usage | — | `Result<List<Map<String, Object>>>` |

### Oauth2Controller
- **Base path**: `/oauth2`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/oauth2/login/github` | GitHub login authentication | — | `Result<String>` |
| GET | `/oauth2/callback/github` | GitHub OAuth2 callback handler | `code: String (@RequestParam)` | `void` |

### PluginController
- **Base path**: `/console/v1`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/plugins` | Create a new plugin | `request: Plugin (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/plugins/{pluginId}` | Update an existing plugin | `pluginId: String (@PathVariable), request: Plugin (@RequestBody)` | `Result<Void>` |
| DELETE | `/console/v1/plugins/{pluginId}` | Delete a plugin | `pluginId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/plugins/{pluginId}` | Retrieve a plugin by ID | `pluginId: String (@PathVariable)` | `Result<Plugin>` |
| GET | `/console/v1/plugins` | List plugins with pagination | `request: BaseQuery (@ModelAttribute)` | `Result<PagingList<Plugin>>` |
| POST | `/console/v1/plugins/{pluginId}/tools` | Create a new tool for a plugin | `pluginId: String (@PathVariable), tool: Tool (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/plugins/{pluginId}/tools/{toolId}` | Update an existing tool | `pluginId: String (@PathVariable), toolId: String (@PathVariable), tool: Tool (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/plugins/{pluginId}/tools/{toolId}` | Delete a tool | `pluginId: String (@PathVariable), toolId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/plugins/{pluginId}/tools/{toolId}` | Retrieve a tool by ID | `pluginId: String (@PathVariable), toolId: String (@PathVariable)` | `Result<Tool>` |
| GET | `/console/v1/plugins/{pluginId}/tools` | List tools for a plugin with pagination | `pluginId: String (@PathVariable), query: ToolQuery (@ModelAttribute)` | `Result<PagingList<Tool>>` |
| POST | `/console/v1/tools/{toolId}/enable` | Enable a tool | `toolId: String (@PathVariable)` | `Result<Void>` |
| POST | `/console/v1/tools/{toolId}/disable` | Disable a tool | `toolId: String (@PathVariable)` | `Result<Void>` |
| POST | `/console/v1/plugins/{pluginId}/tools/{toolId}/test` | Test a tool execution | `pluginId: String (@PathVariable), toolId: String (@PathVariable), request: ToolExecutionRequest (@RequestBody)` | `Result<ToolExecutionResult>` |
| POST | `/console/v1/plugins/{pluginId}/tools/{toolId}/publish` | Publish a tool | `pluginId: String (@PathVariable), toolId: String (@PathVariable)` | `Result<Void>` |
| POST | `/console/v1/tools/query-by-ids` | Query tools by their IDs | `query: ToolQuery (@RequestBody)` | `Result<List<Tool>>` |

### ProviderController
- **Base path**: `/console/v1/providers`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/providers` | Add a new model provider to the system | `request: AddProviderRequest (@RequestBody)` | `Result<Boolean>` |
| PUT | `/console/v1/providers/{provider}` | Update an existing model provider | `provider: String (@PathVariable), request: UpdateProviderRequest (@RequestBody)` | `Result<Boolean>` |
| DELETE | `/console/v1/providers/{provider}` | Delete a model provider from the system | `provider: String (@PathVariable)` | `Result<Boolean>` |
| GET | `/console/v1/providers` | Query available model providers | `request: QueryProviderRequest (@ModelAttribute)` | `Result<List<ProviderConfigInfo>>` |
| GET | `/console/v1/providers/{provider}` | Retrieve detailed information about a specific provider | `provider: String (@PathVariable)` | `Result<ProviderConfigInfo>` |
| POST | `/console/v1/providers/{provider}/models` | Add a new model to a provider | `provider: String (@PathVariable), request: AddModelRequest (@RequestBody)` | `Result<Boolean>` |
| PUT | `/console/v1/providers/{provider}/models/{modelId}` | Update an existing model configuration | `provider: String (@PathVariable), modelId: String (@PathVariable), request: UpdateModelRequest (@RequestBody)` | `Result<Boolean>` |
| DELETE | `/console/v1/providers/{provider}/models/{modelId}` | Delete a model from a provider | `provider: String (@PathVariable), modelId: String (@PathVariable)` | `Result<Boolean>` |
| GET | `/console/v1/providers/{provider}/models` | Retrieve a list of models for a specific provider | `provider: String (@PathVariable)` | `Result<List<ModelConfigInfo>>` |
| GET | `/console/v1/providers/{provider}/models/{modelId}` | Retrieve detailed information about a specific model | `provider: String (@PathVariable), modelId: String (@PathVariable)` | `Result<ModelConfigInfo>` |
| GET | `/console/v1/providers/{provider}/models/{modelId}/parameter_rules` | Retrieve parameter rules for a specific model | `provider: String (@PathVariable), modelId: String (@PathVariable)` | `Result<List<ParameterRule>>` |
| GET | `/console/v1/providers/protocols` | Retrieve a list of supported provider protocols | — | `Result<List<String>>` |

### SystemController
- **Base path**: `/console/v1/system`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/console/v1/system/global-config` | Get global system configuration | — | `Result<GlobalConfig>` |
| GET | `/console/v1/system/health` | Health check endpoint | — | `String` |

### ToolController
- **Base path**: `/console/v1/tools`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/tools` | Create a new tool | `toolEntity: ToolEntity (@RequestBody)` | `Result<ToolEntity>` |
| PUT | `/console/v1/tools/{id}` | Update an existing tool | `id: Long (@PathVariable), toolEntity: ToolEntity (@RequestBody)` | `Result<ToolEntity>` |
| DELETE | `/console/v1/tools/{id}` | Delete a tool | `id: Long (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/tools/{id}` | Get a tool by ID | `id: Long (@PathVariable)` | `Result<ToolEntity>` |
| GET | `/console/v1/tools` | Get all tools for the current workspace | — | `Result<List<ToolEntity>>` |
| GET | `/console/v1/tools/page` | Get tools with pagination | `current: long (@RequestParam), size: long (@RequestParam)` | `Result<PagingList<ToolEntity>>` |
| GET | `/console/v1/tools/search` | Search tools by name | `name: String (@RequestParam)` | `Result<List<ToolEntity>>` |
| GET | `/console/v1/tools/plugin/{pluginId}` | Get tools by plugin ID | `pluginId: String (@PathVariable)` | `Result<List<ToolEntity>>` |
| PATCH | `/console/v1/tools/{id}/enabled` | Enable or disable a tool | `id: Long (@PathVariable), enabled: Boolean (@RequestParam)` | `Result<Void>` |

### WorkflowController
- **Base path**: `/console/v1/apps`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/apps/workflow/debug/run-task` | Execute a workflow task in debug mode | `request: TaskRunRequest (@RequestBody)` | `Result<TaskRunResponse>` |
| POST | `/console/v1/apps/workflow/debug/get-task-process` | Retrieve the current execution status of a workflow task | `request: ProcessGetRequest (@RequestBody)` | `Result<ProcessGetResponse>` |
| POST | `/console/v1/apps/workflow/debug/init` | Initialize workflow debug parameters | `request: InitRequest (@RequestBody)` | `Result<List<TaskRunParam>>` |
| POST | `/console/v1/apps/workflow/debug/resume-task` | Resume a paused workflow task | `request: TaskResumeRequest (@RequestBody)` | `Result<TaskResumeResponse>` |
| POST | `/console/v1/apps/workflow/debug/part-graph/run-task` | Execute a partial workflow graph for testing | `request: TaskPartGraphRequest (@RequestBody)` | `Result<TaskPartGraphResponse>` |
| POST | `/console/v1/apps/workflow/debug/part-graph/stop-task` | Stop a partial workflow graph execution | `request: TaskStopRequest (@RequestBody)` | `Result<Boolean>` |
| POST | `/console/v1/apps/workflow/{appId}/run_stream` | Stream workflow execution events using SSE | `appId: String (@PathVariable), request: ApiTaskRunRequest (@RequestBody)` | `SseEmitter` |

### WorkspaceController
- **Base path**: `/console/v1/workspaces`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/console/v1/workspaces` | Create a new workspace | `workspace: Workspace (@RequestBody)` | `Result<String>` |
| PUT | `/console/v1/workspaces/{workspaceId}` | Update an existing workspace | `workspaceId: String (@PathVariable), workspace: Workspace (@RequestBody)` | `Result<String>` |
| DELETE | `/console/v1/workspaces/{workspaceId}` | Delete a workspace | `workspaceId: String (@PathVariable)` | `Result<Void>` |
| GET | `/console/v1/workspaces/{workspaceId}` | Retrieve a specific workspace | `workspaceId: String (@PathVariable)` | `Result<Workspace>` |
| GET | `/console/v1/workspaces` | List workspaces with pagination | `query: BaseQuery (@ModelAttribute)` | `Result<PagingList<Workspace>>` |

---

## 3. server-start (generator)

### ApplicationController
- **Base path**: `/graph-studio/api/app`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/graph-studio/api/app` | Create app | `param: CreateAppParam (@RequestBody)` | `R<App>` |
| GET | `/graph-studio/api/app` | List apps | — | `R<List<App>>` |
| GET | `/graph-studio/api/app/{id}` | Get app by id | `id: String (@PathVariable)` | `R<App>` |
| PUT | `/graph-studio/api/app` | Sync app | `app: App (@RequestBody)` | `R<App>` |
| DELETE | `/graph-studio/api/app/{id}` | Delete app | `id: String (@PathVariable)` | `R<Boolean>` |

### DSLController
- **Base path**: `/graph-studio/api/dsl`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/graph-studio/api/dsl/export/{id}` | Export app to DSL | `id: String (@PathVariable), dialect: String (@RequestParam)` | `R<String>` |
| GET | `/graph-studio/api/dsl/export-file/{id}` | Export app to DSL file | `id: String (@PathVariable), dialect: String (@RequestParam)` | `ResponseEntity<Resource>` |
| POST | `/graph-studio/api/dsl/import` | Import app from DSL | `param: DSLParam (@RequestBody)` | `R<App>` |
| POST | `/graph-studio/api/dsl/import-file` | Import app from DSL file | `file: MultipartFile (@RequestPart), dialect: String (@RequestParam)` | `R<App>` |

### GeneratorController
- **Base path**: _(none)_

> No explicit endpoints. Extends `ProjectGenerationController` from Spring Initializr; registered as a regular `@Bean` and not annotated with `@RestController`/`@RequestMapping`. Endpoints are inherited from the Initializr framework.

### RunnerController
- **Base path**: `/graph-studio/api/run`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/graph-studio/api/run/app/{id}/stream` | Run app in stream mode | `id: String (@PathVariable), inputs: Map<String, Object> (@RequestBody)` | `Flux<RunEvent>` |
| POST | `/graph-studio/api/run/app/{id}/sync` | Run app in sync mode | `id: String (@PathVariable), inputs: Map<String, Object> (@RequestBody)` | `R<RunEvent>` |

---

## 4. server-start (platform)

### DatasetController
- **Base path**: `/api/dataset`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/api/dataset/dataset` | Create evaluation dataset | `datasetCreateRequest: DatasetCreateRequest (@RequestBody)` | `Result<Dataset>` |
| POST | `/api/dataset/datasetVersion` | Create dataset version | `datasetVersionCreateRequest: DatasetVersionCreateRequest (@RequestBody)` | `Result<DatasetVersion>` |
| GET | `/api/dataset/datasets` | Get evaluation dataset list | `request: DatasetListRequest (@ModelAttribute)` | `Result<PageResult<Dataset>>` |
| GET | `/api/dataset/dataset` | Get evaluation dataset details | `datasetId: Long (@RequestParam)` | `Result<Dataset>` |
| PUT | `/api/dataset/dataset` | Update evaluation dataset | `datasetUpdateRequest: DatasetUpdateRequest (@RequestBody)` | `Result<Dataset>` |
| DELETE | `/api/dataset/dataset` | Delete evaluation dataset | `datasetId: Long (@RequestParam)` | `Result<Void>` |
| POST | `/api/dataset/dataItem` | Create data item | `datasetItemCreateRequest: DatasetItemCreateRequest (@RequestBody)` | `Result<List<DatasetItem>>` |
| GET | `/api/dataset/dataItems` | Get data item list | `request: DatasetItemListRequest (@ModelAttribute)` | `Result<PageResult<DatasetItem>>` |
| GET | `/api/dataset/dataItem` | Get data item details | `id: Long (@PathVariable)` | `Result<DatasetItem>` |
| PUT | `/api/dataset/dataItem` | Update data item | `request: DatasetItemUpdateRequest (@RequestBody)` | `Result<DatasetItem>` |
| DELETE | `/api/dataset/dataItem` | Delete data item | `id: Long (@RequestParam)` | `Result<Void>` |
| GET | `/api/dataset/datasetVersions` | Get dataset version list | `request: DatasetVersionListRequest (@ModelAttribute)` | `Result<PageResult<DatasetVersion>>` |
| PUT | `/api/dataset/datasetVersion` | Update dataset version | `datasetVersionUpdateRequest: DatasetVersionUpdateRequest (@RequestBody)` | `Result<DatasetVersion>` |
| GET | `/api/dataset/experiments` | Get associated experiments | `datasetExperimentsListRequest: DatasetExperimentsListRequest (@ModelAttribute)` | `Result<PageResult<Experiment>>` |
| POST | `/api/dataset/dataItemFromTrace` | Create data item from trace | `dataItemCreateFromTraceRequest: DataItemCreateFromTraceRequest (@RequestBody)` | `Result<List<DatasetItem>>` |

### EvaluatorController
- **Base path**: `/api/evaluator`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/api/evaluator/evaluator` | Create evaluator | `request: EvaluatorCreateRequest (@RequestBody)` | `Result<Evaluator>` |
| POST | `/api/evaluator/evaluatorVersion` | Create evaluator version | `request: EvaluatorVersionCreateRequest (@RequestBody)` | `Result<EvaluatorVersion>` |
| GET | `/api/evaluator/evaluators` | Get evaluator list | `evaluatorListRequest: EvaluatorListRequest (@ModelAttribute)` | `Result<PageResult<Evaluator>>` |
| GET | `/api/evaluator/evaluator` | Get evaluator details | `id: Long (@RequestParam)` | `Result<Evaluator>` |
| GET | `/api/evaluator/evaluatorVersions` | Get evaluator version list | `request: EvaluatorVersionListRequest (@ModelAttribute)` | `Result<PageResult<EvaluatorVersion>>` |
| PUT | `/api/evaluator/evaluator` | Update evaluator | `request: EvaluatorUpdateRequest (@RequestBody)` | `Result<Evaluator>` |
| DELETE | `/api/evaluator/evaluator` | Delete evaluator | `id: Long (@RequestParam)` | `Result<Void>` |
| POST | `/api/evaluator/debug` | Debug evaluator | `request: EvaluatorTestRequest (@RequestBody)` | `Result<EvaluatorDebugResult>` |
| GET | `/api/evaluator/templates` | Get evaluator template list | `request: EvaluatorTemplateListRequest (@ModelAttribute)` | `Result<PageResult<EvaluatorTemplate>>` |
| GET | `/api/evaluator/template` | Get evaluator template details | `templateId: Long (@RequestParam)` | `Result<EvaluatorTemplate>` |
| GET | `/api/evaluator/experiments` | Get evaluator associated experiments | `request: EvaluatorExperimentsListRequest (@ModelAttribute)` | `Result<PageResult<Experiment>>` |

### ExperimentController
- **Base path**: `/api`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/api/experiment` | Create experiment | `request: ExperimentCreateRequest (@RequestBody)` | `Result<Experiment>` |
| GET | `/api/experiments` | Get experiment list | `request: ExperimentListRequest (@ModelAttribute)` | `Result<PageResult<Experiment>>` |
| GET | `/api/experiment` | Get experiment details | `experimentId: Long (@RequestParam)` | `Result<Experiment>` |
| GET | `/api/experiment/results` | Get experiment overview results | `experimentId: Long (@RequestParam)` | `Result<List<ExperimentEvaluatorResult>>` |
| GET | `/api/experiment/result` | Get experiment detailed results | `request: ExperimentEvaluatorResultDetailListRequest (@ModelAttribute)` | `Result<PageResult<ExperimentEvaluatorResultDetail>>` |
| PUT | `/api/experiment/stop` | Stop experiment | `experimentId: Long (@RequestParam)` | `Result<Experiment>` |
| DELETE | `/api/experiment` | Delete experiment | `experimentId: Long (@RequestParam)` | `Result<Void>` |
| PUT | `/api/experiment/restart` | Restart experiment | `experimentId: Long (@RequestParam)` | `Result<Void>` |

### ModelConfigController
- **Base path**: `/api`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/api/model/supported` | Get supported model providers | — | `Result<List<String>>` |
| GET | `/api/models` | Get model configuration list | `request: ModelConfigQueryRequest (@ModelAttribute)` | `Result<PageResult<ModelConfigResponse>>` |
| GET | `/api/model` | Get model configuration details | `id: Long (@RequestParam)` | `Result<ModelConfigResponse>` |
| GET | `/api/models/enabled` | Get enabled model configurations | — | `Result<List<ModelConfigResponse>>` |

### ObservabilityController
- **Base path**: `/api/observability`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| GET | `/api/observability/traces` | Get trace list | `request: TracesQueryRequest (@ModelAttribute)` | `Result<PageResult<TraceSpanDTO>>` |
| GET | `/api/observability/traces/{traceId}` | Get single trace details | `traceId: String (@PathVariable)` | `Result<TraceDetailDTO>` |
| GET | `/api/observability/services` | Get service list | `request: ServicesQueryRequest (@ModelAttribute)` | `Result<ServicesResponseDTO>` |
| GET | `/api/observability/overview` | Get overview information | `request: OverviewQueryRequest (@ModelAttribute)` | `Result<OverviewStatsDTO>` |

### PromptController
- **Base path**: `/api`

| Method | Path | Description | Params | Return |
|---|---|---|---|---|
| POST | `/api/prompt` | Create prompt | `request: PromptCreateRequest (@RequestBody)` | `Result<Prompt>` |
| GET | `/api/prompt` | Get prompt details | `promptKey: String (@RequestParam)` | `Result<Prompt>` |
| GET | `/api/prompts` | Get prompt list | `request: PromptListRequest (@ModelAttribute)` | `Result<PageResult<Prompt>>` |
| PUT | `/api/prompt` | Update prompt | `request: PromptUpdateRequest (@RequestBody)` | `Result<Prompt>` |
| DELETE | `/api/prompt` | Delete prompt | `promptKey: String (@RequestParam)` | `Result<Boolean>` |
| POST | `/api/prompt/version` | Create prompt version | `request: PromptVersionCreateRequest (@RequestBody)` | `Result<PromptVersion>` |
| GET | `/api/prompt/version` | Get prompt version details | `promptKey: String (@RequestParam), version: String (@RequestParam)` | `Result<PromptVersionDetail>` |
| GET | `/api/prompt/versions` | Get prompt version list | `request: PromptVersionListRequest (@ModelAttribute)` | `Result<PageResult<PromptVersion>>` |
| GET | `/api/prompt/template` | Get prompt template details | `promptTemplateKey: String (@RequestParam)` | `Result<PromptTemplateDetail>` |
| GET | `/api/prompt/templates` | Get prompt template list | `request: PromptTemplateListRequest (@ModelAttribute)` | `Result<PageResult<PromptTemplate>>` |
| POST | `/api/prompt/run` | Run prompt debug with streaming support | `request: PromptRunRequest (@RequestBody)` | `Flux<PromptRunResponse>` |
| GET | `/api/prompt/session` | Get session information | `sessionId: String (@RequestParam)` | `Result<ChatSession>` |
| DELETE | `/api/prompt/session` | Delete session | `sessionId: String (@RequestParam)` | `Result<Void>` |
