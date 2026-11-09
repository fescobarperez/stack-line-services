#!/usr/bin/env bash
#
# Deploy Stackline on the application server.
#
#   ./deploy.sh             rebuilds services whose source changed
#   ./deploy.sh configure   syncs crawler AI variables without deploying
#   ./deploy.sh backend     forces the ERP backend
#   ./deploy.sh web         forces the web application
#   ./deploy.sh agents      forces agents-services
#   ./deploy.sh crawling    forces the Python crawler
#   ./deploy.sh all         forces all four services

set -euo pipefail

SERVICES_DIR="$HOME/stackline/stack-line-services"
UI_DIR="$HOME/stackline/stack-line-ui"
AGENTS_DIR="$HOME/stackline/agents-services"
CRAWLER_DIR="$HOME/stackline/sites-crawler"
CRAWLER_REPOSITORY="https://github.com/bragonya/sites-crawler.git"
CRAWLER_BRANCH="feat/initial-crawling-runtime"
DEPLOY_DIR="$SERVICES_DIR/deploy"
TARGET="${1:-auto}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

cleanup_secret_file() {
  if [ -n "${OPENAI_KEY_FILE:-}" ] && [ -e "$OPENAI_KEY_FILE" ]; then
    chmod 600 "$OPENAI_KEY_FILE" 2>/dev/null || true
    rm -f -- "$OPENAI_KEY_FILE"
  fi
}
trap cleanup_secret_file EXIT

sync_openai_environment() {
  if [ -z "${OPENAI_KEY_FILE:-}" ]; then
    return
  fi
  if [ ! -s "$OPENAI_KEY_FILE" ]; then
    echo "The staged OPENAI_API_KEY file is missing or empty" >&2
    exit 1
  fi

  local env_file="$DEPLOY_DIR/.env"
  if [ ! -f "$env_file" ]; then
    echo "Deployment environment file does not exist: $env_file" >&2
    exit 1
  fi

  local openai_key
  IFS= read -r openai_key < "$OPENAI_KEY_FILE"
  if [ -z "$openai_key" ]; then
    echo "OPENAI_API_KEY is empty" >&2
    exit 1
  fi

  OPENAI_KEY_VALUE="$openai_key" python3 - "$env_file" <<'PY'
import os
from pathlib import Path
import stat
import sys
import tempfile

env_path = Path(sys.argv[1])
openai_key = os.environ["OPENAI_KEY_VALUE"]
if "\n" in openai_key or "\r" in openai_key:
    raise SystemExit("OPENAI_API_KEY must be a single line")

updates = {
    "OPENAI_API_KEY": openai_key,
    "AI_MODE": "real",
    "AI_CHAT_MODEL": "gpt-6-luna",
    "AI_CHAT_BASE_URL": "https://api.openai.com/v1/chat/completions",
}
lines = env_path.read_text(encoding="utf-8").splitlines()
seen = set()
updated_lines = []
for line in lines:
    stripped = line.lstrip()
    if not stripped.startswith("#") and "=" in line:
        name = line.split("=", 1)[0].strip()
        if name in updates:
            updated_lines.append(f"{name}={updates[name]}")
            seen.add(name)
            continue
    updated_lines.append(line)

missing = [name for name in updates if name not in seen]
if missing:
    if updated_lines and updated_lines[-1] != "":
        updated_lines.append("")
    updated_lines.append("# Managed by the production deployment workflow.")
    updated_lines.extend(f"{name}={updates[name]}" for name in missing)

mode = stat.S_IMODE(env_path.stat().st_mode)
fd, temporary_name = tempfile.mkstemp(prefix=".env.", dir=env_path.parent)
os.close(fd)
temporary_path = Path(temporary_name)
try:
    temporary_path.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")
    temporary_path.chmod(mode)
    temporary_path.replace(env_path)
finally:
    temporary_path.unlink(missing_ok=True)
PY

  unset openai_key
  cleanup_secret_file
  OPENAI_KEY_FILE=""
  echo "Crawler AI environment updated"
}

ensure_crawler_repository() {
  if [ -d "$CRAWLER_DIR/.git" ]; then
    return
  fi
  if [ -e "$CRAWLER_DIR" ]; then
    echo "Crawler path exists but is not a Git repository: $CRAWLER_DIR" >&2
    exit 1
  fi
  say "Preparing crawler repository"
  git clone --quiet --single-branch --branch "$CRAWLER_BRANCH" \
    "$CRAWLER_REPOSITORY" "$CRAWLER_DIR"
}

case "$TARGET" in
  configure|auto|backend|web|agents|crawling|all) ;;
  *) echo "Invalid target: $TARGET (use configure|auto|backend|web|agents|crawling|all)" >&2; exit 1 ;;
esac

if [ "$TARGET" = "configure" ] && [ -z "${OPENAI_KEY_FILE:-}" ]; then
  echo "The configure target requires OPENAI_KEY_FILE" >&2
  exit 1
fi

