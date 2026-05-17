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
