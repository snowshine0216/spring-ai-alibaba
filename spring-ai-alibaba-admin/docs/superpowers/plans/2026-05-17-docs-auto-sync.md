# docs-auto-sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Claude Code `PreToolUse` hook + companion skill that detects when staged source files affect code-derived docs under `spring-ai-alibaba-admin/docs/` and regenerates only the affected docs before `git commit` / `gh pr create` proceeds.

**Architecture:** Two cooperating pieces. A small bash hook script does drift detection (fast, runs on every Bash call); when it finds drift it exits with code 2 and a stderr message that Claude Code surfaces back to Claude as a blocking error. Claude then invokes the project-local `docs-auto-sync` skill, which reads a self-describing source→doc map, runs per-doc regen recipes using only built-in tools (Glob, Grep, Read, Write, Bash), stages the regenerated docs, and tells the user to retry the commit. The hook is wired up last so the skill itself can be developed without self-blocking.

**Tech Stack:** Bash 3.2+, `jq`, `git`, Claude Code hooks (`.claude/settings.json`), Claude Code skills (`.claude/skills/<name>/SKILL.md`).

**Spec:** [docs/superpowers/specs/2026-05-17-docs-auto-sync-design.md](../specs/2026-05-17-docs-auto-sync-design.md)

**Working directory:** All paths are relative to `spring-ai-alibaba-admin/` (the admin module root). When implementing, `cd` to this directory first.

---

## File structure

Files to create:

- `.claude/skills/docs-auto-sync/source-doc-map.json` — source→doc dependency map (single source of truth).
- `.claude/skills/docs-auto-sync/SKILL.md` — skill instructions invoked by Claude.
- `.claude/skills/docs-auto-sync/regen/api-list.md` — recipe for `docs/api-list.md`.
- `.claude/skills/docs-auto-sync/regen/data-model.md` — recipe for `docs/data-model.md` + `docs/data-model.html`.
- `.claude/skills/docs-auto-sync/regen/module-dependency.md` — recipe for `docs/module-dependency.html`.
- `.claude/skills/docs-auto-sync/regen/external-dependency.md` — recipe for `docs/external-dependency.html`.
- `.claude/hooks/docs-drift-check.sh` — drift-detection script.
- `.claude/hooks/tests/docs-drift-check.test.sh` — bash test harness for the hook.
- `.claude/settings.json` — hook config (wired up LAST after all tests pass).

Files to modify:

- `CLAUDE.md` — add a one-line pointer to the skill under a new "Skills" section.

---

## Task 1: Scaffold directories

**Files:**
- Create: `.claude/skills/docs-auto-sync/` (directory)
- Create: `.claude/skills/docs-auto-sync/regen/` (directory)
- Create: `.claude/hooks/tests/` (directory)

No commit at this step — git doesn't track empty directories. The first real commit happens in Task 2 when `source-doc-map.json` is added.

- [ ] **Step 1: Create the directory tree**

```bash
cd spring-ai-alibaba-admin
mkdir -p .claude/skills/docs-auto-sync/regen
mkdir -p .claude/hooks/tests
```

- [ ] **Step 2: Verify the tree**

Run: `find .claude -type d | sort`
Expected:
```
.claude
.claude/hooks
.claude/hooks/tests
.claude/skills
.claude/skills/docs-auto-sync
.claude/skills/docs-auto-sync/regen
```

---

## Task 2: Write `source-doc-map.json`

**Files:**
- Create: `.claude/skills/docs-auto-sync/source-doc-map.json`

- [ ] **Step 1: Write the map**

Content of `.claude/skills/docs-auto-sync/source-doc-map.json`:

```json
{
  "docs/api-list.md": {
    "globs": [
      "**/src/main/java/**/*Controller.java",
      "**/src/main/java/**/controller/**/*.java"
    ],
    "regenRecipe": "regen/api-list.md"
  },
  "docs/data-model.md": {
    "globs": [
      "docker/middleware/init/mysql/*.sql",
      "**/src/main/java/**/entity/**/*.java",
      "**/src/main/java/**/*Entity.java"
    ],
    "regenRecipe": "regen/data-model.md",
    "alsoRegen": ["docs/data-model.html"]
  },
  "docs/module-dependency.html": {
    "globs": [
      "pom.xml",
      "spring-ai-alibaba-admin-*/pom.xml"
    ],
    "regenRecipe": "regen/module-dependency.md"
  },
  "docs/external-dependency.html": {
    "globs": [
      "**/pom.xml",
      "**/application*.yml",
      "**/application*.yaml"
    ],
    "regenRecipe": "regen/external-dependency.md"
  }
}
```

