# GameMatrix

一个本地优先、可按需扩展的 Android 模块平台。

**定位**：经典游戏是入口，工具与学习是留存，模块化是壁垒。<br>
**Local-first, modular Android platform** — classic games to start, tools and study to stay, modules as the moat.

- **入口（P0）**：经典棋牌与益智——五子棋、围棋、中国象棋、斗地主、数独、华容道、塔防等<br>
- **留存（P1）**：工具箱与学习——二维码、网络诊断、文件哈希、错题本<br>
- **可选模块（P2）**：浏览器、AI 助手、VPN 等——按需安装，不用不装<br>

你只安装自己需要的模块，并可自行管理导航、权限和数据。

[下载最新版本 / Download latest release](https://github.com/3571949306/GameMatrixApp/releases/latest) ·
[查看本次更新 / Release notes](RELEASE_NOTES.md) ·
[反馈问题 / Report an issue](https://github.com/3571949306/GameMatrixApp/issues)

## 你可以用它做什么
## What you can do

### 玩经典游戏（获客入口）
### Play classic games (how you start)

- 五子棋、围棋、中国象棋、斗地主、2048。
- 贪吃蛇、俄罗斯方块、数独、扫雷、推箱子、塔防等益智与休闲游戏。
- 部分游戏支持人机对战或局域网对战。
- 自动记录最近游玩、游戏时长和部分游戏数据。

- Play Gomoku, Go, Chinese Chess, Dou Dizhu, 2048, and more.
- Enjoy classics such as Snake, Tetris, Sudoku, Minesweeper, Sokoban, and tower defense.
- Some games support AI matches or LAN play.
- Track recently played games, play time, and selected game data automatically.

### 按需添加能力（模块化壁垒）
### Add capabilities when needed (modular by design)

应用内提供模块商店，不需要的能力无需安装。模块按用户目标组织：娱乐与对战、学习与整理、阅读与浏览、文本与创作、设备与网络。

The built-in module store lets you install only the capabilities you need. Modules are organized around user goals: entertainment and competition, study and organization, reading and browsing, text and creation, device and network.

- 浏览、搜索、安装、更新或卸载模块；安装前了解权限、联网能力与数据用途。
- 自定义底部导航的显示内容和顺序。
- 浏览器 / AI / VPN 属于可选模块，不强制安装。

- Browse, search, install, update, or uninstall modules; review permissions and data use before install.
- Customize bottom navigation.
- Browser / AI / VPN are optional modules — never forced.

### 使用常用工具与学习（留存理由）
### Use everyday tools and study (why you stay)

- 二维码生成、美化与识别。
- 网络检测、DNS 查询、端口扫描；设备、电池与网络信息。
- Base64、URL、JSON、时间戳、文件哈希、颜色工具。
- 错题记录、科目管理与复习计划。
- 可选：网页浏览与保存、本地或在线 AI 辅助。

- Create, style, and scan QR codes.
- Network checks, DNS, port scan; device and battery info.
- Base64, URL, JSON, timestamps, file hash, color tools.
- Wrong-answer notebook with subjects and review plans.
- Optional: web browsing/saving, local or online AI.

大部分基础能力可在本地使用。联网对战、在线 AI、网页浏览、更新检查和云同步会连接网络；相关操作前会说明用途。

Most basic capabilities work locally. Online matches, AI, browsing, and updates use the network; the app explains why before connecting.

## 下载与安装
## Download and install

1. 打开[最新版本页面](https://github.com/3571949306/GameMatrixApp/releases/latest)。
2. 下载 `app-release.apk`。
3. 点击 APK 并按系统提示完成安装。
4. 如果系统拦截安装，请只为当前使用的浏览器或文件管理器开启“允许安装未知应用”。

1. Open the [latest release page](https://github.com/3571949306/GameMatrixApp/releases/latest).
2. Download `app-release.apk`.
3. Open the APK and follow Android’s installation prompts.
4. If Android blocks installation, enable “Install unknown apps” only for the browser or file manager you are using.

直接安装新版可以保留原有应用数据。请不要先卸载旧版，除非应用明确提示必须重新安装。

Installing a newer version over the existing app normally preserves your data. Do not uninstall the old version first unless the app explicitly asks you to reinstall.

当前正式 APK 面向 64 位 ARM Android 设备，要求 Android 7.0 或更高版本。

The current stable APK targets 64-bit ARM Android devices running Android 7.0 or later.

## 更新应用
## Update the app

你可以在应用设置中检查更新。默认接收稳定版；如果主动开启测试版更新，可能会更早体验新功能，但稳定性也可能稍低。

Check for updates in the app’s settings. Stable releases are selected by default. You may opt into test releases to try new features earlier, but they can be less stable.

为避免下载到被修改的安装包，请优先使用：

To avoid modified installation packages, prefer:

- 应用内更新 / In-app updates
- 本仓库的 [GitHub Releases](https://github.com/3571949306/GameMatrixApp/releases) / [GitHub Releases](https://github.com/3571949306/GameMatrixApp/releases)

## 权限说明
## Permissions

应用只会在相关功能需要时请求权限：

The app requests permissions only when a related feature needs them:

| 权限 / Permission | 用途 / Purpose |
|---|---|
| 相机 / Camera | 扫描二维码、拍摄错题等 / Scan QR codes or capture study material |
| 麦克风 / Microphone | 语音相关功能 / Voice-related features |
| 存储 / Storage | 保存图片、导入导出文件 / Save images and import/export files |
| 网络 / Network | 在线对战、更新检查、网页与 AI / Online matches, updates, web, and AI |

具体以模块详情与系统授权为准。<br>
See module details and system prompts for the final word.
