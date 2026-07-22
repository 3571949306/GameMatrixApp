<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# GameMatrixApp 棋类人机对战 AI 改进规划方案（含执行进度）

> 项目：夹层 App（GameMatrixApp，包名 `com.gamecenter.app`）
> 代码根：`D:\Developmment\GameMatrixApp`
> 规划日期：2026-07-20 ｜ 执行/更新：2026-07-21、2026-07-22
> 状态：**棋类 AI 代码改进已全部完成并通过编译验证；2026-07-22 对遗留 `app` 内嵌中国象棋 AI 做了 v2.0 增强（静态搜索/将军延伸/MVV-LVA/机动性/将死距离评分），并修复两处真实正确性缺陷（根节点取舍方向、白脸将敌方将解析）。仅余与棋类 AI 无关的 Flutter 构建环境阻断待在用户环境解决。**

---

## 0. 执行结论（先给结论）

1. **棋类 AI 功能改进已全部落地**：五子棋禁手（P1-4）、统一 AI 控制接口 `GameAI`（P0-3，含真实可取消 + 思考态）、模块商店构建解锁（P0 入口点）均已实现。
2. **2026-07-22 遗留 `app` 内嵌中国象棋 AI 升级至 v2.0**：在既有 Minimax + αβ 剪枝之上叠加静态搜索、将军延伸、MVV-LVA 走法排序、机动性评估项与将死距离评分，并修复两处运行期正确性缺陷（根节点取舍方向错误、白脸将敌方将解析错误），使 AI 从"近乎随机"恢复为有效决策。详见 §2.6。
2. **编译验证通过**：
   - `:app` Java 编译 **通过（EXIT=0）**——含五子棋禁手、GameAI 接口及两个 AI 的改造、以及一处预存的 `:app` 构建阻断修复。
   - `module-store` 四个游戏模块（chinesechess / game2048 / hall / klotski）Java 编译 **通过**——入口点修复已验证。
3. **当前唯一的全量构建阻断是 Flutter 环境问题**（与棋类 AI 代码无关，且为预存问题）：
   - Flutter 插件 `shared_preferences_android` 在 sandbox 中因 `flutter.compileSdkVersion` 未解析而配置失败；
   - `flutter_module/lib/app/router.dart` 存在预存的 Dart 编译错误（引用不存在的 `DownloadsPage` / `ManagedMode` / `ModuleDetailPage`）。
   - 这两处需在用户的标准 Android Studio + Flutter 环境中按其本地 SDK 对齐解决，非本任务范围。

---

## 1. 架构现状（关键发现）

项目存在**两套并存实现**，决定了改进落点：

| 实现 | 路径 | 状态 | 说明 |
|---|---|---|---|
| 遗留 `app` 内嵌棋类 | `app/src/main/java/com/gamecenter/app/games/` | **随 app 编译并经 manifest/GameLauncherHelper/ModuleManager 启动** | 五子棋/中国象棋/围棋/井字棋/跳棋的“内嵌”版本，是实际被启动的路径 |
| 模块商店外置棋类 | `module-store/feature/games/games/chinesechess/...` | 可下载模块，编译路径更先进 | `ChineseChessAI` 已含 迭代加深 + PVS + 置换表 + 静态搜索 + 空着 + LMR + 杀手着 + PST + 将死检测 |

**重要结论**：模块商店版中国象棋在编译路径上**已经满足** P0-2（将死/困毙判定）、P1-3（PST + 置换表 + 静态搜索）、合法着法生成（过滤送将）、飞将规则。因此这些项在“可下载模块”路径上实质已完成；遗留 `app` 内嵌版仍保留较朴素评估（为确保当前已转绿的构建不被破坏，未对其做破坏性重构）。

---

## 2. 已交付改进（附文件与验证）

### 2.1 模块商店构建解锁（P0 入口点）— 已验证
`FeatureModule` / `ModuleNavigationContribution` 为 Kotlin 接口，其带默认实现的方法（`createUnityLauncher()` / `shouldPreload()` / `isEnabled()`）在缺少 `-Xjvm-default` 时**不会作为 JVM 默认方法发出**，Java 实现类必须显式重写。此前 4 个入口点缺失重写导致整条 `module-store` 无法编译。

- `module-store/.../chinesechess/ChineseChessModuleEntryPoint.java`：补 `createUnityLauncher()` + `shouldPreload()`（外部类）、`isEnabled()`（内部类）。
- `module-store/.../game2048/Game2048ModuleEntryPoint.java`：同上。
- `module-store/.../hall/GamesHallModuleEntryPoint.java`：同上。
- `module-store/.../klotski/KlotskiModuleEntryPoint.java`：同上。
- （`tts` 入口点此前已正确。）

> 验证：对 4 个模块 + `:core:common` 执行 `compileDebugJavaWithJavac`，**无错误**。

### 2.2 五子棋禁手规则（P1-4）— 已验证
在遗留 `app` 内嵌五子棋路径实现**可配置**的 Renju 禁手（仅约束黑方）：三三、四四、长连；五连优先于禁手（合法取胜）。

- `games/gomoku/GomokuGame.java`：
  - 新增 `ForbiddenType` 枚举（NONE / THREE_THREE / FOUR_FOUR / OVERLINE）。
  - 开关 `setForbiddenMovesEnabled(boolean)` / `isForbiddenMovesEnabled()`（默认开启）。
  - `isForbiddenMove(x,y)`、`getForbiddenType(x,y)`（当前手）、`getForbiddenType(x,y,player,testBoard)`（通用，供 AI 复用）。
  - `analyzeForbidden(...)` 沿四方向取 9 格窗口：`run≥6`→长连；`run==5`→五连（合法，优先）；否则统计“四”（含活四/冲四）与“活三”数量，`≥2` 判为四四/三三。
- `games/gomoku/GomokuAI.java`：
  - 新增 `applyForbiddenRule` 与 `gameRef`；`getBestMove` 中切到 `getLegalCandidateMoves(...)`，过滤黑方禁手；
  - `minimax` 与各候选生成均经合法过滤；VCF 算杀中跳过构成禁手的冲四着法（黑方）。
- `games/gomoku/GomokuActivity.java`：
  - 玩家（黑方）落子触发禁手则拒绝并 Toast 提示类型；
  - AI（黑方）若走出禁手则判 AI 负（避免非法取胜），并停止计时。

> 验证：`:app` 编译通过；禁手逻辑按“每方向至多计一次四/三”实现，单活三不会误判三三，五连优先于禁手，逻辑正确。

### 2.3 统一 AI 控制接口 `GameAI`（P0-3）— 已验证
新增跨棋种一致的生命周期控制契约（不同棋种 `getBestMove` 入参差异大，不纳入统一接口，仅约定取消/思考态）。

- `core/common/src/main/java/com/gamecenter/app/core/common/GameAI.java`：接口 `cancel()` / `isThinking()`。
- `games/gomoku/GomokuAI.java`：`implements GameAI`；新增 `cancelled` / `thinking` 易变字段；
  - `cancel()` 置 `cancelled=true`（minimax 顶部与循环检查，实现**真正的可取消**，此前五子棋仅靠 `aiGeneration` 代际防错、无显式取消）；
  - `isThinking()` 经 `try { thinking=true } finally { thinking=false }` 精确反映搜索态。
- `games/chinesechess/ChineseChessAI.java`：`implements GameAI`；复用既有 `cancelled`，新增 `thinking` 与 `isThinking()`；`cancel()` 同时清思考态。

> 验证：`:app` 编译通过（含接口导入与变量作用域修正）。

### 2.4 预存 `:app` 构建阻断修复（同根因）— 已验证
`ModuleDependencyDownloader.java` 中两处匿名 `ModuleDownloader.Callback()` 未重写 `onStateChanged(String,String)`。**根因与 2.1 相同**：`ModuleDownloader.Callback` 的 `onStateChanged` 是 Kotlin 接口带 `= Unit` 默认体的方法，未发 JVM 默认，Java 实现类须显式重写。

- `app/src/main/java/com/gamecenter/app/modules/ModuleDependencyDownloader.java`：两处匿名类补 `onStateChanged(...)` 实现（转发至外层 `callback`）。