# GitHub Actions stages the secret with mode 0600 and passes only its path.
sync_openai_environment
if [ "$TARGET" = "configure" ]; then
  echo "Production crawler AI variables are configured"
  exit 0
fi

# The crawler is public and can be bootstrapped without storing another deploy key.
ensure_crawler_repository

say "1/4 · Updating repositories"
backend_before=$(git -C "$SERVICES_DIR" rev-parse HEAD)
ui_before=$(git -C "$UI_DIR" rev-parse HEAD)
agents_before=$(git -C "$AGENTS_DIR" rev-parse HEAD)
crawler_before=$(git -C "$CRAWLER_DIR" rev-parse HEAD)

git -C "$SERVICES_DIR" pull --ff-only -q origin main
case "$TARGET" in
  backend) ;;
  web)      git -C "$UI_DIR" pull --ff-only -q origin main ;;
  agents)   git -C "$AGENTS_DIR" pull --ff-only -q origin main ;;
  crawling) git -C "$CRAWLER_DIR" pull --ff-only -q origin "$CRAWLER_BRANCH" ;;
  auto|all)
    git -C "$UI_DIR" pull --ff-only -q origin main
    git -C "$AGENTS_DIR" pull --ff-only -q origin main
    git -C "$CRAWLER_DIR" pull --ff-only -q origin "$CRAWLER_BRANCH"
    ;;
esac

backend_after=$(git -C "$SERVICES_DIR" rev-parse HEAD)
ui_after=$(git -C "$UI_DIR" rev-parse HEAD)
agents_after=$(git -C "$AGENTS_DIR" rev-parse HEAD)
crawler_after=$(git -C "$CRAWLER_DIR" rev-parse HEAD)

printf '  backend  %s -> %s\n' "${backend_before:0:7}" "${backend_after:0:7}"
printf '  web      %s -> %s\n' "${ui_before:0:7}" "${ui_after:0:7}"
printf '  agents   %s -> %s\n' "${agents_before:0:7}" "${agents_after:0:7}"
printf '  crawler  %s -> %s\n' "${crawler_before:0:7}" "${crawler_after:0:7}"

say "2/4 · Selecting images"
build=()
case "$TARGET" in
  backend)  build=(backend) ;;
  web)      build=(web) ;;
  agents)   build=(agents) ;;
  crawling) build=(crawling) ;;
  all)      build=(backend crawling web agents) ;;
  auto)
    if [ "$backend_before" != "$backend_after" ] && \
       ! git -C "$SERVICES_DIR" diff --quiet "$backend_before" "$backend_after" -- \
         src build.gradle.kts gradle deploy/Dockerfile; then
      build+=(backend)
    fi
    if [ "$ui_before" != "$ui_after" ]; then
      build+=(web)
    fi
    if [ "$agents_before" != "$agents_after" ] && \
       ! git -C "$AGENTS_DIR" diff --quiet "$agents_before" "$agents_after" -- \
         src build.gradle.kts gradle deploy/Dockerfile; then
      build+=(agents)
    fi
    if [ "$crawler_before" != "$crawler_after" ] && \
       ! git -C "$CRAWLER_DIR" diff --quiet "$crawler_before" "$crawler_after" -- \
         stackline_crawling pyproject.toml Dockerfile; then
      build+=(crawling)
    fi
    ;;
esac

cd "$DEPLOY_DIR"
# Resolve interpolation before touching running containers. In particular,
# production crawling must not start without OPENAI_API_KEY.
docker compose config --quiet

if [ ${#build[@]} -eq 0 ]; then
  echo "  no image rebuild required"
  say "3/4 · Applying configuration"
  docker compose up -d
else
  echo "  rebuilding: ${build[*]}"
  say "3/4 · Building and starting"
  docker compose build "${build[@]}"
  case "$TARGET" in
    auto|all)
      docker compose up -d --no-build
      ;;
    backend)
      # A backend-only rollout still guarantees that its internal dependency exists.
      docker compose up -d crawling
      docker compose up -d --no-deps backend
      ;;
    *)
      docker compose up -d --no-deps "${build[@]}"
      ;;
  esac
fi

say "4/4 · Verifying"
for _ in $(seq 1 40); do
  root_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/ || true)
  api_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/api/clients || true)
  agent_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/webhooks/whatsapp || true)
  crawler_ok=false
  if docker compose exec -T crawling python -c \
      "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/healthz', timeout=5).read()" \
      >/dev/null 2>&1; then
    crawler_ok=true
  fi

  if [ "$root_code" = "200" ] && [ "$api_code" = "401" ] && \
     [ "${agent_code:0:1}" = "4" ] && [ "$crawler_ok" = "true" ]; then
    echo "  Web, API, agents, and crawler are responding"
    docker compose ps --format '  {{.Service}}  {{.Status}}'
    exit 0
  fi
  sleep 5
done

echo "Deployment verification timed out. Recent service logs:" >&2
docker compose logs backend agents crawling --tail 30
exit 1
