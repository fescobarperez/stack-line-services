#!/usr/bin/env bash
#
# Deploy Stackline on the application server.
#
#   ./deploy.sh             rebuilds services whose source changed
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
  auto|backend|web|agents|crawling|all) ;;
  *) echo "Invalid target: $TARGET (use auto|backend|web|agents|crawling|all)" >&2; exit 1 ;;
esac

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
