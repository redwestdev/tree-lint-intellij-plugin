#!/usr/bin/env bash
# Signs the plugin distribution with the local Marketplace key.
# Usage: ./scripts/sign-plugin.sh
# Override the key location with SIGNING_KEY_FILE / SIGNING_CHAIN_FILE.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

key_file="${SIGNING_KEY_FILE:-$HOME/.config/redwest/tree-lint-signing/signing-key.pem}"
chain_file="${SIGNING_CHAIN_FILE:-$HOME/.config/redwest/tree-lint-signing/signing-chain.crt}"

for f in "$key_file" "$chain_file"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing file: $f" >&2
    exit 1
  fi
done

if [[ -z "${PRIVATE_KEY_PASSWORD:-}" ]]; then
  read -rs -p "Key passphrase: " PRIVATE_KEY_PASSWORD
  echo
fi

export CERTIFICATE_CHAIN="$(cat "$chain_file")"
export PRIVATE_KEY="$(cat "$key_file")"
export PRIVATE_KEY_PASSWORD

echo "Signing with $key_file"
rm -f build/distributions/*-signed.zip
./gradlew signPlugin --console=plain

signed="$(ls -t build/distributions/*-signed.zip 2>/dev/null | head -1 || true)"
if [[ -z "$signed" ]]; then
  echo "No signed archive produced - check that :signPlugin was not SKIPPED." >&2
  exit 1
fi

echo
echo "Signed archive: $signed"
