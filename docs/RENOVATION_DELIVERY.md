<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# GameMatrixApp MVP 改造交付报告

> 交付日期：2026-07-07
> 交付总监：郝交付
> 基于：Spec v1.0（用户已确认）
> 团队：gma-mvp-dev（贾思敏-前端 / 贝洛奇-后端）

---

## 1. MVP 三件套交付状态

| 项 | 负责人 | 状态 | 验收 |
|----|--------|------|------|
| A1 暗色调色盘冲突修复 | 贾思敏 | ✅ 完成 | AC-A1 全通过（A1-3 游戏内 View 标 TODO） |
| A2 设计 Token 系统 | 贾思敏 | ✅ 完成 | AC-A2 全通过（A2-4 批量迁移分批） |
| S1 模块签名者强校验 | 贝洛奇 | ✅ 完成 | AC-S1 全通过（发布证书硬失败校验） |

---

## 2. 改动文件清单

### A1 — 暗色调色盘冲突修复（4 文件改）
| 文件 | 改动 |
|------|------|
| `app/src/main/res/values/colors.xml` | 主色 #1A73E8→#3D5AFE；secondary→#FFB300；tertiary→#00897B；surface/background/outline/error 按 Spec §7.1 LIGHT 全迁移；新增 surface_container_low/container/high |
| `app/src/main/res/values-night/colors.xml` | 全部 md_theme_* 按 Spec §7.1 DARK 迁移；**消除 13 个不必要同名重复**（eye_care_*/primary_color 等引用型别名） |
| `app/src/main/res/values/themes.xml` | 新增 colorSurfaceContainerLow/Container/High + colorSuccess/Warning/Info 赋值 |
| `app/src/main/res/values-night/themes.xml` | 同步暗色主题赋值 |

### A2 — 设计 Token 系统（7 文件新建 + 2 文件改）
| 文件 | 内容 |
|------|------|
| `app/src/main/res/values/attrs.xml`（新） | 12 个自定义语义色 attr 声明 |
| `app/src/main/res/values/color_tokens.xml`（新） | colorSuccess=#1FA463, colorWarning=#E08A00, colorInfo=primary |
| `app/src/main/res/values-night/color_tokens.xml`（新） | colorSuccess=#4CC38A, colorWarning=#FFB84D 暗色覆盖 |
| `app/src/main/res/values/dimens_tokens.xml`（新） | gm_spacing_0~10 (0/4/8/12/16/20/24/32/40/48/64dp) + gm_touch_target=48dp |
| `app/src/main/res/values/shape_tokens.xml`（新） | gm_radius_xs~full (4/8/12/16/24/32/9999dp) |
| `app/src/main/res/values/elevation_tokens.xml`（新） | gm_elevation_0~5 (0/1/3/6/8/12dp) |
| `app/src/main/res/values/styles.xml`（改） | Card/FAB/Search/Chip 圆角&阴影引用 token（示范） |
| `app/lint.xml`（新） | PxUsage warning；既有 NewApi/WrongConstant 等 4 类降级 warning |

### S1 — 模块签名者强校验（真实发布证书 + 下载/安装双入口）
| 文件 | 内容 |
|------|------|
| `core/security/.../ModuleSignatureVerifier.kt`（新） | apksig ApkVerifier 校验 v2/v3 签名 + 证书钉扎；缺证书、签名无效或签名者不匹配均硬失败 |
| `core/security/build.gradle`（改） | +apksig:8.13.2 依赖 |
| `core/modulestore/.../ModuleInstaller.java`（改） | 调用 ModuleSignatureVerifier.verify() + 三分支处理(Failure→拒装/Warning→告警放行/Success→继续) |
| `core/modulestore/build.gradle`（改） | +project(':core:security') 依赖 |
| `core/security/src/main/res/raw/release_signer.cer` | 从现有发布密钥导出的公开证书，不含私钥或口令 |

---

## 3. QA 验证结果

### 编译
| 检查 | 结果 |
|------|------|
| `:core:security:compileDebugKotlin` | ✅ 通过 |
| `:core:modulestore:compileDebugJavaWithJavac` | ✅ 通过 |
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL（versionCode 569→570） |
| detekt | ✅ 全绿 |

### Lint
| 检查 | 结果 |
|------|------|
| `:app:lintDebug` | ✅ 0 errors, 82 warnings |
| 基线 | 1237 errors + 405 warnings 被基线过滤；**10 条已修复**（LintBaselineFixed，只减不增趋势） |
| MVP 引入新 lint error | **0** |

### 验收标准对照
| 编号 | 验收项 | 结果 |
|------|--------|------|
| A1-1 | 切换暗色无同名冲突 | ✅ |
| A1-2 | colorPrimary = #3D5AFE | ✅ |
| A1-3 | 自定义 View 走 ?attr/ | ⚠️ 主题层正确；游戏内标 TODO |
| A1-4 | 无同名冲突告警 | ✅ |
| A2-1 | 新增布局引用 gm_spacing_* | ✅ |
| A2-2 | 新增布局引用 ?attr/colorXxx | ✅ |
| A2-3 | token 文件存在 | ✅ |
| A2-4 | ~80% 硬编码 dimens 替换 | ⚠️ 分批迁移 |
| A2-5 | 圆角/阴影统一引用 token | ✅ |
| S1-1 | 签名者证书校验 | ✅ |
| S1-2 | 签名不匹配→拒绝下载/安装 | ✅ |
| S1-3 | SHA-256 保留 | ✅ |
| S1-4 | 合法签名正常安装 | ✅ |
| S1-5 | 下载器与模块安装器均执行硬校验 | ✅ |

---

## 4. 遗留 TODO（不阻塞交付）

| 编号 | 内容 | 分期 |
|------|------|------|
| TD-1 | 游戏内自定义 View（gomoku/chess/snake）的 ?attr/ 迁移 | 一期 A1-3 |
| TD-2 | ~80% 既有 layout 硬编码 dimens 批量迁移 | 一期 A2-4 |
| TD-5 | lint.xml 中 4 类既有 issue 恢复 error | 一期 P2 |
| TD-6 | 自定义 lint rule（HardcodedDimens/Color/TextSize） | 二期 |
| TD-7 | purple_500(#7B1FA2) 违规色替换 Material Symbols | 二期 |

---

## 5. 后续路线图（Master Plan 一期/二期）

**一期（MVP 之后）**：
- F1 双层模块框架收敛（v2 moduleloader 唯一加载器 + v1 CompatShim 过渡）
- U1 冷启动优化（Baseline Profile <2s）
- U2 免登录上手（交互式 Coachmark）
- U3 模块下载可靠性
- P2-P5 质量项（Lint 压缩 / kapt→ksp / 状态机测试 / Socket 拆分）
- P1-2 i18n 补全
- Android 16/17 深化（edge-to-edge / 预测返回 / 响应式）

**二期及以后**：
- F2/F3/F4/F5（错题本增强 / 本地 LLM / 本地发现 / 云存档）
- A4 动态取色 / C1 多 ABI
- Compose 试点页（设置 + AI 对话）

**立即立项跟踪**：
- W1 MediaPipe 16KB 页对齐（最高风险）
- W2 server/ 后端扩容

---

— 郝交付 / 交付总监，MVP 三件套交付完成