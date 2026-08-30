#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
guard.py — 编辑前守卫 hook（质量提升计划 §一 L96）

设计：脚本实体版本化于 scripts/hooks/（CI 可复用同一套路由表）；
.claude/settings.json 仅做接线（PreToolUse matcher Edit|Write|MultiEdit，
stdin 传 JSON）。只覆盖 Claude 系 agent；其他 agent 靠 pre-commit + CI 兜底。

stdin 输入（Claude hook 协议）：
    {"tool_name": "Edit", "tool_input": {"file_path": "path/to/file"}, ...}

路由规则：
    1. 受保护发布资产 → 直接阻断（AGENTS.md 铁律 3）
    2. 按路径域路由对应 verify 脚本，任一失败即阻断（exit 2）
    3. 阻断信息写 stderr，供 agent 自纠

退出码：0 放行；2 阻断；其他非零按协议视为非阻断错误。
"""
import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# 受保护发布资产（AGENTS.md 铁律 3）：除非任务明确要求发布，否则不得改动
PROTECTED = [
    "app/src/main/assets/catalog.json",
    "app/src/main/assets/modules.json",
    "app/src/main/assets/modules/",
    "version.properties",
]

# 路径前缀 → verify 脚本列表（相对仓库根）
ROUTE = [
    ("module-store/feature/games/games/chinesechess/",
     ["scripts/verify_chinese_chess.py", "scripts/verify_agent_contract.py"]),
    ("module-store/feature/games/games/go/",
     ["scripts/verify_go.py", "scripts/verify_agent_contract.py"]),
    ("core/module-host/",
     ["scripts/verify_isolation.py", "scripts/verify_security_clauses.py"]),
    ("core/security/",
     ["scripts/verify_security_clauses.py"]),
    ("app/build.gradle", ["scripts/verify_ratchet.py"]),
    ("module-store/", ["scripts/verify_isolation.py"]),
]


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0  # 非 hook 协议调用（如 CLI 直跑）：放行
    file_path = (payload.get("tool_input") or {}).get("file_path") or ""
    if not file_path:
        return 0
    norm = file_path.replace("\\", "/").lstrip("/")
    if not (REPO / norm).exists() and not file_path.startswith(("D:", "C:")):
        norm_rel = norm
    else:
        try:
            norm_rel = (REPO / norm).resolve().relative_to(REPO).as_posix()
        except Exception:
            norm_rel = norm

    # 1. 受保护资产阻断
    for p in PROTECTED:
        if norm_rel == p or norm_rel.startswith(p):
            print(f"[guard] 受保护发布资产，默认禁止改动: {norm_rel}\n"
                  f"        如任务确属构建/发布流程，请按 AGENTS.md 铁律 3 说明兼容策略后继续。",
                  file=sys.stderr)
            return 2

    # 2. 域路由 verify
    scripts = []
    for prefix, ss in ROUTE:
        if norm_rel.startswith(prefix):
            scripts = ss
            break
    for s in scripts:
        r = subprocess.run([sys.executable, str(REPO / s)], cwd=str(REPO),
                           capture_output=True, text=True)
        if r.returncode != 0:
            tail = (r.stdout + r.stderr).strip().splitlines()[-6:]
            print(f"[guard] {s} 未通过（{norm_rel}）：\n" + "\n".join(tail), file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
