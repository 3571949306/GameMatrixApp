#!/usr/bin/env python3
"""Compile/run real ADB operations against protocol fakes, without Android or Gradle."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "core/adb/src/main/java/com/gamecenter/app/adb"


def main() -> None:
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise RuntimeError("JDK javac/java are required")
    sources = [SOURCE / name for name in (
        "ResourceScope.java", "AdbShell.java", "AdbCommands.java", "AdbPackageInstaller.java",
        "protocol/AdbTransport.java", "protocol/AdbWireConnection.java", "protocol/AdbSync.java",
    )]
    sources.append(ROOT / "core/adb/tests/OperationsRegressionTest.java")
    output_root = ROOT / "build/agent-verification"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="adb-operations-", dir=output_root) as output:
        subprocess.run([javac, "-encoding", "UTF-8", "-d", output, *map(str, sources)], cwd=ROOT, check=True)
        subprocess.run([java, "-Dfile.encoding=UTF-8", "-cp", output,
                        "com.gamecenter.app.adb.OperationsRegressionTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
