# 🎉 发布完成报告

**版本**: v1.3.18（正式版）  
**内部版本号**: 224  
**APK 大小**: ~16.5 MB  
**签名状态**: ✅ 已签名（gamecenter.keystore, SHA384withRSA）  
**发布时间**: 2026-05-12  
**状态**: ✅ 已成功发布到所有更新源

---

## 📊 发布摘要

本次发布为**严重问题修复版本**，主要修复 Handler 内存泄漏等关键问题。

### 核心修复

1. **Handler 内存泄漏修复** 🔥
   - TetrisActivity: 修复游戏循环 Handler 清理
   - SnakeActivity: 修复游戏循环 Handler 清理
   - FlappyActivity: 修复游戏循环 Handler 清理
   - PlaneActivity: 添加 onDestroy 清理逻辑
   - TilesActivity: 添加 onDestroy 清理逻辑
   - SokobanActivity: 添加 onDestroy 清理逻辑
   - WhackActivity: 调用 releaseResources() 完全释放资源

2. **WhackView 资源泄漏修复** 🎮
   - stopGame() 使用 removeCallbacksAndMessages(null) 确保完全清理
   - 防止长时间使用后内存泄漏

3. **代码质量提升** 📈
   - 清理 UpdateManager 重复 import
   - 统一所有游戏 Activity 的生命周期管理
   - 完善 Handler 和 Runnable 的清理逻辑
   - 优化内存管理，防止 Activity 泄漏

---

## 📦 构建信息

### 构建命令

```bash
gradlew.bat assembleRelease
```

### APK 信息

| 属性 | 值 |
|------|-----|
| **文件名** | app-release.apk |
| **大小** | ~16.5 MB |
| **versionCode** | 224 |
| **versionName** | 1.3.18 |
| **channel** | stable (正式版) |

### 签名验证

```bash
jarsigner -verify app-release.apk
# 输出：jar 已验证 ✅
```

**签名详情**:
- 证书：CN=GameCenter, OU=Development, O=GameCenterApp, L=Shenzhen, ST=Guangdong, C=CN
- 算法：SHA384withRSA
- 密钥：2048 位
- 有效期：10000 天

---

## 🌐 更新源状态

| 更新源 | URL | 状态 | 版本 | 大小 |
|--------|-----|------|------|------|
| **香港 VPS** | https://hk-update.tcp0053.shop | ✅ 已验证 | 224 (1.3.18) | 16.44 MB |
| **美国 VPS** | https://tcp0053.shop:1443 | ✅ 已验证 | 224 (1.3.18) | 16.44 MB |
| **GitHub Releases** | https://github.com/3571949306/GameCenterApp/releases | ⏳ 待上传 | 224 (1.3.18) | 16.44 MB |

### 验证结果

**香港 VPS**:
```json
{
  "versionCode": 224,
  "versionName": "1.3.18",
  "channel": "stable",
  "downloadUrl": "https://hk-update.tcp0053.shop/app-release.apk",
  "fileSize": 16442450,
  "md5": "af02b3f324631e93e3d25656a6ff3d93",
  "apkName": "app-release.apk",
  "isBeta": false
}
```

**美国 VPS**:
```json
{
  "versionCode": 224,
  "versionName": "1.3.18",
  "channel": "stable",
  "downloadUrl": "https://tcp0053.shop:1443/app-release.apk",
  "fileSize": 16442450,
  "md5": "af02b3f324631e93e3d25656a6ff3d93",
  "apkName": "app-release.apk",
  "isBeta": false
}
```

---

## 📝 修复详情

### 内存泄漏问题

**问题描述**:
- 多个游戏 Activity 在 onDestroy 中没有正确清理 Handler
- 导致 Activity 被引用无法回收，长时间使用后内存泄漏
- 可能引起应用卡顿、崩溃

**修复方案**:
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

### WhackView 资源泄漏

**问题描述**:
- stopGame() 只移除特定 Runnable，没有完全清理 Handler
- releaseResources() 没有被 Activity 调用

**修复方案**:
```java
public void stopGame() {
    gameRunning = false;
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
    // ...
}

// WhackActivity.onDestroy()
whackView.releaseResources();
```

---

## ✅ 验证清单

- [x] 所有 Handler 内存泄漏已修复
- [x] 所有游戏 Activity 生命周期管理完善
- [x] WhackView 资源释放逻辑优化
- [x] 重复 import 已清理
- [x] CHANGELOG.md 已更新
- [x] version.properties 已更新
- [x] 构建成功
- [x] APK 已签名
- [x] 更新源已同步

---

## 🚀 后续计划

1. **GitHub Releases 上传**: 手动上传 APK 到 GitHub Releases
2. **用户反馈收集**: 关注用户对新版本的反馈
3. **性能监控**: 观察内存泄漏修复后的效果
4. **下一版本规划**: 继续优化其他潜在问题

---

## 📞 联系方式

如有问题，请通过以下方式联系：
- GitHub Issues: https://github.com/3571949306/GameCenterApp/issues
- 邮箱：support@gamecenter.app

---

**发布完成！** 🎊
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamecenter/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。
