#!/usr/bin/env bash
# Writes app/google-services.json and keystore/debug.keystore from the SPAJZA_SECRETS CI secret and
# checks them, so a secret pasted wrongly fails here with a clear message instead of an obscure build
# error. The secret is one JSON object, prepared for the owner so they paste a single value:
#   {"google_services_json": <google-services.json from Firebase>, "debug_keystore_base64": "<PKCS12>"}
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p keystore

python3 - <<'PY'
import base64
import json
import os
import sys

def fail(message):
    print(f"::error title=SPAJZA_SECRETS::{message}")
    sys.exit(1)

try:
    secrets = json.loads(os.environ["SPAJZA_SECRETS"])
    config = secrets["google_services_json"]
    keystore = base64.b64decode("".join(secrets["debug_keystore_base64"].split()), validate=True)
except (ValueError, KeyError, TypeError, AttributeError):
    fail("The secret is not the text that was prepared for it. Paste the whole prepared text again.")
with open("app/google-services.json", "w") as out:
    json.dump(config, out, indent=2)
with open("keystore/debug.keystore", "wb") as out:
    out.write(keystore)
PY

sha1=$(keytool -list -v -keystore keystore/debug.keystore -storetype pkcs12 -storepass android -alias androiddebugkey 2>/dev/null |
  sed -n 's/^[[:space:]]*SHA1:[[:space:]]*//p') || true
[[ -n "$sha1" ]] || { echo "::error title=SPAJZA_SECRETS::The debug keystore inside the secret cannot be read (password android, alias androiddebugkey)."; exit 1; }
echo "::notice title=Debug key SHA-1::$sha1"

python3 - app/google-services.json "$sha1" <<'PY'
import json
import sys

PACKAGE = "sk.matej.spajza"

def fail(message):
    print(f"::error title=google-services.json::{message}")
    sys.exit(1)

config = json.load(open(sys.argv[1]))
expected_hash = sys.argv[2].replace(":", "").lower()
clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
if not clients:
    fail(f"No Android app with the package name {PACKAGE}. Register the app in Firebase with exactly this name.")
oauth = clients[0].get("oauth_client", [])
if not any(o.get("client_type") == 3 for o in oauth):
    fail("No web client for Google sign-in. Enable Google in Firebase Authentication, then get google-services.json again.")
hashes = {o.get("android_info", {}).get("certificate_hash", "").lower() for o in oauth if o.get("client_type") == 1}
if expected_hash not in hashes:
    # The APK still builds; only Google sign-in will fail until the SHA-1 is registered.
    print("::warning title=google-services.json::The debug key's SHA-1 is not registered for this app, so Google "
          "sign-in will fail. Add the SHA-1 from the notice above in the app's Firebase settings, then get "
          "google-services.json again.")
print(f"::notice title=Firebase config::project {config.get('project_info', {}).get('project_id')}, package {PACKAGE}")
PY
