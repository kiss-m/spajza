#!/usr/bin/env bash
# Turns the Android Lint XML report into annotations: errors one by one, warnings grouped by issue id.
set -u
report="${1:-app/build/reports/lint-results-debug.xml}"
[[ -f "$report" ]] || { echo "::notice title=Lint::no report at $report"; exit 0; }
python3 - "$report" <<'PY'
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

def esc(text):
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")

issues = ET.parse(sys.argv[1]).getroot().findall("issue")
errors, warnings = [], defaultdict(list)
for issue in issues:
    location = issue.find("location")
    where = ""
    if location is not None:
        path = location.get("file", "").split("/app/src/")[-1]
        where = f"{path}:{location.get('line', '?')}"
    line = f"{where} {issue.get('message', '')}".strip()
    if issue.get("severity") in ("Error", "Fatal"):
        errors.append((issue.get("id"), line))
    else:
        warnings[issue.get("id")].append(line)

for issue_id, line in errors[:7]:
    print(f"::error title=Lint {issue_id}::{esc(line)[:3000]}")
groups = sorted(warnings.items(), key=lambda kv: -len(kv[1]))
summary = ", ".join(f"{issue_id} {len(lines)}" for issue_id, lines in groups) or "none"
print(f"::notice title=Lint summary::{len(errors)} errors; warnings: {summary}")
for issue_id, lines in groups[:6]:
    print(f"::warning title=Lint {issue_id} ({len(lines)})::{esc(chr(10).join(lines[:8]))[:3000]}")
PY
exit 0