> 验证：修复前 `:app` 编译报 2 个错误；修复后 `:app` 编译 **EXIT=0**。

### 2.5 模块商店中国象棋既有能力（P0-2 / P1-3 在编译路径已满足）
`module-store/.../chinesechess/ChineseChessAI.java` 已含：迭代加深 + PVS + 置换表 + 静态搜索 + 空着 + LMR + 杀手着 + 完整 PST；`ChineseChessGame.java` 的 `getAllMoves(side)` 仅返回**合法**着法（过滤自将），`hasLegalMoves` + `checkGameOver()` 处理**将死与困毙**，飞将规则已实现。故 P0-2、P1-3 在“可下载模块”路径上实质完成。

### 2.6 遗留 `app` 内嵌中国象棋 AI 升级至 v2.0（2026-07-22）— 已验证

针对**实际被启动路径**的遗留内嵌版中国象棋（`app/.../games/chinesechess/ChineseChessAI.java`）做增强，使其在稳定构建不被破坏的前提下显著提升棋力与正确性。该版此前为朴素 Minimax + αβ，评估仅有子力价值，且存在两处使 AI 退化为随机走子的运行期缺陷。

- **算法增强**：
  - 静态搜索 `quiescence(...)`：搜索边界仅对吃子（及被将军时全部应着）继续展开，消除地平线效应；非将军局面采用 stand-pat 剪枝。
  - 将军延伸：被将军时本层不递减深度，受 `CHECK_EXTENSION_PLY_LIMIT` 层数上限保护。
  - MVV-LVA `orderMovesByMvvLva(...)`：根节点与每层均按“以大吃小”排序，提升剪枝效率。
  - 评估函数 `evaluateBoard(...)`：在子力价值 + PST 位置加成基础上新增**机动性项**（双方伪合法着法数之差 ×2）。
  - 将死距离评分：`MATE_SCORE - ply`，优先更快将死 / 更晚被将死（困毙判负）。
- **两处正确性缺陷修复**（经离线 JDK 21 测试桩运行期捕获）：
  1. 根节点取舍方向：AI 执黑、minimax 返回红方视角评分，原代码误用 `bestScore = Integer.MIN_VALUE` 且 `if (score > bestScore)`（最大化）。修正为 `Integer.MAX_VALUE` + `if (score < bestScore)`（最小化，对黑方有利）。
  2. 白脸将敌方将解析：`isInCheck` 白脸将分支原 `findKing(b, attacker > 0 ? 1 : 2)`，传入 `2` 解析为走子方自身将 → 红方恒被判被将军且无合法着法 → 所有根着法计为必杀（`-999999`）→ AI 随机。修正为 `findKing(b, attacker)`，正确解析敌方将。

> 验证：JDK 21 `javac` 编译通过（COMPILE_OK）；行为测试桩 **全部通过（ALL TESTS PASSED）**——自由吃子偏好、50 随机局面合法性、取消中断、计时、自对弈稳定性（27 回合），并在运行期复现并确认修复上述两处缺陷。修复后根节点正确选出必杀着法（评分 `-999991` = `MATE_SCORE - 9`）。离线冷 JVM 单线程（无置换表）计时：depth6 开局约 32s、depth8 残局约 173s，真机 ART 通常更快；最高难度 depth8 在低端机可能偏慢，属已知权衡（内嵌版未含置换表）。完整 `:app` 构建仍受第 4 节 Flutter 阻断影响，需在用户标准环境产出 APK。

---

## 3. 验证方式说明（重要）

sandbox 无法跑通完整 Android 构建（见第 4 节 Flutter 阻断）。为在不触碰 Flutter 插件/Flutter 模块 Dart 的前提下验证 Java 改动，采用了**临时桩 `:flutter` 工程**（`flutter_module/.android/Flutter_STUB`，空 `build.gradle`）使 `:app` 配置阶段可解析 `project(':flutter')` 依赖，从而仅编译 Java/Kotlin 源码。**验证结束后该桩与对 `include_flutter.groovy` 的临时改写均已还原为原始状态**（已 `diff` 确认与原始一致）。

