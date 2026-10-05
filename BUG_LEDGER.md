# BUG LEDGER — 缺陷账本（回归防护专项 §六）

> 规则（verify_ratchet.py 机器执行）：**每个真人可发现的 bug 必须登记，且守卫字段不得为空**。
> 守卫 = 防止同类回归的机器检查（测试名 / verify 脚本 / 服务端配置）。
> `PENDING(...)` 表示守卫待补——计入"无守卫"基线，补上后计数下降（棘轮奖励改进）。
> 本文件必须留在仓库根：`docs/` 整目录被 .gitignore 排除，CI 读不到。

## 条目格式

```
## BL-NNN 一句话症状
- 日期: YYYY-MM-DD
- 类别: 静默失败 | 并发时序 | 窄修复回归 | 配置 | 设备现实
- 根因: 为什么发生（不是"哪里改了"，是"为什么会错"）
- 守卫: <测试/检查名>（待补的写 PENDING 加计划项；不可测类别注明原因）
```

---

## BL-001 parseModulesArray 对 Catalog V2 对象格式静默返回 null
- 日期: 2026-08-30
- 类别: 静默失败 + 窄修复回归
- 根因: 为兼容一种清单格式加解析分支时，未测另一种格式；`JSONArray(jsonStr)` 抛异常被吞→返回 null，调用方（`bundledVersionCodeOf`/`loadModuleList`/SP 缓存）全部静默降级，不崩溃不报错，真人升级模块时才暴露
- 守卫: ModuleListParsingContractTest（双格式等价 + 真实清单全量解析 + 坏输入响亮失败；parseModulesArray 已开 internal 供测试）

## BL-002 Splash 预装安装与核心预加载并发竞态
- 日期: 2026-08-30
- 类别: 并发时序
- 根因: 同一线程池并发跑 install（提取+事务安装 31 模块）与 preload（load 核心模块），同目录读写窗口期竞态。**单测原理上测不到**，只有真机首启时序才踩
- 守卫: PENDING(§六 真机冒烟套件——冷启动黄金路径 + logcat ERROR 扫描)

## BL-003 /admin/feedback 公网暴露
- 日期: 2026-08-30
- 类别: 配置
- 根因: nginx location 未加访问控制，token 轮换后入口仍公网可达
- 守卫: 服务端 nginx `07-hk-update-uk.conf` allow 127.0.0.1/::1/100.64.0.0/10 + deny all（公网 curl 实测 403）；PENDING(§六 冒烟——公网 403 探针)

## BL-004 SourceTestStore.append 非原子写
- 日期: 2026-08-30
- 类别: 静默失败
- 根因: 测速中断可留截断 JSON，下次读取解析失败静默丢数据
- 守卫: `SourceTestStoreTest.append interrupted after temp write leaves old target readable`（Robolectric 接缝在 tmp 写完、rename 前确定性抛出中断，并断言截断 tmp 不改变旧目标；随后验证真实 append 清理 tmp；不覆盖 renameTo 失败后的 direct-write 兜底中断窗口）

## BL-005 verify_security_clauses 因 release_signer.cer 不入库而 CI 假红
- 日期: 2026-08-30
- 类别: 配置（门禁自身缺陷）
- 根因: 机检检查了凭据类文件实体，而该文件被 .gitignore 排除（设计如此）——检查器假设与仓库策略冲突
- 守卫: 9e17bd4 改为检查证书资源接线（ModuleSignatureVerifier 引用存在性），verify_security_clauses.py §8.3 项

## BL-006 客户端 feedback.url 仍指死域 tcp0053.shop
- 日期: 2026-08-30
- 类别: 配置
- 根因: 死域清理只做了 catalog 产物，local.properties 的 feedback.url 与后端 9011 监听是独立链路，未随清
- 守卫: PENDING(§六 真人反馈回路修复)

## BL-007 镜像插队三重反转：测速胜者沉底下载源列表
- 日期: 2026-08-30
- 类别: 窄修复回归 + 静默失败
- 根因: 分发架构 v2 插队实现 `asReversed()` 收集 + `inserted.reversed()` + 逐个 `add(0)`——前两次反转抵消，`add(0)` 自带第三次，净效果 = 镜像顺序反转，胜者沉底。代码注释"顺序保持"是错的，v4 审查被该注释误导关闭了"修插队顺序"项。用户不崩溃、只是永远从慢节点下载——只有真人能感知
- 守卫: ModuleDownloadUrlListContractTest（胜者首顺序 + 双重去重 + fallback 尾追加 + 外来主 URL 直通；buildDownloadUrlList 已提取为纯函数接缝）

## BL-008 androidtest CI job 从未跑过测试（死门禁）
- 日期: 2026-08-31
- 类别: 配置（门禁自身缺陷）
- 根因: androidtest job 缺 "Set up Flutter + flutter pub get" 步骤，settings.gradle:130 硬检查直接 BUILD FAILED in 6s——每次"失败"都发生在配置阶段，16 个测试类一次都没执行过；continue-on-error 把"从未运行"伪装成"运行了但失败"，两周观察期将观察不到任何真实信号
- 守卫: ci.yml androidtest job 已补 Flutter bootstrap 步骤（PR #47）；两周观察期以真实测试执行为准

## BL-009 导航动态化+i18n 改造断裂 AiIntegrationTest（死测试）
- 日期: 2026-08-31
- 类别: 窄修复回归（测试侧）
- 根因: 底部导航从静态 R.id.navigation_ai 改为运行时分配 menuId（BottomNavigationManager.refreshNavigation 按目录顺序 1..n），改造未同步 androidTest；因 BL-008 死门禁，androidTest 从未在 CI 编译，断裂无人发现——#47 复活门禁后第一个真实信号就是它
- 守卫: AiIntegrationTest 重写为按标题匹配选中（与真实点同走 onItemSelected→navigateTo 路径）；androidtest job 每次 push 编译执行

## BL-010 导航恢复将 VPN 清单改回失效元数据和已废弃下载源
- 日期: 2026-09-05
- 类别: 配置 + 静默失败（P1）
- 根因: BottomNavigationCatalog 从已验签 Catalog 缓存恢复清单后，后台可直接调用 `registerLocalFallbackIfNeeded(null)`；旧兜底无条件覆盖可信 vpn，fileSize/sha256 仍为 640752/fe9c62...，而真实 assets 为 1134486/390bf509...，下载后大小/SHA 校验必失败。旧无 Context 兜底的 downloadUrl 还指向 tcp0053.shop；有 Context 时则应保留 `modules.json` 自身的下载地址。
- 修复边界: 无 Context fallback 使用配置的 `BuildConfig.DOWNLOAD_BASE_URL + fileName` 策略；有 Context 的 assets 清单保留其自身 URL。两者配置不一致仍属于构建/发布配置风险，不能表述为已完全解决死域问题。
- 守卫: `ModuleManagerTest.testVpnFallbackMatchesShippedMetadataAndConfiguredUrl`、`testTrustedVpnManifestIsNotOverwrittenByFallback`、`testNullFallbackRegistersVpnWhenManifestTableIsEmpty`

