#!/usr/bin/env python3
"""Publish the verified stable APK to its version-code GitHub Release.

The updater expects a tag shaped like ``v<versionName>-vc<versionCode>`` plus
``app-release.apk`` and its matching ``version.json`` metadata asset. Keeping
that contract in one tool avoids publishing a fallback APK without integrity
metadata that installed clients can safely consume.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


API_BASE = "https://api.github.com"
DEFAULT_REPOSITORY = "3571949306/GameMatrixApp"
ASSET_NAME = "app-release.apk"
METADATA_NAME = "version.json"
USER_AGENT = "GameMatrixApp-release-publisher"


def request_json(
    method: str,
    url: str,
    token: str,
    payload: dict[str, Any] | None = None,
) -> tuple[int, dict[str, Any]]:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    request = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=90) as response:
            response_body = response.read().decode("utf-8")
            return response.status, json.loads(response_body) if response_body else {}
    except HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        try:
            return error.code, json.loads(response_body) if response_body else {}
        except json.JSONDecodeError:
            return error.code, {"message": response_body}


def upload_asset(upload_url: str, asset: Path, asset_name: str, content_type: str, token: str) -> dict[str, Any]:
    target = upload_url.split("{", 1)[0] + "?name=" + quote(asset_name)
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "Content-Type": content_type,
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    request = Request(target, data=asset.read_bytes(), headers=headers, method="POST")
    with urlopen(request, timeout=300) as response:
        return json.loads(response.read().decode("utf-8"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path,
                        help="Generated publishable version.json matching the APK")
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--changelog-file", required=True, type=Path)
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    apk = args.apk.resolve()
    metadata = args.metadata.resolve()
    notes_file = args.changelog_file.resolve()
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")
    if apk.name != ASSET_NAME:
        raise SystemExit(f"GitHub fallback asset must be named {ASSET_NAME}, got {apk.name}")
    if not metadata.is_file() or metadata.name != METADATA_NAME:
        raise SystemExit(f"Metadata asset must be an existing {METADATA_NAME}: {metadata}")
    if not notes_file.is_file():
        raise SystemExit(f"Release notes not found: {notes_file}")
    if args.version_code <= 0:
        raise SystemExit("--version-code must be positive")

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        raise SystemExit("GITHUB_TOKEN or GH_TOKEN is required to publish a GitHub Release")

    notes = notes_file.read_text(encoding="utf-8").strip()
    if not notes:
        raise SystemExit("Release notes are empty")
    tag = f"v{args.version_name}-vc{args.version_code}"
    expected_hash = sha256(apk)
    try:
        metadata_payload = json.loads(metadata.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise SystemExit(f"Metadata is not valid JSON: {error}") from error
    metadata_errors = []
    if metadata_payload.get("versionCode") != args.version_code:
        metadata_errors.append("versionCode")
    if metadata_payload.get("versionName") != args.version_name:
        metadata_errors.append("versionName")
    if metadata_payload.get("githubReleaseTag") != tag:
        metadata_errors.append("githubReleaseTag")
    if metadata_payload.get("apkName") != ASSET_NAME:
        metadata_errors.append("apkName")
    if metadata_payload.get("fileSize") != apk.stat().st_size:
        metadata_errors.append("fileSize")
    if str(metadata_payload.get("sha256", "")).lower() != expected_hash:
        metadata_errors.append("sha256")
    if metadata_errors:
        raise SystemExit("Metadata does not match the APK: " + ", ".join(metadata_errors))

    release_url = f"{API_BASE}/repos/{args.repository}/releases/tags/{quote(tag)}"
    status, release = request_json("GET", release_url, token)
    if status == 404:
        status, release = request_json(
            "POST",
            f"{API_BASE}/repos/{args.repository}/releases",
            token,
            {
                "tag_name": tag,
                "name": f"大象游戏中心 {args.version_name}（版本 {args.version_code}）",
                "body": notes,
                "draft": False,
                "prerelease": False,
            },
        )
    elif status == 200:
        status, release = request_json(
            "PATCH",
            f"{API_BASE}/repos/{args.repository}/releases/{release['id']}",
            token,
            {
                "name": f"大象游戏中心 {args.version_name}（版本 {args.version_code}）",
                "body": notes,
                "draft": False,
                "prerelease": False,
            },
        )
    if status not in (200, 201):
        raise SystemExit(f"Unable to create/update GitHub Release {tag}: {release.get('message', status)}")

    assets_to_upload = [
        (apk, ASSET_NAME, "application/vnd.android.package-archive"),
        (metadata, METADATA_NAME, "application/json; charset=utf-8"),
    ]
    replacement_names = {asset_name for _, asset_name, _ in assets_to_upload}
    for asset in release.get("assets", []):
        if asset.get("name") in replacement_names:
            delete_status, delete_result = request_json(
                "DELETE",
                f"{API_BASE}/repos/{args.repository}/releases/assets/{asset['id']}",
                token,
            )
            if delete_status != 204:
                raise SystemExit(
                    f"Unable to replace existing {asset.get('name')}: {delete_result.get('message', delete_status)}"
                )

    for asset, asset_name, content_type in assets_to_upload:
        uploaded = upload_asset(release["upload_url"], asset, asset_name, content_type, token)
        digest = str(uploaded.get("digest", ""))
        if (
            uploaded.get("name") != asset_name
            or int(uploaded.get("size", -1)) != asset.stat().st_size
            or digest.lower() != f"sha256:{sha256(asset)}"
        ):
            raise SystemExit(
                f"GitHub accepted {asset_name} whose name, size, or SHA-256 did not match the local artifact"
            )

    print(f"GitHub Release ready: {tag} (APK and version metadata SHA-256 verified)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
