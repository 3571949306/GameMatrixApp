#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_security_clauses.py — AGENTS.md 安全条款机器检查（质量提升计划 §一 L93）

把规范中原本"靠人工记忆"的安全条款落成静态检查，零依赖、秒级完成：

  1. §7.8  Release 的 GoActivity 必须 exported=false（入口只允许存在于 Debug source set）
  2. §8.2  外置模块缺 SHA-256 必须拒绝（allowEmpty 仅允许 builtIn）
  3. §8.3  发布证书钉扎（ModuleSignatureVerifier + release_signer.cer）存在
  4. §8.4  资源加载降级路径（MODULE_RESOURCE_FALLBACK）存在
  5. §8.5  目录签名默认开启（enableCatalogSignature 默认 true + TRUSTED 门控）

任一检查失败 → exit 1。
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

FAILS = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f" [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail and not ok else ""))
    if not ok:
        FAILS.append(name)


def read(rel: str) -> str:
    return (REPO / rel).read_text(encoding="utf-8", errors="ignore")


def main() -> int:
    print("=" * 64)
    print(" 安全条款机器检查 (verify_security_clauses)")
    print("=" * 64)

    # 1. §7.8 GoActivity：主/Release source set 不得声明 exported 的 GoActivity
    go_violations = []
    for mf in ("app/src/main/AndroidManifest.xml",
               "app/src/release/AndroidManifest.xml"):
        p = REPO / mf
        if not p.exists():
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(r"<activity\b[^>]*GoActivity[^>]*>", text):
            tag = m.group(0)
            if 'android:exported="true"' in tag:
                go_violations.append(f"{mf}: {tag.strip()[:80]}")
    check("§7.8 Release GoActivity 不允许 exported=true（入口仅限 Debug source set）",
          not go_violations, "; ".join(go_violations))
    debug_mf = REPO / "app/src/debug/AndroidManifest.xml"
    check("§7.8 Debug source set 存在（模拟器验收入口的合法挂载点）", debug_mf.exists())

    # 2. §8.2 外置模块缺 SHA 必须拒绝：SHA 校验的 allowEmpty 只能绑 builtIn
    allowed_sites = 0
    bad_sites = []
    for rel in ("app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt",
                "app/src/main/java/com/gamecenter/app/modules/ModuleDownloadManager.kt"):
        p = REPO / rel
        if not p.exists():
            continue
        for m in re.finditer(r"verifySha256\([^)]*\)", p.read_text(encoding="utf-8", errors="ignore")):
            call = m.group(0)
            if "allowEmpty" in call:
                if re.search(r"allowEmpty\s*=\s*manifest\.builtIn|allowEmpty\s*=\s*it\.builtIn", call):
                    allowed_sites += 1
                else:
                    bad_sites.append(f"{rel}: {call[:70]}")
    check("§8.2 缺 SHA-256 仅对 builtIn 放行（allowEmpty=manifest.builtIn）",
          allowed_sites >= 1 and not bad_sites,
          f"allowed={allowed_sites}, bad={bad_sites}")
    installer = REPO / "app/src/main/java/com/gamecenter/app/modules/store/TransactionInstaller.kt"
    if installer.exists():
        t = installer.read_text(encoding="utf-8", errors="ignore")
        check("§8.2 事务安装链存在 SHA 校验调用", "verifySha256" in t or "sha256" in t.lower())

    # 3. §8.3 发布证书钉扎
    sig = REPO / "core/security/src/main/kotlin/com/gamecenter/app/core/security/ModuleSignatureVerifier.kt"
    sig_ok = sig.exists() and "ApkVerifier" in sig.read_text(encoding="utf-8", errors="ignore")
    check("§8.3 ModuleSignatureVerifier 存在且使用 apksig ApkVerifier", sig_ok)
    # 证书实体（release_signer.cer）被 .gitignore 排除（凭据类文件不入库，仅存在于
    # 本地/发布构建环境），CI 上不能检查文件本身，改为检查代码对资源的接线。
    cer_wired = sig.exists() and "release_signer.cer" in sig.read_text(encoding="utf-8", errors="ignore")
    check("§8.3 发布证书资源接线（ModuleSignatureVerifier 引用 release_signer.cer）", cer_wired)
    wired = REPO / "app/src/main/java/com/gamecenter/app/modules/store/TransactionInstaller.kt"
    check("§8.3 安装链接线 ModuleSignatureVerifier",
          wired.exists() and "ModuleSignatureVerifier" in wired.read_text(encoding="utf-8", errors="ignore"))

    # 4. §8.4 资源降级路径
    loader = REPO / "core/module-host/src/main/kotlin/com/gamecenter/app/core/modulehost/ModuleResourceLoader.kt"
    check("§8.4 MODULE_RESOURCE_FALLBACK 降级路径存在",
          loader.exists() and "MODULE_RESOURCE_FALLBACK" in loader.read_text(encoding="utf-8", errors="ignore"))

    # 5. §8.5 目录签名默认开启
    gradle = read("app/build.gradle")
    m = re.search(r'findProperty\("enableCatalogSignature"\)\s*\?:\s*"(\w+)"', gradle)
    check("§8.5 enableCatalogSignature 默认 true", bool(m) and m.group(1) == "true")
    check("§8.5 CATALOG_SIGNATURE_TRUSTED 门控注入", 'CATALOG_SIGNATURE_TRUSTED' in gradle)
    repo_kt = REPO / "app/src/main/java/com/gamecenter/app/modules/store/StoreCatalogRepository.kt"
    if repo_kt.exists():
        r = repo_kt.read_text(encoding="utf-8", errors="ignore")
        check("§8.5 目录验签接入（ENABLE_CATALOG_SIGNATURE + forceVerify）",
              "ENABLE_CATALOG_SIGNATURE" in r and "CATALOG_SIGNATURE_TRUSTED" in r)

    print("-" * 64)
    if FAILS:
        print(f" 结果: FAIL（{len(FAILS)} 项未过）：{', '.join(FAILS)}")
        print("=" * 64)
        return 1
    print(" 结果: PASS")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