## BL-011 模块更新失败误回滚健康 current，旧安装首次更新被快照协议阻断
- 日期: 2026-09-06
- 类别: 静默失败 + 窄修复回归
- 根因: 更新管理器把下载/事务安装阶段的 onError 当成运行时加载失败，回滚会把仍健康的 current 降回 last_good；同时旧安装没有 current manifest 快照时无法建立可信 last_good，若直接覆盖又会丢失可审计的旧文件归属。
- 守卫: `ModuleUpdateManagerTest.downloadFailureDoesNotRollbackHealthyCurrent`、`TransactionInstallerStateMachineTest.legacyCurrentCanBeQuarantinedWithoutInventingLastGoodMetadata`、`ModuleManagerRollbackProtocolTest`

## BL-012 塔组开始按钮立即清掉新创建的剧情浮层
- 日期: 2026-09-17
- 类别: UI 调用时序
- 根因: 塔组开始按钮先调用 startLevel 创建剧情引子，再执行 clearOverlay，把新剧情当旧塔组一起清掉。
- 守卫: `TdUiRegressionTest.deckStartKeepsStoryVisibleUntilPlayerContinuesToBattle`（真实开始/出战/开战按钮、剧情正文与暂停状态；修复前失败、修复后通过）

## BL-013 旧混合难度用时和单关通关旗标被误认为分难度全战役成绩
- 日期: 2026-09-17
- 类别: 存档迁移语义
- 根因: 旧 td_time_* 存的是所有难度混合最快成绩，不能当普通难度用时；旧 td_easy_done/td_hard_done 任意单关获胜即置真，不能证明当前战役全清。
- 守卫: `TdSaveManagerTest.legacyMixedDifficultyTimeDoesNotBecomeNormalTimeOrBlockFirstNormalWin`、`legacyMixedDifficultyTimesDoNotCompleteNormalCampaign`、`legacySingleLevelClearFlagsDoNotCompleteCampaign`、`legacySingleLevelClearFlagsDoNotBackfillDualCrown`、`legacyUnlockedDualCrownIsPreservedWithoutBeingAwardedAgain`（保留旧成绩和已得成就；修复前失败、修复后通过）

## BL-014 更新预检失败破坏既有 last_good 快照
- 日期: 2026-09-17
- 类别: 安装事务元数据时序
- 根因: 事务尚未备份就预写当前版本的 last_good 元数据，目标路径冲突或新包校验失败时文件仍是旧备份，元数据却已指向新备份。事务之后根据实际文件提交快照，拒绝阶段保留原有效记录。
- 守卫: `ModuleManagerRollbackProtocolTest` 的 `downloaded update rejected before backup preserves existing rollback and reports failure`、`external package verification failure preserves the previous rollback snapshot`、`failed current move records the backup actually written by the transaction`（覆盖备份前后失败时序，修复前失败、修复后通过）

## BL-015 Unity 内容卸载遗留外层包和已安装状态
- 日期: 2026-09-17
- 类别: 运行时与模块状态一致性
- 根因: Unity CONTENT 仅删除解压目录便返回成功，外层 current ZIP 和安装状态未清理；外层卸载亦遗留可信 last_good。成功卸载后同步清理外层状态及属于本模块的可信备份。
- 守卫: `SecureArchiveInstallerTest.unity content uninstall removes runtime outer packages and installed state`（覆盖 runtime/current/last_good/SP/缓存重建及保留无归属邻近文件；修复前失败、修复后通过）

## BL-016 动态游戏入口在回滚后仍用目录新版本定位和校验旧包
- 日期: 2026-09-17
- 类别: 已安装版本与可用更新版本混用
- 根因: DynamicGameActivity 按可用清单 fileName 提前判断文件缺失，并直接将目录 manifest 交给 ModuleLoader。回滚到 v1、目录仍为 v2 时，不同名包误报未安装，同名包用 v2 完整性数据校验 v1。入口改为统一经 ModuleManager 选择已安装快照。
- 守卫: `DynamicGameActivityRollbackTest` 的同文件名/不同文件名冷启动回归（真实 Activity 生命周期和核心完整性/签名校验边界；修复前失败、修复后通过；实际 APK 动态执行另需设备验收）

## BL-017 ZIP 重复安装或修复破坏两层回滚版本一致性
- 日期: 2026-09-17
- 类别: 安装幂等性与跨层失败补偿
- 根因: 同 SHA 快路再次解压会把 runtime current v2 推成 last_good，而 outer last_good 仍为 v1；修复失败时 Facade 又假定发生了新 outer 事务并只回滚外层，令两层版本分裂。
- 修复: 以已校验外层备份重建 runtime last_good；完整目录比对后健康重装直接复用，目录切换失败逆序补偿。Facade 仅在本回调无安装事务、错误为 atomic_switch_failed、规范 current 仍通过 SHA/大小验证时保留旧安装；新事务失败与归档语义拒绝仍正常恢复。
- 守卫: `SecureArchiveInstallerIdempotencyTest` 四项和 `ModuleCoreFacadeArchiveRecoveryTest` 三项，覆盖真实快路、外层新提交、入口缺失、同版本不同内容、同长度损坏及四个 rename 失败点；核心失败先红后绿。统一模块专项156项通过，API24仅完成静态兼容检查，未宣称24设备实测。

## BL-018 连续下一关跳过新塔选择且漏记对局数
- 日期: 2026-09-17
- 类别: 游戏进度与结算流程
- 根因: 仅塔组开始按钮记对局数，重玩和下一关直接创建游戏却漏记；胜利后下一关直接沿用旧塔组，新解锁塔无法在连续流程使用。
- 修复: 统一在 startLevel 建立游戏后记局；下一关先打开对应难度的塔组选择，点击开始才创建并计数。
- 守卫: `TdSessionStatisticsTest` 三项，真实菜单/开始/重玩/结算下一关路径；下一关明确验证 FAN 与 ROCKET 解锁可选、选塔阶段不增局数，开始后恰好新增一局。关键断言修复前失败、修复后通过。

