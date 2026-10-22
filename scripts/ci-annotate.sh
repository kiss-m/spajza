#!/usr/bin/env bash
# Turns Kotlin compiler errors and failed tests into GitHub Actions annotations.
set -u
log="${1:-build-output.txt}"

if [[ -f "$log" ]]; then
  grep -E '^e: ' "$log" | head -30 | while IFS= read -r line; do
    echo "::error title=Kotlin compile error::${line#e: }"
  done
fi

find . -path '*/build/test-results/*' -name 'TEST-*.xml' -print0 2>/dev/null |
  xargs -0 -r grep -l '<failure' |
  while IFS= read -r report; do
    python3 - "$report" <<'PY'
import sys, xml.etree.ElementTree as ET
for case in ET.parse(sys.argv[1]).getroot().iter("testcase"):
    failure = case.find("failure")
    if failure is not None:
        message = (failure.get("message") or "").replace("%", "%25").replace("\r", "").replace("\n", "%0A")
        print(f"::error title={case.get('classname')} > {case.get('name')}::{message[:3000]}")
PY
  done
exit 0
