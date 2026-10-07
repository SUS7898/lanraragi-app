#!/usr/bin/env bash
# Creates the release signing keystore for LRR Viewer and prints the values to put into
# GitHub repository secrets. Run ONCE and back the .jks file up somewhere safe:
# if the key is lost, existing installs cannot be updated (users must uninstall/reinstall).
set -euo pipefail

OUT="${1:-release.jks}"
ALIAS="${KEY_ALIAS:-lrrviewer}"

if [ -f "$OUT" ]; then
  echo "error: $OUT already exists - refusing to overwrite." >&2
  exit 1
fi

command -v keytool >/dev/null || { echo "error: keytool (JDK) not found" >&2; exit 1; }

read -r -s -p "Keystore password (>= 8 chars): " STORE_PW; echo
read -r -s -p "Key password (Enter = same as keystore): " KEY_PW; echo
KEY_PW="${KEY_PW:-$STORE_PW}"

keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$STORE_PW" -keypass "$KEY_PW" \
  -dname "CN=LRR Viewer, OU=Personal, O=SUS7898, C=KR"

echo
echo "Keystore written to $OUT  (alias: $ALIAS)"
echo
echo "== GitHub repository secrets =="
echo "KEYSTORE_BASE64   : (below, one line)"
base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
echo "KEYSTORE_PASSWORD : <the keystore password you typed>"
echo "KEY_ALIAS         : $ALIAS"
echo "KEY_PASSWORD      : <the key password you typed>"
echo
echo "Signing certificate SHA-256 (for README / manual verification):"
keytool -list -v -keystore "$OUT" -alias "$ALIAS" -storepass "$STORE_PW" | grep -A1 "SHA256" | head -2
echo
echo "Optional local use: create keystore.properties (git-ignored) with"
echo "  storeFile=$OUT"
echo "  storePassword=..."
echo "  keyAlias=$ALIAS"
echo "  keyPassword=..."
