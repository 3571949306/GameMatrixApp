from __future__ import annotations

import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import publish_module


class PublishHostKeyPolicyTest(unittest.TestCase):
    def test_requires_known_hosts_and_rejects_unknown_keys(self) -> None:
        key = paramiko.RSAKey.generate(1024)
        with tempfile.TemporaryDirectory() as temp_dir:
            known_hosts = Path(temp_dir) / "known_hosts"
            known_hosts.write_text(
                f"example.test {key.get_name()} {key.get_base64()}\n",
                encoding="utf-8",
            )
            client = paramiko.SSHClient()

            publish_module.configure_ssh_client(
                client, {"host": "example.test", "knownHostsFile": str(known_hosts)}
            )

            self.assertIsInstance(client._policy, paramiko.RejectPolicy)
            self.assertTrue(client.get_host_keys().check("example.test", key))

    def test_missing_known_hosts_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            missing = Path(temp_dir) / "missing_known_hosts"
            with self.assertRaisesRegex(RuntimeError, "known_hosts file not found"):
                publish_module.configure_ssh_client(
                    paramiko.SSHClient(), {"host": "example.test", "knownHostsFile": str(missing)}
                )


class PublishMetadataValidationTest(unittest.TestCase):
    def test_rejects_remote_path_traversal_and_cleartext_base_url(self) -> None:
        with self.assertRaises(ValueError):
            publish_module.validate_publish_metadata_name("../../outside.apk")
        with self.assertRaises(ValueError):
            publish_module.validate_publish_metadata_name("module.zip")
        with self.assertRaises(ValueError):
            publish_module.validate_public_base_url("http://updates.example.test")
        with self.assertRaises(ValueError):
            publish_module.validate_public_base_url(" https://updates.example.test ")
        with self.assertRaises(ValueError):
            publish_module.validate_public_base_url("https://updates.example.test:65536")

        self.assertEqual(
            "https://updates.example.test:2083",
            publish_module.validate_public_base_url("https://updates.example.test:2083/"),
        )

    def test_public_verification_redirect_stays_same_origin(self) -> None:
        origin = ("updates.example.test", 443)
        with self.assertRaises(ValueError):
            publish_module._resolve_public_redirect(
                "https://updates.example.test/modules.json",
                "https://evil.example.test/modules.json",
                origin,
            )
        with self.assertRaises(ValueError):
            publish_module._resolve_public_redirect(
                "https://updates.example.test/modules.json",
                "http://updates.example.test/modules.json",
                origin,
            )

    def test_public_apk_readback_hashes_all_served_bytes(self) -> None:
        class FakeResponse:
            headers = {}

            def getcode(self):
                return 200

            def read(self, _size):
                if self._chunks:
                    return self._chunks.pop(0)
                return b""

            def close(self):
                pass

            def __init__(self):
                self._chunks = [b"apk", b"-bytes"]

        response = FakeResponse()
        with patch.object(publish_module, "_open_public_response", return_value=response):
            digest, size = publish_module._sha256_public_file(
                "https://updates.example.test/modules/game.apk"
            )
        self.assertEqual(9, size)
        self.assertEqual(
            "1e10ba560383b17472b4cf72fef8f9e76c66815a3e6ae8c5a9b0c5e696b0bdf8",
            digest,
        )


if __name__ == "__main__":
    unittest.main()
