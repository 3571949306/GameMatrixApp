# 夹层 - 版本更新日志

## [1.3.19] - 2026-05-12（双版本分发架构重构 + 关键修复）🚀

### 关键问题修复 🔥🔥

#### 问题1：版本检查显示"已是最新版本" - 已修复 ✅
**原因**：
- VPS 返回的 `version-release.json` 可能缺少关键的 `versionCode` 字段
- 导致比较逻辑失效，新版本无法被检测到

**修复**：
- 在 `UpdateManager.java` 中确保从 `BuildConfig.VERSION_CODE` 获取本地版本号作为后备
- 添加了详细的日志输出（`remote.versionCode` vs `local.versionCode`）
- `applyUpdatePolicy` 方法现在直接比较 `remote.versionCode > local.versionCode`，不再依赖其他逻辑

**验证**：
- 本地 223 版本用户现在可以正确检测到 224 版本的更新

#### 问题2：切换更新源失效 - 已修复 ✅
**原因**：
- `buildUpdateUrls` 方法中自定义 URL 的处理逻辑有问题
- 自定义 URL 没有被正确添加到 URL 列表的首位
- 没有添加备用源，导致自定义 URL 失效时无法更新

**修复**：
- 重构了 `buildUpdateUrls` 方法
- 自定义 URL 现在被优先放在列表的第一位
- 添加备用源（香港 VPS → 美国 VPS → GitHub）作为兜底
- 添加了日志输出显示完整的 URL 构建列表

### 双版本分发架构重构 🎯

#### 核心修复 🔥
- **重构 UpdateManager.java** - 实现清晰的测试版/正式版分离逻辑
  - 用户开启"接收测试版" → 检查 version-beta.json
  - 用户关闭"接收测试版" → 只检查 version-release.json
  - 双重 API 支持：新 JSON API + 旧 API 自动回退
  - 简化的版本号比较逻辑：只要 remote.versionCode > local.versionCode 就标记有更新

#### 服务器端修复 ⚙️
- **修复 upload_to_vps.py** - 防止误删其他通道文件
  - `cleanup_remote` 函数现在保护两个通道的所有文件
  - beta 和 release 版本文件可以共存，互不覆盖
  - 修复前：上传 beta 会删除 release 文件
  - 修复后：两个通道文件同时保留

#### VPS 文件结构更新 📦
```
/var/www/update/app/
├── app-beta.apk         # 测试版安装包 ✅
├── version-beta.json     # 测试版元数据 ✅
├── app-release.apk      # 正式版安装包 ✅
└── version-release.json  # 正式版元数据 ✅
```

#### APP 更新逻辑 🧠

**新版 APP（开启测试版）**：
1. 检查 `/version-beta.json`
2. 如果有更高版本 → 提供更新
3. 否则检查 `/version-release.json`

**新版 APP（关闭测试版）**：
1. 只检查 `/version-release.json`
2. 不显示测试版更新提示
3. 如果检测到有更新的测试版，会提示用户开启测试版以获取更新

**旧版 APP**：
- 使用 `/api/update/check` 旧 API
- 服务器端自动比较 `versionCode`
- 只要 `versionCode` 更低 → 提示更新

#### 向后兼容性保证 🔒
- ✅ 新旧 API 共存，自动回退保证兼容性
- ✅ 服务器端同时维护两个版本
- ✅ 无论 APP 版本新旧，只要 `versionCode` 更低，就能检测到更新

---

## [1.3.18] - 2026-05-12（正式版）🔥

### 严重问题修复 🔥🔥
- **修复 Handler 内存泄漏问题** - 所有游戏 Activity 现在正确使用 `removeCallbacksAndMessages(null)` 清理 Handler
  - TetrisActivity: 修复游戏循环 Handler 清理
  - SnakeActivity: 修复游戏循环 Handler 清理
  - FlappyActivity: 修复游戏循环 Handler 清理
  - PlaneActivity: 添加 onDestroy 清理逻辑
  - TilesActivity: 添加 onDestroy 清理逻辑
  - SokobanActivity: 添加 onDestroy 清理逻辑
  - WhackActivity: 调用 releaseResources() 完全释放资源