## BL-019 第二章双入口叙事与关卡路线不符
- 日期: 2026-09-17
- 类别: 内置游戏内容一致性
- 根因: main_006/007/009/012 的关卡文案承诺分流，但数据仍只有一条路线；多路线地图入口绘制也统一使用同一个“入”，不能与波次预告的路线编号对应。
- 修复: 四关新增独立右下入口并在 (3,2) 汇入原路，共用原终点；按整波分配两路并保持原金币、生命、波次强度和原路线。多入口绘制 1/2 等编号，单入口保留原标记。
- 守卫: `TdChapter2RoutesTest` 二十项验证路线拓扑、原有参数、波次分配及实际生成/移动；`TdUiRegressionTest.multipleEntrancesRenderNumbersMatchingWavePreviewAndResetOnRebind` 通过真实绘制检查编号与重新绑定。关键断言修复前失败、修复后通过。Android 15 的 ShippedTdModuleTest 验证第六关真实动态 APK 情报、剧情、双路线和触摸建塔；使用临时进度 fixture 并恢复存档，不代表自然闯关验收。

## BL-020 塔防待合成源残留到新对局，普通点选误消耗新塔
- 日期: 2026-09-17
- 类别: 游戏对局状态生命周期
- 根因: mergeSource 保留旧局塔对象，而合成只按其旧坐标查当前棋盘；玩家旧局选择合成后重玩或重新选关，新局同坐标建塔，再普通点选另一塔就会意外合成。
- 修复: 新局、选关和视图销毁清理待合成源；点选合成前要求来源对象仍属于当前棋盘。取消退出确认等本局暂停路径保持原行为。
- 守卫: `TdUiRegressionTest.pendingMergeDoesNotConsumeNewTowersAfterSettlementRetry`、`pendingMergeDoesNotConsumeNewTowersAfterReturningThroughLevelSelect`，真实塔牌、棋盘触摸、自然败局、结算重玩与重新选关；两项先红后绿，完整塔防227项通过。来源身份检查是防御性保护，未宣称另有已复现的同局替换缺陷。

## BL-021 推箱子首关与第三关地图无解
- 日期: 2026-09-17
- 类别: 内置关卡可玩性
- 根因: 首关箱位/墙体令目标无法被推入，第三关缺少上方绕行位而锁死箱群；原先没有完整关卡合法步序验收。独立求解及未改动真实引擎无剪枝穷举分别穷尽27与537个状态，确认无解。
- 修复: 首关仅调整四格，保留7×5双箱双目标及封闭边界，形成DDLU入门解；第三关只将内部(1,1)墙改为地板，保留三箱三目标。其它8关地图保持原样。
- 守卫: `SokobanLevelContentTest` 参数化10关×结构/公开movePlayer合法通关重放，共20项；第1与第3关合法步序在修复前失败，修复后全部通过。独立解序列不承诺最短；Android实际按钮通关单独验收。

## BL-022 推箱子棋盘挤压底部操作按钮
- 日期: 2026-09-17
- 类别: 游戏布局与触摸可达性
- 根因: 棋盘忽略父容器的高度约束，游戏内容未按剩余空间分配，菜单只隐藏内部面板；Android 15 的360×640dp窗口中撤销按钮被挤至19.5dp且文字裁切。
- 修复: 整个选关滚动容器与游戏面板互斥显隐；方向键与底部操作保留所需高度，棋盘占剩余空间并遵守EXACTLY/AT_MOST，底部三按钮均分宽度且最小48dp高。
- 守卫: `SokobanViewMeasureTest` 三项（两项先红后绿）及 `ShippedSokobanModuleTest` 全部七按钮的48dp和完整可见矩形断言。旧包设备红测明确19.5dp，新包在固定emulator-5558通过，两游戏共5项设备测试通过；真实窗口PixelCopy截图复核无裁切。按钮测试执行真实listener并验证布局区域，不宣称硬件触摸事件端到端测试。

## BL-023 狙击塔操作栏显示可切换策略但实战固定攻击强敌
- 日期: 2026-09-17
- 类别: 游戏操作提示与实际机制不一致
- 根因: 狙击塔的引擎选敌固定STRONG，操作栏却显示其默认FIRST字段并允许循环，玩家会看到不生效的目标优先级。
- 修复: 狙击塔操作栏固定显示强敌并禁用切换，重新选中普通塔时恢复其真实模式和可操作状态；不改变引擎选敌或数值。
- 守卫: `TdUiRegressionTest.sniperTowerOpsShowsFixedStrongTargetAndRejectsUserCycling`（文案先红后绿、禁用触摸不改模式）及 `ordinaryTowerOpsRestoresTargetCyclingAfterSelectingSniper`（普通塔真实循环），使用第四关正常预算和真实选卡/建塔操作。完整塔防229项通过；新设备用例另行验证。

## BL-024 2048终局退出重进恢复旧的未结束棋盘
- 日期: 2026-09-17
- 类别: 游戏存档生命周期
- 根因: onPause仅保存未结束游戏，终局统计后不覆盖旧auto；下一次创建Fragment便读取较早的未结束存档，无提示回退棋盘。
- 修复: 存在游戏实例时保存当前棋盘，包括终局；沿用已有board/score/gameOver格式，不改引擎、重玩或正常存档流程。
- 守卫: `Game2048SaveLifecycleTest` 三项验证真实右滑终局再重建、正常局恢复、重玩清auto及新棋盘恢复，终局项先红后绿。旧包Android15上 `Shipped2048ModuleTest` 真实UiAutomation右滑后终局再重开亦失败，保留窗口截图；用合法SaveManager前置局面，不声称自然游玩到终局。新包设备验收单独记录。

## BL-025 推箱子选关按钮触摸高度不足
- 日期: 2026-09-17
- 类别: 选关触摸可达性
- 根因: 选关按钮依赖主题默认高度，Android15实际仅44dp，未达到已有游戏操作按钮采用的48dp。
- 修复: 所有关卡Button显式设置48dp minimumHeight和minHeight，不调整地图、棋盘缩放或方向键。
- 守卫: `ShippedSokobanModuleTest.shippedFiveNewLevelsCanBeSelectedAndWonThroughRealButtons` 在旧布局第11关按钮176px/44dp处失败，新布局在固定emulator-5558完成11–15逐关实际按钮回放并通过完整可见48dp检查；三游戏共9项设备测试通过。Sokoban33项单元测试仍通过。

## BL-026 扫雷手势结束后仍插旗，切换难度时旧回调越界
- 日期: 2026-09-17
- 类别: 游戏触摸与对局生命周期
- 根因: onTouchEvent在棋盘坐标之外提前返回，UP/CANCEL未移除长按；重开与切换难度也未清理旧格坐标，困难(15,29)回调会访问简单9×9新数组。
- 修复: 单个长按Runnable由cancelPress精确移除，手势终止、移出起始格、暂停、停止、换难度、重开和移除View均清理；浮点边界先于整型行列转换，避免边缘空白误中第0格。
- 守卫: `MinesweeperTouchLifecycleTest` 原网格外抬手、取消、困难转简单三项先红后绿，其中明确复现ArrayIndexOutOfBoundsException；另覆盖正常长按插撤、移出后返回、暂停恢复、重开500ms重新计时和首击安全，完整12项通过。设备验证另行记录。

