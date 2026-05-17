# Technical Diagram Architecture Profiles Design

## Goal

Enhance the `technical-diagram` skill so architecture drawings can preserve the right amount of technical detail instead of always collapsing into a sparse service-level view.

## Problem

The current architecture reference forces one representation:

- module name
- one-line responsibility
- no frameworks, languages, ports, package/module names, deployment details, or vendor-specific details

That works for stakeholder-friendly service diagrams, but it loses important engineering information such as language, framework, concrete module names, middleware products, protocols, and module purpose when the user actually wants a technical architecture drawing.

## Decision

Architecture diagrams will support three explicit profiles:

1. **Product** — concise stakeholder view
2. **Hybrid** — concrete modules plus key stack metadata
3. **Technical** — richest engineering view

If the user requests an architecture diagram without enough detail to infer a profile, the skill must ask which profile they want instead of silently choosing one.

## Profile Semantics

### Product

Use when the user asks for a high-level, product, stakeholder, or service architecture view.

Show:

- service or capability name
- one-line responsibility
- major flows and external actors

Hide unless explicitly requested:

- programming languages
- frameworks
- ports and protocols
- package/module names
- deployment mechanics
- vendor-specific implementation details

### Hybrid

Use when the user wants repo understanding, onboarding value, or a balanced architecture view.

Show:

- concrete module names
- one-line module purpose
- shared stack metadata at group level where useful
- architecturally meaningful external systems and middleware products
- major protocols or interfaces when they explain the design

Prefer putting shared details in group headers rather than repeating them in every node.

### Technical

Use when the user asks for implementation, engineering, runtime, or stack detail.

Show when discoverable:

- concrete module names
- module purpose
- programming language and runtime version
- framework and major libraries
- ports and protocols
- concrete middleware and database products
- important external integrations
- architecturally relevant implementation details

The diagram may be denser than the other profiles, but it must preserve meaning rather than silently abstracting away important facts.

## Selection Flow

1. Route the user request to architecture diagrams as today.
2. If the request already implies a profile, choose it:
   - `high-level`, `product`, `stakeholder`, `service architecture` -> Product
   - `technical`, `implementation`, `runtime`, `stack`, or explicit requests for framework/language/module detail -> Technical
   - `hybrid`, `balanced`, `repo overview`, or `onboarding` -> Hybrid
3. If the request is ambiguous, ask one short follow-up question with the three profile choices.
4. Recommend Hybrid first in the wording because it is usually the best compromise for repo understanding, but do not silently default to it.

## Discovery Contract

Before drawing, build an architecture inventory from the repository and documentation.

For each relevant component, capture when available:

- layer or boundary
- concrete module/component name
- one-line purpose
- language/runtime
- framework
- artifact/module path
- ports
- protocols
- data stores
- middleware dependencies
- external integrations
- evidence source

Suggested sources include:

- root and module build files such as `pom.xml`, `build.gradle`, `package.json`
- application configuration such as `application.yml`
- Docker Compose or deployment files
- project READMEs and architecture docs
- top-level module directories and key entrypoints

The inventory is an internal working model used to decide what the selected profile should render.

## Rendering Contract

The renderer should support richer node density levels:

### Compact Node

- title
- one-line purpose

### Standard Node

- title
- one-line purpose
- one supporting technical line when needed

### Detailed Node

- title
- one-line purpose
- one or two supporting technical lines for stack/interface details

Shared metadata should move to group headers whenever possible, for example:

- `BACKEND (Java 17 · Spring Boot 3.x · port 8080)`
- `FRONTEND (React 18 · Ant Design 5)`

Node bodies should retain module-specific meaning, for example:

- `server-core`
- `Business logic: PromptService · DatasetService`
- `MyBatis-Plus · Druid · JWT`

## Validation Before Finalizing

Before returning a diagram, verify that:

- every visible module/component has a purpose
- known languages and frameworks are preserved when required by the selected profile
- concrete module names were not replaced by generic abstractions when the profile requires fidelity
- real middleware, databases, and external systems were not collapsed away accidentally
- any omitted details were excluded intentionally by profile rules rather than lost during summarization

If a requested profile cannot be fully satisfied because evidence is missing, state what was not found instead of guessing.

## Expected Changes

1. Update `technical-diagram/SKILL.md` so ambiguous architecture requests trigger a profile question.
2. Rewrite `technical-diagram/references/architecture-diagram.md` around the three-profile model and inventory-first workflow.
3. Update `architecture-diagram/SKILL.md` with richer node-density guidance and group-header metadata rules.
4. Update `architecture-diagram/resources/template.html` with examples that support multi-line technical nodes rather than only `title + subtitle`.

## Non-Goals

- Replacing the renderer entirely
- Building a machine-generated graph extraction pipeline
- Forcing every architecture diagram to show all available technical detail
- Changing module dependency, sequence, or database diagrams in this pass

## Success Criteria

- An ambiguous architecture request now causes a profile clarification instead of silent flattening.
- A Hybrid or Technical architecture diagram can retain language, framework, module purpose, concrete middleware, and meaningful interface details when present in the source material.
- Product diagrams remain clean and concise.
- The skill gives consistent instructions about what to preserve, what to omit, and why.
