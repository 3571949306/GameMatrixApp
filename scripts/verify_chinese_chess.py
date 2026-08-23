#!/usr/bin/env python3
"""Compile and run the pure-Java Chinese chess regression suite without Gradle side effects."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "module-store/feature/games/games/chinesechess"
SOURCE = MODULE / "src/main/java/com/gamecenter/app/chinesechess"
TESTS = MODULE / "tests"
OUTPUT = (ROOT / "build/agent-verification/chinesechess").resolve()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        print("JDK is required: javac/java not found on PATH", file=sys.stderr)
        return 2

    allowed_root = (ROOT / "build/agent-verification").resolve()
    if allowed_root not in OUTPUT.parents:
        print(f"refusing unsafe output path: {OUTPUT}", file=sys.stderr)
        return 2
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    OUTPUT.mkdir(parents=True)

    sources = [
        TESTS / "stubs/androidx/annotation/NonNull.java",
        TESTS / "stubs/androidx/annotation/Nullable.java",
        TESTS / "stubs/com/gamecenter/app/core/common/GameAI.java",
        SOURCE / "ChineseChessGame.java",
        SOURCE / "ChineseChessAI.java",
        TESTS / "ChessRegressionTest.java",
    ]
    missing = [str(path.relative_to(ROOT)) for path in sources if not path.is_file()]
    if missing:
        print("missing test inputs:\n- " + "\n- ".join(missing), file=sys.stderr)
        return 2

    compile_cmd = [javac, "-encoding", "UTF-8", "-d", str(OUTPUT), *map(str, sources)]
    subprocess.run(compile_cmd, cwd=ROOT, check=True)
    result = subprocess.run(
        [java, "-Dfile.encoding=UTF-8", "-cp", str(OUTPUT), "ChessRegressionTest"],
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
    if result.returncode != 0:
        return result.returncode
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
