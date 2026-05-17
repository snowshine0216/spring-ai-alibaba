#!/usr/bin/env bash
# check-deps.sh — dependency checklist for Spring AI Alibaba Admin dev env
# Run from the project root (spring-ai-alibaba-admin/).
# Exits 0 if all required deps pass, 1 if any required dep is missing.

set -uo pipefail

PASS="[OK]  "
FAIL="[MISS]"
WARN="[WARN]"

req=0; req_fail=0; opt_fail=0

require() {
  local label="$1"
  if eval "$2" >/dev/null 2>&1; then
    echo "$PASS $label"
    ((req++))
  else
    echo "$FAIL $label"
    ((req_fail++))
  fi
}

optional() {
  local label="$1"
  if eval "$2" >/dev/null 2>&1; then
    echo "$PASS $label"
  else
    echo "$WARN $label  (optional — needed for this feature)"
    ((opt_fail++))
  fi
}

echo ""
echo "=== Build Tools ==="
require "Java 17"  "/opt/homebrew/opt/openjdk@17/bin/java --version"
require "Maven"    "/opt/homebrew/bin/mvn --version"

echo ""
echo "=== Node.js (NVM) ==="
require "NVM installed"  "[ -s \"\${NVM_DIR:-\$HOME/.nvm}/nvm.sh\" ]"
require "Node active"    ". \"\${NVM_DIR:-\$HOME/.nvm}/nvm.sh\" 2>/dev/null && node --version"
optional ".nvmrc in frontend/"  "[ -f frontend/.nvmrc ]"

echo ""
echo "=== Docker Runtime (Colima) ==="
require "Colima installed"        "command -v colima"
require "Docker CLI"              "command -v docker"
require "Docker Compose plugin"   "docker compose version"
require "Docker daemon reachable"  "docker info"
require "Registry mirrors set"    "docker info | grep -A1 'Registry Mirrors' | grep -q 'http'"

echo ""
echo "=== Configuration ==="
require ".env file exists"        "[ -f .env ]"
require "API key in .env"         "grep -qE '^(OPENAI|DASHSCOPE|DEEPSEEK)_API_KEY=[^[:space:]]' .env 2>/dev/null"
require "model-config.yml filled" "[ \"\$(wc -l < spring-ai-alibaba-admin-server-start/model-config.yml 2>/dev/null)\" -gt 3 ]"

echo ""
echo "=== Middleware Ports ==="
require "MySQL      :3306"   "nc -z -w2 localhost 3306"
require "Redis      :6379"   "nc -z -w2 localhost 6379"
require "Elasticsearch:9200" "nc -z -w2 localhost 9200"
require "Nacos      :8848"   "nc -z -w2 localhost 8848"
require "RocketMQ   :18080"  "nc -z -w2 localhost 18080"

echo ""
echo "=== App Endpoints (only valid after backend + frontend start) ==="
optional "Backend  :8080"   "curl -sf --max-time 2 http://localhost:8080/actuator/health -o /dev/null"
optional "Frontend :8000"   "curl -sf --max-time 2 http://localhost:8000 -o /dev/null"

echo ""
echo "────────────────────────────────────────"
if [ "$req_fail" -gt 0 ]; then
  echo "Required: $req passed, $req_fail MISSING"
  echo "Run: .claude/skills/setup-env/scripts/install-deps.sh"
  exit 1
else
  echo "Required: all $req passed"
  [ "$opt_fail" -gt 0 ] && echo "Optional: $opt_fail warnings (see above)"
  exit 0
fi