## BL-027 扫雷插旗与撤旗后剩余雷数不更新
- 日期: 2026-09-17
- 类别: 游戏状态显示
- 根因: 顶部计数仅在开局刷新，View旗数变化没有通知Fragment，导致玩家看到固定初始雷数。
- 修复: 旗数变化和开局归零发布专用回调，由Fragment复用updateMinesDisplay即时更新，不改雷分布或胜败规则。
- 守卫: `MinesweeperFlagDisplayTest` 三档真实Fragment长按手势检查同一顶部TextView的10→9→10、40→39→40、99→98→99，三项均先红后绿；不反射调用插旗或替换监听器。

## BL-028 扫雷难度按钮触摸目标不足48dp
- 日期: 2026-09-17
- 类别: 游戏操作可达性
- 根因: 三档难度Button使用主题默认高度，真实Android15界面的48dp断言失败。
- 修复: 三档难度与重玩Button统一明确48dp最小高度，保持棋盘剩余空间与布局结构。
- 守卫: `ShippedMinesweeperModuleTest` 的真实UiAutomation按钮操作前完整48dp区域检查在旧包失败；新包三档插撤旗/重玩/安全首击及网格外抬手和取消全部通过。固定emulator-5558四游戏13项通过，真实窗口截图复核完整；15项扫雷单测仍通过。

## BL-029 管道工以隐藏旋转值判通关，未判断可见管道连通
- 日期: 2026-09-17
- 类别: 内置游戏规则与关卡生成
- 根因: isAllCorrect逐格比较隐藏targetRotations，导致可见断路也能赢、相同直管半圈后算错、闲置管朝向阻碍胜利；生成的弯管目标朝向也与显示字形不符。旧管形仍可通过旋转解开，错误并不表示所有棋盘无解。
- 修复: 按可见字符对应端口进行双向BFS，左上到右下连通即可，允许支路与闲置开口；生成器按同一端口约定保存可解见证，不参与胜负。结算要求当前局活动且连通，重复调用不再计分推进。
- 守卫: `PipelineConnectionTest` 原15项先红后绿，另补纵向T与上入右出L区分，共17项；独立字符端口验证真实公开旋转/判断/结算，4/5/6棋盘各12固定种子由真实生成器产生并独立求解回放。样本不宣称穷尽，测试夹具与自然生成分别标明。

## BL-030 管道工起终点不明，检查颜色与旋转后局面不一致
- 日期: 2026-09-17
- 类别: 游戏提示与反馈
- 根因: 开始后只显示关卡号，没有明确角点角色；检查颜色依据隐藏答案且跳过十字，旋转后仍残留旧红绿结果与失败提示。
- 修复: 持续显示左上起点→右下终点，并更新角格无障碍说明；所有非空格按起点可达性着色，每次旋转清除旧颜色和失败状态，保留可见管道字形。
- 守卫: `PipelineUiConnectionTest` 端点、可达着色、旋转清反馈与下一关重建四项先红后绿，360×640dp下4/5/6棋盘及48dp操作区第五项保持通过。全部通过真实Fragment/Button执行；确定性局面只替换生成输入，未替换连通或结算逻辑，设备验收另行记录。

## BL-031 管道工开始按钮触摸高度不足48dp
- 日期: 2026-09-17
- 类别: 游戏操作可达性
- 根因: 主题默认开始Button实际352×176px，在Android15/4倍密度仅44dp高；Robolectric默认主题的布局通过不能替代设备主题尺寸验证。
- 修复: 操作和管道按钮显式保留至少48dp宽高，不改变网格尺寸算法或缩字。
- 守卫: `ShippedPipelineModuleTest` 旧包设备明确在176px<192px断言失败，新包通过所有可见Button48dp/完整区域检查，并以真实随机1–5关和实际Button listener完成4/5/6棋盘旋转、胜场精确+5与重结算保护。五游戏15项设备通过；这是listener回放，不宣称系统触摸端到端。

## BL-032 俄罗斯方块首次暂存可重复使用且暂存预览消失
- 日期: 2026-09-17
- 类别: 内置游戏规则与反馈
- 根因: 空槽HOLD调用spawnPiece重置本回合使用标志，允许同一落块再次交换；HUD将已使用HOLD误作隐藏暂存内容条件。
- 修复: 空槽取下一块和占用槽交换后均锁定HOLD，直到真正锁块进入下一回合；横竖屏保留实际暂存块预览。
- 守卫: TetrisHoldLifecycleTest五项真实软控触摸与Canvas命令回归；首次/二次HOLD及两种布局预览先红后绿，硬降解锁与占用槽交换通过。预览测试修正Robolectric默认Paint style为空的测试环境差异后重新跑旧条件红测，非以环境失败充当缺陷证据。旧Android15实际UiAutomation也复现空HOLD未锁，新包设备另行验证。

## BL-033 俄罗斯方块取消难度确认仍改变难度和偏好
- 日期: 2026-09-17
- 类别: 游戏选择与确认
- 根因: 难度按钮在弹出确认前调用setDifficulty，提前改变重力档位、偏好与按钮高亮。
- 修复: 只在确认重开时应用新难度，取消保留完整当前局与原有选择。
- 守卫: TetrisDifficultyConfirmationTest两项真实AppCompat确认/取消回归先红后绿，覆盖View难度、偏好、高亮、棋盘、队列与分数；旧Android15实际UiAutomation取消亦复现2变4。资源包加载故障已单独修正后重跑行为红测。

## BL-034 俄罗斯方块确认弹窗期间继续下落及新局继承旧暂停
- 日期: 2026-09-17
- 类别: 游戏回合生命周期
- 根因: 确认框未暂停View；确认新局只启动View而未清理Fragment手动暂停标志，后续回前台无法恢复。
- 修复: 弹窗持有明确当前View与先前暂停状态，取消/Back/dismiss统一恢复；确认开启新局并清除旧暂停；后台及销毁不恢复旧View，延迟启动绑定仍存活的创建View。
- 守卫: 活动局等待3秒内容变化、手动暂停后新局前后台无法恢复两项先红后绿；另验证原手动暂停保留、Back、后台关闭、带弹窗前后台、销毁及立即离开，共10项真实Fragment难度/模态测试通过。

