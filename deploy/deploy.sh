#!/usr/bin/env bash
#
# Deploy Stackline on the application server, one project at a time.
#
# Each repository deploys only itself (its own workflow calls one target):
#
#   ./deploy.sh backend     the ERP backend   (stack-line-services)
#   ./deploy.sh web         the web app       (stack-line-ui)
#   ./deploy.sh agents      agents-services
#   ./deploy.sh console     the platform console at /console (stackline-console-ui)
#   ./deploy.sh crawling    the Python crawler (sites-crawler)
#   ./deploy.sh configure   syncs crawler AI variables without deploying
#
# Manual only, for a first setup or a recovery (they build several images):
#
#   ./deploy.sh auto        rebuilds every service whose source changed
#   ./deploy.sh all         rebuilds all five services
#
# Deployments never overlap: a server-wide lock makes a second one wait until
# the first finishes, whichever repository started it. Building two images at
# once on this machine is what saturates it.

set -euo pipefail

SERVICES_DIR="$HOME/stackline/stack-line-services"
UI_DIR="$HOME/stackline/stack-line-ui"
AGENTS_DIR="$HOME/stackline/agents-services"
CRAWLER_DIR="$HOME/stackline/sites-crawler"
CRAWLER_REPOSITORY="${CRAWLER_REPOSITORY:-https://github.com/bragonya/sites-crawler.git}"
CRAWLER_BRANCH="feat/initial-crawling-runtime"
CONSOLE_DIR="$HOME/stackline/stackline-console-ui"
CONSOLE_REPOSITORY="${CONSOLE_REPOSITORY:-https://github.com/fescobarperez/stackline-console-ui.git}"
CONSOLE_CLONED=false
DEPLOY_DIR="$SERVICES_DIR/deploy"
LOCK_FILE="$HOME/stackline/.deploy.lock"
# How long a deployment waits for another one before giving up (seconds).
LOCK_WAIT="${DEPLOY_LOCK_WAIT:-2400}"
TARGET="${1:-}"

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

ensure_console_repository() {
  if [ -d "$CONSOLE_DIR/.git" ]; then
    return
  fi
  if [ -e "$CONSOLE_DIR" ]; then
    echo "Console path exists but is not a Git repository: $CONSOLE_DIR" >&2
    exit 1
  fi
  say "Preparing console repository"
  git clone --quiet --single-branch --branch main "$CONSOLE_REPOSITORY" "$CONSOLE_DIR"
  # A fresh clone has no changes to detect, but no image either: build it.
  CONSOLE_CLONED=true
}

case "$TARGET" in
  configure|auto|backend|web|agents|crawling|console|all) ;;
  "") echo "Missing target: use backend|web|agents|console|crawling|configure (or auto|all by hand)" >&2; exit 1 ;;
  *) echo "Invalid target: $TARGET (use backend|web|agents|console|crawling|configure|auto|all)" >&2; exit 1 ;;
esac

# One deployment at a time on this server, whichever repository started it.
mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "Another deployment is running; waiting for it to finish (up to $((LOCK_WAIT / 60)) min)..."
  if ! flock -w "$LOCK_WAIT" 9; then
    echo "Gave up waiting for the other deployment" >&2
    exit 1
  fi
fi

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

# Only the repositories this target needs. The backend starts the crawler
# container too, so it needs its image (and the repository to build it once).
case "$TARGET" in
  backend|crawling|auto|all) ensure_crawler_repository ;;
esac
case "$TARGET" in
  console|auto|all) ensure_console_repository ;;
esac

# Commit of a repository; "none" if it is not on this server (not deployed here).
rev() { git -C "$1" rev-parse HEAD 2>/dev/null || echo none; }

say "1/4 · Updating repositories"
backend_before=$(rev "$SERVICES_DIR")
ui_before=$(rev "$UI_DIR")
agents_before=$(rev "$AGENTS_DIR")
crawler_before=$(rev "$CRAWLER_DIR")
console_before=$(rev "$CONSOLE_DIR")

