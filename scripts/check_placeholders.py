#!/usr/bin/env python3
"""检查中英 strings.xml 中 placeholder（%s/%d/%1$s 等）的一致性。"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KEY_VAL_RE = re.compile(r'<string\s+name="([^"]+)"\s*>(.*?)</string>', re.DOTALL)
PLACEHOLDER_RE = re.compile(r'%(?:\d+\$)?[sdifL]|%\d+\$\.\d+f|%\.\d+f|%02d')

def extract(path):
    d = {}
    with open(path, "r", encoding="utf-8") as f:
        for m in KEY_VAL_RE.finditer(f.read()):
            d[m.group(1)] = m.group(2)
    return d

def check(zh_path, en_path):
    zh = extract(zh_path)
    en = extract(en_path)
    mismatches = []
    for k in zh.keys() & en.keys():
        zh_ph = sorted(PLACEHOLDER_RE.findall(zh[k]))
        en_ph = sorted(PLACEHOLDER_RE.findall(en[k]))
        if zh_ph != en_ph:
            mismatches.append((k, zh[k], en[k], zh_ph, en_ph))
    return mismatches

def main():
    pairs = [
        (ROOT / "app/src/main/res/values/strings.xml",
         ROOT / "app/src/main/res/values-en/strings.xml", "app"),
        (ROOT / "core/network/src/main/res/values/strings.xml",
         ROOT / "core/network/src/main/res/values-en/strings.xml", "core/network"),
        (ROOT / "core/update/src/main/res/values/strings.xml",
         ROOT / "core/update/src/main/res/values-en/strings.xml", "core/update"),
        (ROOT / "module-store/feature/tools/wrongbook/src/main/res/values/strings.xml",
         ROOT / "module-store/feature/tools/wrongbook/src/main/res/values-en/strings.xml", "wrongbook"),
    ]
    total = 0
    for zh, en, name in pairs:
        mm = check(zh, en)
        print(f"=== {name} ===")
        if not mm:
            print("  placeholder 全部一致 [OK]")
        else:
            print(f"  {len(mm)} 处 placeholder 不一致:")
            for k, zv, ev, zp, ep in mm:
                print(f"    - {k}")
                print(f"        zh={zv!r} placeholders={zp}")
                print(f"        en={ev!r} placeholders={ep}")
            total += len(mm)
    if total == 0:
        print("\n[OK] 所有模块 placeholder 全部一致")
    else:
        print(f"\n[WARN] 共 {total} 处 placeholder 不一致")

if __name__ == "__main__":
    main()