## BL-035 俄罗斯方块难度触区过小且功能按钮遮挡游戏控制
- 日期: 2026-09-17
- 类别: 内置游戏操作与可读性
- 根因: 难度按钮固定32dp；浮动设置按钮覆盖底部DROP触区，竖排重开按钮同时压住NEXT预览文字。
- 修复: 难度与功能按钮统一48dp，三个功能按钮排列在HUD上方原有预留行，提供操作说明，移开全部五个落块控制区。
- 守卫: ShippedTetrisModuleTest在旧Android15包明确复现328×128px难度按钮不足192px以及DROP/设置交集152×120px；新包三个功能按钮48dp、两两不交叠且与五个软控完整矩形无交集。真实UiAutomation中心点完成HOLD/硬降，PixelCopy截图复核HOLD内容、NEXT文字和底部控制完整。六游戏21项中20项全程通过，另1项新局分数断言计时修正后同宿主定点重测通过，未称单次21项全绿。

## BL-036 俄罗斯方块落点预览只显示锚点一格
- 日期: 2026-09-17
- 类别: 游戏落点预览
- 根因: onDraw只在pieceX/ghostY调用一次drawGhost，没有绘制当前旋转形状的其余占格，I/O等实际四格与预览不符。
- 修复: 按当前方块与旋转的四个占格绘制完整落点，保留关闭预览、已触底不叠加预览与负行裁剪行为。
- 守卫: TetrisGhostPreviewTest横/竖屏两项均先红(4vs1)后绿，自然首袋七种块各四方向以独立几何旋转/碰撞算法计算落点，并逐块真实hardDrop核对新增四格；第三项验证关闭与触底。测试为绘制命令而非设备像素，旧实际窗口也已留存单格预览图，修复后设备验收另记。

## BL-037 俄罗斯方块消行时误删上方保留堆叠
- 日期: 2026-09-17
- 类别: 游戏消行与棋盘状态
- 根因: executeLineClear对删除行仍递减writeRow，导致上方保留行复制到自身后被清零，单行和多行消除均丢失原堆叠。
- 修复: 自底向上仅对保留行递减目标位置，复制完整行后统一清空顶部空位，保留下方非满行和颜色。
- 守卫: TetrisLineClearTest以公开restoreSnapshot加载明确合法存档夹具，再经真实DROP触摸和动画时钟检查单行、相邻双行、不相邻双行、四行；四项均先红后绿，无消行原棋盘+四格保持通过。全盘/颜色/剩余格数、独立积分、消行数与NEXT恰好消费一次均核对，不宣称自然玩到该残局。

## BL-038 俄罗斯方块消行动画期间重复操作与暂停改变棋盘
- 日期: 2026-09-17
- 类别: 游戏消行阶段与输入边界
- 根因: 方块已锁定但等待动画时仍能DROP/HOLD，重复计分或提前换队列；暂停只移除重力，动画仍会压实并生成下一块。
- 修复: 移动、软降、硬降、旋转与暂存共享可操作状态检查；待消行时自动重力跳过锁块并继续排程。暂停保留动画剩余时长，恢复后完成一次压实；待消行方块不再当活动块重绘。
- 守卫: 同一动画内重复DROP先红132→1032、HOLD提前换块以及暂停1秒后棋盘变化三个回归先红后绿；完整8项消行回归与3项Ghost、5项Hold、10项难度/生命周期共26项通过。高速重力专项与设备夹具验收另行补充。

## BL-039 俄罗斯方块自动下落错误发放手动软降积分
- 日期: 2026-09-17
- 类别: 内置游戏计分
- 根因: gravityTick复用softDrop，未操作时也按每格1分计入分数并触发手动音效。
- 修复: 自动重力复用不计分的移动逻辑，保留触底锁块与排程；手动软降每格1分、硬降每格2分不变。
- 守卫: TetrisScoringTest自动重力回归先红41→42后绿保持41，手动软降加硬降独立计分继续通过；全部38项Tetris单测0失败/错误/跳过，签名模块构建通过。实际安装包设备验收另记。

## BL-040 俄罗斯方块完美消除奖励始终漏算
- 日期: 2026-09-17
- 类别: 内置游戏消行奖励
- 根因: 锁块后在满行尚待动画删除时检查全盘为空，满行自身令Perfect Clear条件始终失败。
- 修复: 检查待消除行之外的格子是否全空，使用既有奖励表及当前计分等级；不改变Combo或Back-to-Back公式。
- 守卫: TetrisScoringTest合法保存夹具单行完美消除先红136后绿936；等级2四行并延续Combo/B2B先红2669后绿6669；同局保留上方锚点不发奖励仍为2669。测试通过公开恢复和真实View落块执行，未以自然整局完成表述。

## BL-041 俄罗斯方块点击暂停遮罩继续后回前台再次暂停
- 日期: 2026-09-17
- 类别: 游戏暂停与继续
- 根因: 遮罩仅恢复View，Fragment的手动暂停标志与按钮图标仍保留旧状态，后续生命周期恢复被拦住。
- 修复: 遮罩与暂停按钮共用resumeCurrentRound，清理手动暂停并同步图标；回调绑定创建时的View，避免旧View影响新局。
- 守卫: TetrisPauseAndRulesTest真实遮罩继续、图标与前后台回归先红后绿；旧安装包Android15通过真实暂停按钮和棋盘触控亦复现前后台后重新暂停，红日志保留，新包设备验收另记。

## BL-042 俄罗斯方块阅读设置和规则时游戏仍继续
- 日期: 2026-09-17
- 类别: 游戏弹窗与回合生命周期
- 根因: 设置和规则没有接入暂停、前台恢复与View销毁逻辑，弹窗覆盖期间仍下落，移除游戏后弹窗也未关闭。
- 修复: 难度确认、设置、规则共享单一当前弹窗；设置转规则先转移归属并沿用首层暂停状态，父框dismiss不提前恢复；最终关闭仅恢复同一存活且原先活动的局面。
- 守卫: TetrisPauseAndRulesTest六项中五项行为先红后绿，手动暂停保留原本通过，覆盖等待、设置转规则、后台关闭、回前台与销毁；原难度确认10项全部保持通过。旧安装包设备实等1600ms复现y0→2/score0→2，新安装包完整设备回归另记。

## BL-043 俄罗斯方块贴墙触底时拒绝合法顺时针旋转
- 日期: 2026-09-17
- 类别: 游戏旋转与碰撞
- 根因: 八转向偏移表按紧凑列表存储，旧from*2+to误选别的转向；JLSTZ右墙触底拒转，I也会采用错误首个合法候选。
- 修复: 显式映射八个相邻转向到现有表的0..7行，保留I独立向量、每行原值与候选顺序。
- 守卫: TetrisRotationKickTest五种JLSTZ实际绘制旋转软键由r0/x7/y18拒转先红后绿为r1/x7/y16，I先红x2后绿x1/y16；无合法候选控制保持原局。旧run12 Android15真实ROTATE触控亦确认T保持r0/x7/y18，前后实际Window截图留存，新包设备验收另记。局面通过明确合法公开恢复准备，不声称自然残局或外部SRS认证。

