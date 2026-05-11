# 夹层 - 版本更新日志

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
