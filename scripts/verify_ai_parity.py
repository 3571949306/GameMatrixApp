#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_ai_parity.py — AI/游戏逻辑双份同步守护（#9 单一真相源·强制版）

背景（2026-08-26 核验）：ChineseChessAI / GoAI / GoGame / GoView /
GoUiPreferences / GomokuAI 在「宿主 app 内嵌」与「module-store 动态模块」各有一份。
工程规范要求两份逻辑一致（仅允许同步声明头与包名不同）。本脚本对每一对做
规整化比对，防止"只修一端"造成的隐性分叉 —— 用机器强制替代"人工记得同步"。

用法：
    python scripts/verify_ai_parity.py
    python scripts/verify_ai_parity.py --repo <path> --strict

--strict：任一配对缺失一侧视为失败；默认缺失侧只 WARN。
任何时候两份内容不一致（除同步头/包名）都会失败（退出码 1）。
"""
import argparse
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 每项：(宿主相对路径, 模块侧相对路径)
PAIRS = [
    (
        "app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java",
        "module-store/feature/games/games/chinesechess/src/main/java/com/gamecenter/app/chinesechess/ChineseChessAI.java",
    ),
    (
        "app/src/main/java/com/gamecenter/app/games/go/GoAI.java",
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoAI.java",
    ),
    (
        "app/src/main/java/com/gamecenter/app/games/go/GoGame.java",
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoGame.java",
    ),
    (
        "app/src/main/java/com/gamecenter/app/games/go/GoView.java",
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoView.java",
    ),
    (
        "app/src/main/java/com/gamecenter/app/games/go/GoUiPreferences.java",
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoUiPreferences.java",
    ),
    (
        "app/src/main/java/com/gamecenter/app/games/gomoku/GomokuAI.java",
        "module-store/feature/games/games/gomoku/src/main/java/com/gamecenter/app/gomoku/GomokuAI.java",
    ),
]

# 规整化时逐行剔除的行模式：纯注释行（含跨行同步声明头）、包名声明、空行。
# 两份副本要求逻辑一致；注释仅作说明，差异不计入逻辑分叉。
IGNORE_PATTERNS = [
    re.compile(r"^\s*//"),
    re.compile(r"^\s*package\s+[\w.]+;?\s*$"),
    re.compile(r"^\s*$"),
]


def normalize(text):
    out = []
    for line in text.splitlines():
        if any(p.search(line) for p in IGNORE_PATTERNS):
            continue
        out.append(line.rstrip())
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=REPO_ROOT)
    ap.add_argument("--strict", action="store_true",
                    help="任一配对缺失一侧视为失败（默认仅 WARN）")
    args = ap.parse_args()
    repo = os.path.abspath(args.repo)

    failed = []      # (name, reason)
    warnings = []    # (name, reason)
    checked = 0

    for host_rel, mod_rel in PAIRS:
        host_path = os.path.join(repo, host_rel)
        mod_path = os.path.join(repo, mod_rel)
        name = os.path.basename(host_rel)

        if not os.path.exists(host_path) or not os.path.exists(mod_path):
            missing = [p for p, pth in ((host_rel, host_path), (mod_rel, mod_path))
                       if not os.path.exists(pth)]
            msg = f"缺失一侧: {missing}"
            if args.strict:
                failed.append((name, msg))
            else:
                warnings.append((name, msg + "（默认仅告警；--strict 时失败）"))
            continue

        with open(host_path, encoding="utf-8", errors="ignore") as fh:
            host_norm = normalize(fh.read())
        with open(mod_path, encoding="utf-8", errors="ignore") as fh:
            mod_norm = normalize(fh.read())

        checked += 1
        if host_norm != mod_norm:
            failed.append((name, "两份内容不一致（除同步头/包名外存在差异，需同步）"))

    print("=" * 72)
    print(" AI/游戏逻辑双份同步守护 (verify_ai_parity)")
    print("=" * 72)
    print(f" 配对总数 : {len(PAIRS)}, 已完成比对: {checked}")
    print("-" * 72)
    for name, reason in warnings:
        print(f" [WARN] {name}: {reason}")
    if failed:
        print(" FAIL:")
        for name, reason in failed:
            print(f"   - {name}: {reason}")
        print("=" * 72)
        print(" 结果: FAIL（存在分叉，请同步双份后重跑）")
        sys.exit(1)
    print(" 所有配对逻辑一致（同步头/包名差异已忽略）")
    print("=" * 72)
    print(" 结果: PASS")
    sys.exit(0)


if __name__ == "__main__":
    main()