# 模块市场当前策略

更新日期：2026-05-24

## 展示入口

- 模块市场默认打开“游戏”分类。
- 顶部分类不再提供“全部”选项，只保留游戏、浏览器、工具箱、AI 助手、VPN。
- 游戏分类内也不显示“全部”子分类；默认展示全部游戏，可再切换益智、休闲、经典。
- 模块市场右上角保留两个显式操作：刷新当前列表、进入已下载模块列表；最右侧为已下载列表按钮。
- 已下载模块列表按全部、游戏、浏览器、工具箱、AI 助手、VPN 分类过滤。
- 模块卡片的下载/打开按钮下方显示“卸载”快捷按钮，仅对已下载模块显示。

## 游戏大厅边界

- 新安装包的游戏大厅只展示五子棋和斗地主。
- 商店下载的游戏安装成功后必须动态注册回游戏大厅。
- 已安装商店游戏既可以从模块市场打开，也可以从游戏大厅打开。
- `GamesFragment.onResume()` 需要调用 `ModuleManager.loadModuleList()` 和 `ModuleManager.registerInstalledGameModules()`，保证用户从商店返回大厅后立即看到新安装的游戏。
- 模块卸载后必须从 `GameRegistry` 动态移除，避免游戏大厅继续显示已删除游戏。

## VPS 文件位置

- 模块清单本地源文件：`deploy/modules.json`
- APK 内置兜底清单：`app/src/main/assets/modules.json`
- VPS 实际服务目录：`/var/www/modules/modules/`
- VPS 兼容备份目录：`/var/www/update/modules/`
- Nginx 的 `/modules/` 路由代理到 `127.0.0.1:9001`，该服务根目录是 `/var/www/modules`。

## 发布要求

- 修改模块清单后，同时更新 `deploy/modules.json` 和 `app/src/main/assets/modules.json`。
- 所有非 builtIn 模块必须有真实存在的下载文件，并保证 `fileSize`、`sha256` 与 VPS 文件一致。
- 上传后至少校验：
  - `https://hk-update.tcp0053.shop/modules.json`
  - 一个 APK 模块，例如 `https://hk-update.tcp0053.shop/modules/feature_game2048_v100.apk`
  - 一个普通游戏模块，例如 `https://hk-update.tcp0053.shop/modules/game_sudoku_v100.zip`

## 2026-05-24 文档同步
- 游戏视觉美化：斗地主（径向渐变桌面+菱形花纹卡牌）、五子棋（木纹渐变棋盘+3D棋子）、华容道（深色渐变+金色边框+脉冲动画）、中国象棋（木纹渐变+四角角标+楚河汉界波浪线）
- 中国象棋提示功能改进：棋盘可视化提示（蓝色脉冲光环+箭头指引）替代坐标文本，状态栏显示中文棋谱描述
- 华容道和中国象棋模块商店上架：创建独立 APK 模块（feature/games/klotski、feature/games/chinesechess），v2.0.0，已上传 VPS
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃
