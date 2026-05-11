#!/usr/bin/env python3
"""
Upload GameCenterApp APK to GitHub Releases.

Supports creating a new release or appending assets to an existing release
tagged with the given version name.

Usage:
    python tools/upload_to_github_release.py \
        --apk app/build/outputs/apk/debug/app-debug.apk \
        --version-name 1.3.8 \
        --changelog "Release notes here" \
        --token $GITHUB_TOKEN

The script intentionally keeps credentials outside Git. Provide the token via:
  - GITHUB_TOKEN environment variable
  - local_private/github/token.txt
  - --token command line argument (not recommended for interactive use)
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TOKEN_FILE = REPO_ROOT / "local_private" / "github" / "token.txt"

GITHUB_API = "https://api.github.com"
REPO_OWNER = "3571949306"
REPO_NAME = "GameCenterApp"


def load_token(token_arg: str | None) -> str:
    """Load GitHub personal access token from argument, env var, or local file."""
    if token_arg:
        return token_arg
    env_token = os.environ.get("GITHUB_TOKEN")
    if env_token:
        return env_token
    if DEFAULT_TOKEN_FILE.exists():
        return DEFAULT_TOKEN_FILE.read_text(encoding="utf-8").strip()
    raise SystemExit(
        "GitHub token not found. Provide it via:\n"
        "  - --token argument\n"
        "  - GITHUB_TOKEN environment variable\n"
        f"  - {DEFAULT_TOKEN_FILE}"
    )


def github_request(
    method: str,
    path: str,
    token: str,
    data: bytes | None = None,
    content_type: str = "application/json",
    allow_404: bool = False,
) -> dict[str, Any] | None:
    """Make an authenticated request to GitHub API."""
    url = f"{GITHUB_API}{path}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if data is not None:
        headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            if resp.status == 204:
                return None
            body = resp.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        if e.code == 404 and allow_404:
            return None
        raise SystemExit(
            f"GitHub API {method} {path} failed with HTTP {e.code}: {error_body}"
        ) from e
    except urllib.error.URLError as e:
        raise SystemExit(f"GitHub API {method} {path} failed: {e.reason}") from e


def find_or_create_release(token: str, tag: str, version_name: str, changelog: str) -> tuple[str, str]:
    """Find existing release by tag name, or create a new one. Returns (upload_url, release_id)."""
    repo_path = f"/repos/{REPO_OWNER}/{REPO_NAME}"

    # Try to find existing release by tag
    release = github_request("GET", f"{repo_path}/releases/tags/{tag}", token, allow_404=True)
    if release:
        print(f"Found existing release for tag {tag}")
        # GitHub returns upload_url with {?name,label} template, clean it
        upload_url = release["upload_url"].split("{")[0]
        return upload_url, release["id"]

    # Create a new release
    print(f"Creating new release for tag {tag}...")
    release_data = json.dumps({
        "tag_name": tag,
        "name": f"GameCenterApp {version_name}",
        "body": changelog,
        "draft": False,
        "prerelease": True,
    }).encode("utf-8")

    release = github_request("POST", f"{repo_path}/releases", token, data=release_data)
    upload_url = release["upload_url"].split("{")[0]
    return upload_url, release["id"]


def upload_asset(token: str, upload_url: str, file_path: Path) -> None:
    """Upload an asset file to a GitHub release."""
    content_type = "application/vnd.android.package-archive"
    full_url = f"{upload_url}?name={file_path.name}"

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": content_type,
    }

    file_size = file_path.stat().st_size
    with open(file_path, "rb") as f:
        file_data = f.read()

    req = urllib.request.Request(full_url, data=file_data, headers=headers, method="POST")

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            if resp.status in (200, 201):
                print(f"Uploaded {file_path.name} ({file_size:,} bytes)")
            else:
                raise SystemExit(f"Upload failed with HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise SystemExit(
            f"Upload failed for {file_path.name} with HTTP {e.code}: {error_body}"
        ) from e
    except urllib.error.URLError as e:
        raise SystemExit(f"Upload failed for {file_path.name}: {e.reason}") from e


def main() -> None:
    parser = argparse.ArgumentParser(description="Upload APK to GitHub Releases")
    parser.add_argument("--apk", required=True, help="Path to APK file")
    parser.add_argument("--version-name", required=True, help="Version name (e.g. 1.3.8)")
    parser.add_argument("--changelog", default="", help="Release notes")
    parser.add_argument("--token", default=None, help="GitHub personal access token")
    args = parser.parse_args()

    apk_path = Path(args.apk).resolve()
    if not apk_path.exists():
        raise SystemExit(f"APK file not found: {apk_path}")

    token = load_token(args.token)
    tag = args.version_name

    print(f"Preparing release v{tag}...")
    upload_url, release_id = find_or_create_release(token, tag, args.version_name, args.changelog)
    upload_asset(token, upload_url, apk_path)
    print(f"Release v{tag} uploaded successfully!")


if __name__ == "__main__":
    main()
