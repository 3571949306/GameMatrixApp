# GameMatrixApp ADB 真机全量测试计划

> 目标设备：小米 ares（M2012K10C）无线调试  
> 目标版本：当前工作区最新 Debug APK（`app-debug.apk`）  
> 测试范围：全部功能、按钮、模块商店下载使用  
> 制定日期：2026-07-20

---

## 1. 测试前准备

### 1.1 环境检查

```powershell
# 1. 确认设备在线
adb devices -l

# 2. 如设备离线，先重连
adb kill-server
adb start-server
adb connect 192.168.10.50:44535

# 3. 清理旧日志
adb -s <serial> logcat -c
```

### 1.2 构建命令

```powershell
# 标准 Debug 构建（不自动升版号）
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace

# 如需刷新预装模块（如修改了模块源码）
.\gradlew.bat :module-store:feature:tools:wrongbook:assembleRelease :app:bundlePreinstalledModules -PautoBumpVersion=false --stacktrace
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

### 1.3 安装命令

```powershell
adb -s <serial> install -r -d app\build\outputs\apk\debug\app-debug.apk
```

### 1.4 日志过滤命令

```powershell
adb -s <serial> logcat -d -t 2000 |
  Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|Resources\$NotFoundException|InflateException|ClassNotFoundException|ActivityNotFoundException|Caused by:"
```

---

## 2. 启动与主流程测试

### 2.1 冷启动

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| 1 | 点击桌面图标启动 | SplashActivity 显示，无崩溃 |
| 2 | 等待 2-3 秒 | 自动进入 MainActivity，默认显示游戏大厅 |
| 3 | 观察启动屏 | 有启动动画/Logo，深色模式正常 |
| 4 | Debug 构建检查右上角 | 可能显示启动耗时毫秒数（APP_LAUNCH_TIME_DISPLAY） |

### 2.2 权限弹窗

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| 1 | 首次启动 | 出现权限说明弹窗（如非 TEST_MODE） |
| 2 | 点击同意 | 进入主界面，无崩溃 |
| 3 | 拒绝通知权限 | 应用仍可正常使用 |

### 2.3 底部导航切换

按顺序点击底部导航每一项：

1. **游戏大厅**（games）
2. **浏览器**（browser）
3. **工具箱**（tools）
4. **AI 助手**（ai）
5. **VPN**（vpn）
6. **我的**（profile）

| 检查项 | 预期 |
|--------|------|
| 切换动画 | 图标有缩放动画（NAV_ACTIVE_ANIM） |
| 返回手势 | 非游戏大厅页返回时切回游戏大厅 |
| 未读徽章 | 游戏大厅 tab 可能显示红点（NAV_BADGE_UNREAD） |

---

## 3. 游戏大厅功能测试

### 3.1 顶部区域

| 元素 | 操作 | 预期 |
|------|------|------|
| 问候语 | 观察 | 根据时间显示正确 |
| 通知铃铛 | 点击 | 打开 NotificationsDialog |
| 头像 | 点击 | 弹出菜单（签到/成就/设置/模块商店等） |
| 搜索框 | 点击 | 进入搜索状态，显示搜索历史 chips |
| 搜索框输入 "go" | 回车 | 过滤显示围棋/五子棋等 |
| 搜索历史 chip | 点击 | 回填搜索框并过滤 |
| 清除历史 | 点击 | 清空搜索历史 chips |

### 3.2 分类 Tab

| 操作 | 预期 |
|------|------|
| 点击全部/益智/休闲/经典 | 游戏列表按分类过滤 |
| 滑动分类 Tab | 流畅切换 |

### 3.3 游戏卡片

| 操作 | 预期 |
|------|------|
| 上下滑动列表 | 卡片正常滚动，无卡顿 |
| 点击卡片 | 打开游戏详情 BottomSheet（GAME_DETAIL_SHEET） |
| 长按卡片 | 弹出菜单：立即开始/收藏切换/分享/桌面快捷方式 |
| 点击收藏按钮 | 图标切换，收藏状态持久化 |
| 观察卡片徽章 | 可能显示热门/评分/时长徽章 |

### 3.4 首页卡片模块

| 模块 | 检查 |
|------|------|
| Hero Banner | 3-5 张轮播，指示器正常，自动轮播 |
| 每日挑战/连胜卡片 | 显示当日状态 |
| 快速统计栏 | 今日时长/连胜/成就数 |
| 今日推荐 | 显示一款游戏，点击可开始 |
| 继续游玩 | 显示最近游戏，点击继续 |
| 休息提醒 | 游玩超 30 分钟显示警示卡片 |
| 最近解锁成就横幅 | 可关闭，当日不再显示 |
| 骨架屏 | 冷启动时短暂显示 shimmer |
| 空状态插图 | 搜索无结果时显示 |

### 3.5 随机游戏 FAB

| 操作 | 预期 |
|------|------|
| 点击右下角骰子 FAB | 随机启动一款游戏 |
| 多次点击 | 每次随机不同游戏 |

### 3.6 下拉刷新

| 操作 | 预期 |
|------|------|
| 在游戏大厅下拉 | 出现主题色刷新头，刷新后列表更新 |

---

## 4. 各游戏功能测试

依次启动以下游戏，每款检查：

1. 能正常进入游戏界面
2. 有开始/重新开始按钮
3. 游戏可操作
4. 返回能正常退出
5. 无 FATAL EXCEPTION

| 游戏 | 包名/Activity | 测试重点 |
|------|---------------|----------|
| 五子棋 | `.games.gomoku.GomokuActivity` | 选择难度后才能下棋，隐藏棋盘到开始游戏 |
| 斗地主 | `.games.doudizhu.DouDiZhuMenuActivity` | 菜单/单机/联机入口 |
| 中国象棋 | `.games.chinesechess.ChineseChessActivity` | 棋盘渲染、悔棋 |
| 围棋 | `.games.go.GoActivity` | 落子、认输 |
| 2048 | `.games.game2048.Game2048Activity` | 滑动合并 |
| 扫雷 | `.games.minesweeper.MinesweeperActivity` | 点击翻开、标记 |
| 数独 | `.games.sudoku.SudokuActivity` | 数字填充 |
| 俄罗斯方块 | `.games.tetris.TetrisActivity` | 下落、旋转 |
| 贪吃蛇 | `.games.snake.SnakeActivity` | 方向控制 |
| 华容道 | `.games.klotski.KlotskiActivity` | 滑动拼图 |
| 记忆翻牌 | `.games.memory.MemoryActivity` | 配对 |
| 井字棋 | `.games.tic.TicTacToeActivity` | 双人对战/AI |
| 其他小游戏 | 对应 Activity | 基本可玩性 |

---

## 5. 浏览器模块测试

### 5.1 启动与首页

| 操作 | 预期 |
|------|------|
| 点击底部导航"浏览器" | 进入浏览器首页 |
| 观察起始页 | 显示快捷入口宫格/卡片流/极简（BROWSER_HOME_PAGE） |
| 点击百度/Bing/Google | 加载对应网页 |

### 5.2 URL Bar

| 操作 | 预期 |
|------|------|
| 输入网址 | 自动识别并加载 |
| 输入关键词 | 使用当前搜索引擎搜索 |
| 长按 URL Bar | 弹出搜索引擎选择菜单 |
| 粘贴并前往 | 粘贴链接后跳转 |

### 5.3 导航控制

| 操作 | 预期 |
|------|------|
| 点击后退/前进 | 正常浏览历史 |
| 左边缘右滑 | 返回上一页（BROWSER_GESTURE_NAV） |
| 点击主页 | 回到浏览器首页 |
| 点击刷新 | 重新加载 |

### 5.4 Tab 管理

| 操作 | 预期 |
|------|------|
| 点击 Tab 按钮 | 打开 Tab 管理页 |
| 新建 Tab | 增加一个 Tab |
| 切换 Tab | WebView 状态保留 |
| 关闭当前 Tab | 自动切换到上次活跃 Tab |
| 关闭所有 Tab | 回到空首页 |

### 5.5 菜单功能

| 菜单项 | 操作 | 预期 |
|--------|------|------|
| 历史记录 | 点击 | 打开 HistoryActivity |
| 收藏夹 | 点击 | 打开 BookmarkActivity，可添加/删除书签 |
| 下载 | 点击 | 打开 DownloadActivity |
| 阅读列表 | 点击 | 打开 ReadingListActivity（BROWSER_READING_LIST） |
| 离线缓存 | 点击 | 打开 OfflineCacheActivity（BROWSER_OFFLINE_CACHE） |
| 隐私仪表盘 | 点击 | 显示追踪拦截统计（BROWSER_TRACKER_PROTECTION） |
| 页面内查找 | 点击 | 顶部出现查找栏（BROWSER_FIND_IN_PAGE） |
| 阅读模式 | 点击 | 提取正文美化显示（BROWSER_READER_MODE） |
| 网页截图 | 点击 | 保存截图（BROWSER_SCREENSHOT） |
| 页面翻译 | 点击 | 跳转到翻译页（BROWSER_TRANSLATE） |
| 夜间模式 | 点击 | 切换强制深色/浅色/自动 |
| 设置 | 点击 | 打开 BrowserSettingsActivity |

### 5.6 浏览器设置

| 设置项 | 检查 |
|--------|------|
| 夜间模式 | 三档切换有效 |
| 搜索引擎 | 百度/Bing/Google/DuckDuckGo 切换 |
| 追踪保护 | 开关有效 |
| 数据节省 | 开关有效 |
| 音量键滚动 | 开关有效 |
| 底部工具栏定制 | 可显隐按钮 |
| 首页风格 | 宫格/卡片流/极简切换 |

---

## 6. 工具箱模块测试

通过底部导航"工具箱"或模块商店进入。

| 工具 | 检查项 |
|------|--------|
| 设备信息 | 显示设备型号/CPU/内存等 |
| 电池信息 | 显示电量/温度/健康度 |
| 网络工具 | Ping/DNS/端口扫描/Traceroute/WiFi 信息 |
| 二维码工具 | 生成/扫描二维码 |
| 颜色工具 | 颜色选择/转换 |
| 文本编解码 | Base64/URL/MD5/SHA 等 |
| 系统信息 | 传感器/存储/屏幕信息 |
| 文件哈希 | 计算文件 MD5/SHA256 |
| 剪贴板工具 | 剪贴板历史 |
| 局域网扫描 | 扫描同网段设备 |
| 测速 | 网络速度测试 |
| Subnet 计算 | 子网划分 |

---

## 7. AI 助手模块测试

| 操作 | 预期 |
|------|------|
| 进入 AI 页面 | 显示对话界面 |
| 输入问题 | 能发送并显示 |
| 语音输入 | 请求录音权限后能语音输入 |
| TTS 朗读 | 回复可语音播放（ENABLE_MIMO_TTS） |
| 历史记录 | 对话记录可滚动 |

---

## 8. 错题本模块测试

| 操作 | 预期 |
|------|------|
| 从游戏大厅头像菜单进入 | 打开错题本界面 |
| 拍照/选图 | 请求相机/存储权限 |
| OCR 识别 | 识别题目文字 |
| AI 解析 | 调用后端或本地模型解析 |
| 保存错题 | 存入错题列表 |
| 查看错题 | 列表可点击展开 |

---

## 9. VPN 模块测试

| 操作 | 预期 |
|------|------|
| 进入 VPN 页面 | 显示 VPN 主界面 |
| 点击连接 | 请求 VPN 权限 |
| 连接成功 | 状态变为已连接，通知栏显示前台服务 |
| 点击断开 | 断开连接 |
| 切换服务器 | 可选不同节点 |

---

## 10. 个人中心测试

| 元素 | 操作 | 预期 |
|------|------|------|
| 游客昵称 | 观察 | 显示默认昵称 |
| 连胜/最佳/总局数 | 观察 | 数据正确 |
| 我的收藏 | 点击 | 有收藏时提示去大厅查看 |
| 战绩统计 | 点击 | 打开 StatsActivity |
| 成就中心 | 点击 | 打开 AchievementCenterActivity |
| 模块商店 | 点击 | 打开 ModuleStoreActivity |
| 设置 | 点击 | 打开 AppSettingsDialog |

### 10.1 设置页测试

| 设置项 | 检查 |
|--------|------|
| 主题模式 | 浅色/深色/跟随系统 |
| 应用语言 | 中文/English/跟随系统 |
| 字号调节 | 小/中/大 |
| 声音 | 开关有效 |
| 振动 | 开关有效 |
| 动态颜色 | 开关有效（预留） |
| 缓存清理 | 点击后清理缓存 |
| 检查更新 | 点击后检查版本 |
| 数据备份 | 导出 JSON 成功 |
| 数据恢复 | 导入 JSON 成功 |
| 关于页面 | 显示版本/GitHub/开源许可 |

---

## 11. 成就中心测试

| 操作 | 预期 |
|------|------|
| 进入成就中心 | 显示圆环进度头部 |
| 点击游戏卡片 | 打开成就详情页 |
| 查看每日挑战 | 显示今日任务 |
| 查看连胜 | 显示连胜记录 |
| 解锁成就 | 游戏内触发后顶部弹出成就 Toast |

---

## 12. 模块商店测试（重点）

### 12.1 商店入口

| 入口 | 操作 |
|------|------|
| Profile 页"模块商店"按钮 | 点击 |
| adb 直接启动 | `adb shell am start -n com.gamecenter.app/.modules.ModuleStoreActivity` |

### 12.2 商店首页

| 元素 | 检查 |
|------|------|
| Hero Banner | 多卡片轮播，可滑动，指示器正常 |
| 顶部统计栏 | 显示总模块数/已安装数/可更新数 |
| 分类 Tab | 游戏/浏览器/工具箱/AI/VPN/已安装 |
| 搜索框 | 可输入，有搜索历史 chips |
| 筛选按钮 | 弹出筛选面板（MODULE_STORE_FILTER） |
| 模块列表 | 网格/列表显示模块卡片 |
| 骨架屏 | 加载时显示 shimmer |
| 空状态 | 无结果时显示插图 |
| 错误重试 | 网络失败时显示重试按钮 |
| 刷新 FAB | 点击刷新目录 |

### 12.3 分类切换

| 分类 | 检查 |
|------|------|
| 游戏 | 显示游戏模块，子分类：全部/益智/休闲/经典 |
| 浏览器 | 显示浏览器模块 |
| 工具箱 | 显示工具模块 |
| AI | 显示 AI 模块 |
| VPN | 显示 VPN 模块 |
| 已安装 | 仅显示已安装模块 |

### 12.4 搜索与筛选

| 操作 | 预期 |
|------|------|
| 搜索 "tts" | 显示 TTS 相关模块 |
| 搜索 "browser" | 显示浏览器模块 |
| 清空搜索 | 恢复当前分类列表 |
| 筛选"已安装" | 仅显示已安装 |
| 筛选"未安装" | 仅显示未安装 |
| 筛选"可更新" | 仅显示有更新 |
| 按大小筛选 | 小/中/大 |
| 按版本筛选 | v1+/v2+ |
| 排序 | 名称/大小/版本/下载量/评分 |
| 搜索历史 | 最多保留 5 条，可点击回填，可清空 |

### 12.5 模块详情 BottomSheet

| 操作 | 预期 |
|------|------|
| 点击模块卡片 | 弹出详情 BottomSheet |
| 查看截图 | 可滑动浏览 |
| 查看描述 | 完整描述可展开 |
| 点击安装/更新 | 开始下载并安装 |
| 点击打开 | 启动模块 |
| 点击卸载 | 卸载模块（非内置） |

### 12.6 模块下载与安装（核心）

| 场景 | 操作 | 预期 |
|------|------|------|
| 下载工具箱 | 点击 tools 模块安装 | 显示进度，下载完成，自动安装 |
| 下载 AI | 点击 ai 模块安装 | 显示进度，下载完成，自动安装 |
| 下载 VPN | 点击 vpn 模块安装 | 显示进度，下载完成，自动安装 |
| 内置模块 | 点击 browser | 显示"内置 vX.X.X"，不触发下载 |
| 更新模块 | 如果服务器有新版本 | 显示更新按钮，点击后下载更新 |
| 一键更新 | 顶部"全部更新"按钮 | 批量更新可更新模块 |
| 下载中断 | 飞行模式后恢复 | 重试逻辑生效，线性退避 |
| CDN 回退 | 主服务器失败 | 尝试 fallbackUrl/CDN |
| SHA256 校验 | 下载完成后 | 校验通过才安装，失败进入 quarantine |
| 事务安装 | 安装失败 | 自动回滚到 last_good |

### 12.7 安装后验证

| 检查 | 操作 |
|------|------|
| 底部导航出现新 Tab | 安装 tools/ai/vpn 后导航动态刷新 |
| 模块可启动 | 点击对应 Tab 进入模块 |
| 模块功能正常 | 在模块内操作无崩溃 |
| 已安装列表 | ModuleStoreActivity 中"已安装"分类显示 |
| InstalledModulesActivity | 通过 adb 打开验证 |

### 12.8 卸载与回滚

| 操作 | 预期 |
|------|------|
| 卸载非内置模块 | 模块被移除，导航 Tab 消失 |
| 重新安装 | 可再次下载安装 |

---

## 13. 更新与恢复测试

### 13.1 应用更新

| 操作 | 预期 |
|------|------|
| 点击设置"检查更新" | 检查服务器版本 |
| 有新版本 | 弹出更新对话框 |
| 点击下载 | 显示进度对话框 |
| 下载完成 | 弹出安装对话框 |
| 点击安装 | 调起系统安装器 |

### 13.2 恢复模式

| 场景 | 预期 |
|------|------|
| 连续闪退 | 下次启动进入 RecoveryActivity |
| 恢复模式 | 可下载稳定版 APK 并安装 |

---

## 14. 深色/浅色主题测试

| 操作 | 预期 |
|------|------|
| 系统深色模式 | 应用跟随切换 |
| 强制浅色 | 应用保持浅色 |
| 强制深色 | 应用保持深色 |
| 各页面 | 文字、背景、图标颜色正常 |

---

## 15. 性能与稳定性测试

| 操作 | 检查 |
|------|------|
| 连续切换底部导航 10 次 | 无卡顿、无崩溃 |
| 连续打开 5 个游戏 | 每个都能正常进入和退出 |
| 浏览器打开 5 个 Tab | 内存正常，切换流畅 |
| 模块商店反复刷新 | 无内存泄漏 |
| 后台切回前台 | 状态恢复正确 |
| 屏幕旋转 | 关键页面不崩溃 |

---

## 16. ADB 快捷测试命令

```powershell
# 启动应用
adb shell am start -n com.gamecenter.app/.SplashActivity

# 直接打开游戏大厅
adb shell am start -n com.gamecenter.app/.MainActivity

# 直接打开模块商店
adb shell am start -n com.gamecenter.app/.modules.ModuleStoreActivity

# 直接打开已安装模块页
adb shell am start -n com.gamecenter.app/.modules.InstalledModulesActivity

# 直接打开浏览器
adb shell am start -n com.gamecenter.app/.browser.ui.BrowserActivity

# 直接打开设置
adb shell am start -n com.gamecenter.app/.settings.SettingsComposeActivity

# 直接打开成就中心
adb shell am start -n com.gamecenter.app/.games.achievement.AchievementCenterActivity

# 直接打开统计
adb shell am start -n com.gamecenter.app/.games.StatsActivity

# 启动指定游戏（示例：五子棋）
adb shell am start -n com.gamecenter.app/.games.gomoku.GomokuActivity

# 模拟 monkey 测试
adb shell monkey -p com.gamecenter.app -c android.intent.category.LAUNCHER 1
```

---

## 17. 通过标准

全部测试用例满足以下条件视为通过：

- [ ] 构建成功，APK 安装成功
- [ ] 应用可正常冷启动，无闪退
- [ ] 底部导航所有 Tab 可正常进入
- [ ] 至少 5 款游戏可正常启动和游玩
- [ ] 浏览器模块首页、导航、Tab、菜单、设置均正常
- [ ] 工具箱主要工具可正常打开
- [ ] 模块商店能正常加载目录、搜索、筛选、查看详情
- [ ] 至少成功下载并安装 1 个非内置模块（如 tools/ai/vpn）
- [ ] 安装后模块能正常启动和使用
- [ ] 设置页所有开关有效
- [ ] 深色/浅色主题切换正常
- [ ] logcat 无 FATAL EXCEPTION / AndroidRuntime / Resources$NotFoundException / InflateException

---

## 18. 失败处理

| 现象 | 处理步骤 |
|------|----------|
| 构建失败 | 优先修复编译错误，检查 feature flag 和依赖 |
| 安装失败 | 检查签名、ABI 是否匹配（小米 ares 为 arm64-v8a）、adb 连接 |
| 启动闪退 | 查看 logcat，定位 FATAL EXCEPTION |
| 模块下载失败 | 检查服务器 modules.json、网络、ETag、SHA256 一致性 |
| 模块安装后崩溃 | 检查 ProGuard 规则、R.styleable 兼容性、事务安装回滚日志 |
| 页面白屏 | 检查资源文件、主题、EdgeToEdge 适配 |

---

## 19. 测试记录模板

每次测试填写：

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-XX-XX |
| 测试人员 | |
| 设备型号 | 小米 ares M2012K10C |
| 设备序列号 | |
| APK 版本 | vX.X.X (build XXX) |
| Git Commit | |
| 测试结果 | 通过/部分通过/失败 |
| 失败项 | |
| Logcat 关键异常 | |
| 截图/录屏 | |

---

## 20. 回滚方法

如测试发现严重问题需要回滚：

```powershell
# 回滚代码
git checkout -- <file>

