#!/usr/bin/env bash
set -eo pipefail

# Fail open if jq is missing — never block a commit because of a tooling gap.
command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
    exit 0
fi

exit 0
