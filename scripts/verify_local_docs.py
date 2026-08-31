#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_local_docs.py — md 上传策略守卫（用户拍板 2026-08-31）

策略：md 文件能否上传由**内容用途**决定——
  - 用于 GitHub 展示/CI 消费的 md → 允许上传（白名单）
  - 工作文档（计划/清单/改动记录/wiki）→ 仅本地存档（docs/ 或根目录 ignore 项）

白名单 = README / CHANGELOG / RELEASE_NOTES / AGENTS / BUG_LEDGER /
         .github/** / build-logic、core、module-store、tools 内的 README 与 AGENTS
其余被 git 追踪的 .md 一律 FAIL（逃逸的工作文档）。
"""
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

# 路径规则（前缀或精确匹配，posix 风格）
ALLOW_EXACT = {"README.md", "CHANGELOG.md", "RELEASE_NOTES.md", "AGENTS.md", "BUG_LEDGER.md"}
ALLOW_PREFIXES = (".github/", "docs/线上/")  # docs/ 整体 ignored，此条为前瞻
ALLOW_STEM_PREFIXES = ("README", "AGENTS")  # 子目录内允许这两类前缀命名（含 README_SSL 等模块说明）


def allowed(path: str) -> bool:
    parts = Path(path).parts
    name = parts[-1]
    if path in ALLOW_EXACT:
        return True
    if any(path.startswith(p) for p in ALLOW_PREFIXES):
        return True
    if len(parts) > 1:
        stem = name.rsplit(".", 1)[0]
        return stem.startswith(ALLOW_STEM_PREFIXES) and name.endswith(".md")
    return False


def main() -> int:
    r = subprocess.run(["git", "ls-files", "*.md", "**/*.md"],
                       cwd=str(REPO), capture_output=True, text=True)
    files = [f for f in r.stdout.splitlines() if f.strip()]
    bad = [f for f in files if not allowed(f)]
    print("=" * 64)
    print(" md 上传策略守卫 (verify_local_docs)")
    print("=" * 64)
    print(f" 追踪中的 .md：{len(files)} 个")
    if not bad:
        print(" 结果: PASS（全部在白名单内）")
        print("=" * 64)
        return 0
    print(" FAIL: 以下工作文档类 md 被上传（策略：仅 GitHub 展示用途的 md 可入库）：")
    for f in bad:
        print(f"   - {f}")
    print(" 处置：`git rm --cached <file>` 保留本地副本，并把路径加入 .gitignore。")
    print("=" * 64)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
