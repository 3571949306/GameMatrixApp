#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_ai_parity.py — AI/游戏逻辑单一真相源守护

背景（2026-08-30 更新）：commit 0c52cb9 已删除全部宿主侧副本，AI/游戏逻辑
（ChineseChessAI / GoAI / GoGame / GoView / GoUiPreferences / GomokuAI）只保留
module-store 动态模块一份。旧版"双份同步比对"随之失效（配对缺失仅 WARN =
假绿灯），本脚本反转为守卫单一真相源：

    宿主 app 内不允许再出现这些类的副本 —— 模块侧是唯一实现。

若有人把模块代码拷回宿主（历史倒退），本脚本立即失败并给出处置指引。

用法：
    python scripts/verify_ai_parity.py
    python scripts/verify_ai_parity.py --repo <path>
"""
import argparse
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 单一真相源：这些实现只允许存在于 module-store 模块侧。
# 键为类名（用于宿主侧按文件名扫描），值为模块侧权威路径（仅用于报错指引）。
SINGLE_SOURCE = {
    "ChineseChessAI.java":
        "module-store/feature/games/games/chinesechess/src/main/java/com/gamecenter/app/chinesechess/ChineseChessAI.java",
    "GoAI.java":
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoAI.java",
    "GoGame.java":
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoGame.java",
    "GoView.java":
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoView.java",
    "GoUiPreferences.java":
        "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoUiPreferences.java",
    "GomokuAI.java":
        "module-store/feature/games/games/gomoku/src/main/java/com/gamecenter/app/gomoku/GomokuAI.java",
}

# 宿主源码根（不允许出现上述副本的目录）
HOST_SRC_ROOTS = [
    "app/src/main/java",
    "app/src/main/kotlin",
    "core",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=REPO_ROOT)
    args = ap.parse_args()
    repo = os.path.abspath(args.repo)

    violations = []
    checked_files = 0
    for root in HOST_SRC_ROOTS:
        root_dir = os.path.join(repo, root)
        if not os.path.isdir(root_dir):
            continue
        for dirpath, _dirnames, filenames in os.walk(root_dir):
            for fn in filenames:
                if fn not in SINGLE_SOURCE:
                    continue
                checked_files += 1
                rel = os.path.relpath(os.path.join(dirpath, fn), repo)
                violations.append((rel, SINGLE_SOURCE[fn]))

    print("=" * 72)
    print(" AI/游戏逻辑单一真相源守护 (verify_ai_parity)")
    print("=" * 72)
    if violations:
        print(" FAIL: 宿主侧出现应为模块独占的实现副本（单一真相源被破坏）：")
        for rel, canonical in violations:
            print(f"   - {rel}")
            print(f"     权威实现: {canonical}")
        print(" 处置：删除宿主副本；宿主如需该能力，经 module-host 接口调用模块侧实现。")
        print("=" * 72)
        sys.exit(1)
    print(f" 守卫类数: {len(SINGLE_SOURCE)}, 宿主侧命中副本: 0")
    print(" 结果: PASS（module-store 模块侧保持唯一实现）")
    print("=" * 72)
    sys.exit(0)


if __name__ == "__main__":
    main()
