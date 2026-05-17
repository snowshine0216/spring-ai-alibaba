#!/usr/bin/env bash
# configure-env.sh — create/validate .env at the project root
# The .env file holds API keys and is sourced by start-backend.sh.
# It is gitignored — never committed.
# Run from the project root (spring-ai-alibaba-admin/).

set -euo pipefail

ENV_FILE=".env"
MODEL_CONFIG="spring-ai-alibaba-admin-server-start/model-config.yml"

# ── Create .env if missing ──────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<'EOF'
# Model provider API keys — uncomment ONE block and fill in your key.
# This file is gitignored. Do NOT commit it.

# --- OpenAI ---
# OPENAI_API_KEY=sk-...
# OPENAI_BASE_URL=https://api.openai.com/v1   # override for proxies / compatible APIs

# --- DashScope (Alibaba Cloud) ---
# DASHSCOPE_API_KEY=...

# --- DeepSeek ---
# DEEPSEEK_API_KEY=...
EOF
  echo "Created $ENV_FILE"
  echo ""
  echo "Edit .env and uncomment + fill in your API key, then re-run this script."
  exit 0
fi

# ── Detect which provider key is configured ─────────────────────────────
detect_provider() {
  if grep -qE '^OPENAI_API_KEY=[^[:space:]#]' "$ENV_FILE" 2>/dev/null; then
    echo "openai"
  elif grep -qE '^DASHSCOPE_API_KEY=[^[:space:]#]' "$ENV_FILE" 2>/dev/null; then
    echo "dashscope"
  elif grep -qE '^DEEPSEEK_API_KEY=[^[:space:]#]' "$ENV_FILE" 2>/dev/null; then
    echo "deepseek"
  else
    echo ""
  fi
}

PROVIDER=$(detect_provider)

if [ -z "$PROVIDER" ]; then
  echo "No API key found in $ENV_FILE."
  echo "Edit .env and uncomment + fill in one of the API key entries, then re-run."
  exit 1
fi

echo "Detected provider: $PROVIDER"

# ── Sync model-config.yml from the matching template ───────────────────
TEMPLATE="spring-ai-alibaba-admin-server-start/model-config-${PROVIDER}.yaml"

if [ ! -f "$TEMPLATE" ]; then
  echo "Template not found: $TEMPLATE"
  exit 1
fi

if cmp -s "$TEMPLATE" "$MODEL_CONFIG" 2>/dev/null; then
  echo "model-config.yml already matches $PROVIDER template — nothing to do."
else
  cp "$TEMPLATE" "$MODEL_CONFIG"
  echo "Copied $TEMPLATE → $MODEL_CONFIG"
fi

echo ""
echo "Configuration OK:"
echo "  Provider : $PROVIDER"
echo "  .env     : $ENV_FILE"
echo "  model cfg: $MODEL_CONFIG"
echo ""
echo "Next: .claude/skills/setup-env/scripts/start-backend.sh"
