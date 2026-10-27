#!/usr/bin/env bash
# scripts/deploy.sh — deploy / operate the CyberStock Pulse Docker stack (HTTPS on :60003)
#
#   ./scripts/deploy.sh up [--no-build]  remove existing containers, (re)build, start, wait until healthy
#   ./scripts/deploy.sh down             stop and remove containers (data volumes are kept)
#   ./scripts/deploy.sh status           service health
#   ./scripts/deploy.sh logs [svc]       follow logs
#   ./scripts/deploy.sh smoke            HTTPS smoke test against the running stack
#
# `up` always tears down any existing container first (compose project "cyberstock" plus the
# fixed-name cyberstock-web), so a stale container can never keep serving an old image or keep
# port 60003 bound. Named volumes (pgdata, redisdata) are NOT removed.
#
# Secrets: the dotenvx-encrypted .env is decrypted in memory (key in .env.keys, or
# DOTENV_PRIVATE_KEY on servers/CI). `--env-file /dev/null` stops Compose from reading the
# encrypted file directly.
#
# Public HTTPS: the shared Caddy in ~/github/hobbyland proxies https://$DOMAIN → cyberstock-web:60003
# over the external `shared-proxy` network (see deploy/Caddyfile.snippet).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT="cyberstock"
FIXED_CONTAINERS=(cyberstock-web)
PROXY_NET="${EXTERNAL_PROXY_NET:-shared-proxy}"
WEB_PORT="${WEB_PORT:-60003}"
APP_URL="https://localhost:${WEB_PORT}"
DAEMON_WAIT_S="${DAEMON_WAIT_S:-90}"
HEALTH_WAIT_S="${HEALTH_WAIT_S:-300}"
DOTENVX=(npx --yes @dotenvx/dotenvx@2.38.2)
# Local key custody (.env.keys); no interactive prompts, no cloud/password-manager integrations
LOCAL=(--no-armor --no-native --no-1password --no-bitwarden)

die() { echo "!! $*" >&2; exit 1; }
log() { echo "==> $*"; }

# ---- Docker daemon ---------------------------------------------------------
ensure_docker_daemon() {
  if docker info >/dev/null 2>&1; then log "Docker daemon is running"; return; fi
  log "Docker daemon is not responding — starting it"
  case "$(uname)" in
    Darwin)
      local app started=0
      for app in OrbStack Docker; do
        if [ -d "/Applications/${app}.app" ]; then open -a "$app"; started=1; break; fi
      done
      [ "$started" -eq 1 ] || die "Install/launch OrbStack or Docker Desktop, then re-run"
      ;;
    *) systemctl start docker 2>/dev/null || sudo systemctl start docker ;;
  esac
  local i
  for i in $(seq 1 "$DAEMON_WAIT_S"); do
    docker info >/dev/null 2>&1 && { log "Docker daemon ready after ${i}s"; return; }
    sleep 1
  done
  die "Docker daemon did not become ready in ${DAEMON_WAIT_S}s"
}

# ---- Secrets-aware compose -------------------------------------------------
compose() {
  [ -f .env ] || die ".env not found. Run ./scripts/secrets.sh init"
  if [ ! -f .env.keys ] && [ -z "${DOTENV_PRIVATE_KEY:-}" ]; then
    die "No decryption key: provide .env.keys or DOTENV_PRIVATE_KEY"
  fi
  "${DOTENVX[@]}" run -f .env --quiet "${LOCAL[@]}" -- docker compose -p "$PROJECT" --env-file /dev/null "$@"
}

# ---- Pre-flight ------------------------------------------------------------
ensure_cert() {
  if [ ! -f certs/server.pem ] || [ ! -f certs/server-key.pem ]; then
    log "No TLS certificate in ./certs — generating a self-signed one"
    "$ROOT/scripts/generate-cert.sh"
  fi
}

ensure_proxy_network() {
  if ! docker network inspect "$PROXY_NET" >/dev/null 2>&1; then
    log "Creating external docker network '$PROXY_NET' (shared with hobbyland's Caddy)"
    docker network create "$PROXY_NET" >/dev/null
  fi
}