# 或回滚到上一个稳定 commit
git log --oneline -5
git checkout <stable-commit>

# 重新构建安装
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
adb -s <serial> install -r -d app\build\outputs\apk\debug\app-debug.apk
```

模块商店相关功能可通过关闭 feature flag 快速回滚：

```gradle
// app/build.gradle
buildConfigField "boolean", "MODULE_STORE_FILTER", "false"
buildConfigField "boolean", "MODULE_STORE_SEARCH_HISTORY", "false"
buildConfigField "boolean", "MODULE_STORE_DETAIL_ENHANCE", "false"
buildConfigField "boolean", "ENABLE_TRANSACTIONAL_INSTALL", "false"
buildConfigField "boolean", "ENABLE_P4_DYNAMIC_NAVIGATION", "false"
```

---

## 21. 测试记录

### 21.1 2026-07-20 真机全量测试

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-07-20 |
| 测试人员 | AI Agent |
| 设备型号 | 小米 ares M2012K10C |
| 设备序列号 | adb-w4dm4dssby7xcunv-Z6Rs5D._adb-tls-connect._tcp |
| APK 版本 | v1.4.1 (build 589) |
| Git Commit | 53f5472 docs(store): 更新混合架构改造文档和修改记录 |
| 测试结果 | 部分通过 |

#### 21.1.1 已验证通过项

- [x] 应用可正常冷启动，无闪退
- [x] 底部导航所有 Tab 可正常进入（游戏大厅、浏览器、工具箱、AI 助手、VPN、我的）
- [x] 模块商店首页加载正常：Hero Banner 轮播、三栏统计（34 总模块 / 30 已安装 / 0 更新）、分类 tab 图标
- [x] 模块商店分类切换正常：游戏 / 浏览器 / 工具箱 / AI 助手 / VPN / 已安装
- [x] 游戏分类子分类切换正常：全部 / 益智 / 休闲 / 经典
- [x] 模块搜索功能正常（在对应分类下）：在“游戏”分类搜索“2048”可正确显示 2048 模块
- [x] 模块详情 BottomSheet 正常显示：名称、版本、截图、描述、信息、当前状态、更新日志、权限说明
- [x] 内置模块显示正确：2048 显示“内置”标签，文件大小显示“内置”
- [x] 设置页可进入，主题切换正常（已验证深色/浅色模式）
- [x] logcat 无 FATAL EXCEPTION / Resources$NotFoundException / InflateException / ClassNotFoundException / ActivityNotFoundException

#### 21.1.2 未通过/待修复项

- [ ] **模块商店搜索范围限制**：当前搜索仅在当前选中的分类下生效。例如在“工具箱”分类搜索“2048”不会返回结果，需在“游戏”分类下搜索。建议后续优化为全部分类搜索或提供搜索范围提示。
- [ ] **非内置模块下载后签名校验失败**：点击 Tools 模块“下载”后，下载流程正常（约 951 KB，耗时约 3.9 秒），但 SHA-256 校验后签名验证失败，导致安装中断。
  - 关键 logcat：
    ```
    E ModuleDownloader: 模块 tools 签名校验失败: 签名者证书不匹配
    D DownloadMetrics: record: tools success=false duration=3900ms attempt=1
    E ModuleManager: onError: tools message=模块签名验证失败
    ```
  - 根因：模块 APK 签名证书与 `ModuleSignatureVerifier` 中配置的受信任证书不一致（debug 构建与服务器模块签名不匹配）。
  - 影响：无法完成“至少成功下载并安装 1 个非内置模块”的通过标准。
  - 修复方向：
    1. 统一 debug/release 签名证书；或
    2. 在 debug 构建中放宽签名校验策略（仅校验 APK 完整性而非证书链）；或
    3. 重新用主 APK 签名证书对服务器模块 APK 进行签名后上传。

#### 21.1.3 关键测试截图/Artifact

本次测试产生的截图与 UI dump 保存在 `test_artifacts/` 目录：

- `screen_current.png` / `ui_current.xml`：模块商店当前状态
- `screen_games_tab.png` / `ui_games_tab.xml`：切换到“游戏”分类
- `screen_search_2048_games.png` / `ui_search_2048_games.xml`：搜索“2048”输入状态
- `screen_search_2048_result.png` / `ui_search_2048_result.xml`：搜索“2048”结果（显示 2048 模块）
- `screen_tools_ok.png` / `ui_tools_ok.xml`：切换到“工具箱”分类，显示 Tools 模块
- `screen_tools_download_3s.png` / `ui_tools_download_3s.xml`：点击下载后 3 秒状态
- `screen_tools_download_8s.png` / `ui_tools_download_8s.xml`：点击下载后 8 秒状态
- 历史文件：`ui_modulestore_current.xml`、`ui_tab_tools_*.xml`、`ui_search2048_*.xml` 等

#### 21.1.4 复现下载失败的方法

```powershell
# 1. 启动应用并进入模块商店（通过桌面图标或 monkey）
adb shell monkey -p com.gamecenter.app -c android.intent.category.LAUNCHER 1

# 2. 手动切换到“工具箱”分类，点击 Tools 模块的“下载”按钮
# 3. 观察下载进度，约 3-4 秒后状态回退为“未安装”

# 4. 抓取 logcat 验证签名失败
adb -s <serial> logcat -d -t 500 | Select-String -Pattern "ModuleDownloader|ModuleManager|签名校验"
```

#### 21.1.5 结论与下一步

- **结论**：当前版本在导航、分类、搜索、详情展示等 UI 流程上表现稳定，无崩溃；但非内置模块的下载安装因签名证书不匹配被安全策略拦截，未能完成端到端的模块安装验证。
- **下一步**：
  1. 修复模块签名证书一致性问题；
  2. 重新上传签名后的模块 APK 到服务器并更新 `modules.json`；
  3. 再次执行本测试计划中的“模块下载与安装”章节，验证事务安装、安装后导航刷新、模块启动等流程；
  4. 评估是否将搜索范围从“当前分类”扩展为“全部分类”以提升体验。