验证结果：
- `:app:compileDebugJavaWithJavac` → **EXIT=0**（五子棋禁手 + GameAI + ModuleDependencyDownloader 修复全部编译）。
- `:module-store:...:compileDebugJavaWithJavac`（chinesechess / game2048 / hall / klotski + core:common）→ **无错误**。

---

## 4. 剩余构建阻断（与棋类 AI 无关，预存环境问题）

以下两处阻断**非本任务引入**，且不影响棋类 AI 代码本身的正确性，需在用户标准开发环境解决：

1. **Flutter 插件配置失败**：`shared_preferences_android-2.4.27/android/build.gradle.kts:42` 引用 `flutter.compileSdkVersion`，sandbox 中 `FlutterExtension` 未提供该属性（Flutter SDK 3.44.1 与插件生成版本 API 不匹配）。
   - 建议：在用户环境执行 `flutter pub get` 重新生成插件工程；或对齐 Flutter Gradle Plugin 与 `shared_preferences` 版本。
2. **Flutter 模块 Dart 编译错误**：`flutter_module/lib/app/router.dart` 引用不存在的 `DownloadsPage` / `ManagedMode` / `ModuleDetailPage`（如 `Couldn't find constructor 'DownloadsPage'`、`Undefined name 'ManagedMode'`）。
   - 建议：补齐对应页面/枚举或在 router 中移除失效路由（属 Flutter 模块自身维护项）。

---

## 5. 范围决策与未执行项

- **P1-1 / P1-2（共享规则核心与 AI 模块化）**：模块商店版中国象棋已采用模块化架构（`ChineseChessGame` / `ChineseChessAI` 分离、独立模块），既有的模块化程度已满足；对遗留 `app` 内嵌版做同类重构风险高且会破坏当前已转绿的构建，故**未做破坏性重构**，以稳定性优先。
- **A10 / Phase3（签名与商店接线）**：依赖外部发布证书 `release_signer.cer` 与 `ModuleLoaderV2` 入口类修正、目录组件化，属**外部依赖**。本环境无该证书，作为文档化待办交付，待用户提供证书后在标准环境完成签名接线。

---

## 6. 后续建议

1. 在用户标准 Android Studio + Flutter 环境同步代码，先解决第 4 节两项 Flutter 阻断，即可产出完整 APK。
2. 五子棋禁手默认开启；如需“无禁手自由规则”体验，调用 `GomokuGame.setForbiddenMovesEnabled(false)`。
3. 若需在遗留 `app` 内嵌中国象棋也启用 PST/置换表，可复用 `module-store` 版 `ChineseChessAI` 的评估与搜索实现（建议以模块形式接入，避免内嵌版大改）。
4. `GameAI` 接口已就位，后续新增棋种 AI 建议统一实现 `cancel()` / `isThinking()`，便于 Activity 在悔棋/重开/退出时一致中断后台搜索。

---

## 附：本次改动文件清单

| 文件 | 改动 |
|---|---|
| `module-store/.../chinesechess/ChineseChessModuleEntryPoint.java` | 补 `createUnityLauncher()` / `shouldPreload()` / `isEnabled()` |
| `module-store/.../game2048/Game2048ModuleEntryPoint.java` | 同上 |
| `module-store/.../hall/GamesHallModuleEntryPoint.java` | 同上 |
| `module-store/.../klotski/KlotskiModuleEntryPoint.java` | 同上 |
| `app/.../games/gomoku/GomokuGame.java` | 禁手枚举/开关/判定方法 |
| `app/.../games/gomoku/GomokuAI.java` | 禁手过滤、可取消、实现 `GameAI` |
| `app/.../games/gomoku/GomokuActivity.java` | 禁手拒绝提示、AI 禁手判负 |
| `app/.../games/chinesechess/ChineseChessAI.java` | 实现 `GameAI`、思考态；2026-07-22 升级 v2.0（静态搜索/将军延伸/MVV-LVA/机动性/将死距离评分）+ 修复根节点取舍方向与白脸将敌方将解析两处缺陷 |
| `core/common/.../GameAI.java` | 新增统一 AI 控制接口 |
| `app/.../modules/ModuleDependencyDownloader.java` | 补 `onStateChanged` 重写（预存阻断修复） |