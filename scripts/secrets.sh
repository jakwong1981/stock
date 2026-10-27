#!/usr/bin/env bash
# Manage the dotenvx-encrypted .env file.
#
#   ./scripts/secrets.sh init               Create .env from .env.example, generate DB/JWT secrets, encrypt
#   ./scripts/secrets.sh set KEY [VALUE]    Set & encrypt a value (prompts silently if VALUE omitted)
#   ./scripts/secrets.sh check              Verify .env is encrypted and .env.keys is not tracked by git
#
# The decryption key lives in .env.keys (git-ignored). For servers/CI, provide it via the
# DOTENV_PRIVATE_KEY environment variable instead of copying the file.
set -euo pipefail
cd "$(dirname "$0")/.."

DOTENVX=(npx --yes @dotenvx/dotenvx@2.38.2)
# Local key custody (.env.keys); no interactive prompts, no cloud/password-manager integrations
LOCAL=(--no-armor --no-native --no-1password --no-bitwarden)

die() { echo "✗ $*" >&2; exit 1; }

cmd_init() {
  [[ -f .env ]] && die ".env already exists. Use 'set' to change values."
  cp .env.example .env
  "${DOTENVX[@]}" set POSTGRES_PASSWORD "$(openssl rand -base64 24 | tr -d '/+=' )" "${LOCAL[@]}" </dev/null >/dev/null
  "${DOTENVX[@]}" set JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n')" "${LOCAL[@]}" </dev/null >/dev/null
  "${DOTENVX[@]}" encrypt "${LOCAL[@]}" </dev/null >/dev/null
  echo "✓ Created encrypted .env and .env.keys (keep .env.keys private; never commit it)."
  echo "  Next: ./scripts/secrets.sh set DEEPSEEK_API_KEY"
}

cmd_set() {
  local key="${1:-}" value="${2:-}"
  [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] || die "Usage: secrets.sh set KEY [VALUE]"
  [[ -f .env ]] || die ".env not found. Run: ./scripts/secrets.sh init"
  if [[ -z "$value" ]]; then
    read -r -s -p "Value for $key: " value; echo
  fi
  [[ -n "$value" ]] || die "Empty value"
  "${DOTENVX[@]}" set "$key" "$value" "${LOCAL[@]}" </dev/null >/dev/null
  echo "✓ $key stored encrypted in .env"
}

cmd_check() {
  local problems=0
  [[ -f .env ]] || die ".env not found"
  # Every non-empty value must be encrypted (except the dotenvx public key header)
  while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^# || "$line" =~ ^DOTENV_PUBLIC_KEY ]] && continue
    local val="${line#*=}"; val="${val%\"}"; val="${val#\"}"
    if [[ -n "$val" && "$val" != encrypted:* ]]; then
      echo "✗ plaintext value: ${line%%=*}"; problems=$((problems + 1))
    fi
  done < .env
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if git ls-files --error-unmatch .env.keys >/dev/null 2>&1; then
      echo "✗ .env.keys is tracked by git — remove it: git rm --cached .env.keys"; problems=$((problems + 1))
    fi
    git check-ignore -q .env.keys || { echo "✗ .env.keys is not git-ignored"; problems=$((problems + 1)); }
  else
    grep -qx '.env.keys' .gitignore || { echo "✗ .env.keys missing from .gitignore"; problems=$((problems + 1)); }
  fi
  (( problems == 0 )) && echo "✓ .env is fully encrypted and .env.keys is ignored" || exit 1
}

case "${1:-}" in
  init) cmd_init ;;
  set) shift; cmd_set "$@" ;;
  check) cmd_check ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
