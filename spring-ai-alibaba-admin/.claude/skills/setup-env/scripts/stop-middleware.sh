#!/usr/bin/env bash
# stop-middleware.sh — stop all middleware containers
# Run from the project root (spring-ai-alibaba-admin/).
# Pass --clean to also delete data volumes (destructive).

set -euo pipefail

COMPOSE_DIR="docker/middleware"
PROD_FILE="docker-compose-prod.yaml"
OVERRIDE_FILE="docker-compose-override.yaml"

CLEAN=false
for arg in "$@"; do
  [ "$arg" = "--clean" ] && CLEAN=true
done

cd "$COMPOSE_DIR"

if $CLEAN; then
  echo "WARNING: --clean will delete all MySQL/ES/Redis/RocketMQ data."
  read -r -p "Type 'yes' to confirm: " confirm
  [ "$confirm" = "yes" ] || { echo "Aborted."; exit 1; }
  docker compose -f "$PROD_FILE" -f "$OVERRIDE_FILE" down -v --remove-orphans
  rm -rf mysql/data/* nacos/data/* nacos/logs/* rocketmq/store/*
  echo "All data volumes cleared."
else
  docker compose -f "$PROD_FILE" -f "$OVERRIDE_FILE" down --remove-orphans
fi

echo "Middleware stopped."