## BL-044 俄罗斯方块备用逆时针旋转越界并复用错误偏移
- 日期: 2026-09-17
- 类别: 游戏旋转API
- 根因: CCW的3→2先计算并访问下标8才覆盖数组，长度8必然越界；覆盖数组又误用JLSTZ形状和方向，使T/I左墙返转失败。
- 修复: CW与CCW共用同一八转向映射与既有I/JLSTZ表，删除临时覆盖数组及多余方向布尔参数。
- 守卫: 自然首袋公开CCW循环实际先红Index8越界，修复后七种块各完整四次逆转通过；T/I左墙分别+1/+2候选两项先红后绿。十项旋转回归与完整Tetris53项通过。当前普通UI只有CW，未声称玩家能从现有按钮触发CCW异常。

## BL-045 俄罗斯方块NEXT预览描边侵入棋盘且全消提示缺失
- 日期: 2026-09-17
- 类别: 游戏画面可读性与成就反馈
- 根因: 顶部只预留120dp，NEXT外框笔画延伸至126.75dp；全消奖励计入gained但浮字仍仅使用Single/Tetris动作名。
- 修复: 竖屏顶部预留132dp，为描边与网格提供间距；全消浮字显示Perfect Clear!并保留B2B前缀与本次奖励，不改总分公式。
- 守卫: TetrisHudFeedbackTest真实onDraw命令在546/550dp游戏剩余高度先红间距-7.1dp后绿至少4dp；两种PC浮字先红后绿明确+900/+6500且长文字位于View内。普通Single与横屏五个48dp控制两控制项保持通过。旧设备截图证实金框重叠和936分仅显示Single+900，新包视觉验收另记。

## BL-046 打砖块结束页重开后小球不再移动
- 日期: 2026-09-17
- 类别: 游戏重新开始
- 根因: 失球结束回调停止Activity循环，结束页触摸只调用View.startGame，没有重新启动Activity的计时任务。
- 修复: View结束页通过重开回调进入Activity.startGame，清理旧任务并重置关卡、累计关数与当前分数，然后重新安排游戏循环；独立View保留原重开行为。
- 守卫: BreakoutGameplayLifecycleTest实际Activity/Handler消费明确末命近底夹具后，通过真实结束页触摸重开再发射，旧代码球坐标不变，修复后160ms真实循环产生位移。5项Lifecycle及3项Resize完整8项通过；设备测试另行验证，不把人工末命准备称自然整局。

## BL-047 打砖块进入下一关后游戏更新循环重复
- 日期: 2026-09-17
- 类别: 游戏速度与关卡切换
- 根因: update内过关回调移除排队任务后，当前帧返回仍无条件排下一帧；1500ms延迟切关又启动一条循环，实际两条链使球速翻倍。
- 修复: 更新返回后重新检查运行、暂停及过关等待状态；统一排队方法先去重，等待下一关期间不排游戏帧。
- 守卫: 原27砖只留最后一砖的明确夹具由真实碰撞完成过关，检查1499ms仍展示完成页、1500ms进入第二关；安全飞行窗口160ms独立位移计算旧约20帧、修复后10帧。自然发射及两次前后台控制也保持单链。未宣称自然完成整关。

## BL-048 打砖块手动暂停后回前台自动继续
- 日期: 2026-09-17
- 类别: 游戏暂停
- 根因: 子类onResume对所有isGamePaused直接resumeGame，覆盖基类已有的手动暂停与生命周期暂停区分。
- 修复: 保留基类自动暂停恢复，子类不再无条件解除手动暂停。
- 守卫: BreakoutGameplayLifecycleTest手动暂停、后台等待、回前台实际旧状态被解除先红，修复后球坐标保持，显式继续后恢复单条游戏循环。自然生命周期暂停恢复原控制保持通过。

## BL-049 打砖块过关等待在后台提前生成下一关
- 日期: 2026-09-17
- 类别: 游戏关卡生命周期
- 根因: 匿名延迟任务仅检查isGameRunning，暂停未取消该任务，后台1500ms到期仍调用startNextLevel。
- 修复: 使用可取消的单一切关任务与原uptime截止时间；暂停移除任务，回前台按剩余时间安排，后台已过期时仅在恢复后切关一次。
- 守卫: 真实末砖碰撞后暂停2000ms，旧代码关卡1变2先红；修复后仍保留完成画面、分数、球和关卡1，恢复后生成关卡2且发射只运行一条循环。完整8项回归通过。

## BL-050 打砖块窗口缩放后砖块留在屏幕外
- 日期: 2026-09-17
- 类别: 游戏布局与可玩性
- 根因: 尺寸改变只重算挡板和球几何，已有砖块仍使用旧宽度，640缩至360时活砖右缘达到420.69。
- 修复: 根据新尺寸更新已有砖块RectF及行渐变画笔，复用砖块身份并保留耐久、已消除状态、颜色、分数、生命与暂停状态。
- 守卫: BreakoutResizeTest三项先红后绿，覆盖缩小、放大再缩回及自然触摸/public update实际形成已消砖与受损砖后暂停缩放。后者仅反射读取，没有私有写入夹具；完整8项通过。此处为真实View布局回归，非设备旋转验收。

## BL-051 打砖块边缘点击发射时球与挡板分离
- 日期: 2026-09-17
- 类别: 游戏触摸与发射
- 根因: ACTION_DOWN先launchBalls切入PLAYING，再移动挡板，原READY球同步分支无法执行；边缘点击后球仍从原屏幕中心发射。
- 修复: 先依据实际触摸裁切挡板位置，在READY同步待发球，再按原速度和随机角度发射；后续MOVE仍只移动挡板。
- 守卫: BreakoutLaunchInputTest四项真实完整MotionEvent测试，左右两项旧代码期望46.8/313.2实际180先红后绿；中心点击与DOWN/MOVE/MOVE/UP保持控制通过。无私有写入，公开update验证第一个安全飞行步；连同原Lifecycle5/Resize3共12项全部通过。设备实际边缘触摸验证另行记录。

## BL-052 贪吃蛇合法进入即将移走的尾格被判自撞
- 日期: 2026-09-17
- 类别: 游戏移动与碰撞规则
- 根因: 非增长tick在删除尾节之前对全部旧身体检查，蛇头进入本步即将腾出的尾格也被判死。
- 修复: 先计算本步是否吃食；增长检查全部旧身体，非增长只检查会保留的身体格，然后沿用原插头、增长/删尾与计分流程。
- 守卫: SnakeGameTailCollisionTest四项正式模块Gradle回归实际1FAIL3PASS后4/4GREEN。公开reset及合法食物夹具吃成4节后UP/LEFT/DOWN，目标尾格原TICK_DIED=2，修复后TICK_MOVED=0，完整有序身体、长度、分数、running/gameOver均正确。五节进入仍保留身体仍死亡、增长+10且保留尾、反向保护三项控制保持通过。未写蛇身/方向/分数状态或调用私有方法；APK与设备验收另行记录。