git -C "$SERVICES_DIR" pull --ff-only -q origin main
case "$TARGET" in
  backend) ;;
  web)      git -C "$UI_DIR" pull --ff-only -q origin main ;;
  agents)   git -C "$AGENTS_DIR" pull --ff-only -q origin main ;;
  crawling) git -C "$CRAWLER_DIR" pull --ff-only -q origin "$CRAWLER_BRANCH" ;;
  console)  git -C "$CONSOLE_DIR" pull --ff-only -q origin main ;;
  auto|all)
    git -C "$UI_DIR" pull --ff-only -q origin main
    git -C "$AGENTS_DIR" pull --ff-only -q origin main
    git -C "$CRAWLER_DIR" pull --ff-only -q origin "$CRAWLER_BRANCH"
    git -C "$CONSOLE_DIR" pull --ff-only -q origin main
    ;;
esac

backend_after=$(rev "$SERVICES_DIR")
ui_after=$(rev "$UI_DIR")
agents_after=$(rev "$AGENTS_DIR")
crawler_after=$(rev "$CRAWLER_DIR")
console_after=$(rev "$CONSOLE_DIR")

printf '  backend  %s -> %s\n' "${backend_before:0:7}" "${backend_after:0:7}"
printf '  web      %s -> %s\n' "${ui_before:0:7}" "${ui_after:0:7}"
printf '  agents   %s -> %s\n' "${agents_before:0:7}" "${agents_after:0:7}"
printf '  crawler  %s -> %s\n' "${crawler_before:0:7}" "${crawler_after:0:7}"
printf '  console  %s -> %s\n' "${console_before:0:7}" "${console_after:0:7}"

say "2/4 · Selecting images"
build=()
case "$TARGET" in
  backend)  build=(backend) ;;
  web)      build=(web) ;;
  agents)   build=(agents) ;;
  crawling) build=(crawling) ;;
  console)  build=(console) ;;
  all)      build=(backend crawling console web agents) ;;
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
    if [ "$CONSOLE_CLONED" = "true" ]; then
      build+=(console)
    elif [ "$console_before" != "$console_after" ] && \
       ! git -C "$CONSOLE_DIR" diff --quiet "$console_before" "$console_after" -- \
         src public index.html package.json package-lock.json vite.config.js Dockerfile Caddyfile; then
      build+=(console)
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
  for service in "${build[@]}"; do
    echo "  building: $service"
    docker compose build "$service"
  done
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
# Only what this target deployed: a service that is down elsewhere does not
# fail another project's deployment.
case "$TARGET" in
  backend)  checks=(api crawler) ;;
  web)      checks=(web) ;;
  agents)   checks=(agents) ;;
  crawling) checks=(crawler) ;;
  console)  checks=(console) ;;
  auto|all) checks=(web api agents crawler console) ;;
esac

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1" || true; }

check() {
  local code
  case "$1" in
    web)     [ "$(http_code http://localhost/)" = "200" ] ;;
    api)     [ "$(http_code http://localhost/api/clients)" = "401" ] ;;
    agents)  code=$(http_code http://localhost/webhooks/whatsapp); [ "${code:0:1}" = "4" ] ;;
    # Inside its own container: it does not depend on the web proxy being redeployed.
    console) docker compose exec -T console wget -q -O /dev/null http://127.0.0.1/console/ >/dev/null 2>&1 ;;
    crawler) docker compose exec -T crawling python -c \
               "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/healthz', timeout=5).read()" \
               >/dev/null 2>&1 ;;
  esac
}

service_of() {
  case "$1" in
    web) echo web ;; api) echo backend ;; agents) echo agents ;;
    console) echo console ;; crawler) echo crawling ;;
  esac
}

pending=("${checks[@]}")
for _ in $(seq 1 40); do
  pending=()
  for c in "${checks[@]}"; do
    check "$c" || pending+=("$c")
  done
  if [ ${#pending[@]} -eq 0 ]; then
    echo "  Responding: ${checks[*]}"
    docker compose ps --format '  {{.Service}}  {{.Status}}'
    exit 0
  fi
  sleep 5
done

echo "Deployment verification timed out (not responding: ${pending[*]}). Recent logs:" >&2
docker compose logs $(for c in "${pending[@]}"; do service_of "$c"; done) --tail 30
exit 1