- [ ] **Step 2: Validate the JSON**

Run: `jq . .claude/skills/docs-auto-sync/source-doc-map.json > /dev/null && echo OK`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/docs-auto-sync/source-doc-map.json
git commit -m "feat(docs-auto-sync): add source-to-doc dependency map"
```

---

## Task 3: Write the hook test harness

**Files:**
- Create: `.claude/hooks/tests/docs-drift-check.test.sh`

The harness creates a temp git repo per test, copies the real `source-doc-map.json` into it, stages whatever files the test specifies, pipes a JSON input simulating Claude Code's tool-input contract, and asserts on exit code + stderr.

- [ ] **Step 1: Write the harness**

Content of `.claude/hooks/tests/docs-drift-check.test.sh`:

```bash
#!/usr/bin/env bash
# Tests for docs-drift-check.sh
# Usage: bash .claude/hooks/tests/docs-drift-check.test.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
HOOK="$ROOT/.claude/hooks/docs-drift-check.sh"
MAP_SRC="$ROOT/.claude/skills/docs-auto-sync/source-doc-map.json"

PASS=0
FAIL=0

# run_test NAME STDIN_JSON EXPECTED_EXIT [STDERR_CONTAINS]
# Reads the SETUP env var to run setup commands inside the tmp repo
# (e.g., creating + staging files).
run_test() {
    local name="$1"; shift
    local input="$1"; shift
    local expected_exit="$1"; shift
    local stderr_contains="${1:-}"

    local tmp; tmp=$(mktemp -d)
    (
        cd "$tmp"
        git init -q
        git config user.email t@example.com
        git config user.name "Test"
        mkdir -p .claude/skills/docs-auto-sync
        cp "$MAP_SRC" .claude/skills/docs-auto-sync/source-doc-map.json
        eval "${SETUP:-:}"
    ) >/dev/null

    local actual_stderr actual_exit
    set +e
    actual_stderr=$(CLAUDE_PROJECT_DIR="$tmp" bash "$HOOK" <<< "$input" 2>&1 >/dev/null)
    actual_exit=$?
    set -e

    local ok=1
    [ "$actual_exit" = "$expected_exit" ] || ok=0
    if [ -n "$stderr_contains" ] && ! echo "$actual_stderr" | grep -q "$stderr_contains"; then
        ok=0
    fi

    if [ "$ok" = "1" ]; then
        echo "  ok   $name"
        PASS=$((PASS+1))
    else
        echo "  FAIL $name"
        echo "       expected exit=$expected_exit got=$actual_exit"
        echo "       stderr=$actual_stderr"
        FAIL=$((FAIL+1))
    fi

    rm -rf "$tmp"
    unset SETUP
}

# ---- Tests are appended below this line in subsequent tasks ----

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" = "0" ]
```

- [ ] **Step 2: Make it executable**

```bash
chmod +x .claude/hooks/tests/docs-drift-check.test.sh
```

- [ ] **Step 3: Run the harness with zero tests — should pass trivially**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected output:
```

Results: 0 passed, 0 failed
```
Exit code: 0

- [ ] **Step 4: Commit**

```bash
git add .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "test(docs-auto-sync): add hook test harness"
```

---

## Task 4: TDD round 1 — non-commit Bash command short-circuits

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)
- Create: `.claude/hooks/docs-drift-check.sh`

- [ ] **Step 1: Append the failing test**

Edit `.claude/hooks/tests/docs-drift-check.test.sh`. Insert before the final `echo "" / Results:` block (i.e., after the `# ---- Tests ... ----` line):

```bash
run_test "non-commit command exits 0" \
    '{"tool_input":{"command":"ls -la"}}' \
    0
```

- [ ] **Step 2: Run — verify it fails with "hook missing"**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: `FAIL non-commit command exits 0` because `.claude/hooks/docs-drift-check.sh` does not exist yet (bash exits 127).

