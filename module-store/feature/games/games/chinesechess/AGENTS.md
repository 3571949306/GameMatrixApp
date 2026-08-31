# 中国象棋：不可破坏的不变量与验收

运行时真源：`module-store/feature/games/games/chinesechess/src/main/`。模块 v300 已收编全部 AI 实现，宿主侧副本已删除，禁止向宿主拷回。不得通过复制粘贴新增第三份棋类规则实现；规则应优先收敛到逻辑层，再由 UI、AI、联机层调用。

## 不可破坏的不变量

- 所有真实落子（玩家、AI、提示确认、联机消息、联机状态重放）必须最终调用 `ChineseChessGame.commitMove(...)`。
- `makeMoveSafe(...)` 只能是 `ChineseChessGame` 内部的搜索/校验模拟方法，必须保持 `private`；生产调用方不得调用它，也不得手工拼接"落子 + switchSide + recordPosition"。
- AI、联机数据和存档数据一律按不可信输入处理；坐标、走棋方、棋子归属和走后不送将都必须由逻辑层重新验证。
- 坐标契约不得混用：AI 着法为 `[fromRow, fromCol, toRow, toCol]`，游戏逻辑为 `[fromX, fromY, toX, toY]`；转换必须在边界处显式完成并写注释。
- 开局库只能在精确初始盘面启用，每个候选仍需通过棋子规则和送将校验。
- 游戏层和 AI 的局面哈希必须使用同一棋子编码（1..7）、阵营编码、坐标顺序和下一走棋方编码。
- 搜索必须同时有常规深度限制、将军延伸限制和绝对 ply 上限。
- 三次重复与长将必须分开：只有同一方在完整重复区间内每一步都连续将军，才判长将方负。

## 实机与棋力验收

- 完整对局、胜负验证和棋力对比默认必须 `undoCount=0`，不得用"悔棋"或重开回退失误来制造胜利。只有测试悔棋功能本身时才允许点击悔棋，并且该局不得计入棋力或胜率结果。
- 实机日志必须记录难度名称、引擎真实搜索档位、双方控制方式、总 ply、`undoCount`、`restartCount`、AI 原始非法着法数和 fallback 次数；没有这些字段的"胜利"不能作为棋力结论。
- 对比难度时必须使用对等、公开的配置并交换先后手；若一方使用更深搜索、外部提示或不同引擎，结论必须明确限定，禁止称为普通用户对该难度的胜率。
- AI 返回 null 或非法着法而裁判仍有合法着法属于引擎契约故障。生产界面可以有防崩溃 fallback，但必须写入 `AI_CONTRACT_VIOLATION` 日志；发布验收要求原始非法着法数与 fallback 次数均为 0。
- 重开、退出或 Fragment 销毁后，旧搜索和旧动画回调不得向新棋盘提交落子；异步结果必须携带并校验对局代次。
- 大厅传入的 `game_difficulty_index` 只可作为内部难度面板的预选推荐，不得跳过用户可见的难度选择，也不得自动开始中国象棋对局。
- 中国象棋布局由动态模块资源加载器解析，普通 `<Button>` 必须显式设置 `android:stateListAnimator="@null"`，不得隐式依赖宿主 Material 主题中的动画资源；涉及布局、主题或资源 ID 的修改必须安装打包后的宿主 APK 做一次真机/模拟器冷启动验收，只有 Gradle 编译通过不能视为完成。

## 本地验证

```text
python scripts/verify_agent_contract.py
python scripts/verify_chinese_chess.py
python scripts/verify_protected_assets.py snapshot
# 运行测试/普通构建后：
python scripts/verify_protected_assets.py verify
```

注意：`:app:testDebugUnitTest` 会经 `mergeDebugAssets` 间接打包预装模块；窄范围修改优先用上表脚本，只有确需全量验证时才运行宿主 Gradle 任务并前后核对受保护文件哈希。禁止用墙钟耗时（如"必须 > 0ms"）作为正确性断言。

普通 `assembleDebug` / `assembleRelease` 不刷新预装模块。只有发布任务明确需要更新模块 APK 和目录元数据时，才运行 `:app:assembleDebugWithPreinstalledModules`、`:app:assembleReleaseWithPreinstalledModules` 或单独的 `:app:bundlePreinstalledModules`。
