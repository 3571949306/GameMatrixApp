#!/usr/bin/env python3
"""Validate the user-facing release announcement before publishing."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


MAX_CHARACTERS = 1600
MAX_BULLETS = 8
RAW_GITHUB_MENTION = re.compile(r"(?<![\\`])@[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
DEVELOPER_ONLY_PATTERNS = {
    "完整 CHANGELOG": re.compile(r"CHANGELOG(?:\.md)?", re.IGNORECASE),
    "构建命令或结果": re.compile(r"(?:gradlew|BUILD SUCCESSFUL)", re.IGNORECASE),
    "Git 操作": re.compile(r"(?:git checkout|git reset|git commit)", re.IGNORECASE),
    "内部调试信息": re.compile(r"(?:logcat|SHA-256|Feature Flag|\bR8\b|\blint\b)", re.IGNORECASE),
    "源码文件名": re.compile(r"\.(?:kt|java|xml|gradle|json|py)\b", re.IGNORECASE),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate RELEASE_NOTES.md as a short, user-facing announcement."
    )
    parser.add_argument(
        "notes",
        nargs="?",
        default="RELEASE_NOTES.md",
        help="Release notes file, or - to read from stdin",
    )
    parser.add_argument(
        "--version-file",
        help="Optional version.properties file whose versionName/versionCode must appear",
    )
    parser.add_argument(
        "--tag",
        help="Optional release tag, expected in the form v<versionName>-vc<versionCode>",
    )
    return parser.parse_args()


def read_text(path: str) -> str:
    if path == "-":
        return sys.stdin.read()
    return Path(path).read_text(encoding="utf-8-sig")


def read_version_properties(path: str) -> tuple[str, str]:
    values: dict[str, str] = {}
    for raw_line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        values[key] = value

    version_name = values.get("versionName", "")
    version_code = values.get("versionCode", "")
    if not version_name or not version_code:
        raise ValueError(f"{path} must define versionName and versionCode")
    return version_name, version_code


def remove_code_spans(text: str) -> str:
    without_fences = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    return re.sub(r"`[^`\n]+`", "", without_fences)


def validate_release_notes(
    text: str,
    *,
    version_name: str | None = None,
    version_code: str | None = None,
    tag: str | None = None,
) -> list[str]:
    errors: list[str] = []
    stripped = text.strip()

    if not stripped:
        return ["release announcement is empty"]
    if len(stripped) > MAX_CHARACTERS:
        errors.append(
            f"announcement is too long ({len(stripped)} characters; maximum {MAX_CHARACTERS})"
        )

    bullet_count = sum(
        1 for line in stripped.splitlines() if re.match(r"^\s*(?:[-*•])\s+\S", line)
    )
    if bullet_count == 0:
        errors.append("announcement must contain at least one clear user-facing bullet")
    elif bullet_count > MAX_BULLETS:
        errors.append(
            f"announcement has too many bullets ({bullet_count}; maximum {MAX_BULLETS})"
        )

    prose = remove_code_spans(stripped)
    mentions = sorted(set(RAW_GITHUB_MENTION.findall(prose)), key=str.lower)
    if mentions:
        errors.append(
            "raw GitHub-style mentions are forbidden: "
            + ", ".join(mentions)
            + " (wrap technical annotations in backticks or rewrite them for users)"
        )

    for label, pattern in DEVELOPER_ONLY_PATTERNS.items():
        match = pattern.search(prose)
        if match:
            errors.append(
                f"announcement contains {label}: {match.group(0)!r}; keep it in CHANGELOG.md"
            )

    if version_name and version_name not in stripped:
        errors.append(f"announcement does not include versionName {version_name}")
    if version_code and version_code not in stripped:
        errors.append(f"announcement does not include versionCode {version_code}")

    if tag:
        expected_tag = (
            f"v{version_name}-vc{version_code}"
            if version_name and version_code
            else None
        )
        if expected_tag and tag != expected_tag:
            errors.append(f"tag {tag} does not match {expected_tag}")

    return errors


def main() -> int:
    args = parse_args()
    try:
        text = read_text(args.notes)
        version_name = None
        version_code = None
        if args.version_file:
            version_name, version_code = read_version_properties(args.version_file)
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"Release notes validation failed: {exc}", file=sys.stderr)
        return 1

    errors = validate_release_notes(
        text,
        version_name=version_name,
        version_code=version_code,
        tag=args.tag,
    )
    if errors:
        print("Release notes validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Release notes OK: {len(text.strip())} characters, "
        f"{sum(1 for line in text.splitlines() if re.match(r'^\s*(?:[-*•])\s+\S', line))} bullets"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