- [ ] **Step 3: Write the minimal hook**

Content of `.claude/hooks/docs-drift-check.sh`:

```bash
#!/usr/bin/env bash
set -eo pipefail

# Fail open if jq is missing — never block a commit because of a tooling gap.
command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
    exit 0
fi

exit 0
```

- [ ] **Step 4: Make it executable**

```bash
chmod +x .claude/hooks/docs-drift-check.sh
```

- [ ] **Step 5: Run — verify the test passes**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected:
```
  ok   non-commit command exits 0

Results: 1 passed, 0 failed
```

- [ ] **Step 6: Commit**

```bash
git add .claude/hooks/docs-drift-check.sh .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "feat(docs-auto-sync): hook short-circuits on non-commit commands"
```

---

## Task 5: TDD round 2 — `git commit` with no staged files exits 0

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)
- Modify: `.claude/hooks/docs-drift-check.sh`

- [ ] **Step 1: Append the failing test**

Insert before the `Results:` block:

```bash
run_test "git commit with no staged files exits 0" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    0
```

- [ ] **Step 2: Run — verify it fails or passes**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: this test may pass already (the hook still falls through to `exit 0`) — but if it fails because `cd "$CLAUDE_PROJECT_DIR"` isn't there yet, the next step fixes that.

- [ ] **Step 3: Update the hook to handle staged-file lookup with empty result**

Replace the body of `.claude/hooks/docs-drift-check.sh` with:

```bash
#!/usr/bin/env bash
set -eo pipefail

command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
    exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
STAGED=$(git diff --cached --name-only)
[ -z "$STAGED" ] && exit 0

exit 0
```

- [ ] **Step 4: Run — verify both tests pass**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected:
```
  ok   non-commit command exits 0
  ok   git commit with no staged files exits 0

Results: 2 passed, 0 failed
```

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/docs-drift-check.sh .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "feat(docs-auto-sync): hook reads staged files and exits 0 when empty"
```

---

## Task 6: TDD round 3 — staged controller blocks with `api-list.md` in stderr

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)
- Modify: `.claude/hooks/docs-drift-check.sh`

- [ ] **Step 1: Append the failing test**

Insert before the `Results:` block:

```bash
export SETUP='
mkdir -p spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller
echo "// stub" > spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller/FooController.java
git add spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller/FooController.java
'
run_test "staged controller blocks with api-list.md in stderr" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    2 \
    "docs/api-list.md"
```

- [ ] **Step 2: Run — verify the test fails**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: `FAIL staged controller blocks ...` — exit code 0, stderr empty.

- [ ] **Step 3: Add glob matching + exit-2 to the hook**

Replace the body of `.claude/hooks/docs-drift-check.sh` with:

```bash
#!/usr/bin/env bash
set -eo pipefail

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
[ -f "$MAP" ] || exit 0

STALE=()
while IFS= read -r doc; do
    globs=$(jq -r --arg d "$doc" '.[$d].globs[]' "$MAP")
    while IFS= read -r glob; do
        # Translate fnmatch glob -> POSIX ERE.
        pattern=$(echo "$glob" | sed 's|\.|\\.|g; s|\*\*|.*|g; s|\*|[^/]*|g')
        if echo "$STAGED" | grep -qE "^${pattern}$"; then
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