# ---- Remove existing containers -------------------------------------------
remove_existing_containers() {
  log "Removing any existing CyberStock containers (project '${PROJECT}')"
  compose down --remove-orphans --timeout 15 >/dev/null 2>&1 || true

  # Leftovers: containers still labelled with the project (e.g. created by a different
  # compose file/version) and fixed-name containers that compose no longer tracks.
  local ids name
  ids="$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT}")"
  if [ -n "$ids" ]; then
    log "Removing leftover project containers: $(echo "$ids" | tr '\n' ' ')"
    # shellcheck disable=SC2086
    docker rm -f $ids >/dev/null
  fi
  for name in "${FIXED_CONTAINERS[@]}"; do
    if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
      log "Removing leftover container ${name}"
      docker rm -f "$name" >/dev/null
    fi
  done

  # Anything else still bound to our HTTPS port would make `up` fail
  local holder
  holder="$(docker ps --format '{{.Names}} {{.Ports}}' | grep -E ":${WEB_PORT}->" | awk '{print $1}' || true)"
  [ -z "$holder" ] || die "Port ${WEB_PORT} is used by container '${holder}' (not part of CyberStock)"
}

# ---- Health ----------------------------------------------------------------
wait_until_healthy() {
  log "Waiting for services to become healthy (up to ${HEALTH_WAIT_S}s)"
  local deadline=$((SECONDS + HEALTH_WAIT_S)) states=""
  while (( SECONDS < deadline )); do
    states="$(compose ps --all --format '{{.Service}} {{.State}} {{.Health}}' 2>/dev/null || true)"
    if grep -qE ' (exited|dead) ' <<<"$states"; then
      echo "$states" >&2; compose logs --tail 40 >&2; die "A service exited during startup"
    fi
    if [ -n "$states" ] && ! grep -qvE ' running healthy$' <<<"$states"; then
      echo "$states"
      log "Healthy — ${APP_URL} (self-signed); public: https://${DOMAIN:-stock.jackstudio.app} via Caddy"
      return 0
    fi
    sleep 3
  done
  echo "$states" >&2; compose logs --tail 40 >&2
  die "Timed out waiting for health checks"
}

smoke() {
  local fail=0
  check() {
    local path="$1" expect="$2" body
    # -k: the container serves a self-signed certificate
    if ! body="$(curl -kfsS --max-time 10 -H 'Accept-Language: zh-HK' "${APP_URL}${path}")"; then
      echo "✗ ${path}"; fail=1; return
    fi
    if grep -qF -- "$expect" <<<"$body"; then echo "✓ ${path}"; else echo "✗ ${path} (missing '${expect}')"; fail=1; fi
  }
  check / '<div id="app">'
  check /api/instruments/0700.HK '騰訊控股'
  check '/api/quotes?symbols=AAPL' '"price":"'
  check '/api/candles?symbol=AAPL&interval=1d&limit=5' '"close":"'
  check /api/market/status '"session"'
  check /ai/health '"status":"UP"'
  check '/ai/news?limit=1' '['
  # Plain HTTP on the TLS port must redirect to HTTPS
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${WEB_PORT}/" || true)"
  if [ "$code" = 301 ]; then echo "✓ http → https redirect"; else echo "✗ http → https redirect (got ${code})"; fail=1; fi
  return $fail
}

# ---- Commands --------------------------------------------------------------
cmd_up() {
  local build=1
  [ "${1:-}" = "--no-build" ] && build=0
  ensure_docker_daemon
  ensure_cert
  ensure_proxy_network
  remove_existing_containers
  if [ "$build" -eq 1 ]; then
    log "Building images"
    compose build
  fi
  log "Starting stack (docker compose up -d --force-recreate)"
  compose up -d --force-recreate --remove-orphans
  wait_until_healthy
  smoke || die "Smoke test failed"
}

case "${1:-}" in
  up) shift; cmd_up "$@" ;;
  down) ensure_docker_daemon; remove_existing_containers ;;
  status) compose ps ;;
  logs) shift; compose logs -f --tail=200 "$@" ;;
  smoke) smoke ;;
  *) sed -n '2,8p' "$0"; exit 1 ;;
esac
