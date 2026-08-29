#!/usr/bin/env python3
"""Compile and run the dynamic Go module regression suite without Gradle side effects."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "module-store/feature/games/games/go"
SOURCE = MODULE / "src/main/java/com/gamecenter/app/go"
TESTS = MODULE / "tests"
OUTPUT = (ROOT / "build/agent-verification/go").resolve()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        print("JDK is required: javac/java not found on PATH", file=sys.stderr)
        return 2

    # Host mirror sync checks were removed with the hot-update flip (0c52cb9):
    # module-store is the single runtime truth source and no host copy exists.

    allowed_root = (ROOT / "build/agent-verification").resolve()
    if allowed_root not in OUTPUT.parents:
        print(f"refusing unsafe output path: {OUTPUT}", file=sys.stderr)
        return 2
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    OUTPUT.mkdir(parents=True)

    sources = [
        TESTS / "stubs/com/gamecenter/app/core/common/GameAI.java",
        SOURCE / "GoGame.java",
        SOURCE / "GoAI.java",
        TESTS / "GoRegressionTest.java",
    ]
    missing = [str(path.relative_to(ROOT)) for path in sources if not path.is_file()]
    if missing:
        print("missing test inputs:\n- " + "\n- ".join(missing), file=sys.stderr)
        return 2

    subprocess.run(
        [javac, "-encoding", "UTF-8", "-d", str(OUTPUT), *map(str, sources)],
        cwd=ROOT,
        check=True,
    )
    result = subprocess.run(
        [java, "-Dfile.encoding=UTF-8", "-cp", str(OUTPUT),
         "com.gamecenter.app.go.GoRegressionTest"],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    try:
        output = result.stdout.decode("utf-8")
    except UnicodeDecodeError:
        output = result.stdout.decode("gb18030", errors="replace")
    print(output, end="")
    return result.returncode


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (subprocess.CalledProcessError, RuntimeError) as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(getattr(exc, "returncode", 1))
