#!/usr/bin/env python3
"""Build one hybrid formal Catalog V2 usable by both new and legacy clients."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import catalog_signing

VALIDATION_MODULE_IDS = {
    "flutter_store_runtime",
    "web_diagnostics",
    "asset_theme_pack",
    "unity_smoke_content",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def package_fields(path: Path, public_base: str) -> dict:
    return {
        "fileName": path.name,
        "fileSize": path.stat().st_size,
        "downloadUrl": f"{public_base.rstrip('/')}/modules/{path.name}",
        "sha256": sha256_file(path),
    }


def normalize_legacy_module(module: dict, package_dir: Path, public_base: str) -> dict:
    item = copy.deepcopy(module)
    built_in = bool(item.get("builtIn")) or not item.get("fileName")
    runtime = "native_service" if item.get("id") == "vpn" else "android"
    delivery = "builtin" if built_in else "apk"
    item["runtimeType"] = runtime
    item["deliveryType"] = delivery
    item["minHostVersionCode"] = int(item.get("minAppVersionCode", item.get("minAppVersion", 0)) or 0)
    item["maxHostVersionCode"] = int(item.get("maxAppVersionCode", 0) or 0)
    item["dependencies"] = list(item.get("depends") or [])
    if isinstance(item.get("changelog"), str):
        item["changelog"] = [item["changelog"]] if item["changelog"].strip() else []
    descriptions = item.get("permissionsDescription") or []
    if descriptions and isinstance(descriptions[0], str):
        permissions = item.get("permissions") or []
        item["permissionsDescription"] = [
            {"id": permissions[index] if index < len(permissions) else f"permission_{index}", "description": value}
            for index, value in enumerate(descriptions)
        ]
    if runtime == "native_service":
        item["serviceType"] = "vpn"
    if not built_in:
        package_path = package_dir / str(item["fileName"])
        if not package_path.is_file():
            raise FileNotFoundError(f"production package missing: {package_path}")
        package = package_fields(package_path, public_base)
        item.update(package)
        item["fallbackUrl"] = ""
        item["githubUrl"] = ""
        item["package"] = package
    return item


def smoke_module(
    module_id: str,
    name: str,
    runtime: str,
    delivery: str,
    package_path: Path,
    public_base: str,
    enabled: bool,
    **runtime_fields: object,
) -> dict:
    package = package_fields(package_path, public_base)
    item = {
        "id": module_id,
        "name": name,
        "description": "Production-controlled runtime lifecycle validation package.",
        "shortDescription": "Signed production runtime validation asset",
        "versionName": "1.0.0",
        "versionCode": 1,
        "runtimeType": runtime,
        "deliveryType": delivery,
        "enabled": enabled,
        "required": False,
        "featured": False,
        "sortOrder": 1000,
        "minHostVersionCode": 592,
        "maxHostVersionCode": 0,
        "category": "internal",
        "permissions": [],
        "permissionsDescription": [],
        "dependencies": [],
        "tags": ["production-validation"],
        "screenshots": [],
        "changelog": ["Initial production validation package"],
        "package": package,
        "entryClass": "",
        "fileName": package["fileName"],
        "fileSize": package["fileSize"],
        "sha256": package["sha256"],
        "downloadUrl": package["downloadUrl"],
        "fallbackUrl": "",
        "githubUrl": "",
        "storeCategory": "internal",
        "kind": {"web": "web", "asset": "asset", "unity": "unity-content"}[runtime],
        "channel": "stable",
        "minAppVersionCode": 592,
        "maxAppVersionCode": 0,
        "depends": [],
        "isBaseFramework": False,
        "builtIn": False,
        "builtInVersionCode": 0,
        "type": "module",
        "rolloutPercent": 100,
        "restartRequired": False,
        "rollbackAllowed": True,
    }
    item.update(runtime_fields)
    return item


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy", type=Path, default=Path("app/src/main/assets/modules.json"))
    parser.add_argument("--package-dir", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--public-base", default="https://hk-update.tcp888.uk:2083")
    parser.add_argument("--catalog-version", type=int, default=4)
    parser.add_argument("--legacy-version", type=int, default=24)
    parser.add_argument("--enable-smoke-modules", action="store_true")
    args = parser.parse_args()

    legacy = json.loads(args.legacy.read_text(encoding="utf-8-sig"))
    modules = [
        normalize_legacy_module(module, args.package_dir, args.public_base)
        for module in legacy.get("modules", [])
        if module.get("id") not in VALIDATION_MODULE_IDS
    ]
    if args.enable_smoke_modules:
        modules.append({
        "id": "flutter_store_runtime",
        "name": "Flutter Store Runtime",
        "versionName": "1.0.0",
        "versionCode": 1,
        "runtimeType": "flutter",
        "deliveryType": "builtin",
        "route": "/store",
        "enabled": True,
        "required": True,
        "featured": False,
        "sortOrder": 1000,
        "minHostVersionCode": 592,
        "category": "internal",
        "builtIn": True,
        "builtInVersionCode": 1,
        "kind": "flutter",
        "type": "module",
        "fileName": "",
        "fileSize": 0,
        "sha256": "",
        "downloadUrl": "",
        "fallbackUrl": "",
        "githubUrl": "",
        "storeCategory": "internal",
        "minAppVersionCode": 592,
        })
        modules.extend([
        smoke_module(
            "web_diagnostics", "Web Runtime Diagnostics", "web", "zip",
            args.package_dir / "web_diagnostics_v2.zip", args.public_base, True,
            entry="index.html",
            versionName="1.0.1",
            versionCode=2,
        ),
        smoke_module(
            "asset_theme_pack", "Asset Runtime Theme Pack", "asset", "zip",
            args.package_dir / "asset_theme_pack_v1.zip", args.public_base, True,
        ),
        smoke_module(
            "unity_smoke_content", "Unity Runtime Content Check", "unity", "content",
            args.package_dir / "unity_smoke_content_v1.zip", args.public_base, True,
            launcherId="unity_smoke",
        ),
        ])
    catalog = {
        "schemaVersion": 2,
        "catalogVersion": args.catalog_version,
        "version": args.legacy_version,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "modules": modules,
    }
    catalog_signing.validate_formal_catalog_v2(catalog)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"CATALOG {args.out} modules={len(modules)} catalogVersion={args.catalog_version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
