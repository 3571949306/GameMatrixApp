#!/usr/bin/env python3
"""Audit active GameMatrixApp Markdown documentation without modifying files."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
VERSION_FILE = ROOT / "version.properties"
CATALOG_DIR = ROOT / "app" / "src" / "main" / "assets"

EXCLUDED_PARTS = {".git", ".gradle", ".kotlin", "build", "test_output", ".tmp", ".venv"}
THIRD_PARTY_PREFIXES = (
    "工具/jadx/",
    "flutter_module/.ios/",
)
USER_FACING_DOCS = {
    "README.md",
    "RELEASE_NOTES.md",
    ".github/ISSUE_TEMPLATE/bug_report.md",
    ".github/ISSUE_TEMPLATE/feature_request.md",
}
DEVELOPER_LANGUAGE_EXEMPTIONS = {
    "API",
    "APK",
    "AAB",
    "ABI",
    "AI",
    "Android",
    "App",
    "BuildConfig",
    "Catalog",
    "CI",
    "Compose",
    "CPU",
    "CRLF",
    "DNS",
    "Dart",
    "Debug",
    "DexClassLoader",
    "Ed25519",
    "Flutter",
    "Git",
    "GitHub",
    "Gradle",
    "HTTP",
    "HTTPS",
    "JSON",
    "Java",
    "Kotlin",
    "LLM",
    "Markdown",
    "Mermaid",
    "Module",
    "ModuleDownloader",
    "ModuleLoader",
    "NetworkResult",
    "OK",
    "OCR",
    "README",
    "Release",
    "Room",
    "Runtime",
    "SDK",
    "SHA",
    "SSL",
    "TCP",
    "URL",
    "UI",
    "UUID",
    "VPN",
    "WebDAV",
    "WebSocket",
    "XML",
    "YAML",
}
HISTORICAL_PREFIXES = (
    "docs/archive/",
    "docs/audits/",
    "docs/refactor/",
    "docs/superpowers/",
    "docs/SECURITY_AUDIT_",
    "项目审计_2026-06-19/",
)
CURRENT_DOCS = {
    "README.md",
    "RELEASE_NOTES.md",
    "CODE_WIKI.md",
    "CLOUD-BUILD.md",
    "docs/CURRENT_STATE.md",
    "docs/DOCUMENTATION_GOVERNANCE.md",
    "docs/DOCUMENTATION_INDEX.md",
    "docs/FEATURE_FLAGS.md",
    "docs/PROJECT_STATUS.md",
    "docs/PUBLISH_GUIDE.md",
    "docs/SECURITY.md",
    "docs/NETWORK_LAYER.md",
    "docs/ERROR_HANDLING.md",
    "docs/COMPOSE_MIGRATION.md",
    "docs/ROOM_MIGRATION.md",
    "docs/BASELINE_PROFILE.md",
    "docs/PROGUARD_AUDIT.md",
    "docs/ADB_REAL_DEVICE_TEST_PLAN.md",
    "docs/modules/MODULE_DEVELOPMENT_GUIDE.md",
    "docs/SPEC.md",
    "docs/PRODUCT_DIRECTION_AND_UX.md",
}
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
CURRENT_VERSION_PATTERN = re.compile(
    r"(?:当前工作树|当前版本|工作版本|生产版本).{0,80}?(?:versionCode\s*[=:]?\s*|vc\s*=\s*)(\d+)",
    re.IGNORECASE,
)


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def is_excluded(path: Path) -> bool:
    return any(part in EXCLUDED_PARTS for part in path.parts)


def is_third_party(rel: str) -> bool:
    return rel.startswith(THIRD_PARTY_PREFIXES)


def is_user_facing(rel: str) -> bool:
    return rel in USER_FACING_DOCS


def strip_protected_markdown(text: str) -> str:
    """Remove code, URLs, paths, identifiers, and link targets before language checks."""
    stripped_lines: list[str] = []
    in_fence = False
    for line in text.splitlines():
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        line = re.sub(r"`[^`]*`", "", line)
        line = re.sub(r"!?\[[^\]]*\]\([^)]*\)", "", line)
        line = re.sub(r"https?://\S+|file://\S+", "", line)
        line = re.sub(r"\b[\w./\\:-]+\.(?:kt|java|xml|json|yaml|yml|md|gradle|properties|apk|aab)\b", "", line)
        line = re.sub(r"\b[A-Z][A-Za-z0-9_]{2,}\b", "", line)
        stripped_lines.append(line)
    return "\n".join(stripped_lines)


def contains_substantial_english_prose(text: str) -> bool:
    protected = strip_protected_markdown(text)
    for line in protected.splitlines():
        words = re.findall(r"[A-Za-z]{3,}", line)
        if not words:
            continue
        meaningful = [word for word in words if word not in DEVELOPER_LANGUAGE_EXEMPTIONS]
        cjk_count = len(re.findall(r"[一-鿿]", line))
        if len(meaningful) >= 6 and cjk_count < len(meaningful) * 0.35:
            return True
    return False


def is_historical(rel: str) -> bool:
    return rel.startswith(HISTORICAL_PREFIXES)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for line in read_text(VERSION_FILE).splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def resolve_target(source: Path, target: str) -> Path | None:
    target = unquote(target.strip()).split("#", 1)[0].split("?", 1)[0]
    if not target or target.startswith(("http://", "https://", "mailto:", "file://")):
        return None
    if target.startswith("/"):
        return ROOT / target.lstrip("/")
    return source.parent / target


def collect_markdown() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if not is_excluded(path)
    )


def main() -> int:
    properties = load_properties()
    working_code = properties["versionCode"]
    stable_code = properties["lastStableVersionCode"]
    docs = collect_markdown()
    active_docs = [path for path in docs if not is_historical(relative(path))]

    broken_links: list[tuple[str, str]] = []
    stale_current_versions: list[tuple[str, str]] = []
    current_docs_missing_header: list[str] = []
    developer_docs_with_english: list[str] = []
    user_docs_missing_bilingual_content: list[str] = []

    for path in docs:
        rel = relative(path)
        if is_third_party(rel):
            continue
        text = read_text(path)

        if is_user_facing(rel):
            has_chinese = bool(re.search(r"[一-鿿]", strip_protected_markdown(text)))
            has_english = bool(re.search(r"[A-Za-z]{3,}", strip_protected_markdown(text)))
            if not (has_chinese and has_english):
                user_docs_missing_bilingual_content.append(rel)
        elif contains_substantial_english_prose(text):
            developer_docs_with_english.append(rel)

        if path not in active_docs:
            continue

        for raw_target in MARKDOWN_LINK.findall(text):
            target = resolve_target(path, raw_target)
            if target is not None and not target.exists():
                broken_links.append((rel, raw_target))

        if rel in CURRENT_DOCS:
            if rel.startswith("docs/") and rel not in {
                "docs/DOCUMENTATION_INDEX.md",
                "docs/DOCUMENTATION_GOVERNANCE.md",
            } and "最后核验" not in text and "最后更新" not in text:
                current_docs_missing_header.append(rel)
            for match in CURRENT_VERSION_PATTERN.finditer(text):
                found = match.group(1)
                if found not in {working_code, stable_code}:
                    stale_current_versions.append((rel, found))

    catalog_summary = {}
    for name in ("catalog.json", "modules.json"):
        data = json.loads(read_text(CATALOG_DIR / name))
        catalog_summary[name] = {
            "catalogVersion": data.get("catalogVersion"),
            "version": data.get("version"),
            "modules": len(data.get("modules", [])),
        }

    print("GameMatrixApp Markdown audit")
    print(f"working version: {properties['versionName']} / vc{working_code}")
    print(f"last stable: {properties['lastStableVersionName']} / vc{stable_code}")
    print(f"active Markdown documents: {len(active_docs)}")
    print(f"historical Markdown documents: {len(docs) - len(active_docs)}")
    print("catalogs:")
    for name, summary in catalog_summary.items():
        print(
            f"  {name}: catalogVersion={summary['catalogVersion']}, "
            f"version={summary['version']}, modules={summary['modules']}"
        )

    if broken_links:
        print("\nbroken internal links:")
        for source, target in broken_links:
            print(f"  {source} -> {target}")

    if stale_current_versions:
        print("\nstale current-version references:")
        for source, version in stale_current_versions:
            print(f"  {source}: vc{version}")

    if current_docs_missing_header:
        print("\ncurrent docs without a date header:")
        for source in current_docs_missing_header:
            print(f"  {source}")

    if developer_docs_with_english:
        print("\ndeveloper documents with substantial English prose:")
        for source in developer_docs_with_english:
            print(f"  {source}")

    if user_docs_missing_bilingual_content:
        print("\nuser-facing documents missing Chinese or English prose:")
        for source in user_docs_missing_bilingual_content:
            print(f"  {source}")

    issues = (
        len(broken_links)
        + len(stale_current_versions)
        + len(developer_docs_with_english)
        + len(user_docs_missing_bilingual_content)
    )
    if issues:
        print(f"\nresult: {issues} issue(s) found")
        return 1

    print("\nresult: no current-document link, version, or language-scope issues found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
