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
