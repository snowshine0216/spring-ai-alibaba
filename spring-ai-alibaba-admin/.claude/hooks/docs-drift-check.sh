#!/usr/bin/env bash
set -eo pipefail

command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
    exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
STAGED=$(git diff --cached --name-only)
[ -z "$STAGED" ] && exit 0

exit 0
