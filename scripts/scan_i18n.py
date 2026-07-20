#!/usr/bin/env python3
"""扫描所有模块 strings.xml，对比中英 key 差异。"""
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KEY_RE = re.compile(r'<string\s+name="([^"]+)"')

def find_strings_files():
    """返回 [(module, values_path, values_en_path_or_None)]"""
    result = []
    for values_path in ROOT.rglob("res/values/strings.xml"):
        if "build" in values_path.parts:
            continue
        # find sibling values-en
        res_dir = values_path.parent.parent
        en_path = res_dir / "values-en" / "strings.xml"
        module = str(value_path_relative_to_root(values_path))
        result.append((module, values_path, en_path if en_path.exists() else None))
    return result

def value_path_relative_to_root(p):
    return p.relative_to(ROOT)

def extract_keys(path):
    """提取 strings.xml 的所有 key（用正则，避免 XML 解析对未转义字符报错）"""
    keys = set()
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    for m in KEY_RE.finditer(content):
        keys.add(m.group(1))
    return keys

def extract_key_value(path):
    """提取 (key -> value) 字典"""
    d = {}
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    # 简单逐行扫描
    for m in re.finditer(r'<string\s+name="([^"]+)"\s*>(.*?)</string>', content, re.DOTALL):
        d[m.group(1)] = m.group(2)
    return d

def main():
    files = find_strings_files()
    print(f"找到 {len(files)} 个有 strings.xml 的模块/位置：")
    for module, vp, ep in files:
        print(f"  - {module}  | values-en: {'YES' if ep else 'NO'}")
    print()

    total_zh = 0
    total_en = 0
    for module, vp, ep in files:
        zh_keys = extract_keys(vp)
        en_keys = extract_keys(ep) if ep else set()
        total_zh += len(zh_keys)
        total_en += len(en_keys)
        print(f"=== {module} ===")
        print(f"  中文 keys: {len(zh_keys)}  英文 keys: {len(en_keys)}")
        missing_en = zh_keys - en_keys
        missing_zh = en_keys - zh_keys
        if missing_en:
            print(f"  中文有但英文缺失 ({len(missing_en)} 条):")
            for k in sorted(missing_en):
                print(f"    - {k}")
        if missing_zh:
            print(f"  英文有但中文缺失 ({len(missing_zh)} 条):")
            for k in sorted(missing_zh):
                print(f"    - {k}")
        if not missing_en and not missing_zh:
            print("  中英 key 完全一致 ✓")
        print()
    print(f"全项目统计：中文 {total_zh} 条 / 英文 {total_en} 条")

if __name__ == "__main__":
    main()
