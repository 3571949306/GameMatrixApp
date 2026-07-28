# GameMatrix

一个本地优先、可按需扩展的 Android 模块平台：在同一应用里玩游戏、使用常用工具、浏览网页、整理错题或使用 AI 辅助功能。你只安装自己需要的能力，并可自行管理导航、权限和数据。

A local-first, modular Android platform for games, everyday tools, web browsing, study organization, and AI assistance. Install only the capabilities you need and manage your own navigation, permissions, and data.

[下载最新版本 / Download latest release](https://github.com/3571949306/GameMatrixApp/releases/latest) ·
[查看本次更新 / Release notes](RELEASE_NOTES.md) ·
[反馈问题 / Report an issue](https://github.com/3571949306/GameMatrixApp/issues)

## 你可以用它做什么
## What you can do

### 玩游戏
### Play games

- 五子棋、围棋、中国象棋、斗地主、2048。
- 贪吃蛇、俄罗斯方块、数独、扫雷、推箱子等休闲游戏。
- 部分游戏支持人机对战或联网对战。
- 自动记录最近游玩、游戏时长和部分游戏数据。

- Play Gomoku, Go, Chinese Chess, Dou Dizhu, 2048, and more.
- Enjoy casual games such as Snake, Tetris, Sudoku, Minesweeper, and Sokoban.
- Some games support matches against AI or online opponents.
- Track recently played games, play time, and selected game data automatically.

### 按需添加能力
### Add capabilities when needed

应用内提供模块商店，不需要的能力无需安装。模块按用户目标组织，例如娱乐与对战、学习与整理、阅读与浏览、文本与创作、设备与网络。

The built-in module store lets you install only the capabilities you need. Modules are organized around user goals, such as entertainment and competition, study and organization, reading and browsing, text and creation, or device and network tasks.

- 浏览、搜索、安装、更新或卸载模块。
- 添加新的游戏、工具、浏览器、AI 助手、错题本或网络能力。
- 安装前了解模块需要的权限、联网能力和数据用途。
- 自定义底部导航的显示内容和顺序。
- 随时在设置中管理已安装模块与本地数据。

- Browse, search, install, update, or uninstall modules.
- Add games, tools, browser features, AI assistance, a wrong-answer notebook, or network capabilities.
- Review required permissions, network access, and data use before installation.
- Customize which items appear in the bottom navigation and in what order.
- Manage installed modules and local data at any time in Settings.

模块提供的是独立能力；是否需要联网、会使用哪些权限，以模块详情和首次操作说明为准。

Each module is an independent capability. Its details and first-use prompts explain whether it needs network access and which permissions it uses.

### 使用常用能力
### Use everyday capabilities

- 二维码生成与识别。
- 网络检测、DNS 查询、端口扫描。
- 设备、电池和网络信息。
- Base64、URL、JSON、时间戳等常用处理。
- 文件哈希和颜色工具。
- 错题记录、科目管理与复习计划。
- 浏览、阅读、翻译和保存网页内容。
- 在需要时使用本地或在线 AI 辅助。

- Create and scan QR codes.
- Run network checks, DNS queries, and port scans.
- View device, battery, and network information.
- Work with Base64, URLs, JSON, timestamps, and related formats.
- Use file hash and color tools.
- Record wrong answers, manage subjects, and plan reviews.
- Browse, read, translate, and save web content.
- Use local or online AI assistance when needed.

大部分基础能力可在本地使用。联网对战、在线 AI、网页浏览、更新检查和云同步等功能会连接网络；应用会在相关操作前说明用途，部分能力可能需要单独安装对应模块。

Most basic capabilities can be used locally. Online matches, online AI, web browsing, update checks, and cloud sync use a network connection. The app explains the purpose before relevant actions, and some capabilities may require their own module.

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
| 照片与文件 / Photos and files | 导入、导出或选择图片 / Import, export, or select images |
| 位置与附近网络 / Location and nearby networks | Wi-Fi、局域网发现等 Android 系统要求的功能 / Android-required support for Wi-Fi and local-network discovery |
| 通知 / Notifications | 显示下载、更新或重要状态 / Show downloads, updates, and important status |
| 安装应用 / Install apps | 安装你主动下载的应用更新 / Install app updates that you choose to download |

你可以随时在 Android 系统设置中关闭不需要的权限；对应功能可能因此无法使用。

You can disable unneeded permissions in Android system settings at any time. Related features may stop working as a result.

## 数据与隐私
## Data and privacy

- 单机游戏记录和大部分设置默认保存在设备本地。
- 联网对战、在线 AI、网页浏览、更新检查和云同步等功能需要连接网络。
- 使用在线服务时，完成请求所需的内容会发送给相应服务；在首次配置或执行相关操作时，请查看模块的说明和权限提示。
- 你可以通过设置管理模块权限、已安装能力、本地缓存和备份；卸载模块或应用前，请先导出需要保留的数据。

- Single-player game records and most settings are stored locally by default.
- Online matches, online AI, web browsing, update checks, and cloud sync need a network connection.
- When you use an online service, the information required to complete the request is sent to that service. Review the module description and permission prompts during setup or before the action.
- Use Settings to manage module permissions, installed capabilities, local cache, and backups. Export data you want to keep before uninstalling a module or the app.

## 常见问题
## Frequently asked questions

### 为什么有些功能需要再次下载？
### Why do some features need another download?

夹层采用按需扩展方式。常用能力可随应用提供，其他游戏、学习、浏览、工具或 AI 能力可以在模块商店中单独安装。安装前可查看模块所需权限、联网能力和数据用途；更新某个模块时不必重新下载整个应用。

GameMatrix uses on-demand extensions. Common capabilities may come with the app, while other game, study, browsing, tool, or AI capabilities can be installed separately from the module store. Review permissions, network access, and data use before installation. Updating one module does not require downloading the entire app again.

### 为什么更新应用时会出现安装提示？
### Why does updating show an installation prompt?

Android 会要求你确认 APK 更新。只有在你主动安装更新时，应用才会引导你开启对应的系统安装权限；普通功能模块由应用内部管理。

Android requires you to confirm APK updates. The app guides you to enable the relevant system installation permission only when you actively install an update. Regular feature modules are managed inside the app.

### 更新后原来的数据还在吗？
### Will my data remain after an update?

使用新版 APK 直接覆盖安装通常会保留数据。卸载后重新安装则可能丢失本地数据。

Installing a new APK over the existing version normally keeps your data. Uninstalling and reinstalling can remove local data.

### 遇到闪退、无法更新或模块安装失败怎么办？
### What if the app crashes, cannot update, or a module fails to install?

请在应用内提交反馈，或前往 [问题反馈页面](https://github.com/3571949306/GameMatrixApp/issues)。描述问题时建议附上手机型号、Android 版本和操作步骤，但不要公开密码、令牌或其他敏感信息。

Send feedback in the app or visit the [issue tracker](https://github.com/3571949306/GameMatrixApp/issues). Include your device model, Android version, and steps to reproduce when possible, but never publish passwords, tokens, or other sensitive information.

## 开源许可
## License

本项目采用 [MIT License](LICENSE)。

This project is licensed under the [MIT License](LICENSE).
