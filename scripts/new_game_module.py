#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
new_game_module.py — 游戏模块脚手架（质量提升计划 §一 L94）

生成一个"天然合规"的动态游戏模块骨架：归一化 build.gradle（含 testImplementation
注入）、入口点 + Fragment 占位、src/test/<pkg>/ 单测占位、tests/stubs/ 占位，
统一 LF 行尾，并输出 settings.gradle include 与 catalog 注册（受保护文件，手工）
提示。

用法：
    python scripts/new_game_module.py --id game_puzzle --name "拼图" --package com.gamecenter.app.puzzle

约定：
    - 模块落位 module-store/feature/games/games/<id 去掉 game_ 前缀>/
    - id 必须以 game_ 开头（catalog 分类约定）
    - package 必须以 com.gamecenter.app. 开头
"""
import argparse
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
MODULES_ROOT = REPO / "module-store" / "feature" / "games" / "games"


def build_gradle(pkg: str) -> str:
    return f"""plugins {{
    id 'com.android.application'
}}

// 由 scripts/new_game_module.py 生成：归一化基线 + 单测依赖注入
def releaseSigningProperties = new Properties()
def releaseSigningFile = rootProject.file("keystore.properties")
if (releaseSigningFile.exists()) {{
    releaseSigningProperties.load(releaseSigningFile.withInputStream {{ it }} )
}}

android {{
    namespace '{pkg}'
    compileSdk 35

    defaultConfig {{
        applicationId "{pkg}"
        minSdk 26
        targetSdk 35
        versionCode 100
        versionName "1.0.0"
    }}

    signingConfigs {{
        release {{
            if (!releaseSigningProperties.isEmpty()) {{
                storeFile rootProject.file(releaseSigningProperties['STORE_FILE'])
                storePassword releaseSigningProperties['STORE_PASSWORD']
                keyAlias releaseSigningProperties['KEY_ALIAS']
                keyPassword releaseSigningProperties['KEY_PASSWORD']
                enableV1Signing = true
                enableV2Signing = true
            }}
        }}
    }}

    buildTypes {{
        release {{
            minifyEnabled false
            if (signingConfigs.release.storeFile != null) {{
                signingConfig signingConfigs.release
            }}
        }}
    }}

    compileOptions {{
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }}
}}

dependencies {{
    compileOnly project(':core:common')
    compileOnly project(':core:network')
    compileOnly files("${{project.rootDir}}/app/build/libs/app-classes.jar")
    compileOnly files("${{project.rootDir}}/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar")

    compileOnly 'androidx.appcompat:appcompat:1.6.1'
    compileOnly 'com.google.android.material:material:1.9.0'
    compileOnly 'androidx.fragment:fragment:1.5.0'

    // 单测（AGENTS.md：修 bug 必须带回归测试）
    testImplementation 'junit:junit:4.13.2'
    testImplementation project(':core:common')
}}

tasks.matching {{ it.name.contains("compile") && (it.name.contains("JavaWithJavac") || it.name.contains("Kotlin")) }}.configureEach {{
    dependsOn(":app:packageAppClasses", ":app:processDebugResources")
}}
"""


def entry_point(pkg: str, cls: str, module_id: str, name: str) -> str:
    return f"""package {pkg};

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.List;

/** 由 scripts/new_game_module.py 生成；真源在本模块，禁止向宿主拷贝副本。 */
public class {cls}EntryPoint implements ModuleInterface, FeatureModule {{

    private boolean running;

    @Override
    public void init(Context context) {{}}

    @Override
    public void start(Context context) {{
        running = true;
    }}

    @Override
    public void stop() {{
        running = false;
    }}

    @Override
    public String getId() {{
        return "{module_id}";
    }}

    @Override
    public String getName() {{
        return "{name}";
    }}

    @Override
    public boolean isRunning() {{
        return running;
    }}

