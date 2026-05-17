---
name: docs-auto-sync
description: Regenerate docs/ artifacts (api-list.md, data-model.md/.html, module-dependency.html, external-dependency.html) that depend on changed source files. Use when the PreToolUse hook blocks a git commit / gh pr create with a "Docs drift detected" message, or when the user runs /docs-auto-sync, or when they ask to "sync docs", "refresh API list", or "regenerate data model".
---

# docs-auto-sync

Regenerate stale docs under `spring-ai-alibaba-admin/docs/` based on the source→doc map at `source-doc-map.json` (sibling of this file).

## Inputs

- The list of stale docs from the hook's stderr message (when invoked after a hook block).
- Otherwise, fall back to staged files from `git diff --cached --name-only`.
- `source-doc-map.json` — read it once at start.

## Process

1. Read `source-doc-map.json`.
2. Identify the set of stale docs. Prefer the list provided by the hook; otherwise derive it by matching staged files against each doc's `globs`.
3. For each stale doc, follow the self-contained recipe at `regen/<recipeName>.md`. Recipes use only built-in tools (Glob, Grep, Read, Edit, Write, Bash). They never depend on user-global skills.
4. Write the regenerated content back to the doc's original path. If `alsoRegen` is set, write those companion files too.
5. Stage the regenerated files: `git add <each doc>`.
6. Report a one-paragraph summary listing what was regenerated and reminding the user to retry the original commit.

## Hard rules

- **Never regenerate `docs/architecture-diagram.html`** — it is hand-curated. If SQL schemas or the module list changed substantially, emit a one-line TODO in your reply ("architecture-diagram.html may need manual review"), but do not touch the file.
- **Respect Forbidden Areas in CLAUDE.md:**
  - `external_key` on `PromptEntity` — keep in any doc that mentions it.
  - Default `nacos.server-addr` value in `application.yml` — preserve verbatim wherever it appears.
  - `POST /api/prompts/search` path — always present in `docs/api-list.md`.
- **Read-only on source code.** The skill writes only under `docs/`. Never edit Java, SQL, YAML, or POM files.

## Output format

Print exactly this format (counts are best-effort — leave blank or omit if not easy to compute):

```
Regenerated:
  - docs/api-list.md (controllers: +2, endpoints: +7)
  - docs/data-model.md (tables: +1)
Staged. Retry your commit.
```
