#!/usr/bin/env python3
"""Run pure-Java ADB/sync/fastboot regression tests without Gradle or release assets."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        print("JDK required: javac/java were not found on PATH", file=sys.stderr)
        return 2
    source = ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/protocol"
    sources = sorted(source.glob("*.java"))
    sources.append(ROOT / "core/adb/tests/ProtocolRegressionTest.java")
    with tempfile.TemporaryDirectory(prefix="gamematrix-adb-tests-") as output:
        subprocess.run(
            [javac, "-encoding", "UTF-8", "--release", "17", "-d", output, *map(str, sources)],
            cwd=ROOT, check=True, timeout=60,
        )
        return subprocess.run(
            [java, "-Xmx128m", "-Dfile.encoding=UTF-8", "-cp", output,
             "com.gamecenter.app.adb.protocol.ProtocolRegressionTest"],
            cwd=ROOT, check=False, timeout=60,
        ).returncode


if __name__ == "__main__":
    raise SystemExit(main())
