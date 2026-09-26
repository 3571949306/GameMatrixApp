#!/usr/bin/env python3
"""
i18n 一致性检查脚本（CI 拦截）。

用法:
    python scripts/check_i18n.py            # 默认从项目根目录扫描
    python scripts/check_i18n.py /path/to/project

扫描范围:
    所有 res/values/ 下含 <string> 元素的 XML 文件（strings.xml 与
    strings_*.xml），逐文件与 values-en*/ 同名文件配对检查。

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
    """返回 [(values_path, values_en_path_or_None)]

    通用遍历所有 res/values/ 目录下含 <string> 元素的 XML 文件，
    按"相对 values 目录的文件名"与 values-en*/ 中的同名文件配对，
    覆盖 strings.xml 与 strings_*.xml（如 strings_game_doudizhu_new.xml），
    消除只扫描 strings.xml 造成的 CI 盲区。
    其他 locale 变体目录（values-night 等不含 <string> 的）自动跳过。
    """
    def has_string_elem(path):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            return True  # 语法错误交给主流程报 XML PARSE ERROR
        return any(elem.tag == "string" for elem in tree.getroot().iter())

    result = []
    for res_dir in sorted(root.rglob("res")):
        if not res_dir.is_dir() or "build" in res_dir.parts:
            continue
        # 跳过非 main 源集（src/debug 等构建变体不属于本地化范围）
        if res_dir.parent.parent.name == "src" and res_dir.parent.name != "main":
            continue
        default_dir = res_dir / "values"
        if not default_dir.is_dir():
            continue
        en_dirs = sorted(p for p in res_dir.iterdir()
                         if p.is_dir() and p.name.startswith("values-en"))
        for xml_path in sorted(default_dir.glob("*.xml")):
            if not has_string_elem(xml_path):
                continue
            rel_name = xml_path.relative_to(default_dir)
            en_path = next((d / rel_name for d in en_dirs if (d / rel_name).exists()), None)
            result.append((xml_path, en_path))

    # 英文侧独有、values/ 无同名文件的条目：非阻断提示（不影响运行时回退，但应知晓）
    for res_dir in sorted(root.rglob("res")):
        if not res_dir.is_dir() or "build" in res_dir.parts:
            continue
        if res_dir.parent.parent.name == "src" and res_dir.parent.name != "main":
            continue
        default_dir = res_dir / "values"
        if not default_dir.is_dir():
            continue
        for en_dir in sorted(p for p in res_dir.iterdir()
                             if p.is_dir() and p.name.startswith("values-en")):
            for xml_path in sorted(en_dir.glob("*.xml")):
                if not has_string_elem(xml_path):
                    continue
                base = default_dir / xml_path.name
                if base.exists() and has_string_elem(base):
                    continue  # 正常配对，已在上面处理
                # 英文侧独有、values/ 无同名文件的条目：非阻断提示
                result.append((base, xml_path))
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
        print("ERROR: no string resource files found", file=sys.stderr)
        return 2

    has_error = False
    total_zh = 0
    total_en = 0

    for zh_path, en_path in files:
        # 英文侧独有条目：values/ 侧文件不存在，只报存在性提示，不进入逐 key 检查
        if en_path is not None and not zh_path.exists():
            print(f"\n=== {en_path.relative_to(root)} ===")
            print("  [INFO] values-en file without values/ counterpart (non-blocking)")
            total_en += len(extract_keys(en_path))
            continue
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
            # 尚未建立英文镜像的文件（历史存量，按阶段补齐）：
            # 列出但不阻断 CI；一旦建立镜像即纳入下方严格对齐检查。
            print("  [INFO] no values-en counterpart - not yet localized (non-blocking)")
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
