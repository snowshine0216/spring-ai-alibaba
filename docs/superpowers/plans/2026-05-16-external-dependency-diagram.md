# External Dependency Diagram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-class external dependency diagram support to `technical-diagram` and generate a project-level external dependency diagram for Spring AI Alibaba.

**Architecture:** Extend the router skill with one new diagram type and one focused reference document. Keep classification logic in the reference, while the generated repo artifact remains a self-contained HTML diagram rendered through the existing `architecture-diagram` conventions.

**Tech Stack:** Markdown skill docs, HTML + inline SVG, existing `architecture-diagram` design system.

---

## File Map

- Modify `/Users/snow/.codex/skills/technical-diagram/SKILL.md` — expose and route the new diagram type.
- Create `/Users/snow/.codex/skills/technical-diagram/references/external-dependency.md` — define discovery, grouping, and rendering rules.
- Create `/Users/snow/Documents/Repository/spring-ai-alibaba/docs/spring-ai-alibaba-external-dependencies.html` — generated repository diagram artifact.

### Task 1: Add a failing routing check for the missing diagram type

**Files:**
- Check: `/Users/snow/.codex/skills/technical-diagram/SKILL.md`

- [ ] **Step 1: Run a check that should fail before the change**

```bash
python3 - <<'PY'
from pathlib import Path
text = Path('/Users/snow/.codex/skills/technical-diagram/SKILL.md').read_text()
required = [
    'external dependency diagram',
    'references/external-dependency.md',
]
missing = [item for item in required if item not in text]
raise SystemExit(1 if missing else 0)
PY
```

Expected: exit code `1` because the new type and route do not exist yet.

### Task 2: Add the new external dependency reference and route it

**Files:**
- Modify: `/Users/snow/.codex/skills/technical-diagram/SKILL.md`
- Create: `/Users/snow/.codex/skills/technical-diagram/references/external-dependency.md`

- [ ] **Step 1: Update the supported type list and routing list**

Add `external dependency diagram` as the fifth supported type and wire it to `references/external-dependency.md`.

- [ ] **Step 2: Create the reference document**

Include:

```markdown
# External Dependency Diagram

## Discovery Sources by Project Type

### Java
- `pom.xml`
- relevant child `pom.xml` files when scope is narrower than the whole repo
- `application.yml` / `application.yaml`
- `README.md`

### Python
- `pyproject.toml`
- `requirements.txt` and lockfiles
- runtime config such as `.env.example`, YAML, or TOML config
- `README.md`

### Go
- `go.mod`
- runtime config files
- deployment manifests when they reveal infrastructure
- `README.md`

### TypeScript
- `package.json`
- workspace manifests and lockfiles when relevant
- runtime config such as `.env.example`, YAML, or JSON config
- `README.md`
```

Then define the exact three rendered groups, classification rules, visual color families, and final validation checklist from the approved spec.

- [ ] **Step 3: Re-run the routing check**

Run the Task 1 command again.

Expected: exit code `0`.

### Task 3: Gather evidence for the Spring AI Alibaba diagram

**Files:**
- Inspect: `/Users/snow/Documents/Repository/spring-ai-alibaba/pom.xml`
- Inspect: `/Users/snow/Documents/Repository/spring-ai-alibaba/README.md`
- Inspect relevant `application.yml` files under `/Users/snow/Documents/Repository/spring-ai-alibaba/`

- [ ] **Step 1: Extract candidate framework/library dependencies**

Use root `pom.xml` plus relevant child POMs to identify the small set of architecturally meaningful Java dependencies.

- [ ] **Step 2: Extract candidate middleware**

Use repo docs and runtime config to identify concrete middleware such as Nacos, Redis, SQL databases, and observability backends.

- [ ] **Step 3: Extract candidate external APIs**

Use repo docs and config to identify network APIs such as DashScope, OpenAI, or DeepSeek when they are explicitly evidenced.

### Task 4: Generate the HTML diagram artifact

**Files:**
- Create: `/Users/snow/Documents/Repository/spring-ai-alibaba/docs/spring-ai-alibaba-external-dependencies.html`

- [ ] **Step 1: Build a self-contained HTML+SVG diagram**

Use the existing dark theme, top-to-bottom reading order, three distinct group colors, and a compact legend.

- [ ] **Step 2: Keep only evidence-backed dependencies**

Represent the repository as the source node, then connect to:

- **Key Java Dependencies**
- **Key Middleware**
- **Key External APIs**

### Task 5: Verify the change set

**Files:**
- Verify: `/Users/snow/.codex/skills/technical-diagram/SKILL.md`
- Verify: `/Users/snow/.codex/skills/technical-diagram/references/external-dependency.md`
- Verify: `/Users/snow/Documents/Repository/spring-ai-alibaba/docs/spring-ai-alibaba-external-dependencies.html`

- [ ] **Step 1: Run content assertions**

```bash
python3 - <<'PY'
from pathlib import Path
skill = Path('/Users/snow/.codex/skills/technical-diagram/SKILL.md').read_text()
ref = Path('/Users/snow/.codex/skills/technical-diagram/references/external-dependency.md').read_text()
html = Path('/Users/snow/Documents/Repository/spring-ai-alibaba/docs/spring-ai-alibaba-external-dependencies.html').read_text()
checks = {
    'skill route': 'references/external-dependency.md' in skill,
    'java pom': '`pom.xml`' in ref,
    'java app yml': '`application.yml`' in ref or '`application.yaml`' in ref,
    'java readme': '`README.md`' in ref,
    'python guidance': 'Key Python Dependencies' in ref,
    'go guidance': 'Key Go Dependencies' in ref,
    'typescript guidance': 'Key TypeScript Dependencies' in ref,
    'html artifact': '<svg' in html and 'Key Java Dependencies' in html,
}
failed = [name for name, ok in checks.items() if not ok]
raise SystemExit(f'failed: {failed}' if failed else 0)
PY
```

Expected: exit code `0`.

- [ ] **Step 2: Re-read the rendered dependency list against repository evidence**

Expected: every visible dependency is justified by `README.md`, `pom.xml`, or inspected runtime configuration; uncertain items are omitted rather than guessed.
