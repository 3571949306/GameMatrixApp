#!/usr/bin/env python3
"""Compile and run the pure-Java QR Plus regression suite without Gradle side effects."""

from __future__ import annotations

import glob
import os
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "module-store/feature/tools/tools"
SOURCE = MODULE / "src/main/java/com/gamecenter/app/tools"
TESTS = MODULE / "src/test/java/com/gamecenter/app/tools"
OUTPUT = (ROOT / "build/agent-verification/qr").resolve()


def find_junit_jars() -> list:
    """从本机 Gradle 缓存定位 junit/hamcrest（仅测试运行期依赖）。"""
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    jars = []
    junit_hits = sorted(glob.glob(str(cache / "junit/junit/*/*/junit-*.jar")))
    if not junit_hits:
        return jars
    jars.append(junit_hits[-1])
    hamcrest_hits = sorted(glob.glob(str(cache / "org.hamcrest/hamcrest-core/*/*/hamcrest-core-*.jar")))
    if hamcrest_hits:
        jars.append(hamcrest_hits[-1])
    return jars


def check_i18n_pairs() -> int:
    """校验 values/ 与 values-en/ 中 tool_qr_plus_* key 成对。"""
    zh = ROOT / "app/src/main/res/values/strings.xml"
    en = ROOT / "app/src/main/res/values-en/strings.xml"
    if not zh.is_file() or not en.is_file():
        print("missing strings.xml", file=sys.stderr)
        return 1
    import re
    zh_keys = set(re.findall(r'name="(tool_qr_plus_[^"]+)"', zh.read_text(encoding="utf-8")))
    en_keys = set(re.findall(r'name="(tool_qr_plus_[^"]+)"', en.read_text(encoding="utf-8")))
    missing_en = sorted(zh_keys - en_keys)
    missing_zh = sorted(en_keys - zh_keys)
    if missing_en or missing_zh:
        print("i18n key mismatch:", file=sys.stderr)
        for k in missing_en:
            print(f"  missing in values-en: {k}", file=sys.stderr)
        for k in missing_zh:
            print(f"  missing in values: {k}", file=sys.stderr)
        return 1
    print(f"I18N_KEYS=PASS ({len(zh_keys)} pairs)")
    return 0


def check_no_hardcoded_cn() -> int:
    """QrPlusController / QrImageIo 用户可见区域禁止残留中文字面量 Toast/按钮。"""
    targets = [
        SOURCE / "QrPlusController.java",
        SOURCE / "QrImageIo.java",
    ]
    # 允许出现在注释里的中文；检测字符串字面量中的 CJK
    import re
    bad = []
    for path in targets:
        text = path.read_text(encoding="utf-8")
        # 去掉块注释和行注释后再扫字面量
        no_block = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        no_line = re.sub(r"//.*?$", "", no_block, flags=re.M)
        for m in re.finditer(r'"([^"\\]|\\.)*"', no_line):
            s = m.group(0)
            if re.search(r"[一-鿿]", s):
                # 允许 GALLERY_RELATIVE_PATH 产品已暴露路径常量
                if "Pictures/二维码" in s:
                    continue
                bad.append(f"{path.name}: {s[:60]}")
    if bad:
        print("hardcoded Chinese literals:", file=sys.stderr)
        for b in bad:
            print(f"  {b}", file=sys.stderr)
        return 1
    print("NO_HARDCODED_CN=PASS")
    return 0


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

    # 纯 Java 逻辑真源 + 回归套件（Android 门面不进 javac）
    sources = [
        SOURCE / "QrHistoryCodec.java",
        SOURCE / "QrStyleMath.java",
        SOURCE / "QrPayloads.java",
        TESTS / "QrPlusRegressionTest.java",
    ]
    missing = [str(path.relative_to(ROOT)) for path in sources if not path.is_file()]
    if missing:
        print("missing test inputs:\n- " + "\n- ".join(missing), file=sys.stderr)
        return 2

    junit_jars = find_junit_jars()
    if not junit_jars:
        print("junit jar not found in local Gradle cache", file=sys.stderr)
        return 2
    provided = os.pathsep.join(junit_jars)

    compile_cmd = [javac, "-encoding", "UTF-8", "-cp", provided, "-d", str(OUTPUT), *map(str, sources)]
    subprocess.run(compile_cmd, cwd=ROOT, check=True)
    result = subprocess.run(
        [java, "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join([str(OUTPUT), provided]),
         "com.gamecenter.app.tools.QrPlusRegressionTest"],
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

    exit_code = 0
    if "QR_PLUS_TEST_RESULT=PASS" not in output or "QR_PLUS_TEST_RESULT=FAIL" in output:
        exit_code = 1

    if check_i18n_pairs() != 0:
        exit_code = 1
    if check_no_hardcoded_cn() != 0:
        exit_code = 1

    return exit_code


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