## BL-053 贪吃蛇缓慢滑动在松手前不能转向
- 日期: 2026-09-17
- 类别: 游戏触摸与即时转向
- 根因: View只在GestureDetector.onFling请求方向，持续按住或缓慢拖动超过系统滑动阈值仍继续直走。
- 修复: DOWN记录起点，MOVE首次超过平台scaledTouchSlop时按主轴请求方向；UP仅补充尚未提出的方向，每个手势最多请求一次，沿用原游戏反向保护与速度。
- 守卫: SnakeGestureInputTest慢MOVE回归旧代码仍向右先红，修复后在UP之前的真实View计时更新即向上，后续同手势移动不再二次转向。完整快速滑动、短抖动与反向保护控制继续通过；连同尾格规则共10项正式模块单测全部通过，Release构建成功。真实安装包设备验证另记。

## BL-054 贪吃蛇取消或暂停后的旧手势仍会转向
- 日期: 2026-09-17
- 类别: 游戏输入生命周期
- 根因: 旧GestureDetector的触摸锚点跨取消或暂停保留，之后的MOVE/UP可能用失效手势请求新方向。
- 修复: CANCEL、开始新局、暂停、停止和View离窗时清除本地未完成手势；已经接受的游戏方向保持原语义，结束页DOWN重开后的旧MOVE也不被继承。
- 守卫: CANCEL与pause两个独立用例旧状态产生意外转向先红后绿；同一生命周期用例继续检查stop和真实窗口detach后的旧事件无效，新DOWN仍可操作。GameOver点击重开控制通过，完整6项输入回归及4项尾格规则共10项通过。旧pause失败中未执行的stop/detach分支不冒称分别取得修复前RED。

## BL-055 记忆翻牌局中切换难度导致无法通关或提前结束
- 日期: 2026-09-17
- 类别: 游戏难度与配对目标
- 根因: 难度按钮直接改当前totalCards/pairCount，却保留旧牌组。简单局切困难后只有8对却要求12对；困难局切简单后只配8对即获胜，尚余8张牌。
- 修复: 使用待选尺寸保存下一局难度，startNewGame一次性提交当前尺寸与配对目标；局中提示下一局生效，保留当前牌面、配对和正在进行的选择。
- 守卫: MemoryDifficultyTransitionTest三项正式模块回归2FAIL1PASS后3/3GREEN。两向切换都先完成一对、翻开第二对首张再选难度，通过真实Fragment按钮与800ms Handler配完整局；旧代码分别无法显示再来一局或第8对提前显示，修复后8/12对各正常结束且下一局24/16张。三档局前选择与完整配对控制保持通过。仅反射读取自然洗牌值决定合法点击，无私有状态写入；设备触摸及几何另行记录。

## BL-056 记忆翻牌难度按钮触摸高度不足
- 日期: 2026-09-17
- 类别: 游戏控制可触达性
- 根因: 小字号难度按钮依赖主题默认测量，Android15密度4实测只有176px即44dp高。
- 修复: 三个难度按钮与开始/再来按钮显式设置最小48dp高度，保持原文字、横向权重和游戏牌组。
- 守卫: ShippedMemoryModuleTest两项在旧包初始几何实际RED，保存真实Window与405x176px按钮边界；新run17两项均通过所有按钮完整可见和48dp、牌面互不重叠、真实触摸配完8/12对及下一局24/16张。难度按钮实测405x192px。困难局实际第8对仍8/12继续、第12对正常通关，结算按钮和完整水果图案已视觉核验；本条不将旧包几何失败误称已取得难度规则的设备RED。

## BL-057 小鸟飞翔高密度屏幕管道开口小于鸟身体
- 日期: 2026-09-17
- 类别: 游戏碰撞几何与屏幕密度
- 根因: 鸟半径和管道宽度已按density放大，开口仍使用180/126等固定像素。密度4时鸟直径192px，默认和困难开口均不足，首管也不可能安全容纳鸟。
- 修复: 默认开口及setPipeConfig传入的dp开口统一转换为像素；保留mdpi原尺寸和原速度/重力/跳跃参数。
- 守卫: FlappyDensityGeometryTest三项正式模块NativeCanvas回归2FAIL1PASS后3/3GREEN。公开触摸与两次update自然产生第一管，真实像素旧默认开口181px/困难127px小于191px鸟体，修复后dp开口与渲染/碰撞几何一致且可容完整鸟。mdpi默认和三难度控制保持；仅只读几何，不写RNG/管道/鸟位置，不宣称自然穿管或连续随机管道可玩。设备验收另记。

## BL-058 小鸟飞翔高密度连续管道产生互相冲突的高度要求
- 日期: 2026-09-17
- 类别: 游戏连续障碍与碰撞几何
- 根因: 鸟直径与管道宽度已按density放大，相邻管道仍固定约300px；密度4时横向碰撞跨度432px，两根独立随机开口会同时约束小鸟，安全高度不相交时必死。
- 修复: 自然生成计数使用300dp乘density，保留mdpi原节奏和原速度、重力、跳跃、随机开口来源。
- 守卫: FlappyPipeSpacingTest真实完整DOWN/UP与公开update自然产生两管，正式3项2FAIL1PASS后3/3GREEN。旧Normal间距300px、Hard300.29248px均小于432px；修复后满足300dp基准及无同时横向碰撞，mdpi三档控制保持。连同开口3项共6/6通过，无私有状态/RNG写入。此批证明消除互相矛盾的双管高度约束，不声称所有随机飞行轨迹可玩；设备自然穿管另验。

## BL-059 小鸟飞翔菜单按钮触摸高度不足
- 日期: 2026-09-17
- 类别: 游戏控制可触达性
- 根因: 小字号难度按钮依赖主题默认测量，Android15密度4旧包实测352x176px，只有44dp高。
- 修复: 三个难度及重新开始按钮显式最小48dp，保持原难度、权重和游戏内容。
- 守卫: ShippedFlappyModuleTest旧包两项在初始44dp几何明确RED；run18首轮Normal/Hard两项都通过实际Window全部按钮192px=48dp、完整可见触区、真实选择及自然首管像素检查。旧几何失败尚未起飞，不冒称设备开口RED。完整35项另有两个Snake输入前置失败已保留，不影响本条独立两项通过事实。

