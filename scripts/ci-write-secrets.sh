#!/usr/bin/env bash
# Writes app/google-services.json and keystore/debug.keystore from the CI secrets and checks them,
# so a secret pasted wrongly fails here with a clear message instead of as an obscure build error.
#   GOOGLE_SERVICES_JSON  the google-services.json file from the Firebase console, pasted as is
#   DEBUG_KEYSTORE_B64    the dedicated debug keystore, base64 encoded
set -euo pipefail
cd "$(dirname "$0")/.."

fail() {
  echo "::error title=$1::$2"
  exit 1
}

mkdir -p keystore
printf '%s' "$DEBUG_KEYSTORE_B64" | tr -d ' \r\n' | base64 --decode > keystore/debug.keystore 2>/dev/null ||
  fail "DEBUG_KEYSTORE_B64" "The secret is not valid base64. Paste the whole text of the keystore file again."
sha1=$(keytool -list -v -keystore keystore/debug.keystore -storetype pkcs12 -storepass android -alias androiddebugkey 2>/dev/null |
  sed -n 's/^[[:space:]]*SHA1:[[:space:]]*//p') ||
  fail "DEBUG_KEYSTORE_B64" "The secret is not the expected keystore (password android, alias androiddebugkey)."
[[ -n "$sha1" ]] || fail "DEBUG_KEYSTORE_B64" "The keystore has no androiddebugkey entry."
echo "::notice title=Debug key SHA-1::$sha1"

printf '%s\n' "$GOOGLE_SERVICES_JSON" > app/google-services.json
python3 - app/google-services.json "$sha1" <<'PY'
import json
import sys

PACKAGE = "sk.matej.spajza"

def fail(message):
    print(f"::error title=GOOGLE_SERVICES_JSON::{message}")
    sys.exit(1)

try:
    config = json.load(open(sys.argv[1]))
except ValueError:
    fail("The secret is not valid JSON. Paste the whole google-services.json file again.")
expected_hash = sys.argv[2].replace(":", "").lower()
clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
if not clients:
    fail(f"No Android app with the package name {PACKAGE}. Register the app in Firebase with exactly this name.")
oauth = clients[0].get("oauth_client", [])
if not any(o.get("client_type") == 3 for o in oauth):
    fail("No web client for Google sign-in. Enable Google in Firebase Authentication, then download google-services.json again.")
hashes = {o.get("android_info", {}).get("certificate_hash", "").lower() for o in oauth if o.get("client_type") == 1}
if expected_hash not in hashes:
    # The APK still builds; only Google sign-in will fail until the SHA-1 is registered.
    print("::warning title=GOOGLE_SERVICES_JSON::The debug key's SHA-1 is not registered for this app, so Google "
          "sign-in will fail. Add the SHA-1 from the notice above in the app's Firebase settings, then download "
          "google-services.json again.")
print(f"::notice title=Firebase config::project {config.get('project_info', {}).get('project_id')}, package {PACKAGE}")
PY
