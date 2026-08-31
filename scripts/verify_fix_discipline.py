#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_fix_discipline.py — 修复纪律机器门禁（质量提升计划 §六）

规则：PR diff 触碰生产代码（app/core/module-store 的 main 源集 .kt/.java）时，
同一 PR 必须包含测试/守卫变更，否则 CI 红。治的是"窄修复"：
只对着症状改代码、不写复现测试、回归无人兜底。

守卫变更的合法形态（任一即可）：
    */src/test/**、*/src/androidTest/**、模块根 tests/**、
    scripts/verify_*.py、scripts/hooks/**、config/**、BUG_LEDGER.md

逃生门：PR 打 label `no-test-justified`（纯重构/发布批次/文档），
须在 PR 描述「修复报告单」写明理由。

上下文：
    CI（pull_request 事件）：从 $GITHUB_EVENT_PATH 读 base sha + labels
    本地预检：python scripts/verify_fix_discipline.py --base origin/main
    其他事件（push 到 main 等）：跳过（exit 0）

退出码：0 通过/跳过；1 违规。
"""
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

PROD_PREFIXES = (
    "app/src/main/",
    "app/src/debug/",
    "app/src/release/",
)
PROD_MODULE_PREFIXES = ("core/", "module-store/", "tools/")  # 再要求含 /src/main/
PROD_SUFFIXES = {".kt", ".java"}

GUARD_MARKERS = (
    "/src/test/", "/src/androidTest/", "/tests/",
    "scripts/verify_", "scripts/hooks/", "config/", "BUG_LEDGER.md",
)

ESCAPE_LABEL = "no-test-justified"


def is_production(path: str) -> bool:
    p = path.replace("\\", "/")
    if not Path(p).suffix in PROD_SUFFIXES:
        return False
    if p.startswith(PROD_PREFIXES):
        return True
    if p.startswith(PROD_MODULE_PREFIXES) and "/src/main/" in p:
        return True
    return False


def is_guard(path: str) -> bool:
    p = path.replace("\\", "/")
    return any(m in p or p == m for m in GUARD_MARKERS)


def git_diff_files(base: str, head: str = "HEAD") -> list:
    r = subprocess.run(
        ["git", "diff", "--name-only", f"{base}..{head}"],
        cwd=str(REPO), capture_output=True, text=True)
    if r.returncode != 0:
        print(f"[fix-discipline] git diff 失败: {r.stderr.strip()}", file=sys.stderr)
        print("  CI 侧需 checkout fetch-depth: 0；本地需先 `git fetch origin main`。",
              file=sys.stderr)
        raise SystemExit(2)
    return [f for f in r.stdout.splitlines() if f.strip()]


def from_github_event():
    """返回 (base_sha, labels) 或 None（非 PR 事件）。"""
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return None
    path = os.environ.get("GITHUB_EVENT_PATH", "")
    if not path or not Path(path).exists():
        return None
    try:
        event = json.loads(Path(path).read_text(encoding="utf-8"))
        pr = event.get("pull_request", {})
        base = (pr.get("base") or {}).get("sha") or ""
        labels = [l.get("name", "") for l in pr.get("labels", [])]
        return base or None, labels
    except (OSError, json.JSONDecodeError):
        return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="diff 基线 ref/sha（本地预检用）")
    args = ap.parse_args()

    ev = from_github_event()
    if ev is not None:
        base, labels = ev
    elif args.base:
        base, labels = args.base, []
    else:
        print("[fix-discipline] 非 PR 上下文且未给 --base：跳过")
        return 0

    files = git_diff_files(base)
    prod = [f for f in files if is_production(f)]
    guards = [f for f in files if is_guard(f)]

    print("=" * 64)
    print(" 修复纪律门禁 (verify_fix_discipline)")
    print("=" * 64)
    print(f" diff 文件数: {len(files)}  生产代码: {len(prod)}  测试/守卫: {len(guards)}")

    if not prod:
        print(" 结果: PASS（未触碰生产代码）")
        print("=" * 64)
        return 0
    if guards:
        print(" 结果: PASS（生产变更伴随守卫变更）")
        for g in guards[:8]:
            print(f"   + {g}")
        print("=" * 64)
        return 0
    if ESCAPE_LABEL in labels:
        print(f" WARN: 生产变更无测试/守卫，但 PR 带 label `{ESCAPE_LABEL}`——放行；"
              "请在「修复报告单」写明理由")
        print("=" * 64)
        return 0

    print(" FAIL: 以下生产代码变更没有任何测试/守卫变更伴随：")
    for f in prod[:20]:
        print(f"   - {f}")
    if len(prod) > 20:
        print(f"   … 共 {len(prod)} 个")
    print(" 处置：① 补复现测试（修复前失败、修复后通过）或登记 BUG_LEDGER.md 守卫；")
    print(f"       ② 确属不可测/无需测（纯重构、发布批次）→ PR 打 label `{ESCAPE_LABEL}` 并写明理由。")
    print("=" * 64)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
