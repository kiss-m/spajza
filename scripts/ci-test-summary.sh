#!/usr/bin/env bash
# Prints one notice per module with its unit test counts, so a green run visibly ran the tests.
set -u
python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET
from collections import defaultdict

totals = defaultdict(lambda: [0, 0, 0])
for report in glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True):
    module = report.split("/build/")[0] or "root"
    variant = report.split("/test-results/")[1].split("/")[0]
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    key = f"{module} ({variant})"
    totals[key][0] += int(root.get("tests", 0))
    totals[key][1] += int(root.get("failures", 0)) + int(root.get("errors", 0))
    totals[key][2] += int(root.get("skipped", 0))
summary = "; ".join(f"{key}: {t} tests, {f} failed, {s} skipped" for key, (t, f, s) in sorted(totals.items()))
print(f"::notice title=Unit tests::{summary or 'no test reports found'}")
PY
exit 0
