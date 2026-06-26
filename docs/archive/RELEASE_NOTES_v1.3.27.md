# GameMatrixApp v1.3.27 正式版

发布日期：2026-05-21

内部版本号：265

## 主要更新

- 修复主界面顶部内容被手机状态栏遮挡的问题。
- 修复 AI 页面切换新本地模型后仍提示“未配置云端模型”的问题。
- 五子棋、中国象棋难度选择从滑杆改为按钮选择。
- 五子棋、中国象棋难度统一为低 / 中 / 高 / 大师四档。
- 下调中难度 AI 搜索预算，让中档更适合普通玩家。
- 五子棋、中国象棋对局底部按钮改为两行布局，避免窄屏只能看到部分按钮。

## 验证

- `:app:test`
- `:app:assembleDebug`
- `:app:assembleRelease`

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店

---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