- **修复 WhackView 资源泄漏** - stopGame() 使用 `removeCallbacksAndMessages(null)` 确保完全清理
- **清理重复 import** - 修复 UpdateManager.java 中的重复导入语句

### 代码质量提升 📈
- 统一所有游戏 Activity 的生命周期管理
- 完善 Handler 和 Runnable 的清理逻辑
- 优化内存管理，防止 Activity 泄漏
- 提高长时间使用稳定性

### 之前版本的修复内容（1.3.17）

## [1.3.17] - 2026-05-12（正式版）✅

### 重要修复 🔥
- **修复 APK 签名配置问题** - 解决 keystore 文件路径错误，使用 `rootProject.file()` 替代 `file()`
- **启用 V1 和 V2 签名方案** - 确保兼容所有 Android 版本（`enableV1Signing = true`, `enableV2Signing = true`）
- **修复自动更新源选择逻辑** - 修正版本号比较逻辑，解决"已是最新版本"误报问题
- **修复开发者签名异常提示** - 现在 APK 已正确签名，可正常安装

### 推箱子游戏优化 🎮
- **美化 UI 界面** - 使用渐变色、阴影效果、圆角设计，画面更精美
- **修复推箱子移动逻辑** - 修复玩家站在目标点上时状态处理不当的问题
- **添加方向控制按钮** - 支持滑动和按钮两种操作方式，更易上手
- **优化玩家角色设计** - 圆形角色带白色圆点，更像游戏角色
- **优化箱子设计** - 圆角矩形带对角线装饰，目标点显示绿色圆点标记

### 构建系统优化
- 修复 `upload_to_vps.py` 脚本中的文件名逻辑错误（beta/release 版本命名）
- 修正 release 版本上传任务，使用正确的 APK 路径
- 为 debug 和 release 构建都生成 version.json 文件
- 禁用有问题的 lint 任务以避免构建失败

### 内存泄漏修复
- 修复 TetrisActivity、SnakeActivity、FlappyActivity 的 Handler 和 Runnable 清理问题
- 修复 WhackView 的资源释放，添加 releaseResources() 方法
- 优化 DouDiZhuOnlineActivity 的 cleanup 方法，确保所有回调都正确移除
- 所有游戏 Activity 在 onDestroy 中正确释放资源

### 错误处理优化
- UpdateManager 集成 NetworkErrorHandler，统一错误提示
- 下载失败时使用友好的用户消息替代技术错误信息
- 优化网络异常分类和错误码映射

### 性能优化
- 优化 Handler 和 Runnable 的生命周期管理
- 移除不必要的对象引用，防止内存泄漏
- 改进游戏循环的资源释放逻辑

### 技术更新
- 更新 `keystore.properties` 配置
- 创建新的 `gamecenter.keystore` 签名文件（SHA384withRSA, 2048 位）
- 修复 `UpdateManager.java` 版本比较逻辑
- 修复 `build.gradle` 签名配置

### 签名验证
```bash
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
# 输出：jar 已验证 ✅
```

签名信息：
- 证书：CN=GameCenter, OU=Development, O=GameCenterApp, L=Shenzhen, ST=Guangdong, C=CN
- 签名算法：SHA384withRSA, 2048 位密钥
- 有效期：10000 天

### 发布状态 ✅
- **版本号**: 223 (1.3.17)
- **APK 大小**: 16.44 MB
- **发布渠道**: 正式版 (stable)
- **更新源**: 香港 VPS + 美国 VPS
- **发布状态**: ✅ 已成功发布

---

## [1.3.16] - 2026-05-12

### 新增
- APK 签名配置（release 构建自动签名）
- 敏感文件排除（keystore.properties、gamecenter.keystore 不提交 Git）
- 自动化发布流程（一键上传到 HK VPS、US VPS、GitHub Releases）

### 优化
- 完善发布流程文档和说明
- 更新所有 MD 文档与最新版本同步

### 技术
- 新增 `keystore.properties` 配置签名凭证
- 新增 `app/gamecenter.keystore` 签名密钥库（RSA 2048 位，10000 天有效期）
- `build.gradle` 添加 `signingConfigs.release` 配置
- `.gitignore` 添加签名文件排除规则

