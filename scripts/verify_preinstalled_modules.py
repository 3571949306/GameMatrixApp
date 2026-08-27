#!/usr/bin/env python3
"""Verify that every external catalog module is bundled by the host build task."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle"
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "catalog.json"

MAPPING_RE = re.compile(
    r"^\s*\['(:module-store:[^']+)',\s*'([^']+)',\s*'([^']+)',\s*'([^']+)',\s*(true|false)\]",
    re.MULTILINE,
)


def main() -> int:
    gradle_text = BUILD_GRADLE.read_text(encoding="utf-8")
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))

    mappings = {
        match.group(4): {
            "project": match.group(1),
            "source": match.group(2),
            "target": match.group(3),
            "preinstall": match.group(5) == "true",
        }
        for match in MAPPING_RE.finditer(gradle_text)
    }
    external = {
        module["id"]: module["fileName"]
        for module in catalog["modules"]
        if module.get("fileName")
    }

    errors: list[str] = []
    missing = sorted(set(external) - set(mappings))
    extra = sorted(set(mappings) - set(external))
    if missing:
        errors.append(f"catalog modules missing from bundle mapping: {', '.join(missing)}")
    if extra:
        errors.append(f"bundle mapping has no external catalog module: {', '.join(extra)}")

    targets = [entry["target"] for entry in mappings.values()]
    duplicates = sorted({name for name in targets if targets.count(name) > 1})
    if duplicates:
        errors.append(f"duplicate preinstalled APK target names: {', '.join(duplicates)}")

    for module_id, file_name in external.items():
        entry = mappings.get(module_id)
        if not entry:
            continue
        if not entry["preinstall"]:
            errors.append(f"{module_id}: preinstall flag is false")
        if entry["target"] != file_name:
            errors.append(
                f"{module_id}: target APK {entry['target']!r} does not match catalog {file_name!r}"
            )
        project_dir = ROOT.joinpath(*entry["project"].lstrip(":").split(":"))
        if not (project_dir / "build.gradle").exists():
            errors.append(f"{module_id}: mapped project does not exist: {entry['project']}")

    if errors:
        print("PREINSTALLED MODULES: FAIL")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"PREINSTALLED MODULES: PASS ({len(external)} external catalog modules mapped and enabled)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
