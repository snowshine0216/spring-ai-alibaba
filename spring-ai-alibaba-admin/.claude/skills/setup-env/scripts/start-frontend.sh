#!/usr/bin/env bash
# start-frontend.sh — install deps, build spark-flow, start the dev server
# Uses NVM to activate the correct Node version.
# Run from the project root (spring-ai-alibaba-admin/).
#
# Node version resolution order:
#   1. frontend/.nvmrc  (preferred — commit this file to pin the version)
#   2. NVM default (whatever `nvm alias default` says)
#
# To pin your version: echo '24' > frontend/.nvmrc

set -euo pipefail

FRONTEND_DIR="frontend"
NVM_INIT="${NVM_DIR:-$HOME/.nvm}/nvm.sh"

# ── Load NVM ────────────────────────────────────────────────────────────
if [ ! -s "$NVM_INIT" ]; then
  echo "NVM not found at $NVM_INIT"
  echo "Install NVM: https://github.com/nvm-sh/nvm"
  exit 1
fi

# shellcheck disable=SC1090
source "$NVM_INIT" --no-use

if [ -f "$FRONTEND_DIR/.nvmrc" ]; then
  echo "Activating Node version from frontend/.nvmrc..."
  (cd "$FRONTEND_DIR" && nvm use)
else
  echo "No frontend/.nvmrc found — using NVM default."
  echo "To pin a version: echo '24' > frontend/.nvmrc"
  nvm use default 2>/dev/null || nvm use node
fi

echo "Node: $(node --version)   npm: $(npm --version)"
echo ""

cd "$FRONTEND_DIR"

# ── Install root deps (--ignore-scripts avoids husky failure) ───────────
echo "Installing dependencies..."
npm install --ignore-scripts

# Fix binary permissions (Makefile targets do the same)
chmod +x node_modules/tailwindcss/lib/cli.js 2>/dev/null || true
if [ ! -f node_modules/cross-env/dist/bin/cross-env.js ]; then
  echo "cross-env missing — installing..."
  npm install cross-env --force
fi
chmod +x node_modules/cross-env/dist/bin/cross-env.js 2>/dev/null || true

# ── Build spark-flow (workspace dep of main) ────────────────────────────
echo ""
echo "Building spark-flow..."
(cd packages/spark-flow && npm run build)

# ── Start dev server ────────────────────────────────────────────────────
echo ""
echo "Starting frontend dev server at http://localhost:8000"
echo "(proxies /api/* and /console/* to backend :8080)"
echo ""
cd packages/main && exec npm run dev
