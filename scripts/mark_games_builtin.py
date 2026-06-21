#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将modules.json中所有游戏标记为内置（builtIn=true）
保留更新能力（保留versionCode、entryClass等）
"""
import json
import sys
from pathlib import Path

# 这些是游戏ID（非nav模块）
GAME_IDS = {
    "game_2048", "chinesechess", "klotski", "blackjack", "breakout",
    "brotato", "checkers", "dice", "flappy", "go", "guess",
    "knife", "match", "memory", "minesweeper", "pipeline", "plane",
    "reaction", "rock", "snake", "sokoban", "sudoku", "tetris",
    "tic", "tiles", "whack"
}

# 这些已经是builtIn=true的
ALREADY_BUILTIN = {"gomoku", "doudizhu"}

def mark_games_builtin(json_path: str):
    path = Path(json_path)
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    modified_count = 0
    for module in data.get("modules", []):
        module_id = module.get("id", "")
        if module_id in GAME_IDS or module_id in ALREADY_BUILTIN:
            old_builtin = module.get("builtIn", False)
            # 标记为内置
            module["builtIn"] = True
            # 保留versionCode和entryClass以便更新
            # 保留fallbackUrl和githubUrl以便下载更新
            # 清空直接下载字段（因为代码已内置）
            if module_id in GAME_IDS:  # 只清空之前是false的
                module["fileSize"] = 0
                module["sha256"] = ""
                module["downloadUrl"] = ""
            # 添加或保留builtInVersionCode
            if "builtInVersionCode" not in module:
                module["builtInVersionCode"] = module.get("versionCode", 100)
            modified_count += 1
            print(f"  ✓ {module_id}: builtIn {old_builtin} -> True")

    # 写回
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"\n✅ 共修改 {modified_count} 个游戏模块")
    return modified_count


if __name__ == "__main__":
    json_path = sys.argv[1] if len(sys.argv) > 1 else r"D:\Developmment\GameMatrixApp\app\src\main\assets\modules.json"
    print(f"📝 处理文件: {json_path}")
    print("=" * 60)
    mark_games_builtin(json_path)
