#!/usr/bin/env bash
# Tests for docs-drift-check.sh
# Usage: bash .claude/hooks/tests/docs-drift-check.test.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
HOOK="$ROOT/.claude/hooks/docs-drift-check.sh"
MAP_SRC="$ROOT/.claude/skills/docs-auto-sync/source-doc-map.json"

PASS=0
FAIL=0

# run_test NAME STDIN_JSON EXPECTED_EXIT [STDERR_CONTAINS]
# Reads the SETUP env var to run setup commands inside the tmp repo
# (e.g., creating + staging files).
run_test() {
    local name="$1"; shift
    local input="$1"; shift
    local expected_exit="$1"; shift
    local stderr_contains="${1:-}"

    local tmp; tmp=$(mktemp -d)
    (
        cd "$tmp"
        git init -q
        git config user.email t@example.com
        git config user.name "Test"
        mkdir -p .claude/skills/docs-auto-sync
        cp "$MAP_SRC" .claude/skills/docs-auto-sync/source-doc-map.json
        eval "${SETUP:-:}"
    ) >/dev/null

    local actual_stderr actual_exit
    set +e
    actual_stderr=$(PATH="${HOOK_PATH:-$PATH}" CLAUDE_PROJECT_DIR="$tmp" bash "$HOOK" <<< "$input" 2>&1 >/dev/null)
    actual_exit=$?
    set -e

    local ok=1
    [ "$actual_exit" = "$expected_exit" ] || ok=0
    if [ -n "$stderr_contains" ] && ! echo "$actual_stderr" | grep -q "$stderr_contains"; then
        ok=0
    fi

    if [ "$ok" = "1" ]; then
        echo "  ok   $name"
        PASS=$((PASS+1))
    else
        echo "  FAIL $name"
        echo "       expected exit=$expected_exit got=$actual_exit"
        echo "       stderr=$actual_stderr"
        FAIL=$((FAIL+1))
    fi

    rm -rf "$tmp"
    unset SETUP
}

# ---- Tests are appended below this line in subsequent tasks ----

run_test "non-commit command exits 0" \
    '{"tool_input":{"command":"ls -la"}}' \
    0

run_test "git commit with no staged files exits 0" \
    '{"tool_input":{"command":"git commit -m wip"}}' \
    0

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" = "0" ]
