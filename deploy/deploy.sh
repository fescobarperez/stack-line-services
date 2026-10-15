#!/usr/bin/env bash
#
# Despliegue de Stackline en el servidor.
#
#   ./deploy.sh          reconstruye solo lo que cambió
#   ./deploy.sh backend  fuerza el backend
#   ./deploy.sh web      fuerza el front
#   ./deploy.sh agents   fuerza el agente (agents-services)
#   ./deploy.sh all      fuerza los tres
#
# Reconstruir el backend cuesta ~8 minutos en una t4g.small, así que por
# defecto se compara el commit antes y después del pull y solo se toca lo
# que de verdad se movió.

set -euo pipefail

SERVICES_DIR="$HOME/stackline/stack-line-services"
UI_DIR="$HOME/stackline/stack-line-ui"
AGENTS_DIR="$HOME/stackline/agents-services"
DEPLOY_DIR="$SERVICES_DIR/deploy"
TARGET="${1:-auto}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# ── Traer cambios ─────────────────────────────────────────────────────
say "1/4 · Actualizando repositorios"
back_before=$(git -C "$SERVICES_DIR" rev-parse HEAD)
ui_before=$(git -C "$UI_DIR" rev-parse HEAD)
agents_before=$(git -C "$AGENTS_DIR" rev-parse HEAD)

git -C "$SERVICES_DIR" pull --ff-only -q origin main
git -C "$UI_DIR"       pull --ff-only -q origin main
git -C "$AGENTS_DIR"   pull --ff-only -q origin main

back_after=$(git -C "$SERVICES_DIR" rev-parse HEAD)
ui_after=$(git -C "$UI_DIR" rev-parse HEAD)
agents_after=$(git -C "$AGENTS_DIR" rev-parse HEAD)

printf '  backend  %s → %s\n' "${back_before:0:7}" "${back_after:0:7}"
printf '  front    %s → %s\n' "${ui_before:0:7}" "${ui_after:0:7}"
printf '  agente   %s → %s\n' "${agents_before:0:7}" "${agents_after:0:7}"

# ── Decidir qué reconstruir ───────────────────────────────────────────
say "2/4 · Decidiendo qué reconstruir"
build=()
case "$TARGET" in
  backend) build=(backend) ;;
  web)     build=(web) ;;
  agents)  build=(agents) ;;
  all)     build=(backend web agents) ;;
  auto)
    # Solo importa lo que entra en la imagen. Un cambio en el README o en
    # docker-compose.yml no justifica recompilar el jar.
    if [ "$back_before" != "$back_after" ] && \
       ! git -C "$SERVICES_DIR" diff --quiet "$back_before" "$back_after" -- src build.gradle.kts gradle deploy/Dockerfile; then
      build+=(backend)
    fi
    if [ "$ui_before" != "$ui_after" ]; then
      build+=(web)
    fi
    if [ "$agents_before" != "$agents_after" ] && \
       ! git -C "$AGENTS_DIR" diff --quiet "$agents_before" "$agents_after" -- src build.gradle.kts gradle deploy/Dockerfile; then
      build+=(agents)
    fi
    ;;
  *) echo "Objetivo no válido: $TARGET (usa auto|backend|web|agents|all)"; exit 1 ;;
esac

cd "$DEPLOY_DIR"

if [ ${#build[@]} -eq 0 ]; then
  echo "  sin cambios que requieran reconstruir"
  say "3/4 · Aplicando configuración"
  docker compose up -d
else
  echo "  a reconstruir: ${build[*]}"
  say "3/4 · Construyendo y levantando"
  docker compose up -d --build "${build[@]}"
fi

# ── Comprobar que quedó en pie ────────────────────────────────────────
say "4/4 · Verificando"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/ || true)
  if [ "$code" = "200" ]; then
    # El front responde aunque el backend esté caído: se comprueba también
    # la API, que es la que depende del jar y de las migraciones.
    api=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/api/clients || true)
    # El agente: Caddy le reenvía /webhooks/whatsapp. Sin token responde
    # 4xx; un 502 significa que el contenedor no está arriba.
    wa=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/webhooks/whatsapp || true)
    if [ "$api" = "401" ] && [ "${wa:0:1}" = "4" ]; then
      echo "  ✓ front, API y agente respondiendo"
      docker compose ps --format '  {{.Service}}  {{.Status}}'
      exit 0
    fi
  fi
  sleep 5
done

echo "  ✗ no respondió a tiempo. Últimas líneas del backend y del agente:"
docker compose logs backend --tail 25
docker compose logs agents --tail 25
exit 1
