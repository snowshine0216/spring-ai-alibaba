# Regen recipe: docs/api-list.md

## Sources to scan
- spring-ai-alibaba-admin-server-start/src/main/java/**/*Controller.java
- spring-ai-alibaba-admin-server-openapi/src/main/java/**/*Controller.java
- spring-ai-alibaba-admin-server-core/src/main/java/**/*Controller.java

## Step 1: Read the current doc

Use Read on `docs/api-list.md`. Note the existing header, table column order, and section ordering. The regen output MUST preserve these.

## Step 2: Collect controller files

Use Glob with each source pattern above. Then Grep `-l` for `@RestController|@Controller` across the results to filter out non-controllers (e.g., abstract base classes).

## Step 3: Extract endpoints

For each controller file, Read it and extract:
- Class-level `@RequestMapping("...")` path prefix (empty if absent).
- For each public method annotated with `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, or `@RequestMapping`:
  - HTTP verb (from the annotation type, or `method=` arg of `@RequestMapping`).
  - Full path: class prefix + method path.
  - Parameters: each `@PathVariable`, `@RequestParam`, `@RequestBody` — record the Java type (simple name) and the parameter name.
  - Return type: simple class name. Peel one layer of `Result<...>`, `ResponseEntity<...>`, `SseEmitter`, `Flux<...>`.

## Step 4: Emit the new doc

Match the existing `docs/api-list.md` format:
- One section per controller (use class simple name as heading).
- Section ordering: `/console/v1/**` first, then `/api/v1/apps/**`, then `/api/{dataset|evaluator|experiment|prompt|observability|model}/**`, then `/graph-studio/**`.
- Within a section, methods ordered by path.
- Markdown table columns: `Method | Path | Params | Returns | Notes`.
- Update the count line at the top: "REST API inventory — N endpoints across M controllers" with current N, M.

## Step 5: Write

Use Write to overwrite `docs/api-list.md`.

## Step 6: Stage

Run: `git add docs/api-list.md`.

## Forbidden modifications
- Never remove `POST /api/prompts/search` (community-exposed; see admin module CLAUDE.md).
- Never relabel `external_key` if it appears as a parameter or schema reference.