## BL-060 小鸟飞翔首次进入时游戏速度翻倍
- 日期: 2026-09-17
- 类别: 游戏帧循环与一致速度
- 根因: onViewCreated中的startGame已post同一个gameLoop，紧接着onResume再次post，产生两条每16ms更新链；重开会清旧队列，导致首次与重开速度不同。
- 修复: onResume恢复后先移除旧gameLoop回调，再安排唯一一次更新链。
- 守卫: FlappyFragmentLifecycleTest实际FragmentActivity/Fragment/Handler三项正式1FAIL2PASS后3/3GREEN；完整触摸自然首管生成，160ms实际旧位移60px、修复后30px。真实重开按钮及两轮Activity暂停/恢复控制保持单链；未调用update或写私有runnable/pipe/bird。完整Flappy10项通过。run18旧设备自然首管轨迹约360px/s作为辅助证据；新设备精确帧率未额外宣称。

## BL-061 小鸟飞翔重新开始后顶部仍显示上局分数
- 日期: 2026-09-17
- 类别: 游戏计分反馈
- 根因: View.startGame把内部score归零，Fragment顶部TextView仅在过管回调更新，重开没有刷新。
- 修复: startGame同时把顶部实际分数字段更新为零，保留最高分和原计分规则。
- 守卫: FlappyRestartScoreTest通过公开父布局280px使正常生成公式自然得到90px中心，真实完整触摸与16ms Handler自然首管得1分，再实际重开；旧public0但TextView仍Score1明确RED，修复后立即Score0，完整10项GREEN。没有写pipe/RNG/score或调用计分回调；此为有界真实布局fixture，不冒称典型手机随机场景。真实设备自然过管后重开回归另记。

## BL-062 配对消除首次选难度后预告与配对统计不一致
- 日期: 2026-09-17
- 类别: 游戏开局信息
- 根因: 未开始游戏时难度只更新待选basePairCount，统计仍读取当前pairCount=8且不刷新，普通10对预告与0/8统计冲突。
- 修复: 仅首次READY且没有已完成结算时使用待选分母并即时刷新；局中和已完成结算保留当前牌组及原目标。
- 守卫: MatchChallengeContentTest新增freshReadyPreviewStatsAgreeWithEverySelectedDifficulty实际Fragment按钮正式RED，Normal期望10实际8；修复后全部七项GREEN，三难度8/10/12均一致。保留已完成结算后切下一难度的原统计与失误6/4/2分母；Hard在旧Normal首次失败后未执行，不单独冒称Hard已RED。新增三阶段挑战属于内容扩充，六项新增期待的旧代码失败不记为旧玩法缺陷。

## BL-063 配对消除难度按钮触摸高度不足
- 日期: 2026-09-17
- 类别: 游戏控制可触达性
- 根因: 小字号难度按钮依赖主题默认测量，未保证48dp最小操作高度。
- 修复: 三个难度按钮和开始按钮明确最小48dp，保留横向权重与文字。
- 守卫: MatchChallengeContentTest实际宿主主题/Fragment布局六项初始RED中几何用例在控件至少48dp断言失败；修复后七项全部GREEN，360x640dp下568/480dp实际内容高度、三牌组、牌面至少48dp且正方形/不重叠/完整可见、最后两次布局几何相同且参与布局的View无pending请求。设备触摸与真实Window另验，不把单元布局当设备通过。
- 设备补证: run19旧Match693692字节原APK实际405x176px=44dp按钮明确RED；run20新包实际至少192px=48dp，完整三关自然洗牌只读规划、真实触摸与37次800ms回调全部通过。root查看完整牌面和正常结算/下一关Window。本轮整组37项另有TD截图超时，保留原失败与同包同测试定向通过，未冒称单轮全绿。

## BL-064 peer token 加密不可用时明文降级写入且被迁移 clear 静默抹除
- 日期: 2026-09-18
- 类别: 凭据存储降级
- 根因: getEncryptedPrefs 异常分支返回同文件明文 SharedPreferences；savePeerToken 先把令牌写入该明文实例，随后"迁移清理"对同一文件 clear()，令牌既落盘明文又被静默丢失；clearPeerToken 直接对返回值链式调用亦有 NPE 风险。
- 修复: 加密不可用时 getEncryptedPrefs 返回 null；savePeerToken 判空 fail-closed 跳过持久化；getLastPeerToken/clearPeerToken 判空处理，保留旧明文数据只读迁移路径。
- 守卫: scripts/verify_security_clauses.py §9.1 两项静态不变量（getEncryptedPrefs 禁止返回明文实例、savePeerToken 写前判空）；修复前 FAIL、修复后 PASS 已实测。JVM 侧因无 Robolectric 不作运行时断言，真机配对回归另记。

## BL-065 华容道提示归属三例红：测试接缝用裸 idle() 泵主线程队列，被 ViewRootImpl 同步栅栏挡住
- 日期: 2026-09-20（根因定位并转绿）
- 类别: 测试夹具缺陷（非生产缺陷；生产回调投递路径正确）
- 根因: KlotskiHintOwnershipTest 的 QueuedHintFragment.runSearch 只做 searches.get(i).run() + shadowOf(mainLooper).idle()。夹具在 setUp 里手工 measure/layout 根视图，且 requestHint 里 updateStatus/refreshHintButton 会再触发 requestLayout ⇒ ViewRootImpl.scheduleTraversals() 在主线程 MessageQueue 队头 postSyncBarrier()。Robolectric PAUSED looper 下 idle() 只放行到期消息且**尊重同步栅栏**：队列实测 `[BARRIER/sync, msg/sync, msg/sync, msg/sync]`，提示回调整批排在栅栏之后永远跑不到（同一条 canary `Handler(mainLooper).post` + idle() 同样不执行；而 Choreographer 的 DoFrame 是异步消息，所以帧回调照常运行——这正是"能渲染、收不到回调"的分裂现象）。此前记录的"回调已过守卫但走 hint==null 分支"是误判：反射队列转储证明回调根本没执行。KlotskiGame.getHint 三盘（4/8/16 步）一直正确。
- 修复: 测试接缝改为先投递一帧（idleFor 32ms，异步消息越过栅栏并让 doTraversal 撤栏），再 idle() 排空被放行的同步回调；顺带把写死的"练习 1/3"改为按 KlotskiPracticeLevels.all().size() 推导（华容道练习局 3→8 后必须跟着长，否则再次假红）。生产代码零改动。
- 守卫: KlotskiHintOwnershipTest 7/7（sameBoardRestartRejectsOldResultBeforeChangingNewRequestBusyState、switchingPracticeRejectsAWhileBStillSolvesItsActualCapturedBoard、pauseAndDestroyInvalidatePendingRealSearchCallbacks、completedPracticeSurvivesNewActivityWithoutRepeatingRecordWriteAndCanAdvance 等）；华容道模块 22/22 全绿。修复前该三类稳定红、修复后全绿已实测。