- [ ] **Step 4: Run — verify all three tests pass**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected:
```
  ok   non-commit command exits 0
  ok   git commit with no staged files exits 0
  ok   staged controller blocks with api-list.md in stderr

Results: 3 passed, 0 failed
```

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/docs-drift-check.sh .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "feat(docs-auto-sync): hook matches staged files against source globs and exits 2"
```

---

## Task 7: TDD round 4 — staged SQL blocks with `data-model.md`

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)

This test pins the data-model.md glob path without changing the hook — just verifies the glob translator handles `docker/middleware/init/mysql/*.sql`.

- [ ] **Step 1: Append the test**

Insert before the `Results:` block:

```bash
export SETUP='
mkdir -p docker/middleware/init/mysql
echo "CREATE TABLE foo (id BIGINT);" > docker/middleware/init/mysql/admin-schema.sql
git add docker/middleware/init/mysql/admin-schema.sql
'
run_test "staged SQL blocks with data-model.md in stderr" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    2 \
    "docs/data-model.md"
```

- [ ] **Step 2: Run — verify it passes**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: 4 passed, 0 failed.

If it fails (glob translator is too narrow), debug by printing `$pattern` and `$STAGED` inside the hook. Most likely cause: the `**` rule turning `docker/middleware/init/mysql/*.sql` into a pattern that only matches deep paths.

- [ ] **Step 3: Commit**

```bash
git add .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "test(docs-auto-sync): cover SQL → data-model.md drift detection"
```

---

## Task 8: TDD round 5 — unrelated staged file does not block

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)

- [ ] **Step 1: Append the test**

```bash
export SETUP='
echo "README" > README.md
git add README.md
'
run_test "staged README does not block" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    0
```

- [ ] **Step 2: Run — verify it passes**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: 5 passed, 0 failed.

- [ ] **Step 3: Commit**

```bash
git add .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "test(docs-auto-sync): unrelated staged file does not trigger drift"
```

---

## Task 9: TDD round 6 — `gh pr create` is also intercepted

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (append test)

- [ ] **Step 1: Append the test**

```bash
export SETUP='
mkdir -p spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller
echo "// stub" > spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller/FooController.java
git add spring-ai-alibaba-admin-server-start/src/main/java/com/x/controller/FooController.java
'
run_test "gh pr create with stale source also blocks" \
    '{"tool_input":{"command":"gh pr create --title t --body b"}}' \
    2 \
    "docs/api-list.md"
```

- [ ] **Step 2: Run — verify it passes**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: 6 passed, 0 failed.

- [ ] **Step 3: Commit**

```bash
git add .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "test(docs-auto-sync): gh pr create is intercepted the same as git commit"
```

---

## Task 10: TDD round 7 — `jq` missing → fail open

**Files:**
- Modify: `.claude/hooks/tests/docs-drift-check.test.sh` (extend harness + append test)

This test simulates a missing `jq` by invoking the hook with a restricted `PATH`. Only the hook invocation has the restricted PATH — SETUP keeps the full `PATH` so it can still call `git`.

- [ ] **Step 1: Modify `run_test` to support a per-call PATH override**

Edit `.claude/hooks/tests/docs-drift-check.test.sh`. Find the line inside `run_test` that reads:

```bash
    actual_stderr=$(CLAUDE_PROJECT_DIR="$tmp" bash "$HOOK" <<< "$input" 2>&1 >/dev/null)
```

Replace it with:

```bash
    actual_stderr=$(PATH="${HOOK_PATH:-$PATH}" CLAUDE_PROJECT_DIR="$tmp" bash "$HOOK" <<< "$input" 2>&1 >/dev/null)
```

This reads an optional `HOOK_PATH` env var; when unset it falls back to the inherited `PATH`. SETUP (above) is unaffected.

- [ ] **Step 2: Append the failing test**

Insert below the `# ---- Tests ----` line, alongside the other test calls:

```bash
HOOK_PATH="$(mktemp -d)" run_test "jq missing → fail open" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    0
```

Using `mktemp -d` guarantees an empty directory with no executables.

- [ ] **Step 3: Run — verify all 7 tests pass**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: 7 passed, 0 failed.

If this test fails (i.e., hook errors instead of exiting 0), the `command -v jq` guard in the hook is not the first line. Re-check Task 4's hook content: `command -v jq >/dev/null 2>&1 || exit 0` must come before any other use of `jq`.

- [ ] **Step 4: Commit**

```bash
git add .claude/hooks/tests/docs-drift-check.test.sh
git commit -m "test(docs-auto-sync): hook fails open when jq is missing"
```

---

## Task 11: Write `SKILL.md`

**Files:**
- Create: `.claude/skills/docs-auto-sync/SKILL.md`

- [ ] **Step 1: Write the skill manifest**

Content of `.claude/skills/docs-auto-sync/SKILL.md`:

```markdown
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
```

- [ ] **Step 2: Verify the frontmatter parses**

Run: `head -5 .claude/skills/docs-auto-sync/SKILL.md`
Expected: frontmatter with `name:` and `description:`.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/docs-auto-sync/SKILL.md
git commit -m "feat(docs-auto-sync): add skill manifest"
```

---

## Task 12: Write `regen/api-list.md` recipe

**Files:**
- Create: `.claude/skills/docs-auto-sync/regen/api-list.md`

- [ ] **Step 1: Write the recipe**

Content of `.claude/skills/docs-auto-sync/regen/api-list.md`:

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/docs-auto-sync/regen/api-list.md
git commit -m "feat(docs-auto-sync): add api-list regen recipe"
```

---

## Task 13: Write `regen/data-model.md` recipe

**Files:**
- Create: `.claude/skills/docs-auto-sync/regen/data-model.md`

- [ ] **Step 1: Write the recipe**

Content of `.claude/skills/docs-auto-sync/regen/data-model.md`:

```markdown
# Regen recipe: docs/data-model.md + docs/data-model.html

## Sources to scan
- docker/middleware/init/mysql/admin-schema.sql
- docker/middleware/init/mysql/agentscope-schema.sql
- All Java files matching: **/src/main/java/**/entity/**/*.java
- All Java files matching: **/src/main/java/**/*Entity.java

