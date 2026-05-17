#!/usr/bin/env bash
# start-backend.sh — start the Spring Boot backend with the local profile
# Sources .env from the project root for API keys.
# Run from the project root (spring-ai-alibaba-admin/).

set -euo pipefail

ENV_FILE=".env"
SERVER_DIR="spring-ai-alibaba-admin-server-start"
PROFILE="${1:-local}"  # pass 'dev' as arg to use the remote dev server profile

# ── Load API keys from .env ─────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  echo "Missing .env — run: .claude/skills/setup-env/scripts/configure-env.sh"
  exit 1
fi

# Load KEY=VALUE pairs from .env — skip blanks, strip comments
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue  # blank / comment line
  line="${line%% #*}"   # strip trailing inline comment (space + #)
  line="${line%%	#*}"  # strip trailing inline comment (tab + #)
  line="${line%"${line##*[![:space:]]}"}"  # trim trailing whitespace
  [[ "$line" =~ ^[A-Z_][A-Z0-9_]*=.* ]] || continue  # only KEY=VALUE
  export "${line?}"
done < "$ENV_FILE"

# ── Validate at least one API key is set ───────────────────────────────
if [ -z "${OPENAI_API_KEY:-}" ] && [ -z "${DASHSCOPE_API_KEY:-}" ] && [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "No API key found in .env. Edit .env and uncomment your key, then re-run."
  exit 1
fi

# ── Set Java 17 via jenv ────────────────────────────────────────────────
# Uses the per-directory .java-version (pinned to 17 at the admin root).
# No shell-rc mutation: we resolve JAVA_HOME directly from jenv each run.
if ! command -v jenv >/dev/null 2>&1; then
  echo "jenv not found — run: .claude/skills/setup-env/scripts/install-deps.sh"
  echo "  (or, if jenv was just installed, open a new terminal / source ~/.zshrc)"
  exit 1
fi
JAVA_HOME_RESOLVED="$(jenv prefix 17 2>/dev/null)" || {
  echo "jenv has no JDK 17 registered — run: .claude/skills/setup-env/scripts/install-deps.sh"
  exit 1
}
export JAVA_HOME="$JAVA_HOME_RESOLVED"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Java:  $("$JAVA_HOME/bin/java" --version 2>&1 | head -1)"
echo "Maven: $(mvn --version 2>&1 | head -1)"
echo "Profile: $PROFILE"
echo ""

# ── Build sibling modules, then start ───────────────────────────────────
# Install all modules to local .m2 first — spring-boot:run in server-start
# requires core/runtime/openapi to be resolvable. First run ~500 MB download.
echo "Installing project modules..."
mvn install -DskipTests -q

echo "Starting Spring Boot..."
cd "$SERVER_DIR"
exec mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
