#!/usr/bin/env python3
"""Print safe class-level diagnostics from Gradle XML failures without test messages."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORTS = sorted(ROOT.glob("**/build/test-results/testDebugUnitTest/TEST-*.xml"))
MISSING_CLASS = re.compile(
    r"(?:NoClassDefFoundError|ClassNotFoundException)[:\s]+([A-Za-z0-9_.$/]+)"
)


def main() -> int:
    failures = 0
    for report in REPORTS:
        try:
            root = ET.parse(report).getroot()
        except (OSError, ET.ParseError) as error:
            print(f"TEST_XML_UNREADABLE {report.relative_to(ROOT)} {type(error).__name__}")
            continue
        for case in root.iter("testcase"):
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                failures += 1
                details = "\n".join((failure.get("message", ""), failure.text or ""))
                match = MISSING_CLASS.search(details)
                missing = match.group(1).replace("/", ".") if match else ""
                exception = failure.get("type", "UnknownFailure").split(".")[-1]
                print(
                    f"TEST_FAILURE {report.relative_to(ROOT)} "
                    f"{case.get('classname', 'UnknownClass')}#{case.get('name', 'UnknownTest')} "
                    f"exception={exception} missingClass={missing or 'none'}"
                )
    if failures == 0:
        print("No JUnit XML failures were available for summary.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
