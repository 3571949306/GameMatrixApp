#!/usr/bin/env python3
"""Validate and sign a production Catalog V2 without persisting private keys."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)


PRIVATE_KEY_ENV = "GAME_MATRIX_CATALOG_ED25519_PRIVATE_KEY"
RUNTIMES = {"flutter", "web", "asset", "android", "native_service", "unity"}
DELIVERY_TYPES = {"builtin", "apk", "zip", "content"}
RUNTIME_DELIVERIES = {
    "flutter": {"builtin"},
    "web": {"builtin", "zip"},
    "asset": {"builtin", "zip"},
    "android": {"builtin", "apk"},
    "native_service": {"builtin", "apk"},
    "unity": {"builtin", "apk", "content"},
}
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
MODULE_ID_RE = re.compile(r"^[A-Za-z0-9_.-]+$")


class CatalogValidationError(ValueError):
    """Raised when a catalog is not safe to sign as formal V2."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise CatalogValidationError(message)


def validate_formal_catalog_v2(catalog: dict[str, Any]) -> None:
    """Reject legacy/incomplete catalogs before a trusted signature is issued."""
    _require(catalog.get("schemaVersion") == 2, "schemaVersion must be 2")
    _require(
        isinstance(catalog.get("catalogVersion"), int)
        and catalog["catalogVersion"] > 0,
        "catalogVersion must be a positive integer",
    )
    modules = catalog.get("modules")
    _require(isinstance(modules, list), "modules must be an array")

    seen_ids: set[str] = set()
    for index, module in enumerate(modules):
        path = f"modules[{index}]"
        _require(isinstance(module, dict), f"{path} must be an object")
        module_id = module.get("id")
        _require(
            isinstance(module_id, str) and bool(MODULE_ID_RE.fullmatch(module_id)),
            f"{path}.id is invalid",
        )
        _require(module_id not in seen_ids, f"duplicate module id: {module_id}")
        seen_ids.add(module_id)
        _require(
            isinstance(module.get("name"), str) and bool(module["name"].strip()),
            f"{path}.name is required",
        )
        _require(
            isinstance(module.get("versionName"), str)
            and bool(module["versionName"].strip()),
            f"{path}.versionName is required",
        )
        _require(
            isinstance(module.get("versionCode"), int) and module["versionCode"] > 0,
            f"{path}.versionCode must be positive",
        )

        runtime = module.get("runtimeType")
        delivery = module.get("deliveryType")
        _require(runtime in RUNTIMES, f"{path}.runtimeType is invalid or missing")
        _require(
            delivery in DELIVERY_TYPES,
            f"{path}.deliveryType is invalid or missing",
        )
        _require(
            delivery in RUNTIME_DELIVERIES[runtime],
            f"{path} runtimeType/deliveryType combination is invalid",
        )
        if runtime == "flutter":
            _require(
                isinstance(module.get("route"), str)
                and module["route"].startswith("/"),
                f"{path}.route must be an absolute Flutter route",
            )
        if runtime == "web":
            entry = module.get("entry")
            _require(
                isinstance(entry, str)
                and bool(entry)
                and not entry.startswith(("/", "\\"))
                and ".." not in Path(entry).parts,
                f"{path}.entry must be a safe relative path",
            )
        if runtime == "android" and delivery != "builtin":
            _require(
                isinstance(module.get("entryClass"), str)
                and bool(module["entryClass"].strip()),
                f"{path}.entryClass is required for downloaded Android modules",
            )
        if runtime == "native_service":
            _require(
                isinstance(module.get("serviceType"), str)
                and bool(module["serviceType"].strip()),
                f"{path}.serviceType is required",
            )
            if delivery == "apk":
                _require(
                    isinstance(module.get("entryClass"), str)
                    and bool(module["entryClass"].strip()),
                    f"{path}.entryClass is required for downloaded Native Service modules",
                )
        if runtime == "unity":
            _require(
                isinstance(module.get("launcherId"), str)
                and bool(module["launcherId"].strip()),
                f"{path}.launcherId is required",
            )

        min_host = module.get("minHostVersionCode", 0)
        max_host = module.get("maxHostVersionCode", 0)
        _require(
            isinstance(min_host, int) and min_host >= 0,
            f"{path}.minHostVersionCode must be non-negative",
        )
        _require(
            isinstance(max_host, int) and max_host >= 0,
            f"{path}.maxHostVersionCode must be non-negative",
        )
        _require(
            not max_host or max_host >= min_host,
            f"{path} host version range is invalid",
        )

        if delivery != "builtin":
            package = module.get("package")
            _require(isinstance(package, dict), f"{path}.package is required")
            _require(
                isinstance(package.get("fileName"), str)
                and bool(package["fileName"].strip()),
                f"{path}.package.fileName is required",
            )
            urls = [
                package.get("downloadUrl"),
                package.get("fallbackUrl"),
                package.get("githubUrl"),
            ]
            _require(
                any(isinstance(url, str) and url.startswith("https://") for url in urls),
                f"{path}.package requires at least one HTTPS URL",
            )
            _require(
                isinstance(package.get("sha256"), str)
                and bool(SHA256_RE.fullmatch(package["sha256"])),
                f"{path}.package.sha256 must be 64 hexadecimal characters",
            )


