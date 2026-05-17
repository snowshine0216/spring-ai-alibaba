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