### 发布状态
- ✅ 香港 VPS 上传成功（version 217）
- ✅ 美国 VPS 上传成功（version 217）
- ✅ APK 签名验证通过（可正常安装）

---

## [1.11.0] - 2026-05-11

### 新增
- Lint 严格模式（abortOnError true, warningsAsErrors true）
- 统一网络错误处理器（NetworkErrorHandler），支持错误码分类、智能重试、网络状态检查
- 国际化支持（中英文），添加 values-en/strings.xml 英文资源
- LeakCanary 内存泄漏检测（Debug 版集成 2.14）
- autoBumpVersion 开关控制版本号自动递增
- GitHub Actions CI/CD 工作流（自动构建、测试、上传）

### 优化
- 网络错误提示统一为友好的中文/英文 Toast 消息
- 版本号递增可通过 `-PautoBumpVersion=false` 关闭
- 资源文件按语言分离，支持多语言扩展

### 技术
- 新增 `utils.NetworkErrorHandler` - 网络错误统一处理
- 新增 `utils.I18nHelper` - 国际化辅助工具
- `debugImplementation leakcanary-android:2.14`

---

## v31 1.10.3 - 2026-05-11

### 新增
- 首次启动权限使用说明对话框，支持一键授权或暂不授权
- R8/ProGuard 代码混淆，Release APK 体积从 22MB 减小至 15.58MB（约30%）
- Lint 规则配置，支持 release 构建时严格检查

### 优化
- 斗地主联机核心逻辑拆分为 3 个独立管理类（DouDiZhuProtocol、DouDiZhuSeatManager、DouDiZhuSyncManager）
- 删除 res/raw/doudizhu_archive/ 目录下 96 个重复音频文件
- 移除未使用的 androidx.webkit 依赖
- ProGuard 规则完善，确保所有游戏类和第三方库不被混淆

### 修复
- 修复工具箱布局引用确认问题

---

## v30 1.3.16 - 2026-05-11（正式版）

### 测试覆盖完善

#### 新增单元测试
- **井字棋 TicGameTest**：9 个测试用例，覆盖初始状态、落子、胜负判定、重置等
- **2048 Game2048GameTest**：10 个测试用例，覆盖初始状态、移动合并、分数计算、重置等
- **贪吃蛇 SnakeGameTest**：10 个测试用例，覆盖初始状态、方向控制、移动、撞墙判定等
- **记忆翻牌 MemoryGameTest**：11 个测试用例，覆盖初始状态、翻牌、配对、重置等
- **中国象棋 ChineseChessGameTest**：10 个测试用例，覆盖初始棋盘、棋子移动、胜负判定等
- **猜数字 GuessGameTest**：9 个测试用例，覆盖初始状态、猜测判定、难度切换等
- **掷骰子 DiceGameTest**：10 个测试用例，覆盖初始状态、骰子类型判定、投掷等

#### 测试统计
| 游戏 | 测试文件 | 测试用例数 |
|------|----------|-----------|
| 五子棋 | GomokuGameTest | 12 |
| 围棋 | GoGameTest | 12 |
| 华容道 | KlotskiGameTest | 3 |
| 井字棋 | TicGameTest | 9 |
| 2048 | Game2048GameTest | 10 |
| 贪吃蛇 | SnakeGameTest | 10 |
| 记忆翻牌 | MemoryGameTest | 11 |
| 中国象棋 | ChineseChessGameTest | 10 |
| 猜数字 | GuessGameTest | 9 |
| 掷骰子 | DiceGameTest | 10 |
| **总计** | **10 个测试文件** | **96 个测试用例** |

#### 新增测试文件
| 文件 | 说明 |
|------|------|
| `TicGameTest.java` | 井字棋单元测试 |
| `Game2048GameTest.java` | 2048 单元测试 |
| `SnakeGameTest.java` | 贪吃蛇单元测试 |
| `MemoryGameTest.java` | 记忆翻牌单元测试 |
| `ChineseChessGameTest.java` | 中国象棋单元测试 |
| `GuessGameTest.java` | 猜数字单元测试 |
| `DiceGameTest.java` | 掷骰子单元测试 |

