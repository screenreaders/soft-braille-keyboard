#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  SENTRY_AUTH_TOKEN=... SENTRY_ORG_SLUG=... SENTRY_TEAM_SLUG=... \
    ./scripts/create_sentry_project.sh [project-slug] [project-name]

Defaults:
  project-slug = soft-braille-keyboard
  project-name = Soft Braille Keyboard

Requirements:
  - curl
  - jq
  - Sentry auth token with project:write, org:read, org:write scopes
EOF
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

require_cmd() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_cmd curl
require_cmd jq
require_env SENTRY_AUTH_TOKEN
require_env SENTRY_ORG_SLUG
require_env SENTRY_TEAM_SLUG

PROJECT_SLUG="${1:-soft-braille-keyboard}"
PROJECT_NAME="${2:-Soft Braille Keyboard}"
SENTRY_BASE_URL="${SENTRY_BASE_URL:-https://sentry.io}"

API_BASE="${SENTRY_BASE_URL%/}/api/0"
AUTH_HEADER="Authorization: Bearer ${SENTRY_AUTH_TOKEN}"

echo "Checking whether project already exists..."
PROJECT_URL="${API_BASE}/projects/${SENTRY_ORG_SLUG}/${PROJECT_SLUG}/"
EXISTING_STATUS="$(curl -sS -o /tmp/sentry_project_get.json -w '%{http_code}' \
  -H "$AUTH_HEADER" "$PROJECT_URL" || true)"

if [[ "$EXISTING_STATUS" == "200" ]]; then
  echo "Project already exists: ${PROJECT_SLUG}"
  PROJECT_JSON="$(cat /tmp/sentry_project_get.json)"
else
  echo "Creating project ${PROJECT_SLUG} in ${SENTRY_ORG_SLUG}/${SENTRY_TEAM_SLUG}..."
  CREATE_URL="${API_BASE}/teams/${SENTRY_ORG_SLUG}/${SENTRY_TEAM_SLUG}/projects/"
  PROJECT_JSON="$(curl -fsS -X POST \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    --data @- \
    "$CREATE_URL" <<EOF
{
  "name": "${PROJECT_NAME}",
  "slug": "${PROJECT_SLUG}",
  "platform": "android"
}
EOF
)"
fi

PROJECT_ID="$(printf '%s' "$PROJECT_JSON" | jq -r '.id')"
PROJECT_WEB_URL="$(printf '%s' "$PROJECT_JSON" | jq -r '.webUrl // .url // empty')"

if [[ -z "$PROJECT_ID" || "$PROJECT_ID" == "null" ]]; then
  echo "Could not read project id from Sentry response." >&2
  printf '%s\n' "$PROJECT_JSON" >&2
  exit 1
fi

echo "Fetching client keys / DSN..."
KEYS_URL="${API_BASE}/projects/${SENTRY_ORG_SLUG}/${PROJECT_SLUG}/keys/"
KEYS_JSON="$(curl -fsS -H "$AUTH_HEADER" "$KEYS_URL")"
DSN="$(printf '%s' "$KEYS_JSON" | jq -r '.[0].dsn.public // empty')"

if [[ -z "$DSN" ]]; then
  echo "Project created, but no public DSN was found." >&2
  printf '%s\n' "$KEYS_JSON" >&2
  exit 1
fi

cat <<EOF
Sentry project ready.

Project slug: ${PROJECT_SLUG}
Project id: ${PROJECT_ID}
Project URL: ${PROJECT_WEB_URL}
Public DSN: ${DSN}

Next steps:
  export SENTRY_DSN='${DSN}'
  ./gradlew :app:assembleDebug
EOF
