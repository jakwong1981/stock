#!/usr/bin/env bash
# Build, lint and test every component, then build Docker images.
#
#   ./scripts/build.sh                 everything: frontend + backend + analytics-ai + docker images
#   ./scripts/build.sh frontend|backend|ai|images
#   ./scripts/build.sh mobile <https://api.example.com>   build web bundle for Capacitor and sync iOS/Android
#
# Options (env): SKIP_TESTS=1  skip test suites
#                SKIP_IT=1     skip backend integration tests (they need Docker for Testcontainers)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
die() { echo "✗ $*" >&2; exit 1; }

# Testcontainers needs DOCKER_HOST when Docker runs via OrbStack / Colima / Docker Desktop contexts
if [[ -z "${DOCKER_HOST:-}" ]] && command -v docker >/dev/null 2>&1; then
  ctx_host="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  [[ -n "$ctx_host" ]] && export DOCKER_HOST="$ctx_host"
fi

build_frontend() {
  log "Frontend: install, i18n check, tests, type-check & build"
  cd "$ROOT/frontend"
  npm ci --no-audit --no-fund
  npm run i18n:check
  [[ "${SKIP_TESTS:-}" == 1 ]] || npm run test
  npm run build
  cd "$ROOT"
}

build_backend() {
  log "Backend: compile, unit + integration tests, package"
  cd "$ROOT/backend"
  local args=(-B -q)
  if [[ "${SKIP_TESTS:-}" == 1 ]]; then
    args+=(-DskipTests)
  elif [[ "${SKIP_IT:-}" == 1 ]]; then
    args+=(-Dtest='!ApiIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false)
  fi
  mvn "${args[@]}" verify
  cd "$ROOT"
}

build_ai() {
  log "Analytics AI: venv, lint, tests"
  cd "$ROOT/analytics-ai"
  local py="${PYTHON:-}"
  if [[ -z "$py" ]]; then
    for c in python3.13 python3.12 python3.11 python3.15 python3.14 python3; do
      if command -v "$c" >/dev/null && "$c" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)'; then
        py="$c"; break
      fi
    done
  fi
  [[ -n "$py" ]] || die "Python ≥ 3.11 is required (set PYTHON=/path/to/python3.x)"
  [[ -d .venv ]] || "$py" -m venv .venv
  .venv/bin/pip install -q --upgrade pip
  .venv/bin/pip install -q -e '.[dev]'
  .venv/bin/ruff check .
  [[ "${SKIP_TESTS:-}" == 1 ]] || .venv/bin/python -m pytest -q
  cd "$ROOT"
}

build_images() {
  log "Docker images"
  command -v docker >/dev/null || die "docker not found"
  # Build-time only: no secrets are needed or passed to image builds
  POSTGRES_PASSWORD=build JWT_SECRET=build docker compose -p cyberstock --env-file /dev/null build
}

build_mobile() {
  local api="${1:-}"
  [[ "$api" =~ ^https:// ]] || die "Usage: build.sh mobile https://your-public-host  (native apps require HTTPS)"
  log "Mobile: web bundle with VITE_API_BASE_URL=$api, then Capacitor sync"
  cd "$ROOT/frontend"
  npm ci --no-audit --no-fund
  VITE_API_BASE_URL="$api" npm run build
  npx cap sync
  echo "✓ Open native projects with: (cd frontend && npx cap open ios)  /  (cd frontend && npx cap open android)"
  cd "$ROOT"
}

case "${1:-all}" in
  all) build_frontend; build_backend; build_ai; build_images ;;
  frontend) build_frontend ;;
  backend) build_backend ;;
  ai) build_ai ;;
  images) build_images ;;
  mobile) shift; build_mobile "$@" ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
log "Done"