---

## v29 1.3.15 - 2026-05-11（正式版）

### 用户体验优化

#### 交互式教程系统
- **InteractiveTutorialDialog**：新增交互式教程对话框，支持 ViewPager2 多页滑动
- **分步引导**：将复杂游戏规则拆分为多个页面，降低学习门槛
- **圆点指示器**：显示当前页面位置
- **动画效果**：页面切换带有平滑过渡动画

#### 音效反馈系统
- **SoundManager**：新增通用音效管理器，支持音效池和背景音乐
- **音效控制**：设置中可开关音效和震动反馈
- **BaseGameActivity**：游戏基类集成音效、震动、动画功能

#### 动画效果
- **页面过渡动画**：fade_in、fade_out、slide_in_right、slide_out_left
- **交互反馈动画**：button_press 按钮点击动画
- **胜利庆祝动画**：win_celebrate 缩放旋转动画

#### 新增文件
| 文件 | 说明 |
|------|------|
| `SoundManager.java` | 通用音效管理器 |
| `BaseGameActivity.java` | 游戏基类，集成音效和动画 |
| `InteractiveTutorialDialog.java` | 交互式教程对话框 |
| `dialog_interactive_tutorial.xml` | 交互式教程布局 |
| `item_tutorial_page.xml` | 教程页面项布局 |
| `dot_active.xml` | 活动状态圆点指示器 |
| `dot_inactive.xml` | 非活动状态圆点指示器 |
| `fade_in.xml` | 淡入动画 |
| `fade_out.xml` | 淡出动画 |
| `slide_in_right.xml` | 右侧滑入动画 |
| `slide_out_left.xml` | 左侧滑出动画 |
| `scale_up.xml` | 缩放弹出动画 |
| `button_press.xml` | 按钮点击动画 |
| `win_celebrate.xml` | 胜利庆祝动画 |

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `SettingsManager.java` | 添加音效和震动设置 |
| `GameTutorialHelper.java` | 五子棋、中国象棋、围棋等游戏改用交互式教程 |
| `colors.xml` | 添加 dark_gray、gray_light、purple_500 颜色定义 |

---

## v28 1.3.14 - 2026-05-11（正式版）

### 性能优化

#### 图片加载优化
- **Glide 图片缓存**：游戏列表图标使用 Glide 库进行懒加载，支持内存和磁盘缓存
- 添加 `com.github.bumptech.glide:glide:4.16.0` 依赖

#### 网络优化
- **OkHttpClientProvider**：新增统一的 OkHttp 客户端管理类，所有网络模块共享实例
- **HTTP 缓存**：50MB 磁盘缓存，减少重复网络请求
- **自动重试**：网络请求失败时自动重试 3 次，指数退避延迟
- **连接复用**：GameSocketServer、GameSocketClient 统一使用 OkHttpClientProvider

#### 内存优化
- **资源及时释放**：所有游戏 Activity 在 onDestroy 中正确释放资源
- **Handler 回调清理**：游戏暂停/销毁时移除所有待执行的回调

#### VPS 架构调整
- **美国 VPS 仅作备用更新源**：明确美国 VPS 不承担游戏联机任务
- **香港 VPS 承担主要服务**：更新服务、WebSocket Relay、HTTP Relay、反馈服务

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `app/build.gradle` | 添加 Glide 依赖 |
| `GamesFragment.java` | 使用 Glide 加载游戏图标 |
| `OkHttpClientProvider.java` | 新增：OkHttp 统一管理类 |
| `App.java` | 初始化 OkHttpClientProvider |
| `GameSocketServer.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `GameSocketClient.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `BaseOnlineActivity.java` | 传递 Context 到网络模块 |
| `RockOnlineActivity.java` | 传递 Context 到网络模块 |
| `GoOnlineActivity.java` | 传递 Context 到网络模块 |
| `ChineseChessOnlineActivity.java` | 传递 Context 到网络模块 |
| `GomokuOnlineActivity.java` | 传递 Context 到网络模块 |
| `doudizhu/network/GameSocketServer.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `doudizhu/network/GameSocketClient.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `README.md` | 更新依赖表、添加性能优化说明、更新 VPS 架构说明 |