## Step 1: Read the current docs

Use Read on `docs/data-model.md` and `docs/data-model.html`. Note the section ordering (schema-by-schema), table-by-table column ordering, and the structure of the inline Mermaid ER diagram in the HTML.

## Step 2: Parse SQL DDL

For each `*.sql` file under `docker/middleware/init/mysql/`:
- Find every `CREATE TABLE \`name\` ( ... );` block (Grep + Read).
- For each table, extract:
  - Column name, SQL type, nullability, default, comment (from `COMMENT '...'`).
  - Primary key declaration.
  - Index / unique key declarations.
  - Foreign key declarations (if any).
- Note which schema (`admin-schema` vs `agentscope-schema`) the table belongs to.

## Step 3: Cross-reference with Java entities

For each `@TableName("table_name")`-annotated class found via Grep:
- Map the Java field names + types to the SQL columns by `@TableField("col")` or naming convention (camelCase ↔ snake_case).
- Record only Java types that disambiguate SQL types (e.g., `Date` vs `LocalDateTime`). Do not invent fields.

## Step 4: Emit `docs/data-model.md`

Preserve the existing structure:
- Top-level heading + intro line with current counts ("N tables across 2 schemas").
- One H2 per schema (`agentscope-schema`, `admin-schema`).
- One H3 per table.
- Under each table: a Markdown column table (`Column | Type | Null | Key | Default | Comment`).
- After columns: a `**Relationships:**` line noting declared FKs (only `dataset_*` and `evaluator_version` have these — confirm by checking the SQL).

## Step 5: Emit `docs/data-model.html`

The HTML file wraps a Mermaid `erDiagram` block. Regenerate it from the same table data:
- One `entity` block per table.
- One relation line per declared FK (`||--o{` style).
- Preserve the surrounding HTML scaffold (head, body, mermaid CDN script tag) by reading the current file and only updating the `<pre class="mermaid">...</pre>` content.

## Step 6: Write & stage

- Write `docs/data-model.md`.
- Write `docs/data-model.html`.
- Run: `git add docs/data-model.md docs/data-model.html`.

## Forbidden modifications
- Never delete the `dataset` or `dataset_item` table rows even though the feature is deprecated — admin module CLAUDE.md flags this as Legacy Overhead that must be retained.
- Never drop the `external_key` column from `prompt` if it appears in SQL.
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/docs-auto-sync/regen/data-model.md
git commit -m "feat(docs-auto-sync): add data-model regen recipe"
```

---

## Task 14: Write `regen/module-dependency.md` recipe

**Files:**
- Create: `.claude/skills/docs-auto-sync/regen/module-dependency.md`

- [ ] **Step 1: Write the recipe**

Content of `.claude/skills/docs-auto-sync/regen/module-dependency.md`:

```markdown
# Regen recipe: docs/module-dependency.html

## Sources to scan
- pom.xml (admin module aggregator POM)
- spring-ai-alibaba-admin-server-start/pom.xml
- spring-ai-alibaba-admin-server-openapi/pom.xml
- spring-ai-alibaba-admin-server-core/pom.xml
- spring-ai-alibaba-admin-server-runtime/pom.xml

