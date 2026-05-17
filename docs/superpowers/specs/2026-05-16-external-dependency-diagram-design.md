# External Dependency Diagram Skill Enhancement Design

## Goal

Extend the `technical-diagram` skill so it can route and draw **external dependency diagrams**, then use that workflow to create a project-level external dependency diagram for Spring AI Alibaba.

## Scope

This enhancement covers:

- one new supported diagram type: `external dependency diagram`
- one new reference document under the skill's `references/` directory
- language-aware source inspection guidance for Java, Python, Go, and TypeScript projects
- one generated external dependency diagram artifact for this repository under `docs/`

It does not change the existing architecture, module dependency, sequence, or database diagram behavior.

## Skill Behavior

### New Diagram Type

`technical-diagram` will list `external dependency diagram` as a first-class supported type and route it to `references/external-dependency.md`.

### Required Groups

Every external dependency diagram must divide evidence-backed dependencies into exactly these three groups:

1. **Key Language Dependencies** — important framework/library dependencies from the build manifest
2. **Key Middleware** — infrastructure services such as databases, registries, queues, caches, or observability backends
3. **Key External APIs** — remote SaaS/vendor APIs and model providers called over the network

For Java projects, the first group should be labeled **Key Java Dependencies**. Other languages should use the corresponding language name, for example **Key Python Dependencies**.

### Visual Encoding

Use a distinct color family for each group so the categories remain readable at a glance:

- language dependencies: emerald / green
- middleware: orange / amber
- external APIs: cyan / blue

Keep the dominant reading order top-to-bottom, show the project as the source node, and connect it to the three dependency groups with clearly labeled flows.

## Discovery Rules

### Java

Before drawing, inspect:

- `pom.xml`
- relevant child `pom.xml` files when the requested scope is narrower than the whole repository
- `application.yml` or `application.yaml`
- `README.md`

Then classify the discovered dependencies into:

- **Key Java Dependencies**
- **Key Middleware**
- **Key External APIs**

### Python

Inspect:

- `pyproject.toml`
- `requirements.txt` and/or lockfiles when present
- runtime config such as `.env.example`, YAML, or TOML config files
- `README.md`

Then classify them into:

- **Key Python Dependencies**
- **Key Middleware**
- **Key External APIs**

### Go

Inspect:

- `go.mod`
- runtime config files such as YAML, TOML, or environment examples
- deployment manifests when they reveal infrastructure
- `README.md`

Then classify them into:

- **Key Go Dependencies**
- **Key Middleware**
- **Key External APIs**

### TypeScript

Inspect:

- `package.json`
- workspace manifests and lockfiles when relevant
- runtime config such as `.env.example`, YAML, or JSON config files
- `README.md`

Then classify them into:

- **Key TypeScript Dependencies**
- **Key Middleware**
- **Key External APIs**

## Diagram Requirements

- Render only dependencies supported by inspected evidence.
- Prefer architecturally meaningful dependencies over exhaustive package inventories.
- Keep vendor APIs separate from middleware even when both are configured in the same file.
- If evidence is incomplete or ambiguous, omit the dependency or annotate the uncertainty rather than guessing.
- Use the `architecture-diagram` skill for rendering, consistent with the rest of `technical-diagram`.

## Repository Artifact

Create a project-level diagram under:

`docs/spring-ai-alibaba-external-dependencies.html`

The diagram should be based on repository evidence from the root `README.md`, the root `pom.xml`, and the relevant runtime configuration files that expose concrete middleware or external API usage.

## Validation

Before considering the work complete:

- confirm the new diagram type appears in `SKILL.md`
- confirm the new reference is wired into the routing list
- confirm the Java instructions explicitly require `pom.xml`, `application.yml` / `application.yaml`, and `README.md`
- confirm Python, Go, and TypeScript reference guidance is present
- confirm the generated HTML diagram exists in `docs/`
- compare every rendered dependency against inspected repository evidence
