<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# 🎉 v1.3.18 发布总结

## 发布时间
2026 �?5 �?12 �?

## 版本信息
- **版本�?*: 1.3.18
- **内部版本�?*: 224
- **渠道**: 正式�?(stable)
- **APK 大小**: 16.44 MB
- **MD5**: af02b3f324631e93e3d25656a6ff3d93

## 严重问题修复 �?

### 1. Handler 内存泄漏修复 🔥
修复�?7 个游�?Activity �?Handler 内存泄漏问题�?
- �?TetrisActivity - 修复游戏循环 Handler 清理
- �?SnakeActivity - 修复游戏循环 Handler 清理
- �?FlappyActivity - 修复游戏循环 Handler 清理
- �?PlaneActivity - 添加 onDestroy 清理逻辑
- �?TilesActivity - 添加 onDestroy 清理逻辑
- �?SokobanActivity - 添加 onDestroy 清理逻辑
- �?WhackActivity - 调用 releaseResources() 完全释放资源

### 2. WhackView 资源泄漏修复 🎮
- �?stopGame() 使用 `removeCallbacksAndMessages(null)` 确保完全清理
- �?WhackActivity 正确调用 releaseResources()
- �?防止长时间使用后内存泄漏

### 3. 代码质量提升 📈
- �?清理 UpdateManager 重复 import
- �?统一所有游�?Activity 的生命周期管�?
- �?完善 Handler �?Runnable 的清理逻辑
- �?优化内存管理，防�?Activity 泄漏

## 构建信息

### 构建结果
```
BUILD SUCCESSFUL in 1m 28s
47 actionable tasks: 29 executed, 18 up-to-date
```

### 签名验证
```
jarsigner -verify app-release.apk
输出：jar 已验�?�?
```

## 更新源验�?�?

### 香港 VPS
- URL: https://your-server.example.com/version.json
- 状态：�?已验�?
- versionCode: 224
- versionName: 1.3.18
- channel: stable

### 美国 VPS
- URL: https://your-server.example.com:1443/version.json
- 状态：�?已验�?
- versionCode: 224
- versionName: 1.3.18
- channel: stable

## 文档更新 �?
- �?CHANGELOG.md - 已更�?v1.3.18 更新日志
- �?version.properties - 已更新版本号
- �?RELEASE_COMPLETE.md - 已更新发布报�?

## 修复效果

### 内存泄漏修复�?
- Activity �?onDestroy 后无法被垃圾回收
- 长时间使用导致内存占用持续增�?
- 可能引起应用卡顿、崩�?

### 内存泄漏修复�?
- Activity 正确释放所有资�?
- Handler 回调完全清理
- 内存使用稳定，无泄漏风险

## 技术细�?

### 统一清理模式
所有游�?Activity 现在使用统一的清理模式：

```java
@Override
protected void onDestroy() {
    super.onDestroy();
    isRunning = false;
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
    handler = null;
    // 其他资源清理...
}
```

### WhackView 资源释放
```java
public void stopGame() {
    gameRunning = false;
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
    moleRunnable = null;
    timerRunnable = null;
}

public void releaseResources() {
    stopGame();
    handler = null;
    random = null;
    holes = null;
    moleUp = null;
    moleHit = null;
}
```

## 待办事项
- [ ] 手动上传 APK �?GitHub Releases
- [ ] 收集用户反馈
- [ ] 监控内存泄漏修复效果

## 总结
v1.3.18 是一个严重问题修复版本，成功修复了所有游�?Activity �?Handler 内存泄漏问题。通过统一的生命周期管理，确保了长时间使用的稳定性。所有更新源已成功同步，用户可以安全升级�?

**发布状�?*: �?成功
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平�?Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题�?
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言�?
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项�?
- 发布前检查需覆盖中文/英文两种语言、深�?浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮�?
## 2026-05-15 文档同步：Dependabot �?CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin �?8.13.2、Gradle Wrapper �?8.13、Kotlin �?2.2.21、Hilt �?2.57.2�?
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1�?
- GitHub Actions 已改为验证型 CI：使�?JDK 21，执�?debug 构建与单元测试，不在云端构建 release 包，避免暴露或依�?release 签名文件�?
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修�?`version.properties`�?
- `.gitignore` �?`data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamecenter/app/ai/data/` 源码�?
- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部�?GitHub Release 发布仍以本机发布流程为准�?
## 2026-05-24 文档同步
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店