    @Override
    public Fragment createFragment(Context context) {{
        return new {cls}ModuleFragment();
    }}

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions() {{
        return Arrays.asList(
                new ModuleNavigationContribution(
                        NavigationSlot.GAMES, "{module_id}",
                        () -> new {cls}ModuleFragment()));
    }}
}}
"""


def fragment(pkg: str, cls: str, name: str) -> str:
    return f"""package {pkg};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** 由 scripts/new_game_module.py 生成：程序化 UI 占位，不依赖模块资源。 */
public class {cls}ModuleFragment extends Fragment {{

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {{
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        TextView title = new TextView(requireContext());
        title.setText("{name}");
        title.setTextSize(20f);
        root.addView(title);
        return root;
    }}
}}
"""


def smoke_test(pkg: str, cls: str) -> str:
    return f"""package {pkg};

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 由 scripts/new_game_module.py 生成：单测占位——实现游戏逻辑后替换为真实回归测试。 */
public class {cls}ModuleSmokeTest {{

    @Test
    public void placeholder() {{
        assertEquals(2, 1 + 1);
        assertTrue(true);
    }}
}}
"""


def stubs_readme() -> str:
    return """# tests/stubs

javac 绕行模式测试用的桩类目录（如宿主 BuildConfig、框架类的最小替身）。
占位生成；不使用时保留目录结构即可。
"""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True, help="模块 id，必须以 game_ 开头，如 game_puzzle")
    ap.add_argument("--name", required=True, help="显示名（中文），如 拼图")
    ap.add_argument("--package", dest="pkg", required=True,
                    help="包名，必须以 com.gamecenter.app. 开头")
    args = ap.parse_args()

    module_id = args.id
    if not module_id.startswith("game_"):
        print("FAIL: --id 必须以 game_ 开头", file=sys.stderr)
        return 1
    if not args.pkg.startswith("com.gamecenter.app."):
        print("FAIL: --package 必须以 com.gamecenter.app. 开头", file=sys.stderr)
        return 1

    short = module_id[len("game_"):]
    cls = "".join(p.capitalize() for p in short.split("_"))
    pkg = args.pkg
    root = MODULES_ROOT / short

    if root.exists():
        print(f"FAIL: 目标已存在 {root}", file=sys.stderr)
        return 1

    files = {
        "build.gradle": build_gradle(pkg),
        "src/main/AndroidManifest.xml": '<?xml version="1.0" encoding="utf-8"?>\n'
                                        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" />\n',
        f"src/main/java/{pkg.replace('.', '/')}/{cls}ModuleEntryPoint.java":
            entry_point(pkg, cls, module_id, args.name),
        f"src/main/java/{pkg.replace('.', '/')}/{cls}ModuleFragment.java":
            fragment(pkg, cls, args.name),
        f"src/test/java/{pkg.replace('.', '/')}/{cls}ModuleSmokeTest.java":
            smoke_test(pkg, cls),
        "tests/stubs/README.md": stubs_readme(),
    }
    for rel, content in files.items():
        target = root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        # 统一 LF
        target.write_bytes(content.replace("\r\n", "\n").encode("utf-8"))

    print("=" * 64)
    print(f" 模块骨架已生成: {root.relative_to(REPO)}")
    print("=" * 64)
    print(" 后续手工步骤：")
    print(f" 1. settings.gradle 增加 include: ':{root.relative_to(REPO).as_posix()}'")
    print(f"    （现格式：include ':module-store:feature:games:games:{short}'，以现有条目为准）")
    print(" 2. catalog.json / modules.json 注册（受保护文件，走发布流程：")
    print("    scripts/build_production_catalog.py + catalog_signing.py + publish_module.py）")
    print(f" 3. 入口类: {pkg}.{cls}ModuleEntryPoint  id: {module_id}")
    print(" 4. 实现游戏逻辑，替换 SmokeTest 为真实回归测试（AGENTS.md 铁律 4）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
