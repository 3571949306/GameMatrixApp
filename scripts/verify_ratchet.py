#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_ratchet.py — "只降不增"计数器门禁（质量提升计划 §二/§四）

对三类结构性劣化指标实施棘轮（ratchet）：只允许减少，不允许新增。
基线存于 scripts/quality_baseline.json；任何指标超线立即失败（exit 1）。

指标口径（与基线生成逻辑一致，改动口径须同步重定基线并在 PR 说明）：
  empty_catch_count             app/core/module-store 源码中空 catch 块数量
  buildconfig_boolean_flag_count  所有 build.gradle* 中 `buildConfigField "boolean"` 数量
                                  （同时是 §四 L188 "禁止新增 boolean flag" 的机器检查）
  isolation_soft_count          verify_isolation.py 中声明为 SOFT 的过渡项数量
                                  （SOFT 逐步 HARD 化，只减不增）

用法：
    python scripts/verify_ratchet.py             # 校验（CI 用）
    python scripts/verify_ratchet.py --update    # 重新生成基线（改进落地后手动）
"""
import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE = Path(__file__).resolve().parent / "quality_baseline.json"

SRC_SUFFIXES = {".java", ".kt"}
SRC_ROOTS = ["app/src/main", "app/src/main/kotlin", "core", "module-store"]
EMPTY_CATCH = re.compile(r"catch\s*\([^)]*\)\s*\{\s*\}")
BOOLEAN_FLAG = re.compile(r'buildConfigField\s+"boolean"')
SOFT_MARKER = re.compile(r"#\s*-+\s*SOFT")


def count_empty_catch() -> int:
    n = 0
    for root in SRC_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for f in base.rglob("*"):
            if f.suffix not in SRC_SUFFIXES or "build" in f.parts:
                continue
            try:
                n += len(EMPTY_CATCH.findall(f.read_text(encoding="utf-8", errors="ignore")))
            except OSError:
                pass
    return n


def count_boolean_flags() -> int:
    n = 0
    for g in REPO.glob("**/build.gradle*"):
        if any(x in g.parts for x in (".gradle", ".git", "build", ".github")):
            continue
        try:
            n += len(BOOLEAN_FLAG.findall(g.read_text(encoding="utf-8", errors="ignore")))
        except OSError:
            pass
    return n


def count_isolation_soft() -> int:
    p = REPO / "scripts" / "verify_isolation.py"
    return len(SOFT_MARKER.findall(p.read_text(encoding="utf-8", errors="ignore")))


def current_metrics() -> dict:
    return {
        "empty_catch_count": count_empty_catch(),
        "buildconfig_boolean_flag_count": count_boolean_flags(),
        "isolation_soft_count": count_isolation_soft(),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--update", action="store_true", help="用当前值重写基线")
    args = ap.parse_args()

    now = current_metrics()

    if args.update:
        BASELINE.write_text(
            json.dumps(
                {"_comment": "只降不增基线（verify_ratchet.py 消费）。允许减少，禁止增加。",
                 "as_of": "2026-08-30",
                 "metrics": now},
                ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")
        print(f"基线已更新: {now}")
        return 0

    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))["metrics"]

    print("=" * 64)
    print(" 只降不增棘轮门禁 (verify_ratchet)")
    print("=" * 64)
    regressed = []
    for key, now_v in now.items():
        base_v = baseline.get(key, 0)
        status = "OK " if now_v <= base_v else "REGRESS"
        print(f" [{status}] {key}: now={now_v} baseline={base_v}")
        if now_v > base_v:
            regressed.append(key)
    print("-" * 64)
    if regressed:
        print(" FAIL: 以下指标超基线，禁止引入新的结构性劣化：")
        for k in regressed:
            print(f"   - {k}")
        print(" 如本次改动确属改进（如清理后统计口径变化），运行 "
              "`python scripts/verify_ratchet.py --update` 重定基线并在 PR 中说明。")
        print("=" * 64)
        return 1
    print(" 结果: PASS（全部指标不高于基线）")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
