#!/usr/bin/env python3
"""翻转模块在出厂清单中的 builtIn 标志（模块热更改造 / 回退两用）。

2026-08-29 模块热更改造（docs/模块热更改造计划_2026-08-29.md Phase 1.1）：
把 11 个"回归内置"的游戏翻回 builtIn=false，使其加入外置模块更新链路。
宿主启动时 GameRegistry 对 builtIn=false 的游戏注册 DynamicGameActivity，
由 DexClassLoader 加载预装/下载的模块 APK；商店对非内置模块提供单独更新。

用法：
    python scripts/flip_games_builtin.py --builtin false                 # 默认翻转 11 个游戏（正向）
    python scripts/flip_games_builtin.py --builtin true --ids tetris    # 回退单个游戏
    python scripts/flip_games_builtin.py --dry-run                      # 只预览

行为：
- 只改动 builtIn 字段；activityClass/builtInVersionCode/fileName/sha256/downloadUrl
  全部保留（回滚时只需把 builtIn 翻回 true，无需重建字段）。
- 顶层 version 与 catalogVersion 各 +1（远程清单防降级门控依赖递增，
  见 ModuleManager.fetchRemoteModulesInternal 的 remoteVersion < localVersion 拒收逻辑）。
- modules.json 与 catalog.json 是逐字节相同的双胞胎文件，本脚本双写保持一致。
"""

import argparse
import datetime
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TARGETS = [
    REPO / "app" / "src" / "main" / "assets" / "modules.json",
    REPO / "app" / "src" / "main" / "assets" / "catalog.json",
]

# 模块热更改造 Phase 1 的 11 个目标游戏（第一批灰度：tetris/snake/sudoku）
DEFAULT_IDS = [
    "tetris", "snake", "sudoku",
    "gomoku", "go", "blackjack", "checkers",
    "game_2048", "klotski", "doudizhu", "minesweeper",
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", default=",".join(DEFAULT_IDS), help="逗号分隔的模块 id（默认 11 个游戏）")
    parser.add_argument("--builtin", choices=["true", "false"], default="false", help="目标 builtIn 值")
    parser.add_argument("--dry-run", action="store_true", help="只预览变更，不写文件")
    args = parser.parse_args()

    ids = [i.strip() for i in args.ids.split(",") if i.strip()]
    target_builtin = args.builtin == "true"

    raw = TARGETS[0].read_bytes()
    data = json.loads(raw.decode("utf-8"))
    modules = {m["id"]: m for m in data["modules"]}

    missing = [i for i in ids if i not in modules]
    if missing:
        print(f"错误：清单中不存在这些模块: {missing}", file=sys.stderr)
        return 1

    changed = []
    for mid in ids:
        m = modules[mid]
        if m.get("builtIn") == target_builtin:
            print(f"跳过（已是目标状态）: {mid} builtIn={target_builtin}")
            continue
        m["builtIn"] = target_builtin
        if target_builtin:
            # 回退到内置：提醒 downloadUrl/sha256 必须仍然有效，供将来再次翻出使用
            if not m.get("downloadUrl") or not m.get("sha256"):
                print(f"警告：{mid} 缺 downloadUrl/sha256，回退后再翻出需先补齐", file=sys.stderr)
        changed.append(mid)

    if not changed:
        print("无变更。")
        return 0

    data["version"] = int(data.get("version", 0)) + 1
    data["catalogVersion"] = int(data.get("catalogVersion", 0)) + 1
    data["generatedAt"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    payload = json.dumps(data, ensure_ascii=True, indent=2).encode("utf-8") + b"\n"
    action = "翻为内置(builtIn=true)" if target_builtin else "翻为外置(builtIn=false)"
    print(f"计划变更: {len(changed)} 个模块 {action}: {changed}")
    print(f"顶层 version -> {data['version']}, catalogVersion -> {data['catalogVersion']}")
    if args.dry_run:
        print("dry-run：未写文件。")
        return 0

    for target in TARGETS:
        target.write_bytes(payload)
    # 自检：双胞胎必须仍然逐字节相同
    if TARGETS[0].read_bytes() != TARGETS[1].read_bytes():
        print("错误：双写后 modules.json 与 catalog.json 不一致！", file=sys.stderr)
        return 1
    print(f"已双写并校验一致: {TARGETS[0].name} / {TARGETS[1].name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
