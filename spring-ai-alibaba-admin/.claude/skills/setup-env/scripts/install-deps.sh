#!/usr/bin/env bash
# install-deps.sh — install missing build/runtime prerequisites
# Idempotent: safe to re-run. Only installs what's missing.
# Run from the project root (spring-ai-alibaba-admin/).

set -euo pipefail

COLIMA_YAML="$HOME/.colima/default/colima.yaml"
COMPOSE_PLUGIN="$HOME/.docker/cli-plugins/docker-compose"

step() { echo ""; echo "▶ $*"; }
ok()   { echo "  ✓ $*"; }
skip() { echo "  · $* (already present)"; }

# ── Java 17 ────────────────────────────────────────────────────────────
step "Java 17"
if /opt/homebrew/opt/openjdk@17/bin/java --version >/dev/null 2>&1; then
  skip "openjdk@17"
else
  brew install openjdk@17
  ok "openjdk@17 installed"
fi
# Persist on PATH
if ! grep -q "openjdk@17" "$HOME/.zshrc" 2>/dev/null; then
  cat >> "$HOME/.zshrc" <<'EOF'

# Java 17 (Spring AI Alibaba Admin)
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
EOF
  ok "Added Java 17 to ~/.zshrc"
fi

# ── Maven ───────────────────────────────────────────────────────────────
step "Maven"
if /opt/homebrew/bin/mvn --version >/dev/null 2>&1; then
  skip "maven"
else
  brew install maven
  ok "maven installed"
fi

# ── Colima + Docker CLI ─────────────────────────────────────────────────
step "Colima + Docker CLI + docker-compose plugin"
if ! command -v colima >/dev/null 2>&1; then
  brew install colima docker docker-compose
  ok "colima + docker + docker-compose installed"
else
  skip "colima"
fi

# Link docker-compose as a CLI plugin so "docker compose" works
if ! docker compose version >/dev/null 2>&1; then
  mkdir -p "$(dirname "$COMPOSE_PLUGIN")"
  ln -sfn /opt/homebrew/opt/docker-compose/bin/docker-compose "$COMPOSE_PLUGIN"
  ok "docker-compose plugin linked at $COMPOSE_PLUGIN"
else
  skip "docker compose plugin"
fi

# ── Colima: memory + registry mirrors ──────────────────────────────────
step "Colima configuration (6 GB RAM + registry mirrors for CN)"
if [ -f "$COLIMA_YAML" ]; then
  # Increase memory to 6 GB if still at default 2 GB
  if grep -q "^memory: 2$" "$COLIMA_YAML"; then
    sed -i '' 's/^memory: 2$/memory: 6/' "$COLIMA_YAML"
    ok "Colima memory set to 6 GB"
  else
    skip "Colima memory (already set)"
  fi

  # Add registry mirrors if not present
  if grep -q "^docker: {}$" "$COLIMA_YAML"; then
    sed -i '' 's/^docker: {}$/docker:\
  registry-mirrors:\
    - https:\/\/docker.m.daocloud.io\
    - https:\/\/docker.nju.edu.cn\
    - https:\/\/dockerhub.icu/' "$COLIMA_YAML"
    ok "Registry mirrors added to Colima config"
  elif ! grep -q "registry-mirrors" "$COLIMA_YAML" 2>/dev/null; then
    ok "Colima docker section already customised — verify mirrors manually"
  else
    skip "Registry mirrors (already set)"
  fi
else
  echo "  ! $COLIMA_YAML not found — start Colima once first, then re-run this script"
fi

# ── docker-compose-override.yaml ────────────────────────────────────────
step "docker/middleware/docker-compose-override.yaml (macOS fixes)"
OVERRIDE="docker/middleware/docker-compose-override.yaml"
if [ -f "$OVERRIDE" ]; then
  skip "$OVERRIDE"
else
  cat > "$OVERRIDE" <<'YAML'
# macOS (Colima) override — two fixes:
# 1. Removes UID/GID user constraint on RocketMQ (macOS UID can't exec container scripts).
# 2. Reduces Elasticsearch heap to 512 MB so it fits in a 6 GB Colima VM.
services:
  rmq_namesrv:
    user: ""
  rmq_broker:
    user: ""
  rmq_proxy:
    user: ""
  elasticsearch:
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - xpack.security.enrollment.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      - cluster.name=es-cluster
      - node.name=es-node-1
      - bootstrap.memory_lock=true
      - http.cors.enabled=true
      - 'http.cors.allow-origin="*"'
      - action.destructive_requires_name=false
YAML
  ok "$OVERRIDE created"
fi

echo ""
echo "────────────────────────────────────────"
echo "Install complete. Next steps:"
echo "  1. source ~/.zshrc  (or open a new terminal)"
echo "  2. .claude/skills/setup-env/scripts/configure-env.sh"
echo "  3. colima start"
echo "  4. .claude/skills/setup-env/scripts/start-middleware.sh"
