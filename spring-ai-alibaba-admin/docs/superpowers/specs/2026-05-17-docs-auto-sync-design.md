# docs-auto-sync — Design Spec

**Date:** 2026-05-17
**Scope:** `spring-ai-alibaba-admin/` (Maven module)
**Owner:** snow

## Goal

Keep code-derived documents under `spring-ai-alibaba-admin/docs/` from drifting away from the source code. Specifically: when a contributor commits code that changes controllers, entities, SQL schemas, POMs, or `application*.yml`, the corresponding doc artifact is regenerated automatically and bundled into the same commit.

## Non-goals

- Regenerating the hand-curated `architecture-diagram.html`. The skill detects likely drift but never overwrites this file.
- CI-side enforcement. Initial scope is local enforcement via a Claude Code hook. A CI check is a possible follow-up but out of scope here.
- Regenerating files outside `docs/`. The skill is read-only on source code.

## Decisions

| Question | Decision |
|---|---|
| Trigger mechanism | Claude Code `PreToolUse` hook (local) |
| Hook event | `PreToolUse` on Bash, scoped to `git commit` and `gh pr create` commands |
| Sync scope | All code-derived docs in `docs/`, restricted to those whose sources actually changed |
| Sync behavior | Block the tool call; the skill regenerates affected docs and stages them; Claude retries the commit |
| Skill location | `spring-ai-alibaba-admin/.claude/skills/docs-auto-sync/` (project-scoped, checked into git) |

## Approach selected

**Smart selective regen.** A source→doc map drives both drift detection and regen. Only the docs whose sources changed are regenerated.

Alternatives considered:

- *Always regenerate all docs* — simpler, but slow and produces noisy diffs on every commit.
- *Drift-detect + manual sync* — most user control, but defeats the "auto" intent.

## Architecture

Two cooperating pieces:

1. **Drift-detection hook** (bash, fast). Invoked by Claude Code's `PreToolUse` for Bash calls. Short-circuits when the command isn't `git commit` or `gh pr create`. Otherwise inspects `git diff --cached --name-only`, matches against globs in `source-doc-map.json`, and exits with code `2` plus a stderr message if any doc is stale. Exit code 2 causes Claude Code to feed stderr back to Claude as a blocking tool-use error.

2. **Regen skill** (Claude-driven, smart). When Claude sees the hook's block, it invokes the `docs-auto-sync` skill. The skill reads the list of stale docs, runs per-doc regen recipes (delegating to the existing `codebase-analysis` skill where possible), writes the regenerated docs back to their original paths, stages them with `git add`, and tells the user to retry the original commit.

### End-to-end flow

```
Claude runs: git commit -m "..." (or gh pr create ...)
   ↓
PreToolUse hook (matcher: Bash)
   ↓
docs-drift-check.sh
   • git diff --cached --name-only
   • Match each path against source globs in source-doc-map.json
   • If any matches → exit 2, stderr lists stale docs
   ↓ (Claude reads the block)
Claude invokes docs-auto-sync skill
   • Per stale doc, follow regen/<recipe>.md
   • Recipes delegate to codebase-analysis skill where possible
   • Write back to docs/<file>
   • git add docs/<regenerated files>
   • Report: "Synced. Retry your commit."
   ↓
Claude retries the original tool call — hook now passes — commit succeeds.
```

### File layout

```
spring-ai-alibaba-admin/
├── .claude/
│   ├── settings.json                       # checked in; hook config for all contributors
│   ├── hooks/
│   │   └── docs-drift-check.sh             # bash drift detector
│   └── skills/
│       └── docs-auto-sync/
│           ├── SKILL.md                    # main skill instructions
│           ├── source-doc-map.json         # source-glob → doc mapping
│           └── regen/
│               ├── api-list.md             # per-doc regen recipe
│               ├── data-model.md
│               ├── module-dependency.md
│               └── external-dependency.md
└── docs/                                   # existing — managed by the skill
    ├── api-list.md
    ├── data-model.md
    ├── data-model.html
    ├── module-dependency.html
    ├── external-dependency.html
    └── architecture-diagram.html           # NOT auto-regenerated (hand-curated)
```

