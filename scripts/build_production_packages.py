#!/usr/bin/env python3
"""Build deterministic non-executable Web, Asset, and Unity validation packages."""

from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path


FIXED_TIMESTAMP = (2026, 7, 21, 0, 0, 0)


def add_bytes(archive: zipfile.ZipFile, name: str, content: bytes) -> None:
    info = zipfile.ZipInfo(name, FIXED_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, content)


def add_file(archive: zipfile.ZipFile, name: str, source: Path) -> None:
    add_bytes(archive, name, source.read_bytes())


def build_packages(source_root: Path, output_dir: Path) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []

    web_output = output_dir / "web_diagnostics_v2.zip"
    with zipfile.ZipFile(web_output, "w") as archive:
        add_file(archive, "index.html", source_root / "web_diagnostics" / "index.html")
        add_file(archive, "style.css", source_root / "web_diagnostics" / "style.css")
    outputs.append(web_output)

    asset_output = output_dir / "asset_theme_pack_v1.zip"
    asset_manifest = {
        "schemaVersion": 1,
        "moduleId": "asset_theme_pack",
        "versionCode": 1,
        "files": ["data/theme.json"],
    }
    with zipfile.ZipFile(asset_output, "w") as archive:
        add_bytes(
            archive,
            "asset-manifest.json",
            (json.dumps(asset_manifest, ensure_ascii=False, indent=2) + "\n").encode(),
        )
        add_file(
            archive,
            "data/theme.json",
            source_root / "asset_theme_pack" / "data" / "theme.json",
        )
    outputs.append(asset_output)

    unity_output = output_dir / "unity_smoke_content_v1.zip"
    unity_manifest = {
        "schemaVersion": 1,
        "moduleId": "unity_smoke_content",
        "versionCode": 1,
        "launcherId": "unity_smoke",
        "files": ["content/smoke.txt"],
    }
    with zipfile.ZipFile(unity_output, "w") as archive:
        add_bytes(
            archive,
            "unity-manifest.json",
            (json.dumps(unity_manifest, ensure_ascii=False, indent=2) + "\n").encode(),
        )
        add_file(
            archive,
            "content/smoke.txt",
            source_root / "unity_smoke_content" / "content" / "smoke.txt",
        )
    outputs.append(unity_output)
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=Path("production_modules"))
    parser.add_argument("--out-dir", type=Path, required=True)
    args = parser.parse_args()
    outputs = build_packages(args.source_root, args.out_dir)
    for output in outputs:
        print(f"BUILT {output} ({output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
