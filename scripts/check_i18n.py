#!/usr/bin/env python3
"""
i18n 一致性检查脚本（CI 拦截）。

用法:
    python scripts/check_i18n.py            # 默认从项目根目录扫描
    python scripts/check_i18n.py /path/to/project

退出码:
    0 — 所有模块中英 key 完全一致、placeholder 一致
    1 — 发现缺失或 placeholder 不一致，CI 应拦截

注意：本脚本只读不写，可在 CI 中安全调用。
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

KEY_VAL_RE = re.compile(r'<string\s+name="([^"]+)"\s*>(.*?)</string>', re.DOTALL)
KEY_ONLY_RE = re.compile(r'<string\s+name="([^"]+)"')
PLACEHOLDER_RE = re.compile(r'%(?:\d+\$)?[sdifL]|%\d+\$\.\d+f|%\.\d+f|%02d')


def find_strings_files(root):
    """返回 [(values_path, values_en_path_or_None)]"""
    result = []
    for values_path in root.rglob("res/values/strings.xml"):
        if "build" in values_path.parts:
            continue
        res_dir = values_path.parent.parent
        en_path = res_dir / "values-en" / "strings.xml"
        result.append((values_path, en_path if en_path.exists() else None))
    return result


def extract_keys(path):
    keys = set()
    with open(path, "r", encoding="utf-8") as f:
        for m in KEY_ONLY_RE.finditer(f.read()):
            keys.add(m.group(1))
    return keys


def extract_key_value(path):
    d = {}
    with open(path, "r", encoding="utf-8") as f:
        for m in KEY_VAL_RE.finditer(f.read()):
            d[m.group(1)] = m.group(2)
    return d


def validate_xml(path):
    """校验 XML 语法。"""
    try:
        ET.parse(path)
        return None
    except ET.ParseError as e:
        return str(e)


def check_placeholder(zh_path, en_path):
    """检查 placeholder 一致性。返回 [(key, zh_ph, en_ph)]"""
    zh = extract_key_value(zh_path)
    en = extract_key_value(en_path)
    mismatches = []
    for k in zh.keys() & en.keys():
        zh_ph = sorted(PLACEHOLDER_RE.findall(zh[k]))
        en_ph = sorted(PLACEHOLDER_RE.findall(en[k]))
        if zh_ph != en_ph:
            mismatches.append((k, zh_ph, en_ph))
    return mismatches


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    if not root.exists():
        print(f"ERROR: project root not found: {root}", file=sys.stderr)
        return 2

    print(f"i18n check - scanning {root}")
    files = find_strings_files(root)
    if not files:
        print("ERROR: no strings.xml found", file=sys.stderr)
        return 2

    has_error = False
    total_zh = 0
    total_en = 0

    for zh_path, en_path in files:
        rel = zh_path.relative_to(root)
        print(f"\n=== {rel} ===")

        # XML 语法
        err = validate_xml(zh_path)
        if err:
            print(f"  XML PARSE ERROR (zh): {err}")
            has_error = True
            continue
        if en_path:
            err = validate_xml(en_path)
            if err:
                print(f"  XML PARSE ERROR (en): {err}")
                has_error = True
                continue

        zh_keys = extract_keys(zh_path)
        en_keys = extract_keys(en_path) if en_path else set()
        total_zh += len(zh_keys)
        total_en += len(en_keys)
        print(f"  zh={len(zh_keys)} en={len(en_keys)}")

        if en_path is None:
            print("  [WARN] no values-en counterpart - English translations missing")
            has_error = True
            continue

        missing_en = zh_keys - en_keys
        missing_zh = en_keys - zh_keys
        if missing_en:
            print(f"  [FAIL] {len(missing_en)} keys missing English translation:")
            for k in sorted(missing_en)[:20]:
                print(f"      - {k}")
            if len(missing_en) > 20:
                print(f"      ... and {len(missing_en) - 20} more")
            has_error = True
        if missing_zh:
            print(f"  [FAIL] {len(missing_zh)} keys missing Chinese translation:")
            for k in sorted(missing_zh)[:20]:
                print(f"      - {k}")
            if len(missing_zh) > 20:
                print(f"      ... and {len(missing_zh) - 20} more")
            has_error = True
        if not missing_en and not missing_zh:
            print("  [OK] keys aligned")

        # placeholder 一致性
        ph_mismatch = check_placeholder(zh_path, en_path)
        if ph_mismatch:
            print(f"  [FAIL] {len(ph_mismatch)} placeholder mismatches:")
            for k, zp, ep in ph_mismatch:
                print(f"      - {k}: zh={zp} en={ep}")
            has_error = True

    print(f"\nTotal: zh={total_zh} en={total_en}")
    if has_error:
        print("RESULT: FAIL - i18n inconsistencies found")
        return 1
    print("RESULT: PASS - all modules aligned")
    return 0


if __name__ == "__main__":
    sys.exit(main())
