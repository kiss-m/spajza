#!/usr/bin/env bash
# Writes a placeholder app/google-services.json so the app builds without Firebase secrets.
# Firebase calls fail at runtime with it; use the real file (never committed) to run the app.
set -euo pipefail
target="$(dirname "$0")/../app/google-services.json"
if [[ -f "$target" ]]; then
  echo "app/google-services.json already exists, leaving it untouched"
  exit 0
fi
cat > "$target" <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "spajza-placeholder",
    "storage_bucket": "spajza-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": { "package_name": "sk.matej.spajza" }
      },
      "oauth_client": [
        { "client_id": "000000000000-placeholder.apps.googleusercontent.com", "client_type": 3 }
      ],
      "api_key": [ { "current_key": "AIzaPlaceholderPlaceholderPlaceholder00" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
JSON
echo "Wrote placeholder $target"