## Source → Doc map

| Doc | Source globs (relative to `spring-ai-alibaba-admin/`) | Regen scope |
|---|---|---|
| `docs/api-list.md` | `**/src/main/java/**/*Controller.java`, `**/src/main/java/**/controller/**/*.java` | All `@RestController` / `@Controller` classes across the 4 admin modules. Extract class-level + method-level `@RequestMapping` / `@GetMapping` / etc. Emit Markdown table grouped by controller. |
| `docs/data-model.md` + `docs/data-model.html` | `docker/middleware/init/mysql/*.sql`, `**/src/main/java/**/entity/**/*.java`, `**/src/main/java/**/*Entity.java` | Parse two SQL schemas (`admin-schema.sql`, `agentscope-schema.sql`) for `CREATE TABLE`. Cross-reference with `@TableName`-annotated entities. Emit MD table per table + HTML with inline Mermaid ER diagram. |
| `docs/module-dependency.html` | `pom.xml`, `spring-ai-alibaba-admin-*/pom.xml` | Walk Maven reactor. For each `spring-ai-alibaba-admin-*` module, extract internal `<dependency>` entries. Emit HTML with inline Mermaid graph. |
| `docs/external-dependency.html` | `**/pom.xml`, `**/application*.yml`, `**/application*.yaml` | Detect middleware deps by GAV + config: MySQL, Redis, Elasticsearch, Nacos, RocketMQ, OSS, model-provider SDKs (DashScope / OpenAI / DeepSeek). Emit HTML grouped by category with version + role. |
| `docs/architecture-diagram.html` | (none — hand-curated) | **Never auto-regenerated.** Flag drift only if SQL schemas or module list changed substantially. Drift is reported as a TODO in skill output. |

### `source-doc-map.json` shape

```json
{
  "docs/api-list.md": {
    "globs": ["**/src/main/java/**/*Controller.java", "**/src/main/java/**/controller/**/*.java"],
    "regenRecipe": "regen/api-list.md"
  },
  "docs/data-model.md": {
    "globs": ["docker/middleware/init/mysql/*.sql", "**/src/main/java/**/entity/**/*.java", "**/src/main/java/**/*Entity.java"],
    "regenRecipe": "regen/data-model.md",
    "alsoRegen": ["docs/data-model.html"]
  },
  "docs/module-dependency.html": {
    "globs": ["pom.xml", "spring-ai-alibaba-admin-*/pom.xml"],
    "regenRecipe": "regen/module-dependency.md"
  },
  "docs/external-dependency.html": {
    "globs": ["**/pom.xml", "**/application*.yml", "**/application*.yaml"],
    "regenRecipe": "regen/external-dependency.md"
  }
}
```

## Policies

- **Glob-match → assume stale.** No mtime comparisons. If any staged file matches a glob, mark the corresponding doc stale. Occasional unneeded regen is acceptable.
- **Missing doc → skip silently.** If a doc file does not exist yet, do not block on it (first-run safe).
- **Architecture diagram is sacred.** Never overwritten. Drift is warned about only.
- **Restricted areas honored.** Per `spring-ai-alibaba-admin/CLAUDE.md`:
  - Preserve `external_key` on `PromptEntity` wherever it appears in docs.
  - Preserve the default `nacos.server-addr` value in `application.yml` when listed in `external-dependency.html`.
  - Always list `POST /api/prompts/search` in `api-list.md`.
- **Read-only on source.** The skill writes only to `docs/`.
- **Local enforcement only.** The hook is Claude Code-side, not git-side. Contributors using plain `git` outside Claude bypass it; this is acceptable for v1. CI enforcement is a follow-up.

## Components

