from __future__ import annotations

import base64
import copy
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import catalog_signing


class CatalogSigningTest(unittest.TestCase):
    RFC_PRIVATE_SEED = (
        "9d61b19deffd5a60ba844af492ec2cc4"
        "4449c5697b326919703bac031cae7f60"
    )

    def setUp(self) -> None:
        self.catalog = {
            "schemaVersion": 2,
            "catalogVersion": 1,
            "modules": [
                {
                    "id": "test_web",
                    "name": "Test Web",
                    "versionName": "1.0.0",
                    "versionCode": 1,
                    "runtimeType": "web",
                    "deliveryType": "zip",
                    "entry": "index.html",
                    "minHostVersionCode": 591,
                    "package": {
                        "fileName": "test_web.zip",
                        "downloadUrl": "https://example.test/test_web.zip",
                        "sha256": "a" * 64,
                    },
                }
            ],
        }

    def test_signs_and_verifies_exact_catalog_bytes(self) -> None:
        seed = bytes.fromhex(self.RFC_PRIVATE_SEED)
        os.environ[catalog_signing.PRIVATE_KEY_ENV] = base64.b64encode(seed).decode()
        private_key = catalog_signing.load_private_key_from_environment()
        payload = b'{"schemaVersion":2,"catalogVersion":1,"modules":[]}'

        signature = catalog_signing.sign_catalog(payload, private_key)
        public_key = catalog_signing.public_key_base64(private_key)

        self.assertTrue(
            catalog_signing.verify_catalog_signature(payload, signature, public_key)
        )
        self.assertFalse(
            catalog_signing.verify_catalog_signature(
                payload + b"\n", signature, public_key
            )
        )

    def test_accepts_formal_multi_runtime_record(self) -> None:
        catalog_signing.validate_formal_catalog_v2(self.catalog)

    def test_rejects_legacy_runtime_inference(self) -> None:
        legacy = copy.deepcopy(self.catalog)
        legacy["modules"][0].pop("runtimeType")

        with self.assertRaisesRegex(
            catalog_signing.CatalogValidationError, "runtimeType"
        ):
            catalog_signing.validate_formal_catalog_v2(legacy)

    def test_rejects_http_package_url(self) -> None:
        insecure = copy.deepcopy(self.catalog)
        insecure["modules"][0]["package"]["downloadUrl"] = (
            "http://example.test/test_web.zip"
        )

        with self.assertRaisesRegex(
            catalog_signing.CatalogValidationError, "HTTPS"
        ):
            catalog_signing.validate_formal_catalog_v2(insecure)

    def test_rejects_duplicate_module_ids(self) -> None:
        duplicate = copy.deepcopy(self.catalog)
        duplicate["modules"].append(copy.deepcopy(duplicate["modules"][0]))

        with self.assertRaisesRegex(
            catalog_signing.CatalogValidationError, "duplicate module id"
        ):
            catalog_signing.validate_formal_catalog_v2(duplicate)

    def test_rejects_runtime_delivery_mismatch(self) -> None:
        mismatch = copy.deepcopy(self.catalog)
        mismatch["modules"][0]["deliveryType"] = "apk"

        with self.assertRaisesRegex(
            catalog_signing.CatalogValidationError,
            "runtimeType/deliveryType combination",
        ):
            catalog_signing.validate_formal_catalog_v2(mismatch)


if __name__ == "__main__":
    unittest.main()
