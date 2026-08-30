# 模块隔离与目录信任策略（P1）

适用范围：`core/module-host/**` 及宿主侧模块装载、资源加载、目录信任相关改动。

- 模块装载判定唯一真源为 `core/module-host ModuleLoader`；宿主侧 `com.gamecenter.app.modules.ModuleLoader` 仅是兼容门面，不得再新增第二套 Dex/资源/校验实现。
- 隔离策略：`catalog fileName` 为空 → 视为宿主内嵌代码（允许宿主 classloader 直载，随宿主发布）；`fileName` 非空（含预装内置 APK）→ 一律走外置 `DexClassLoader` 加载，文件缺失或清单缺 SHA-256 时必须拒绝，**禁止回退宿主陈旧副本**。
- 外置模块与预装内置 APK 装载必须同时通过：非空 SHA-256+大小校验、`ModuleSignatureVerifier` 发布证书钉扎（`core/security/res/raw/release_signer.cer`）。清单缺 SHA 或校验失败走 `onVerifyFailure` 清理回调，不允许"免检装载"。
- 动态模块资源加载依赖运行时探测 `AssetManager.addAssetPath` 私有 API 可用性；探测失败时记录 `MODULE_RESOURCE_FALLBACK` 并以宿主资源降级运行，不做任何绕过。
- 目录 Ed25519 签名默认开启（`enableCatalogSignature=true`）：已配置 `catalogEd25519PublicKeys` 时为强验证模式；未配置公钥的 release/stable 发布构建必须失败，本地开发构建仅警告并以兼容模式运行（`CATALOG_SIGNATURE_TRUSTED=false`）。
- 交付前必须核对 `version.properties`：默认 `autoBumpVersion=true` 会在每次 `assembleDebug` 后自动递增 versionCode 并回写，属构建系统既定行为；任何"成功构建"都必须记录当前 versionCode。如不需要自动递增，使用 `-PautoBumpVersion=false` 构建。

## 机器检查

以上条款已由 `scripts/verify_security_clauses.py`（11 项）与 `scripts/verify_isolation.py`（HARD/SOFT 不变量）覆盖并接入 CI；改动本目录前本地先行运行。
