# Technical Diagram Architecture Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the technical-diagram workflow so architecture diagrams ask for a fidelity profile when ambiguous and can preserve richer engineering detail when the selected profile requires it.

**Architecture:** Keep `technical-diagram` as the router, move profile semantics and discovery rules into the architecture-specific reference, and expand the renderer guidance/template so richer node layouts are possible without making every diagram dense. Verify the documentation contract with simple content assertions before and after the edit.

**Tech Stack:** Markdown skill files, self-contained HTML template, shell-based verification with `rg`.

---

## File Structure

- Modify `/Users/snow/.codex/skills/technical-diagram/SKILL.md` — add architecture-profile routing behavior for ambiguous requests.
- Modify `/Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md` — replace the single L2-only contract with Product / Hybrid / Technical profiles, inventory-first discovery, and validation rules.
- Modify `/Users/snow/.codex/skills/architecture-diagram/SKILL.md` — add node-density guidance and group-header metadata rules for richer architecture output.
- Modify `/Users/snow/.codex/skills/architecture-diagram/resources/template.html` — provide richer example markup that demonstrates multi-line technical nodes and group-header metadata.

### Task 1: Capture the failing contract checks

**Files:**
- Verify: `/Users/snow/.codex/skills/technical-diagram/SKILL.md`
- Verify: `/Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md`
- Verify: `/Users/snow/.codex/skills/architecture-diagram/SKILL.md`
- Verify: `/Users/snow/.codex/skills/architecture-diagram/resources/template.html`

- [ ] **Step 1: Run failing checks for profile selection and richer rendering guidance**

```bash
rg -n "Product|Hybrid|Technical" /Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md
rg -n "ask.*profile|profile.*ask" /Users/snow/.codex/skills/technical-diagram/SKILL.md
rg -n "Compact Node|Standard Node|Detailed Node|group header" /Users/snow/.codex/skills/architecture-diagram/SKILL.md
rg -n "Java 17|Spring Boot|technical line" /Users/snow/.codex/skills/architecture-diagram/resources/template.html
```

Expected: the checks fail to find the new contract language because the current skills only support the sparse service-level view.

### Task 2: Add profile-aware routing in `technical-diagram`

**Files:**
- Modify: `/Users/snow/.codex/skills/technical-diagram/SKILL.md`

- [ ] **Step 1: Add architecture-profile selection guidance**

Add a short section that says:

```markdown
## Architecture Profile Selection

For architecture diagrams, resolve the fidelity profile before drawing:

1. `Product` — concise stakeholder/service view
2. `Hybrid` — concrete modules plus key stack details
3. `Technical` — richest engineering view

If the user clearly implies one profile, use it. If the request is simply for an "architecture diagram" without enough signal to choose, ask the user which profile they want before proceeding. Do not silently default.
```

- [ ] **Step 2: Re-run the profile-selection check**

```bash
rg -n "Architecture Profile Selection|Do not silently default" /Users/snow/.codex/skills/technical-diagram/SKILL.md
```

Expected: PASS.

### Task 3: Replace the architecture reference with the new three-profile contract

**Files:**
- Modify: `/Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md`

- [ ] **Step 1: Rewrite the reference around the approved design**

The replacement must include:

```markdown
- explicit Product / Hybrid / Technical profile definitions
- when to choose each profile
- the ambiguous-request prompt rule
- an architecture inventory with language, framework, runtime, ports, protocols, module path, stores, middleware, integrations, and evidence source
- profile-specific rendering rules
- final validation checks that catch accidental loss of known technical detail
```

- [ ] **Step 2: Re-run the profile and inventory checks**

```bash
rg -n "## Product Profile|## Hybrid Profile|## Technical Profile|Architecture Inventory|evidence source|Do not guess" /Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md
```

Expected: PASS.

### Task 4: Expand renderer guidance for richer nodes

**Files:**
- Modify: `/Users/snow/.codex/skills/architecture-diagram/SKILL.md`

- [ ] **Step 1: Add node-density and group-header guidance**

Add documentation for:

```markdown
- Compact Node: title + purpose
- Standard Node: title + purpose + one supporting technical line
- Detailed Node: title + purpose + one or two supporting technical lines
- shared metadata in group headers, e.g. `BACKEND (Java 17 · Spring Boot 3.x · port 8080)`
- when to move detail into summary cards instead of overfilling nodes
```

- [ ] **Step 2: Re-run renderer guidance checks**

```bash
rg -n "Compact Node|Standard Node|Detailed Node|group headers|summary cards" /Users/snow/.codex/skills/architecture-diagram/SKILL.md
```

Expected: PASS.

### Task 5: Update the HTML template examples

**Files:**
- Modify: `/Users/snow/.codex/skills/architecture-diagram/resources/template.html`

- [ ] **Step 1: Replace or augment the simple examples with richer architecture examples**

The template should demonstrate:

```html
- a group label such as `BACKEND (Java 17 · Spring Boot 3.x · port 8080)`
- a multi-line technical node with name, purpose, and one extra technical line
- comments explaining compact / standard / detailed node density
```

- [ ] **Step 2: Re-run the template checks**

```bash
rg -n "Java 17|Spring Boot 3.x|Compact node|Standard node|Detailed node" /Users/snow/.codex/skills/architecture-diagram/resources/template.html
```

Expected: PASS.

### Task 6: Verify the integrated contract

**Files:**
- Verify all modified skill files

- [ ] **Step 1: Run all content checks together**

```bash
rg -n "Product|Hybrid|Technical" /Users/snow/.codex/skills/technical-diagram/references/architecture-diagram.md
rg -n "Architecture Profile Selection|Do not silently default" /Users/snow/.codex/skills/technical-diagram/SKILL.md
rg -n "Compact Node|Standard Node|Detailed Node|group headers|summary cards" /Users/snow/.codex/skills/architecture-diagram/SKILL.md
rg -n "Java 17|Spring Boot 3.x|Compact node|Standard node|Detailed node" /Users/snow/.codex/skills/architecture-diagram/resources/template.html
```

Expected: PASS for every check.

- [ ] **Step 2: Manually inspect the architecture reference for contradictions**

Confirm that:

```text
- Product remains concise
- Hybrid is the balanced repo-understanding view
- Technical preserves stack and interface detail
- ambiguous requests require a user choice
- missing evidence is reported instead of invented
```

- [ ] **Step 3: Commit the repo-side plan document**

```bash
git add docs/superpowers/plans/2026-05-15-technical-diagram-architecture-profiles.md
git commit -m "docs: plan technical diagram architecture profiles"
```