### 1. `.claude/settings.json`

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "bash $CLAUDE_PROJECT_DIR/.claude/hooks/docs-drift-check.sh"
          }
        ]
      }
    ]
  }
}
```

### 2. `.claude/hooks/docs-drift-check.sh`

```bash
#!/usr/bin/env bash
# Claude Code PreToolUse hook on Bash. Reads tool input JSON on stdin.
# Exits 0 unless the command is git commit / gh pr create AND staged files
# affect a code-derived doc. In that case, exits 2 with stderr listing
# stale docs; Claude sees the stderr as a blocking error.
set -eo pipefail

# Fail open if jq is missing — never block a commit because of a tooling gap.
command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
STAGED=$(git diff --cached --name-only)
[ -z "$STAGED" ] && exit 0

MAP=".claude/skills/docs-auto-sync/source-doc-map.json"
STALE=()

while IFS= read -r doc; do
  globs=$(jq -r --arg d "$doc" '.[$d].globs[]' "$MAP")
  while IFS= read -r glob; do
    pattern=$(echo "$glob" | sed 's|\*\*|.*|g; s|\*|[^/]*|g')
    if echo "$STAGED" | grep -qE "$pattern"; then
      STALE+=("$doc")
      break
    fi
  done <<< "$globs"
done < <(jq -r 'keys[]' "$MAP")

