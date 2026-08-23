#!/usr/bin/env python3
"""Snapshot and verify release assets that normal builds/tests must not rewrite."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SNAPSHOT = ROOT / "build/agent-verification/protected-assets.json"
FIXED_PATHS = (
    Path("app/src/main/assets/catalog.json"),
    Path("app/src/main/assets/modules.json"),
    Path("version.properties"),
)


def protected_paths() -> list[Path]:
    module_dir = ROOT / "app/src/main/assets/modules"
    apk_paths = sorted(path.relative_to(ROOT) for path in module_dir.glob("*.apk"))
    return [*FIXED_PATHS, *apk_paths]


def fingerprint(path: Path) -> dict[str, object]:
    absolute = ROOT / path
    if not absolute.is_file():
        return {"exists": False}
    digest = hashlib.sha256()
    with absolute.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"exists": True, "size": absolute.stat().st_size, "sha256": digest.hexdigest()}


def current_state() -> dict[str, dict[str, object]]:
    return {path.as_posix(): fingerprint(path) for path in protected_paths()}


def snapshot(output: Path) -> int:
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(current_state(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"PROTECTED ASSETS: snapshot written to {output}")
    return 0


def verify(input_path: Path) -> int:
    input_path = input_path.resolve()
    if not input_path.is_file():
        print(f"PROTECTED ASSETS: FAIL\n- snapshot not found: {input_path}", file=sys.stderr)
        return 2
    before = json.loads(input_path.read_text(encoding="utf-8"))
    after = current_state()
    changes = []
    for path in sorted(set(before) | set(after)):
        if before.get(path) != after.get(path):
            changes.append(f"{path}: {before.get(path)} -> {after.get(path)}")
    if changes:
        print("PROTECTED ASSETS: FAIL\n- " + "\n- ".join(changes), file=sys.stderr)
        return 1
    print(f"PROTECTED ASSETS: PASS ({len(after)} files unchanged)")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("snapshot", "verify"))
    parser.add_argument("--file", type=Path, default=DEFAULT_SNAPSHOT)
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    raise SystemExit(snapshot(args.file) if args.command == "snapshot" else verify(args.file))
