#!/usr/bin/env bash
# sanity-check.sh — verify the full dev environment is working correctly
# Run from the project root (spring-ai-alibaba-admin/).
# Checks: middleware ports, MySQL schema, backend API, frontend.

set -uo pipefail

PASS="[OK]  "
FAIL="[FAIL]"
WARN="[WARN]"
fails=0

check_port() {
  local label="$1" host="${2:-localhost}" port="$3"
  if nc -z -w2 "$host" "$port" 2>/dev/null; then
    echo "$PASS $label ($host:$port)"
  else
    echo "$FAIL $label ($host:$port) — not reachable"
    ((fails++))
  fi
}

check_http() {
  local label="$1" url="$2" expected_status="${3:-200}"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null; true)
  if [ "$status" = "$expected_status" ]; then
    echo "$PASS $label ($url) → $status"
  else
    echo "$FAIL $label ($url) → $status (expected $expected_status)"
    ((fails++))
  fi
}

check_http_warn() {
  local label="$1" url="$2"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null; true)
  if [ "$status" = "200" ] || [ "$status" = "401" ] || [ "$status" = "302" ]; then
    echo "$PASS $label ($url) → $status"
  else
    echo "$WARN $label ($url) → $status (backend or frontend not started yet)"
  fi
}

check_mysql_tables() {
  local expected="${1:-27}"
  if ! nc -z -w2 localhost 3306 2>/dev/null; then
    echo "$FAIL MySQL table check skipped — port 3306 not reachable"
    ((fails++))
    return
  fi
  local count
  count=$(docker exec mysql mysql -uadmin -padmin admin \
    -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='admin';" \
    --skip-column-names 2>/dev/null || echo "0")
  count=$(echo "$count" | tr -d '[:space:]')
  if [ "$count" -ge "$expected" ]; then
    echo "$PASS MySQL schema ($count tables in 'admin' db)"
  else
    echo "$FAIL MySQL schema — found $count tables, expected >= $expected"
    echo "      Hint: tables may not have been initialized yet."
    echo "      Run: docker logs mysql | tail -20"
    ((fails++))
  fi
}

check_es_index() {
  if ! nc -z -w2 localhost 9200 2>/dev/null; then
    echo "$WARN Elasticsearch index check skipped — port 9200 not reachable"
    return
  fi
  local health
  health=$(curl -sf --max-time 5 "http://localhost:9200/_cluster/health" 2>/dev/null \
    | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
  if [ "$health" = "green" ] || [ "$health" = "yellow" ]; then
    echo "$PASS Elasticsearch cluster ($health)"
  else
    echo "$FAIL Elasticsearch cluster status: $health"
    ((fails++))
  fi
}

check_nacos() {
  if ! nc -z -w2 localhost 7848 2>/dev/null; then
    echo "$WARN Nacos HTTP console check skipped — port 7848 not reachable"
    return
  fi
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://localhost:7848/nacos" 2>/dev/null; true)
  if [ "$status" = "200" ] || [ "$status" = "302" ]; then
    echo "$PASS Nacos HTTP console (:7848) → $status"
  else
    echo "$WARN Nacos HTTP console (:7848) → $status"
  fi
}

# ══════════════════════════════════════════════════════
echo ""
echo "=== Middleware Ports ==="
check_port "MySQL"         localhost 3306
check_port "Redis"         localhost 6379
check_port "Elasticsearch" localhost 9200
check_port "Nacos gRPC"    localhost 8848
check_port "RocketMQ proxy" localhost 18080

echo ""
echo "=== Data Layer ==="
check_mysql_tables 27
check_es_index
check_nacos

echo ""
echo "=== Application Endpoints ==="
check_http_warn "Backend  /actuator/health" "http://localhost:8080/actuator/health"
check_http_warn "Frontend :8000"            "http://localhost:8000"

echo ""
echo "=== Container Health ==="
docker ps --format "  {{.Names}}: {{.Status}}" 2>/dev/null | sort || \
  echo "  (docker not available)"

echo ""
echo "=== .env / model config ==="
if [ -f ".env" ]; then
  provider=""
  grep -qE '^OPENAI_API_KEY=[^[:space:]#]'    .env 2>/dev/null && provider="openai"
  grep -qE '^DASHSCOPE_API_KEY=[^[:space:]#]' .env 2>/dev/null && provider="dashscope"
  grep -qE '^DEEPSEEK_API_KEY=[^[:space:]#]'  .env 2>/dev/null && provider="deepseek"
  if [ -n "$provider" ]; then
    echo "$PASS .env — provider: $provider"
  else
    echo "$FAIL .env — no API key set (or key is commented out)"
    ((fails++))
  fi
else
  echo "$FAIL .env missing — run: .claude/skills/setup-env/scripts/configure-env.sh"
  ((fails++))
fi

model_lines=$(wc -l < "spring-ai-alibaba-admin-server-start/model-config.yml" 2>/dev/null || echo 0)
model_lines=$(echo "$model_lines" | tr -d '[:space:]')
if [ "$model_lines" -gt 3 ]; then
  echo "$PASS model-config.yml ($model_lines lines)"
else
  echo "$FAIL model-config.yml is empty — run: .claude/skills/setup-env/scripts/configure-env.sh"
  ((fails++))
fi

echo ""
echo "════════════════════════════════════════"
if [ "$fails" -eq 0 ]; then
  echo "All checks passed."
  exit 0
else
  echo "$fails check(s) failed. Review output above."
  exit 1
fi