if [ ${#STALE[@]} -gt 0 ]; then
  {
    echo "Docs drift detected. Source files affecting these docs are staged:"
    printf '  - %s\n' "${STALE[@]}"
    echo ""
    echo "Invoke the docs-auto-sync skill to regenerate, then retry the commit."
  } >&2
  exit 2
fi

exit 0
```

### 3. `.claude/skills/docs-auto-sync/SKILL.md`

```markdown
---
name: docs-auto-sync
description: Regenerate docs/ artifacts (api-list.md, data-model.md/.html, module-dependency.html, external-dependency.html) that depend on changed source files. Use when the PreToolUse hook blocks a git commit / gh pr create with a "Docs drift detected" message, or when the user runs /docs-auto-sync, or when they ask to "sync docs", "refresh API list", or "regenerate data model".
---

# docs-auto-sync

Regenerate stale docs in `docs/` based on the source→doc map.

## Inputs
- Staged source files (from `git diff --cached --name-only`)
- `source-doc-map.json` (sibling file)

## Process
1. Read `source-doc-map.json`. For each doc, check whether any staged file matches its globs.
2. For each stale doc, follow the self-contained recipe at `regen/<recipe>.md` using only built-in tools (Glob, Grep, Read, Edit, Write, Bash). Recipes do not depend on user-global skills.
3. Write the regenerated doc back to its original path. Do not touch other files in docs/.
4. `git add` the regenerated docs.
5. Report back to the user with: list of regenerated docs and a reminder to retry the original commit.

## Hard rules
- Never regenerate `docs/architecture-diagram.html` — it is hand-curated. If SQL schemas or module list changed substantially, emit a one-line TODO ("architecture-diagram.html may need manual review") but do not touch the file.
- Respect Forbidden Areas in CLAUDE.md:
  - `external_key` on `PromptEntity` — keep in any doc that mentions it.
  - Default `nacos.server-addr` value in `application.yml` — preserve verbatim.
  - `POST /api/prompts/search` path — keep listed in api-list.md.
- Read-only on source code. The skill only writes under `docs/`.

## Output
Print a short summary:
```
Regenerated:
  - docs/api-list.md (controllers: +2, endpoints: +7)
  - docs/data-model.md (tables: +1)
Staged. Retry your commit.
```
```

### 4. Per-doc regen recipes (`regen/*.md`)

Each recipe is **self-contained** — it does not depend on user-global skills (like the gstack `codebase-analysis`). Every step is expressed in terms of Claude's built-in tools (Glob, Grep, Read, Edit, Write, Bash). This ensures the skill works for every contributor who clones the repo.

Each recipe has the same shape:

- **Sources to scan** — explicit glob list, fed to Glob.
- **Extraction rules** — what to pull from each source (annotations, SQL DDL, YAML keys, Maven coordinates), expressed as Grep patterns + Read steps.
- **Output format** — must match the existing doc style; preserve front-matter, section order, counts header.
- **Forbidden modifications** — restated from CLAUDE.md.

Example sketch for `regen/api-list.md`:

```markdown
# Regen recipe: docs/api-list.md

## Sources to scan
- spring-ai-alibaba-admin-server-start/src/main/java/**/*Controller.java
- spring-ai-alibaba-admin-server-openapi/src/main/java/**/*Controller.java
- spring-ai-alibaba-admin-server-core/src/main/java/**/*Controller.java

## Extraction (steps Claude executes)
1. Use Glob with each source pattern above to collect all controller file paths.
2. Use Grep `-l` for `@RestController|@Controller` to filter to actual controller classes.
3. For each controller file, Read the file and extract:
   - Class-level @RequestMapping("...") path prefix.
   - For each public method:
     - HTTP verb from @GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping (or @RequestMapping method=)
     - Path from the annotation value, prepended with the class-level prefix.
     - Params: @PathVariable, @RequestParam, @RequestBody (record name + key type)
     - Return type: simple class name (peel `Result<...>`, `ResponseEntity<...>`, `SseEmitter`).

## Output format
Match the existing docs/api-list.md style (Read the current file first to confirm the format):
- One section per controller, ordered by path prefix.
- Markdown table: Method | Path | Params | Returns | Notes.
- Preserve the intro line ("REST API inventory — N endpoints across M controllers"); update counts.
- Group order: /console/v1 first, then /api/v1/apps, then /api/{dataset|evaluator|...}, then /graph-studio.

## Write
Write the regenerated content to docs/api-list.md using the Write tool.

## Forbidden modifications
- Never remove POST /api/prompts/search (path is community-exposed; see CLAUDE.md).
```

Recipes for `data-model.md`, `module-dependency.md`, `external-dependency.md` follow the same structure with their respective source globs and extraction rules — all using only Claude's built-in tools.

## User experience

1. Developer edits `PluginController.java`, stages it, asks Claude to commit.
2. Hook fires, detects `*Controller.java` matches the `api-list.md` glob, exits 2 with stderr.
3. Claude reads: *"Docs drift detected: docs/api-list.md. Invoke the docs-auto-sync skill."*
4. Claude invokes `docs-auto-sync` → recipe regenerates `api-list.md` → `git add docs/api-list.md` → "Staged. Retry your commit."
5. Claude retries `git commit` → hook passes → commit succeeds with the doc update bundled.

New contributors get the hook automatically (it's in `.claude/settings.json`, checked in). Per-machine opt-out is possible via `.claude/settings.local.json`.

## Testing strategy

- **Hook script:** unit-test with a small fixture repo. Cases: no staged files; staged file matches a glob; staged file matches no glob; non-commit Bash command; commit when doc is missing.
- **Skill end-to-end:** manually exercise the five scenarios (one per doc) by staging a single source change and asking Claude to commit. Verify the right doc gets regenerated and staged.
- **Bypass safety:** verify `.claude/settings.local.json` override disables the hook on demand.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Glob misses a source file → silent doc drift | Conservative globs (broad over narrow); the architecture-diagram TODO catches structural drift the glob can't see. |
| Regen produces a noisy diff that obscures the real change | Each recipe pins the output format to match the existing file. Deterministic ordering. If diffs are still noisy, follow up with a snapshot test. |
| Regen is slow → commit feels sluggish | Selective regen keeps the common case to one doc per commit. If a single regen exceeds a few seconds, optimize the recipe. |
| Contributor bypasses by running `git` outside Claude | Acceptable for v1. Follow-up: add a CI check that diffs docs/ against a regen on PR. |
| `jq` not installed on contributor machine | Hook fails open: log a one-line warning, exit 0. Document `jq` as a prerequisite in CONTRIBUTING. |

## Open items

None.