## Step 1: Read the current doc

Use Read on `docs/module-dependency.html` to note the HTML scaffold and the Mermaid graph style. The skill replaces only the Mermaid block content.

## Step 2: Extract internal dependencies

For each child POM:
- Grep for `<artifactId>spring-ai-alibaba-admin-` inside `<dependency>` blocks (use Read on the file and parse manually — POMs are small).
- Record edges: `<child module> --> <dependency module>`.

## Step 3: Build the graph

Expected edges based on the current architecture (verify before emitting):
- `server-start --> server-openapi`
- `server-start --> server-core`
- `server-openapi --> server-core`
- `server-core --> server-runtime`

If a new edge is detected, include it. If an edge disappears, remove it.

## Step 4: Emit the Mermaid block

```
graph LR
    start[spring-ai-alibaba-admin-server-start]
    openapi[spring-ai-alibaba-admin-server-openapi]
    core[spring-ai-alibaba-admin-server-core]
    runtime[spring-ai-alibaba-admin-server-runtime]
    start --> openapi
    start --> core
    openapi --> core
    core --> runtime
```

## Step 5: Write & stage

- Update only the `<pre class="mermaid">...</pre>` block inside the existing HTML — preserve all surrounding HTML/CSS/script tags exactly.
- Run: `git add docs/module-dependency.html`.

## Forbidden modifications
- Do not rename modules in the diagram. Maven artifact ids and the Java package prefix (`com.alibaba.cloud.ai.studio.*`) do not match — list artifacts.
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/docs-auto-sync/regen/module-dependency.md
git commit -m "feat(docs-auto-sync): add module-dependency regen recipe"
```

---

## Task 15: Write `regen/external-dependency.md` recipe

**Files:**
- Create: `.claude/skills/docs-auto-sync/regen/external-dependency.md`

- [ ] **Step 1: Write the recipe**

Content of `.claude/skills/docs-auto-sync/regen/external-dependency.md`:

```markdown
# Regen recipe: docs/external-dependency.html

## Sources to scan
- All POMs under spring-ai-alibaba-admin-*/pom.xml + the aggregator pom.xml
- All YAML configs: spring-ai-alibaba-admin-server-start/src/main/resources/application*.yml + application*.yaml

## Step 1: Read the current doc

Use Read on `docs/external-dependency.html`. Note the category groupings (MySQL, Redis, Elasticsearch, Nacos, RocketMQ, OSS, Model Providers) and the per-row format (name, version, role, related config).

## Step 2: Detect middleware

For each category, look for both a Maven coordinate AND a config key:

| Category | Maven artifactId substring | Config key |
|---|---|---|
| MySQL | `mysql-connector-j` or `mysql-connector-java` | `spring.datasource.url` |
| Redis | `lettuce-core` or `spring-boot-starter-data-redis` | `spring.data.redis.host` |
| Elasticsearch | `elasticsearch-java` or `co.elastic.clients` | `spring.elasticsearch.uris` |
| Nacos | `nacos-client` | `nacos.server-addr` |
| RocketMQ | `rocketmq-spring-boot-starter` | `rocketmq.name-server` |
| OSS | `aliyun-sdk-oss` | `aliyun.oss.endpoint` |
| Model Providers | `dashscope-sdk-java`, `openai-java`, `deepseek` | model-config*.yml entries |

Use Grep across all source files for both the artifactId substring (in POMs) and the config key (in YAMLs). Report only categories where BOTH are present (or where only the config is present — flag with a TODO).

## Step 3: Extract version

For each detected dep, find the `<version>` in the POM (may be in dependencyManagement / BOM — search the BOM and parent POM if not directly present).

## Step 4: Emit the HTML

Preserve the existing HTML scaffold. Replace only the body content (the cards / table per category). For each category, emit:
- Category name (heading).
- One row per detected dep with: name, version, role description, config key.

## Step 5: Write & stage

- Use Write to update `docs/external-dependency.html`.
- Run: `git add docs/external-dependency.html`.

