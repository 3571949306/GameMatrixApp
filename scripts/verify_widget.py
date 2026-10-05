#!/usr/bin/env python3
"""Compile and run the pure-Java widget lines regression suite without Gradle side effects."""

from __future__ import annotations

import glob
import os
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "app"
SOURCE = MODULE / "src/main/java/com/gamecenter/app/widget"
TESTS = MODULE / "src/test/java/com/gamecenter/app/widget"
OUTPUT = (ROOT / "build/agent-verification/widget").resolve()


def find_junit_jars() -> list:
    """从本机 Gradle 缓存定位 junit/hamcrest（仅测试运行期依赖，与 app 模块 libs.junit 同源）。"""
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    jars = []
    junit_hits = sorted(glob.glob(str(cache / "junit/junit/*/*/junit-*.jar")))
    if not junit_hits:
        return jars
    jars.append(junit_hits[-1])
    # hamcrest 是 junit4 的运行期传递依赖；测试只用 assertEquals（无 assertThat），
    # 缺失也能跑，找到则一并挂上以贴近 Gradle 环境。
    hamcrest_hits = sorted(glob.glob(str(cache / "org.hamcrest/hamcrest-core/*/*/hamcrest-core-*.jar")))
    if hamcrest_hits:
        jars.append(hamcrest_hits[-1])
    return jars


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

    # 与 verify_rating 的分层惯例一致：GameMatrixWidgetProvider 是 Android 门面
    # （AppWidgetProvider/RemoteViews，不进纯 javac 测试），三行文案拼装逻辑全部在
    # 纯 Java 的 GameMatrixWidgetLines；JUnit4 用例带 main 自运行入口，Gradle 与
    # 本脚本共用同一份用例。
    sources = [
        SOURCE / "GameMatrixWidgetLines.java",
        TESTS / "GameMatrixWidgetLinesTest.java",
    ]
    missing = [str(path.relative_to(ROOT)) for path in sources if not path.is_file()]
    if missing:
        print("missing test inputs:\n- " + "\n- ".join(missing), file=sys.stderr)
        return 2

    junit_jars = find_junit_jars()
    if not junit_jars:
        print("junit jar not found in local Gradle cache (modules-2/files-2.1/junit)", file=sys.stderr)
        return 2
    provided = os.pathsep.join(junit_jars)

    compile_cmd = [javac, "-encoding", "UTF-8", "-cp", provided, "-d", str(OUTPUT), *map(str, sources)]
    subprocess.run(compile_cmd, cwd=ROOT, check=True)
    result = subprocess.run(
        [java, "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join([str(OUTPUT), provided]),
         "com.gamecenter.app.widget.GameMatrixWidgetLinesTest"],
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
    if "WIDGET_TEST_RESULT=PASS" not in output or "WIDGET_TEST_RESULT=FAIL" in output:
        return 1
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