def load_private_key_from_environment() -> Ed25519PrivateKey:
    encoded = os.environ.get(PRIVATE_KEY_ENV, "").strip()
    if not encoded:
        raise RuntimeError(f"{PRIVATE_KEY_ENV} is required")
    try:
        raw = base64.b64decode(encoded, validate=True)
    except ValueError as error:
        raise RuntimeError(f"{PRIVATE_KEY_ENV} must be valid Base64") from error
    if len(raw) != 32:
        raise RuntimeError(f"{PRIVATE_KEY_ENV} must contain a raw 32-byte seed")
    if not any(raw):
        raise RuntimeError("The all-zero private key placeholder is forbidden")
    return Ed25519PrivateKey.from_private_bytes(raw)


def sign_catalog(catalog_bytes: bytes, private_key: Ed25519PrivateKey) -> str:
    return base64.b64encode(private_key.sign(catalog_bytes)).decode("ascii")


def public_key_base64(private_key: Ed25519PrivateKey) -> str:
    public_bytes = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    return base64.b64encode(public_bytes).decode("ascii")


def verify_catalog_signature(
    catalog_bytes: bytes, signature_base64: str, public_key_base64_value: str
) -> bool:
    try:
        public_key = Ed25519PublicKey.from_public_bytes(
            base64.b64decode(public_key_base64_value, validate=True)
        )
        public_key.verify(
            base64.b64decode(signature_base64, validate=True), catalog_bytes
        )
        return True
    except (ValueError, TypeError):
        return False
    except Exception:
        return False


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--signature-out", type=Path, required=True)
    parser.add_argument("--nginx-include-out", type=Path)
    parser.add_argument("--public-key-out", type=Path)
    args = parser.parse_args()

    catalog_bytes = args.catalog.read_bytes()
    try:
        catalog = json.loads(catalog_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CatalogValidationError("catalog must be valid UTF-8 JSON") from error
    _require(isinstance(catalog, dict), "catalog root must be an object")
    validate_formal_catalog_v2(catalog)

    private_key = load_private_key_from_environment()
    signature = sign_catalog(catalog_bytes, private_key)
    public_key = public_key_base64(private_key)
    if not verify_catalog_signature(catalog_bytes, signature, public_key):
        raise RuntimeError("post-sign verification failed")

    atomic_write(args.signature_out, signature + "\n")
    if args.nginx_include_out:
        atomic_write(
            args.nginx_include_out,
            f'add_header X-Catalog-Signature "{signature}" always;\n',
        )
    if args.public_key_out:
        atomic_write(args.public_key_out, public_key + "\n")
    print("Catalog V2 validated and signed; private key was not persisted.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
