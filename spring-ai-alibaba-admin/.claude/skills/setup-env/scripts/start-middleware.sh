#!/usr/bin/env bash
# start-middleware.sh — start all middleware containers
# Run from the project root (spring-ai-alibaba-admin/).
# Requires: Colima running, docker compose plugin installed.

set -euo pipefail

COMPOSE_DIR="docker/middleware"
PROD_FILE="docker-compose-prod.yaml"
OVERRIDE_FILE="docker-compose-override.yaml"

if ! colima status 2>&1 | grep -qi running; then
  echo "Colima is not running. Starting it..."
  colima start
fi

cd "$COMPOSE_DIR"

# Pull images if any are missing (fast no-op when all images are cached)
echo "Pulling images (no-op if cached)..."
docker compose -f "$PROD_FILE" -f "$OVERRIDE_FILE" pull --quiet

echo "Starting containers..."
docker compose -f "$PROD_FILE" -f "$OVERRIDE_FILE" up -d

echo ""
echo "Waiting for MySQL to be healthy..."
for i in $(seq 1 40); do
  status=$(docker inspect mysql --format "{{.State.Health.Status}}" 2>/dev/null || echo "missing")
  if [ "$status" = "healthy" ]; then
    echo "MySQL ready."
    break
  fi
  if [ "$i" -eq 40 ]; then
    echo "MySQL did not become healthy in time. Check: docker logs mysql"
    exit 1
  fi
  sleep 3
done

echo ""
echo "Container status:"
docker ps --format "table {{.Names}}\t{{.Status}}" | sort

echo ""
echo "Run sanity check: .claude/skills/setup-env/scripts/sanity-check.sh"
