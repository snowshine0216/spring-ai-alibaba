# /setup-env — Spring AI Alibaba Admin Local Dev Environment

Sets up and starts the full local dev stack: middleware (MySQL / Redis / ES /
Nacos / RocketMQ), Spring Boot backend (:8080), and React frontend (:8000).

**All scripts live in** `.claude/skills/setup-env/scripts/` and are run from
the **project root** (`spring-ai-alibaba-admin/`).

---

## Dependency Checklist

Run this first — it tells you what's missing before touching anything:

```bash
.claude/skills/setup-env/scripts/check-deps.sh
```

**Required deps:**

| | Dep | Notes |
|---|---|---|
| Build | Java 17 | `/opt/homebrew/opt/openjdk@17` (keg-only) |
| Build | Maven 3.9+ | Uses Java 17 via `JAVA_HOME` |
| Node | NVM + Node | Script sources `~/.nvm/nvm.sh`; pin version with `frontend/.nvmrc` |
| Docker | Colima (VM) | Docker Desktop download is blocked from CN — use Colima instead |
| Docker | Docker CLI | Homebrew `docker` formula |
| Docker | Compose plugin | Symlinked to `~/.docker/cli-plugins/docker-compose` |
| Config | `.env` | Project root — holds API keys, gitignored |
| Config | `model-config.yml` | Synced from provider template by `configure-env.sh` |

---

## Step 1 — Install missing deps

```bash
.claude/skills/setup-env/scripts/install-deps.sh
source ~/.zshrc   # pick up Java 17 PATH change
```

What it does: installs Java 17, Maven, Colima, Docker CLI, docker-compose
plugin, and writes `docker/middleware/docker-compose-override.yaml` with two
macOS fixes (RocketMQ user permissions + Elasticsearch heap reduction).

---

## Step 2 — Configure API key

Create `.env` at the project root and sync `model-config.yml`:

```bash
.claude/skills/setup-env/scripts/configure-env.sh
```

First run creates `.env` with commented-out placeholders. Edit it, uncomment
your provider's key (OpenAI / DashScope / DeepSeek), then run the script again.
It detects the active provider and copies the matching template into
`model-config.yml`.

```
# .env (project root — gitignored)
OPENAI_API_KEY=sk-...
```

To use a different provider later: update `.env` and re-run `configure-env.sh`.

---

## Step 3 — Start Colima

```bash
colima start   # first time: ~60 s to boot the VM
```

Colima must be running before middleware can start. After a reboot, always
start Colima first. For auto-start at login: `brew services start colima`.

---

## Step 4 — Start middleware

```bash
.claude/skills/setup-env/scripts/start-middleware.sh
```

Starts: MySQL (3306), Redis (6379), Elasticsearch (9200), Nacos (8848),
RocketMQ (18080), Kibana (5601), LoongCollector (4318).

Blocks until MySQL is healthy, then prints container status. First run
downloads images (~2–3 GB).

**macOS note:** the script uses `docker-compose-override.yaml` which is
required on macOS — do not use `run.sh prod` directly.

---

## Step 5 — Start the backend

Open a dedicated terminal and run from the project root:

```bash
.claude/skills/setup-env/scripts/start-backend.sh
# or with the remote dev profile (requires VPN to 47.239.212.78):
.claude/skills/setup-env/scripts/start-backend.sh dev
```

- Sources `.env` to inject the API key into the process.
- Sets `JAVA_HOME` to Java 17 automatically.
- First run downloads Maven deps (~500 MB).
- Ready when logs show `Started SaaStudioAdmin`.

---

## Step 6 — Start the frontend

Open a second dedicated terminal:

```bash
.claude/skills/setup-env/scripts/start-frontend.sh
```

- Sources NVM and activates the Node version from `frontend/.nvmrc` (if
  present) or NVM default.
- Runs `npm install --ignore-scripts` (plain `npm install` breaks on husky).
- Builds `packages/spark-flow` before starting `packages/main`.
- Frontend dev server: `http://localhost:8000` (proxies `/api/*` and
  `/console/*` to backend `:8080`).

**To pin your Node version:**

```bash
echo '24' > frontend/.nvmrc   # or 22, lts/jod, etc.
```

---

## Step 7 — Sanity check

After all services are up, verify everything is working:

```bash
.claude/skills/setup-env/scripts/sanity-check.sh
```

Checks: all 5 middleware ports reachable, MySQL has 27+ tables, Elasticsearch
cluster is healthy, Nacos HTTP console responds, backend `/actuator/health`
returns 200, frontend returns 200.

---

## Shutdown

```bash
# Stop backend: Ctrl-C in its terminal
# Stop frontend: Ctrl-C in its terminal

# Stop middleware
.claude/skills/setup-env/scripts/stop-middleware.sh

# Stop + wipe all data (destructive — prompts to confirm)
.claude/skills/setup-env/scripts/stop-middleware.sh --clean

# Stop Colima VM (optional — frees ~6 GB RAM)
colima stop
```

---

## Daily workflow (after first-time setup)

```bash
colima start
.claude/skills/setup-env/scripts/start-middleware.sh
# terminal 2:
.claude/skills/setup-env/scripts/start-backend.sh
# terminal 3:
.claude/skills/setup-env/scripts/start-frontend.sh
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `command not found: java` | `source ~/.zshrc` or open a new terminal |
| `docker compose: unknown command` | Run `install-deps.sh` — it links the compose plugin |
| Docker pull timeout | Colima mirrors not active — `colima stop && colima start` |
| RocketMQ `Permission denied runbroker.sh` | Started without the override file — use `start-middleware.sh` |
| Elasticsearch exit 137 (OOM) | `~/.colima/default/colima.yaml`: set `memory: 6`, then restart Colima |
| MySQL `Table 'mysql.user' doesn't exist` | `stop-middleware.sh && rm -rf docker/middleware/mysql/data/* && start-middleware.sh` |
| Backend `No model config` | `model-config.yml` is empty — re-run `configure-env.sh` |
| Backend can't connect to Nacos | Nacos still initializing — wait 30 s, run `sanity-check.sh` again |
| `npm install` fails on husky | `start-frontend.sh` already passes `--ignore-scripts` |
| `cross-env: command not found` | `cd frontend && npm install cross-env --force` |
| Node wrong version | `echo '24' > frontend/.nvmrc` then re-run `start-frontend.sh` |