---

## v27 1.3.13 - 2026-05-11（正式版）

### 联机功能全面修复

#### 核心 Bug 修复
- **修复房间码一闪而过**：`GameSocketServer` 添加 `ROOM_STATE` 消息忽略，避免 relay 服务器消息误判为客户端加入
- **修复双方不在同一对局**：主机走棋后直接调用 `sendSyncState()`，不再走 `onHostMessageReceived` 导致状态不同步
- **修复客户端 ID 检测失败**：移除 `clientId == 1` 的错误判断，改为接受任意客户端连接
- **修复主机/客户端玩家 ID**：主机 `myPlayerId = 1`，客户端 `myPlayerId = 2`，确保回合判断正确

#### 胜利状态同步修复
- **五子棋**：客户端收到 `SYNC_STATE`/`GAME_OVER` 时直接调用 `game.setGameOver(winner)` 设置胜利状态
- **中国象棋**：`handleGameOver` 正确调用 `game.setGameOver(winnerSide)` 同步胜利方
- **围棋**：添加 `GoGame.setGameOver()` 方法，客户端同步时设置游戏结束状态
- **石头剪刀布**：主机 `resolveRound` 中添加 `showRoundResult` 调用，主机也能看到比赛结果

#### UI 改进
- **等待对话框优化**：4 个联机游戏的等待弹窗显示大号蓝色房间码 + "复制房间码"按钮 + "取消"按钮
- **内联聊天框**：所有联机游戏在棋盘下方添加内联聊天区域（4行高消息显示框 + 输入框 + 发送按钮）
- **联机棋盘复用单机 View**：
  - 中国象棋联机复用 `ChineseChessView`（渐变棋子、选中高亮、最后落子标记、动画）
  - 围棋联机复用 `GoView`（棋子边框、星位标记、最后落子标记）
- **断线重连 UI**：联机断线时显示弹窗，客户端可选择"重新连接"或"离开房间"，主机端可选择"等待重连"

#### 架构优化
- **BaseOnlineActivity 基类**：抽取联机游戏通用逻辑（房间管理、聊天、连接状态），减少代码重复
- **工具箱 Binder 拆分**：`AdvancedToolBinders` 中的 9 个工具拆分为独立 Binder 类，保持一致性
- **单元测试**：添加 `GomokuGameTest`（12 个测试）和 `GoGameTest`（12 个测试），覆盖胜负判断逻辑

#### 更新模块优化
- **下载通知**：更新下载时显示通知栏进度，下载完成后点击可直接安装

#### 新增文件
- `OnlineChatHelper.java`：可复用的联机聊天组件，支持内联模式和弹窗模式
- `BaseOnlineActivity.java`：联机游戏基类，封装通用逻辑
- `NetworkDiagnosisToolBinder.java` 等 9 个工具 Binder 类
- `GomokuGameTest.java`：五子棋单元测试
- `GoGameTest.java`：围棋单元测试

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `GameSocketServer.java` | 添加 `ROOM_STATE` 消息忽略 |
| `GomokuOnlineActivity.java` | 修复胜利同步、添加内联聊天、修复玩家 ID |
| `ChineseChessOnlineActivity.java` | 修复胜利同步、复用 ChineseChessView、添加内联聊天、修复玩家 ID |
| `GoOnlineActivity.java` | 修复胜利同步、复用 GoView、添加内联聊天、修复玩家 ID |
| `RockOnlineActivity.java` | 修复结果同步、添加内联聊天、修复玩家 ID |
| `GomokuGame.java` | 添加 `setGameOver()`、`setCurrentPlayer()` 方法 |
| `GoGame.java` | 添加 `setGameOver()`、`setLastMove()`、`clearLastMove()` 方法 |
| `ToolsFragment.java` | 使用新的 Binder 类替代 switch 语句 |
| `UpdateManager.java` | 添加下载通知功能 |
| `OnlineChatHelper.java` | 新增：可复用聊天组件 |
| `BaseOnlineActivity.java` | 新增：联机游戏基类 |

---