## Forbidden modifications
- Preserve the default `nacos.server-addr` value verbatim if displayed (admin module CLAUDE.md flags this as a stability contract).
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/docs-auto-sync/regen/external-dependency.md
git commit -m "feat(docs-auto-sync): add external-dependency regen recipe"
```

---

## Task 16: Wire up the hook in `.claude/settings.json`

This is the activation step. Do this only after all tests pass and all recipes are committed.

**Files:**
- Create: `.claude/settings.json`

- [ ] **Step 1: Verify all hook tests still pass**

Run: `bash .claude/hooks/tests/docs-drift-check.test.sh`
Expected: 7 passed, 0 failed.

- [ ] **Step 2: Write `settings.json`**

Content of `.claude/settings.json`:

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

- [ ] **Step 3: Validate the JSON**

Run: `jq . .claude/settings.json > /dev/null && echo OK`
Expected: `OK`

- [ ] **Step 4: Commit (the hook is now live)**

```bash
git add .claude/settings.json
git commit -m "feat(docs-auto-sync): wire up PreToolUse hook (activates docs drift check)"
```

---

## Task 17: End-to-end smoke test

Manually verify the system works in a real session. This is not a TDD step — it's a sanity check that the hook + skill actually compose correctly.

- [ ] **Step 1: Stage a touch to a controller**

Pick any existing controller. For example:

```bash
# Locate one
ls spring-ai-alibaba-admin-server-start/src/main/java/**/controller/*.java | head -1
```

Touch its modification time by appending and removing a blank line, or just stage it as-is if `git diff` already shows changes. If clean, do:

```bash
# Append a harmless comment to provoke a diff
target=$(find spring-ai-alibaba-admin-server-start/src/main/java -name '*Controller.java' | head -1)
echo "" >> "$target"
git add "$target"
```

- [ ] **Step 2: Attempt a commit through Claude Code**

In a fresh Claude Code session in this directory, ask Claude to `git commit -m "test: trigger docs-auto-sync"`.

Expected:
- The Bash tool call is intercepted by the hook.
- Claude sees stderr: `Docs drift detected. Source files affecting these docs are staged:\n  - docs/api-list.md`.
- Claude invokes the `docs-auto-sync` skill.
- The skill regenerates `docs/api-list.md` and stages it.
- Claude retries the commit, which now succeeds.
- The resulting commit includes both the controller change AND the regenerated `docs/api-list.md`.

- [ ] **Step 3: Revert the smoke-test commit**

```bash
git reset --hard HEAD~1
```

Only proceed if the smoke test passed. If it did not, debug the hook (run the test harness; tail Claude Code's hook logs at `~/.claude/logs/` if available) before continuing.

- [ ] **Step 4: No commit needed for this task** (the verification is the deliverable).

---

## Task 18: Document the skill

**Files:**
- Modify: `CLAUDE.md` (admin module)

- [ ] **Step 1: Add a "Skills" section to admin module CLAUDE.md**

Edit `CLAUDE.md` to insert this block immediately after the `## Key References` section:

```markdown
## Project Skills

- **docs-auto-sync** (`.claude/skills/docs-auto-sync/`) — keeps code-derived docs in `docs/` in sync with the source. Triggered automatically by a PreToolUse hook on `git commit` and `gh pr create`. Manually invokable via `/docs-auto-sync`. Requires `jq` on PATH (fails open if missing).
```

- [ ] **Step 2: Commit**

This commit will itself pass through the hook. The hook should see only `CLAUDE.md` staged, find no glob match, and let it through.

```bash
git add CLAUDE.md
git commit -m "docs: document docs-auto-sync skill in admin module CLAUDE.md"
```

If the hook *does* block this commit unexpectedly, that is a bug — re-run the test harness and inspect.

---

## Self-review checklist (run after implementing every task)

- [ ] All 7 hook tests pass.
- [ ] `.claude/settings.json` is valid JSON.
- [ ] `.claude/skills/docs-auto-sync/source-doc-map.json` is valid JSON.
- [ ] `SKILL.md` and all 4 recipes exist and are non-empty.
- [ ] `docs/architecture-diagram.html` is NOT in the source-doc-map (only flagged, never regenerated).
- [ ] Smoke test produced a clean commit with the regenerated doc bundled in.
