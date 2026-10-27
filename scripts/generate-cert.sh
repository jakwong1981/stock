#!/usr/bin/env bash
# scripts/generate-cert.sh — self-signed TLS certificate for the cyberstock-web nginx (port 60003)
#
# Usage:
#   scripts/generate-cert.sh            # writes certs/server.pem + certs/server-key.pem
#   CERT_DIR=/elsewhere scripts/generate-cert.sh
#
# Same convention as ~/github/hobbyland: the shared Caddy terminates public TLS with
# Let's Encrypt and reaches this container over HTTPS (tls_insecure_skip_verify), so this
# certificate secures the hop inside docker and https://localhost:60003.
# Idempotent: existing certificates are kept; delete them to regenerate.
set -euo pipefail

cd "$(dirname "$0")/.."

CERT_DIR="${CERT_DIR:-certs}"
CERT_FILE="${CERT_DIR}/server.pem"
KEY_FILE="${CERT_DIR}/server-key.pem"

if [ -f "$CERT_FILE" ] && [ -f "$KEY_FILE" ]; then
  echo "==> Certificate already exists at ${CERT_DIR}/ — skipping (delete to regenerate)"
  exit 0
fi

command -v openssl >/dev/null 2>&1 || { echo "!! openssl not found (brew install openssl)" >&2; exit 1; }

mkdir -p "$CERT_DIR"

# 825 days is the maximum validity Apple platforms accept for self-signed certs.
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$KEY_FILE" \
  -out "$CERT_FILE" \
  -days 825 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:cyberstock-web,IP:127.0.0.1" 2>/dev/null

# nginx runs unprivileged inside the container and must be able to read the key
chmod 644 "$CERT_FILE" "$KEY_FILE"

echo "==> Self-signed certificate written to ${CERT_DIR}/ (SAN: localhost, cyberstock-web, 127.0.0.1)"
