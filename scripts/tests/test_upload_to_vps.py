from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

import upload_to_vps


class KnownHostsLoadingTest(unittest.TestCase):
    def test_malformed_unrelated_line_does_not_disable_host_validation(self) -> None:
        key = paramiko.RSAKey.generate(1024)
        with tempfile.TemporaryDirectory() as temp_dir:
            known_hosts = Path(temp_dir) / "known_hosts"
            known_hosts.write_text(
                "ssh-keyscan : unknown option -- o\n"
                f"example.test {key.get_name()} {key.get_base64()}\n",
                encoding="utf-8",
            )
            client = paramiko.SSHClient()

            count = upload_to_vps.load_known_hosts_lenient(
                client, str(known_hosts)
            )

            self.assertEqual(1, count)
            self.assertTrue(client.get_host_keys().check("example.test", key))

    def test_rejects_file_without_any_valid_host_key(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            known_hosts = Path(temp_dir) / "known_hosts"
            known_hosts.write_text("not a host key\n", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "no valid host keys"):
                upload_to_vps.load_known_hosts_lenient(
                    paramiko.SSHClient(), str(known_hosts)
                )


if __name__ == "__main__":
    unittest.main()
