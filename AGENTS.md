# GameMatrixApp Agent 工程规范

本文件适用于仓库根目录及全部子目录。任何 Agent 在修改代码前必须先阅读本文件；若子目录存在更具体的 `AGENTS.md`，两者同时生效，以更具体者为准。

## 1. 开工前必须完成

1. 运行 `git status --short`，确认已有改动并把它们视为用户资产；禁止覆盖、回滚或顺手格式化无关文件。
2. 用 `rg` 定位调用链、测试和构建入口，明确本次允许修改的文件清单。
3. 小范围任务先使用专项验证，不得直接运行可能重打包全部动态模块的宽泛 Gradle 任务。
4. 修改发布资产或生成文件前，先记录其 SHA-256；验证后必须确认未发生非预期变化。

## 2. 源码真源与镜像

- 动态游戏的运行时真源位于 `module-store/feature/games/games/<game>/src/main/`。
- 中国象棋运行时真源为 `module-store/feature/games/games/chinesechess/src/main/`。
- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java` 是宿主兼容镜像。修改中国象棋 AI 时必须同步真源与该镜像；除同步头和包名外，两份实现必须一致。
- 不得通过复制粘贴新增第三份棋类规则实现。规则应优先收敛到逻辑层，再由 UI、AI、联机层调用。

## 3. 中国象棋不可破坏的不变量

- 所有真实落子（玩家、AI、提示确认、联机消息、联机状态重放）必须最终调用 `ChineseChessGame.commitMove(...)`。
- `makeMoveSafe(...)` 只能是 `ChineseChessGame` 内部的搜索/校验模拟方法，必须保持 `private`；生产调用方不得调用它，也不得手工拼接“落子 + switchSide + recordPosition”。
- AI、联机数据和存档数据一律按不可信输入处理；坐标、走棋方、棋子归属和走后不送将都必须由逻辑层重新验证。
- 坐标契约不得混用：AI 着法为 `[fromRow, fromCol, toRow, toCol]`，游戏逻辑为 `[fromX, fromY, toX, toY]`；转换必须在边界处显式完成并写注释。
- 开局库只能在精确初始盘面启用，每个候选仍需通过棋子规则和送将校验。
- 游戏层和 AI 的局面哈希必须使用同一棋子编码（1..7）、阵营编码、坐标顺序和下一走棋方编码。
- 搜索必须同时有常规深度限制、将军延伸限制和绝对 ply 上限。
- 三次重复与长将必须分开：只有同一方在完整重复区间内每一步都连续将军，才判长将方负。

## 4. 受保护文件与副作用

以下文件是发布流程产物，除非任务明确要求构建/发布，否则不得改动：

- `app/src/main/assets/catalog.json`
- `app/src/main/assets/modules.json`
- `app/src/main/assets/modules/*.apk`
- `version.properties`

修改签名、清单、权限、数据库迁移、网络协议、模块加载器或 Gradle 发布任务属于高风险变更：必须说明兼容策略，补针对性测试，并在交付中列出风险。

当前 `:app:testDebugUnitTest` 会经 `mergeDebugAssets` 间接打包预装模块，可能重写上述发布资产。中国象棋窄范围修改优先运行：

```text
python scripts/verify_agent_contract.py
python scripts/verify_chinese_chess.py
python scripts/verify_protected_assets.py snapshot
# 运行测试/普通构建后：
python scripts/verify_protected_assets.py verify
```

只有确需全量验证时才运行宿主 Gradle 任务，并在前后核对受保护文件哈希。禁止用墙钟耗时（如“必须 > 0ms”）作为正确性断言。

普通 `assembleDebug` / `assembleRelease` 不刷新预装模块。只有发布任务明确需要更新模块 APK 和目录元数据时，才运行 `:app:assembleDebugWithPreinstalledModules`、`:app:assembleReleaseWithPreinstalledModules` 或单独的 `:app:bundlePreinstalledModules`。

## 5. 编辑与完成标准

- 使用小而可审查的补丁；禁止无关重命名、全仓格式化或大规模机械改写。
- 不得使用 `git reset --hard`、`git checkout --` 或删除用户未提交工作来制造“干净”状态。
- 修 bug 必须加入能在修复前失败、修复后通过的回归测试；测试应验证状态和不变量，不依赖随机时序。
- 完成前至少运行 `python scripts/verify_agent_contract.py`、相关专项测试以及 `git diff --check`。若全局检查被开工前已经存在的脏文件/行尾问题阻塞，必须对本次新增文件和补丁范围运行 scoped `git diff --check -- <paths>`，并在交付中列出全局基线问题；不得借机改写无关文件。
- 若无法运行某项验证，必须在交付中明确写出未验证项、原因和风险，不得以“应该可以”代替证据。
- 最终说明仅列出本次实际修改、验证结果、遗留风险；不得把工作区原有改动冒充本次成果。

## 6. 中国象棋实机与棋力验收

- 完整对局、胜负验证和棋力对比默认必须 `undoCount=0`，不得用“悔棋”或重开回退失误来制造胜利。只有测试悔棋功能本身时才允许点击悔棋，并且该局不得计入棋力或胜率结果。
- 实机日志必须记录难度名称、引擎真实搜索档位、双方控制方式、总 ply、`undoCount`、`restartCount`、AI 原始非法着法数和 fallback 次数；没有这些字段的“胜利”不能作为棋力结论。
- 对比难度时必须使用对等、公开的配置并交换先后手；若一方使用更深搜索、外部提示或不同引擎，结论必须明确限定，禁止称为普通用户对该难度的胜率。
- AI 返回 null 或非法着法而裁判仍有合法着法属于引擎契约故障。生产界面可以有防崩溃 fallback，但必须写入 `AI_CONTRACT_VIOLATION` 日志；发布验收要求原始非法着法数与 fallback 次数均为 0。
- 重开、退出或 Fragment 销毁后，旧搜索和旧动画回调不得向新棋盘提交落子；异步结果必须携带并校验对局代次。
- 大厅传入的 `game_difficulty_index` 只可作为内部难度面板的预选推荐，不得跳过用户可见的难度选择，也不得自动开始中国象棋对局。
- 中国象棋布局由动态模块资源加载器解析，普通 `<Button>` 必须显式设置 `android:stateListAnimator="@null"`，不得隐式依赖宿主 Material 主题中的动画资源；涉及布局、主题或资源 ID 的修改必须安装打包后的宿主 APK 做一次真机/模拟器冷启动验收，只有 Gradle 编译通过不能视为完成。

## 7. 围棋规则、AI 与实机验收

- 围棋动态实现位于 `module-store/feature/games/games/go/src/main/`，宿主仍保留 `app/src/main/java/com/gamecenter/app/games/go/` 兼容实现。`GoGame`、`GoAI`、`GoView` 和 `GoUiPreferences` 修改时必须同步两份（除包名和同步声明外逻辑一致），并分别验证动态模块与当前宿主路由，禁止只修其中一条启动路径。
- 所有难度和实战必须共用同一个规则提交入口：落子模拟必须包含提子、自杀禁着、简单劫、坐标边界和轮次校验；搜索只能读取棋盘快照，禁止原地修改实战棋盘或 MCTS 树节点。UI 必须把 AI 返回值视为不可信输入，提交拒绝时记录 `GO_AI_CONTRACT_VIOLATION`，不得伪装成停一手。
- AI 只有在没有合法着法或满足可解释且已测试的终局停着策略时才可返回停一手；禁止用固定随机概率停着。普通（中等）难度至少应识别提子、救一气棋、避免自打吃、连接/切断与角边效率，困难档不得仅靠增加损坏算法的墙钟时间区分棋力。
- 9 路围棋统一采用中国面积计分：黑白各自的盘上棋子加仅被该方包围的空点，白方贴目保留 `6.5`；提子数只用于对局信息展示，不得在面积分中重复加入，胜负、UI、回放和测试必须读取同一个 `Score` 结果。
- 大厅的 `game_difficulty_index` 只可预选难度，默认无推荐时选择普通（2/4），难度面板必须保持可见且不得自动开局。默认使用增强棋盘，并以 `game_go_ui/board_style_v1` 持久化简洁模式；宿主与动态模块必须共用相同偏好语义。
- AI 思考期间玩家不得停一手或重复提交；返回菜单、重开、结束和销毁必须取消搜索并递增对局代次，回写前再次校验代次、白方回合和游戏未结束。程序化创建的动态模块 `Button` 必须调用 `setStateListAnimator(null)`，避免宿主主题资源 ID 冲突。
- 围棋专项回归优先运行 `python scripts/verify_go.py`。规则测试至少覆盖提子、自杀、越界、即时劫、隔手可回、两次停着和面积计分；AI 测试必须覆盖搜索不改输入、四档原始非法着法为 0、普通档无随机停着和旧回调隔离。棋力或完整对局验收记录 `difficulty`、真实策略/预算、总 ply、`undoCount=0`、`restartCount`、raw illegal、rejected commit 与 fallback；围棋没有悔棋按钮，不得通过重开筛选有利对局。
- 模拟器自动验收可通过 `app/src/debug/AndroidManifest.xml` 直接启动围棋；该入口只能存在于 Debug source set，Release 的 `GoActivity` 必须保持 `android:exported="false"`。

## 8. 模块隔离与目录信任策略（P1）

- 模块装载判定唯一真源为 `core/module-host ModuleLoader`；宿主侧 `com.gamecenter.app.modules.ModuleLoader` 仅是兼容门面，不得再新增第二套 Dex/资源/校验实现。
- 隔离策略：`catalog fileName` 为空 → 视为宿主内嵌代码（允许宿主 classloader 直载，随宿主发布）；`fileName` 非空（含预装内置 APK）→ 一律走外置 `DexClassLoader` 加载，文件缺失或清单缺 SHA-256 时必须拒绝，**禁止回退宿主陈旧副本**。
- 外置模块与预装内置 APK 装载必须同时通过：非空 SHA-256+大小校验、`ModuleSignatureVerifier` 发布证书钉扎（`core/security/res/raw/release_signer.cer`）。清单缺 SHA 或校验失败走 `onVerifyFailure` 清理回调，不允许"免检装载"。
- 动态模块资源加载依赖运行时探测 `AssetManager.addAssetPath` 私有 API 可用性；探测失败时记录 `MODULE_RESOURCE_FALLBACK` 并以宿主资源降级运行，不做任何绕过。
- 目录 Ed25519 签名默认开启（`enableCatalogSignature=true`）：已配置 `catalogEd25519PublicKeys` 时为强验证模式；未配置公钥的 release/stable 发布构建必须失败，本地开发构建仅警告并以兼容模式运行（`CATALOG_SIGNATURE_TRUSTED=false`）。
- 交付前必须核对 `version.properties`：默认 `autoBumpVersion=true` 会在每次 `assembleDebug` 后自动递增 versionCode 并回写，属构建系统既定行为；任何"成功构建"都必须记录当前 versionCode，避免把自动递增误报为本次改动。如不需要自动递增，使用 `-PautoBumpVersion=false` 构建。

## 9. 分支与发布规范（强制）

- 主分支 `main` 受分支保护：CI（lint-and-test / build / gitleaks）全绿才允许合入。
- 所有改动先推子分支 `feature/**` 或 `hotfix/**`（推送即触发 CI），CI 绿灯后才可合并回 `main`。
- 完整规范（分支模型、提交流程、发布流程、禁止上传清单）见本地文件
  `docs/开发规范-分支与发布.md`（该文件不上传 GitHub，任何 agent 开工前必读）。
