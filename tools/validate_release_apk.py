#!/usr/bin/env python3
"""Fail a release when the APK is oversized, multi-ABI, or keeps Flutter symbols."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path


MIB = 1024 * 1024
FLUTTER_LIBRARIES = {"libflutter.so", "libapp.so"}
DEBUG_SECTION_MARKERS = (
    b".debug_",
    b".symtab\x00",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate production APK size, ABI set, and Flutter debug symbols."
    )
    parser.add_argument("apk", type=Path, help="Release APK to validate")
    parser.add_argument(
        "--max-size-mb",
        type=float,
        default=120.0,
        help="Maximum APK file size in MiB (default: 120)",
    )
    parser.add_argument(
        "--expected-abi",
        action="append",
        help="Expected APK ABI; repeat for more than one ABI (optional)",
    )
    return parser.parse_args()


def stream_contains_debug_marker(
    archive: zipfile.ZipFile, info: zipfile.ZipInfo
) -> bytes | None:
    overlap = max(len(marker) for marker in DEBUG_SECTION_MARKERS) - 1
    tail = b""
    with archive.open(info, "r") as stream:
        while chunk := stream.read(1024 * 1024):
            data = tail + chunk
            for marker in DEBUG_SECTION_MARKERS:
                if marker in data:
                    return marker
            tail = data[-overlap:] if overlap else b""
    return None


def validate(args: argparse.Namespace) -> list[str]:
    failures: list[str] = []
    if not args.apk.is_file():
        return [f"APK does not exist: {args.apk}"]
    if args.max_size_mb <= 0:
        return ["--max-size-mb must be greater than zero"]

    apk_size_mb = args.apk.stat().st_size / MIB
    if apk_size_mb > args.max_size_mb:
        failures.append(
            f"APK is {apk_size_mb:.2f} MiB; limit is {args.max_size_mb:.2f} MiB"
        )

    expected_abis = set(args.expected_abi or [])
    actual_abis: set[str] = set()
    flutter_entries: list[zipfile.ZipInfo] = []

    try:
        with zipfile.ZipFile(args.apk) as archive:
            for info in archive.infolist():
                parts = info.filename.split("/")
                if len(parts) != 3 or parts[0] != "lib" or not parts[2].endswith(".so"):
                    continue
                actual_abis.add(parts[1])
                if parts[2] in FLUTTER_LIBRARIES:
                    flutter_entries.append(info)

            if expected_abis and actual_abis != expected_abis:
                failures.append(
                    "ABI set is "
                    f"{sorted(actual_abis)}; expected {sorted(expected_abis)}"
                )

            if not flutter_entries:
                failures.append("APK contains no libflutter.so or libapp.so entries")
            else:
                for info in flutter_entries:
                    marker = stream_contains_debug_marker(archive, info)
                    if marker is not None:
                        failures.append(
                            f"{info.filename} retains debug section marker "
                            f"{marker.rstrip(bytes([0])).decode('ascii', errors='replace')}"
                        )
    except zipfile.BadZipFile:
        failures.append(f"Not a valid APK/ZIP archive: {args.apk}")

    if not failures:
        print(
            "Release APK OK: "
            f"{apk_size_mb:.2f} MiB, ABIs={','.join(sorted(actual_abis))}, "
            "Flutter debug sections absent"
        )
    return failures


def main() -> int:
    args = parse_args()
    failures = validate(args)
    if failures:
        print("Release APK validation failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
