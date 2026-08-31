#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
blast_radius.py — 爆炸半径查询（质量提升计划 §六）

治"窄修复"的第二环：agent 看不到自己改动的影响面。
输入改动文件（显式路径或 --base diff），输出：
    1. 受影响模块（按顶层目录聚合）
    2. 依赖方（grep 对被改类 FQN 的 import 引用，封顶展示）
    3. 应跑的 verify 脚本（复用 scripts/hooks/guard.py 的 ROUTE 表，单一真相源）

用法：
    python scripts/blast_radius.py --base origin/main
    python scripts/blast_radius.py app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt
退出码恒 0（信息工具，不做门禁）。
"""
import argparse
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "scripts" / "hooks"))
try:
    from guard import ROUTE  # 单一真相源：与编辑守卫同一张路由表
except Exception:
    ROUTE = []

SRC_SUFFIXES = {".kt", ".java"}
MAX_DEPENDENTS_SHOWN = 15


def changed_files(base: str, explicit: list) -> list:
    if explicit:
        return explicit
    if base:
        r = subprocess.run(["git", "diff", "--name-only", f"{base}..HEAD"],
                           cwd=str(REPO), capture_output=True, text=True)
        if r.returncode != 0:
            print(f"[blast] git diff 失败: {r.stderr.strip()}", file=sys.stderr)
            return []
        return [f for f in r.stdout.splitlines() if f.strip()]
    return []


def fqn_of(path: Path):
    """从源文件路径推导包名+类名（Java/Kotlin 惯例：目录即包）。"""
    posix = path.as_posix()
    for marker in ("/src/main/java/", "/src/main/kotlin/", "/src/main/", "/src/"):
        if marker in posix:
            rel = posix.split(marker, 1)[1]
            return rel.rsplit(".", 1)[0].replace("/", ".")
    return None


def find_dependents(fqn: str) -> list:
    pkg_class = fqn.rsplit(".", 1)
    if len(pkg_class) != 2:
        return []
    dep = set()
    for pat in (f"import {fqn}", f"import {pkg_class[0]}.*"):
        r = subprocess.run(["git", "grep", "-l", pat, "--", "*.kt", "*.java"],
                           cwd=str(REPO), capture_output=True, text=True)
        if r.returncode == 0:
            dep.update(r.stdout.splitlines())
    return sorted(dep)


def route_for(rel: str) -> list:
    scripts = []
    for prefix, ss in ROUTE:
        if rel.startswith(prefix):
            scripts.extend(ss)
    return sorted(set(scripts))


def module_of(rel: str) -> str:
    parts = Path(rel).parts
    if parts and parts[0] in ("core", "module-store", "tools"):
        return "/".join(parts[:3]) if len(parts) > 2 else parts[0]
    return parts[0] if parts else rel


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="diff 基线 ref（默认不启用）")
    ap.add_argument("files", nargs="*", help="显式改动文件路径")
    args = ap.parse_args()

    files = changed_files(args.base, args.files)
    prod = [f for f in files
            if Path(f).suffix in SRC_SUFFIXES and "/src/main/" in f.replace("\\", "/")]
    other = [f for f in files if f not in prod]

    print("=" * 64)
    print(" 爆炸半径报告 (blast_radius)")
    print("=" * 64)
    if not prod:
        print(" 无生产源码变更。" + (f" 其他变更 {len(other)} 个文件。" if other else ""))
        return 0

    modules, verify_scripts, all_deps = set(), set(), set()
    for f in prod:
        rel = Path(f).as_posix()
        modules.add(module_of(rel))
        verify_scripts.update(route_for(rel))
        p = REPO / rel
        fqn = fqn_of(p) if p.exists() else None
        deps = find_dependents(fqn) if fqn else []
        deps = [d for d in deps if d != rel]
        all_deps.update(deps)
        print(f"\n ◆ {rel}")
        if fqn:
            print(f"   FQN: {fqn}")
        if deps:
            for d in deps[:MAX_DEPENDENTS_SHOWN]:
                print(f"   ← 依赖方: {d}")
            if len(deps) > MAX_DEPENDENTS_SHOWN:
                print(f"   ← …共 {len(deps)} 个依赖方")
        else:
            print("   ← 未发现 import 级依赖方（可能经接口/DI 间接引用，人工确认）")

    print("\n" + "-" * 64)
    print(" 受影响模块: " + (", ".join(sorted(modules)) or "—"))
    print(f" 依赖方合计: {len(all_deps)} 个文件")
    print(" 应跑 verify 脚本:")
    if verify_scripts:
        for s in verify_scripts:
            print(f"   python {s}")
    else:
        print("   （路由表无匹配域——按 PR 模板规范检查单跑 agent_contract + 对应域脚本）")
    print(" 别忘了：同一 PR 伴随测试/守卫变更（verify_fix_discipline 门禁）")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
