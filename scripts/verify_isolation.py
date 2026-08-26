#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_isolation.py — GameMatrixApp 模块隔离护栏

在每个隔离改造阶段后运行，断言隔离不变量并跟踪改造进度。

设计：
- 硬不变量 (HARD)：当前必须成立、且改造不得破坏。违反 -> 退出码 1（阻断 CI/合并）。
- 过渡项 (SOFT)：跟踪改造进度的目标。未达标 -> WARN（不阻断），达标 -> PASS。

隔离目标背景（2026-08-26 审计）：
- 运行时主线加载器是 Kotlin ModuleManager/ModuleLoader（按 catalog entryClass 反射）。
- Java 子系统（core/moduleloader 的 ModuleLoaderV2 + core/modulestore 的
  ModuleInstaller/ModuleUninstaller/BuiltInModuleUpdater/ModuleLifecycleManager）
  为遗留/失效代码：ModuleLoaderV2 硬编码错误的入口类名，永远无法加载真实模块。
  改造目标是收敛到单一加载器，消除冗余/静默失败路径与 SP 文件冲突隐患。

用法：
    python scripts/verify_isolation.py
    python scripts/verify_isolation.py --repo <path>
"""
import argparse
import json
import os
import re
import sys

SRC_EXTS = (".java", ".kt", ".kotlin")

# 跳过非源码/生成目录
SKIP_DIRS = {
    ".git", "build", ".gradle", ".idea", "node_modules",
    ".workbuddy", "captures", "generated", ".gradle-composite",
}

HARD_FAILS = []   # (name, detail)
SOFT_ITEMS = []   # (level, name, detail)  level in {PASS, WARN}


def rel(path, repo):
    try:
        return os.path.relpath(path, repo)
    except Exception:
        return path


def walk_sources(repo):
    out = []
    for root, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for f in files:
            if f.endswith(SRC_EXTS):
                out.append(os.path.join(root, f))
    return out


def read_text(path):
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as fh:
            return fh.read()
    except Exception:
        return ""


def top_module(path, repo):
    """返回文件所属顶层模块目录名（用于跨模块冲突判定）。"""
    r = rel(path, repo)
    parts = r.split(os.sep)
    return parts[0] if parts else r


def main():
    ap = argparse.ArgumentParser()
    default_repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--repo", default=default_repo)
    args = ap.parse_args()
    repo = os.path.abspath(args.repo)

    files = walk_sources(repo)
    contents = {f: read_text(f) for f in files}

    # ---------- HARD: SP 文件名跨模块唯一 ----------
    # 同一 SP 文件名出现在不同顶层模块目录 == 潜在命名冲突（如主线与遗留线
    # 同时写 module_manager_prefs）。同模块内多文件引用同一 SP 属合法。
    sp_pat = re.compile(r'getSharedPreferences\(\s*["\']([^"\']+)["\']')
    sp_map = {}
    for f, c in contents.items():
        for m in sp_pat.finditer(c):
            sp_map.setdefault(m.group(1), set()).add(top_module(f, repo))
    conflict = {n: sorted(fs) for n, fs in sp_map.items() if len(fs) > 1}
    if conflict:
        detail = "; ".join(f"{n} -> {fs}" for n, fs in conflict.items())
        HARD_FAILS.append(("SP 文件名跨模块冲突", detail))
    else:
        SOFT_ITEMS.append(("PASS", "SP 文件名无跨模块冲突",
                           f"扫描到 {len(sp_map)} 个唯一 SP 文件名"))

    # ---------- HARD: 主线模块加载必须走 DexClassLoader（Kotlin ModuleLoader） ----------
    main_loader = [f for f in files
                   if f.endswith(os.path.join("modules", "ModuleLoader.kt"))]
    if main_loader:
        c = contents[main_loader[0]]
        if "DexClassLoader" in c:
            SOFT_ITEMS.append(("PASS", "主线模块加载使用 DexClassLoader",
                               rel(main_loader[0], repo)))
        else:
            HARD_FAILS.append(("主线模块加载未使用 DexClassLoader",
                               rel(main_loader[0], repo)))
    else:
        HARD_FAILS.append(("未找到主线 ModuleLoader",
                           "期望 app/src/main/.../modules/ModuleLoader.kt"))

    # ---------- SOFT: ModuleResourceLoader 实现数量（目标 1） ----------
    res_defs = [rel(f, repo) for f in files
                if os.path.basename(f) in ("ModuleResourceLoader.java",
                                           "ModuleResourceLoader.kt")]
    if len(res_defs) <= 1:
        SOFT_ITEMS.append(("PASS", "ModuleResourceLoader 实现唯一",
                           f"{len(res_defs)} 个"))
    else:
        SOFT_ITEMS.append(("WARN", "存在多个 ModuleResourceLoader 实现",
                           f"{len(res_defs)} 个: {res_defs}（阶段1待收敛）"))

    # ---------- SOFT: 硬编码入口 "com.gamecenter.module."+id+".ModuleEntry" ----------
    # 精确匹配 ModuleLoaderV2.java:249 的拼接形态：
    #   "com.gamecenter.module." + <expr> + ".ModuleEntry"
    # 注意字符串字面量含有引号与前置点号 ".ModuleEntry"；
    # 中间的 <expr> 可能是 moduleInfo.getModuleId() 这类带点号/括号的表达式。
    hard_pat = re.compile(
        r'"com\.gamecenter\.module\."\s*\+\s*.+?\s*\+\s*"\.ModuleEntry"')
    hits = []
    for f, c in contents.items():
        for m in hard_pat.finditer(c):
            line = c[:m.start()].count("\n") + 1
            hits.append(f"{rel(f, repo)}:{line}")
    if hits:
        SOFT_ITEMS.append(("WARN", "硬编码模块入口残留",
                           "; ".join(hits) + "（应使用 catalog entryClass）"))
    else:
        SOFT_ITEMS.append(("PASS", "无硬编码模块入口",
                           "未发现 com.gamecenter.module.*.ModuleEntry"))

    # ---------- SOFT: loadBuiltInModule 是否经宿主 classloader 直载入口类 ----------
    # 目标：builtIn 模块也应经 DexClassLoader 隔离。但「宿主内嵌」模块（catalog fileName 为空，
    # 如 games_hall/browser/breakout）经宿主 classloader 是预期行为；其余内置模块若配置了独立
    # APK 却回退宿主 classloader，属隔离缺口（见下方 APK 预装完整性检查）。
    if main_loader:
        c = contents[main_loader[0]]
        idx = c.find("fun loadBuiltInModule")
        if idx != -1:
            nxt = c.find("\nfun ", idx + 10)
            seg = c[idx: nxt if nxt != -1 else idx + 3000]
            if ("context.classLoader" in seg or "context.getClassLoader()" in seg) \
                    and "loadClass" in seg and "DexClassLoader" not in seg:
                SOFT_ITEMS.append(("WARN", "builtIn 模块可能经宿主 classloader 加载",
                                   f"{rel(main_loader[0], repo)} 的 loadBuiltInModule（宿主内嵌模块为预期；APK 配置缺口见下）"))
            else:
                SOFT_ITEMS.append(("PASS", "builtIn 加载隔离无硬违规",
                                   "未检测到宿主 classloader 直载入口类"))
        else:
            SOFT_ITEMS.append(("WARN", "未找到 loadBuiltInModule",
                               "无法判定 builtIn 隔离（人工核查）"))

    # ---------- SOFT: 内置模块已配独立 APK 但 assets 缺预装包（隔离缺口追踪） ----------
    # 内置模块若 catalog fileName 非空，预期经 DexClassLoader 隔离加载；若 assets/modules/
    # 未打包该 APK，则运行时回退宿主 classloader（陈旧副本），属隔离缺口，需打包 APK
    # 或把 catalog fileName 置空（对齐 games_hall/browser 的宿主内嵌模式）。
    cat_path = os.path.join(repo, "app", "src", "main", "assets", "catalog.json")
    assets_mod = os.path.join(repo, "app", "src", "main", "assets", "modules")
    missing_apk = []
    try:
        if os.path.exists(cat_path):
            with open(cat_path, encoding="utf-8") as fh:
                cat = json.load(fh)
            bundled = set(os.listdir(assets_mod)) if os.path.isdir(assets_mod) else set()
            for m in cat.get("modules", []):
                if m.get("builtIn") and m.get("fileName"):
                    if m["fileName"] not in bundled:
                        missing_apk.append(m.get("id", m.get("fileName")))
    except Exception:
        pass
    if missing_apk:
        SOFT_ITEMS.append(("WARN", "内置模块已配独立 APK 但 assets 缺预装包",
                           f"{missing_apk}（运行时回退宿主 classloader，隔离缺口；建议打包 APK 或 catalog fileName 置空）"))
    else:
        SOFT_ITEMS.append(("PASS", "内置模块 APK 预装完整性",
                           "配置 fileName 的内置模块均有预装 APK（或无需）"))

    # ---------- SOFT: ModuleScopedPreferences 作用域约束（Phase 3 数据隔离） ----------
    # 数据隔离强约束：模块 SP 应经 ModuleScopedPreferences 带 moduleId 前缀，且禁止伪造
    # 其它模块作用域（同名/越权断言）。断言该类存在且内含作用域前缀/分隔符与 require 断言。
    msp = [f for f in files if os.path.basename(f) == "ModuleScopedPreferences.kt"]
    if msp:
        c = contents[msp[0]]
        has_scope = ("PREFIX" in c and "SEP" in c and "scopedName" in c)
        has_assert = ("require(" in c and "禁止伪造其它模块作用域" in c)
        if has_scope and has_assert:
            SOFT_ITEMS.append(("PASS", "ModuleScopedPreferences 已落地并强制作用域",
                               rel(msp[0], repo)))
        else:
            SOFT_ITEMS.append(("WARN", "ModuleScopedPreferences 作用域约束不完整",
                               f"{rel(msp[0], repo)} 缺少前缀/分隔符或断言"))
    else:
        SOFT_ITEMS.append(("WARN", "ModuleScopedPreferences 未落地",
                           "模块 SP 仍依赖扁平命名，数据隔离仅靠命名纪律（Phase 3 待实施）"))

    # ---------- SOFT: module-store 不应再裸调 getSharedPreferences("扁平名") ----------
    # Phase 3 全量迁移目标：模块 SP 一律经 ModuleScopedPreferences（带 moduleId 作用域前缀），
    # 禁止裸调 getSharedPreferences("扁平名") 绕过作用域约束。命中视为隔离回归。
    raw_pat = re.compile(r'getSharedPreferences\(')
    raw_hits = []
    for f, c in contents.items():
        if "module-store" not in f.replace("\\", "/"):
            continue
        for ln, line in enumerate(c.splitlines(), 1):
            if "ModuleScopedPreferences" in line:
                continue
            # AiPreferences 读取旧版未作用域明文（PREFS_NAME）作为一次性迁移源，属预期例外
            if "PREFS_NAME" in line:
                continue
            if raw_pat.search(line):
                raw_hits.append(f"{rel(f, repo)}:{ln}")
    if raw_hits:
        SOFT_ITEMS.append(("WARN", "module-store 存在裸 getSharedPreferences 调用",
                           f"{len(raw_hits)} 处待迁移: " + "; ".join(raw_hits[:20])))
    else:
        SOFT_ITEMS.append(("PASS", "module-store 无裸 getSharedPreferences 调用",
                           "全部模块 SP 已路由至 ModuleScopedPreferences"))

    # ---------- SOFT: 冗余加载子系统收敛（目标：仅保留 Kotlin 主线） ----------
    java_loader = [rel(f, repo) for f in files
                   if os.path.basename(f) == "ModuleLoaderV2.java"]
    legacy_managers = [rel(f, repo) for f in files
                       if os.path.basename(f) in (
                           "ModuleLifecycleManager.java",
                           "ModuleInstaller.java",
                           "ModuleUninstaller.java",
                           "BuiltInModuleUpdater.java",
                           "ModuleHotReloader.java")]
    if java_loader or legacy_managers:
        items = java_loader + legacy_managers
        SOFT_ITEMS.append(("WARN", "遗留 Java 加载子系统残留",
                           f"{len(items)} 个文件待收敛: {items}（阶段1待处理）"))
    else:
        SOFT_ITEMS.append(("PASS", "遗留 Java 加载子系统已清除",
                           "无 ModuleLoaderV2/ModuleLifecycleManager 等"))

    # ---------- 输出 ----------
    print("=" * 72)
    print(" GameMatrixApp 模块隔离护栏 (verify_isolation)")
    print("=" * 72)
    print(f" 扫描源码文件 : {len(files)}")
    print("-" * 72)
    for lvl, name, detail in SOFT_ITEMS:
        icon = "PASS " if lvl == "PASS" else "WARN "
        print(f" [{icon}] {name}")
        print(f"         {detail}")
    print("-" * 72)
    if HARD_FAILS:
        print(" HARD FAIL (阻断):")
        for name, detail in HARD_FAILS:
            print(f"   - {name}: {detail}")
        print("=" * 72)
        print(" 结果: FAIL")
        sys.exit(1)
    print(" HARD 不变量全部通过")
    print("=" * 72)
    print(" 结果: PASS（过渡项见上方 WARN）")
    sys.exit(0)


if __name__ == "__main__":
    main()
