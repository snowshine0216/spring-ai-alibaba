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