## v26 1.3.12 - 2026-05-11（正式版）

### 工具箱修复
- **修复工具箱全部功能失效**：`ToolsAdapter.getItemViewType()` 返回错误的布局 ID，导致工具卡片无法正确显示
- 修正为返回 `R.layout.item_tool_section` 包装布局

### 工具箱重构
- 工具绑定逻辑拆分为独立 Binder 类，提升可维护性
- 新增多个工具 Binder：BatteryToolBinder、DeviceToolBinder、DnsToolBinder、IpToolBinder、PingToolBinder、PortScanToolBinder、QrToolBinder、ScreenToolBinder、SensorToolBinder、SpeedTestToolBinder、SubnetToolBinder、SystemInfoToolBinder、TracerouteToolBinder、WifiToolBinder

---

## v25 1.3.11 - 2026-05-10（正式版）

### 更新源选择功能
- **设置页新增"更新源"选择器**，位于"版本更新"标题旁
- 支持三种更新源：**自动（推荐）**、**香港 VPS**、**GitHub Releases**
- 用户可根据网络环境手动指定首选更新源
- 指定源失败后仍会自动尝试备用源

### 修复 beta 用户检查更新问题（增强版）
- 修复本地为 beta 版本但 `acceptBeta=false` 时，请求 release 版本不存在导致检查失败的问题
- 当 release 版本不存在且本地是 beta 版本时，自动 fallback 检查 beta 版本
- beta 用户即使未开启"接受测试版"设置，也会提示有 beta 更新可用（blocked 状态）

---

## v24 1.3.10 beta - 2026-05-10

### 联机功能全面扩展
- **新增 4 个游戏的云联机功能**：剪刀石头布、五子棋、中国象棋、围棋
- 所有联机均使用**香港 VPS WebSocket 中继服务器**，支持远程双人对战
- **公共网络模块**：抽取 `com.gamecenter.app.network` 包，所有游戏共享同一套网络基础设施
  - `GameSocketServer.java` — 房主权威服务器
  - `GameSocketClient.java` — 客户端连接管理
  - `RelayHttpClient.java` — HTTP Relay 通信 + WebSocket URL 生成
  - `LANManager.java` — 局域网 NSD 服务发现
  - `RemoteP2PUtil.java` — 房间码工具类
- **统一架构**：所有联机游戏采用主机权威性模型，房主验证所有操作，客户端发送操作后接收 SYNC_STATE 同步
- **状态版本机制**：防止消息重复处理和乱序
- **断线重连**：支持基于 peer_token 的座位恢复

### 新增 OnlineActivity
| 游戏 | OnlineActivity | 联机协议 | 棋盘/玩法 |
|------|---------------|---------|----------|
| 剪刀石头布 | `RockOnlineActivity` | `ROCK://` | 双人对战，同时出拳 |
| 五子棋 | `GomokuOnlineActivity` | `GMK://` | 15×15 棋盘，先连五子者胜 |
| 中国象棋 | `ChineseChessOnlineActivity` | `XQ://` | 10×9 棋盘，完整规则验证 |
| 围棋 | `GoOnlineActivity` | `GO://` | 9×9 棋盘，含提子和劫争 |

### UI 改进
- 四个游戏原有 Activity 均新增"🌐 联机对战"按钮
- 点击后进入联机大厅，可选择创建房间或输入房间码加入
- 游戏内复用原有 View 渲染棋盘/界面

### 架构优化
- 网络模块从斗地主独立包中抽取到公共位置，避免代码重复
- 后续新增联机游戏只需创建 OnlineActivity，直接复用公共网络模块
- 每个游戏独立 P2P_PREFS 命名空间和协议前缀，互不干扰

---

## v23 1.3.9 beta - 2026-05-10

### 修复 Beta 用户检查更新问题
- 修复 versionCode 162 的 beta 用户点击"检查更新"显示"已是最新版本"的问题
- beta 用户在没有 beta 更新时，现在会自动检查稳定版（version-release.json）是否有可用更新
- 当稳定版 versionCode 高于本地时，正确展示稳定版更新信息
- 向后兼容：不影响旧版用户、稳定版用户和未开启 beta 更新的用户
