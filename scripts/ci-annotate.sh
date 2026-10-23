#!/usr/bin/env bash
# Turns Gradle failures, compiler errors and failed tests into GitHub Actions annotations, so they
# can be read from the check-run API without downloading logs. GitHub keeps only 10 annotations of
# one level per step, so related lines are grouped into a few annotations.
set -u
log="${1:-build-output.txt}"

python3 - "$log" <<'PY'
import glob
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAX_MESSAGE = 6000
log_path = Path(sys.argv[1])
lines = log_path.read_text(errors="replace").splitlines() if log_path.exists() else []


def annotate(title, message):
    message = message[:MAX_MESSAGE].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    title = title.replace("%", "%25").replace("\n", " ").replace(",", ";").replace("::", ": ")
    print(f"::error title={title}::{message}")


def chunks(items, size):
    for start in range(0, len(items), size):
        yield items[start:start + size]


# Gradle failure summaries: the text between "* What went wrong:" and "* Try:".
blocks, current = [], None
for line in lines:
    if line.startswith("* What went wrong:"):
        current = []
    elif current is not None and (line.startswith("* Try:") or line.startswith("BUILD FAILED")):
        blocks.append("\n".join(current).strip())
        current = None
    elif current is not None:
        current.append(line)
if current:
    blocks.append("\n".join(current).strip())
for block in blocks[:3]:
    annotate("Gradle failure", block)

# Compiler, KSP, Java (generated Hilt code) and resource errors, in order of appearance.
error_pattern = re.compile(r"^e: |\.java:\d+: error: |AAPT: error|^ERROR: |error: resource ")
errors = [line for line in lines if error_pattern.search(line)]
for group in list(chunks(errors, 12))[:3]:
    annotate("Build errors", "\n".join(group))

# Failed tests from the JUnit XML reports.
failures = []
for report in sorted(glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True)):
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        if failure is not None:
            message = (failure.get("message") or failure.text or "").strip()
            failures.append(f"{case.get('classname')} > {case.get('name')}\n{message[:1500]}")
for group in list(chunks(failures, 4))[:3]:
    annotate("Failed tests", "\n\n".join(group))
PY
exit 0
