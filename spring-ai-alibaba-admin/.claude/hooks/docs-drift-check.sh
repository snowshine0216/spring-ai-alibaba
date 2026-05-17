#!/usr/bin/env bash
set -eo pipefail

# Fail open if jq is missing — never block a commit because of a tooling gap.
command -v jq >/dev/null 2>&1 || exit 0

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

if ! echo "$COMMAND" | grep -qE '^(git +commit|gh +pr +create)\b'; then
    exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
STAGED=$(git diff --cached --name-only)
[ -z "$STAGED" ] && exit 0

MAP=".claude/skills/docs-auto-sync/source-doc-map.json"
[ -f "$MAP" ] || exit 0

STALE=()
while IFS= read -r doc; do
    globs=$(jq -r --arg d "$doc" '.[$d].globs[]' "$MAP")
    while IFS= read -r glob; do
        # Translate fnmatch glob -> POSIX ERE.
        # Use a placeholder for ** before escaping dots and replacing single *.
        pattern=$(echo "$glob" | sed 's|\*\*|__DS__|g; s|\.|\\.|g; s|\*|[^/]*|g; s|__DS__|.*|g')
        if echo "$STAGED" | grep -qE "^${pattern}$"; then
            STALE+=("$doc")
            break
        fi
    done <<< "$globs"
done < <(jq -r 'keys[]' "$MAP")

if [ ${#STALE[@]} -gt 0 ]; then
    {
        echo "Docs drift detected. Source files affecting these docs are staged:"
        printf '  - %s\n' "${STALE[@]}"
        echo ""
        echo "Invoke the docs-auto-sync skill to regenerate, then retry the commit."
    } >&2
    exit 2
fi

exit 0
