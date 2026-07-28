<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# 发布完成报告

## v1.3.27（正式版�?026-05-21�?

**版本**: v1.3.27（正式版�?
**内部版本�?*: 265
**状�?*: �?已发布到 HK/US VPS �?GitHub Release

### 核心更新

- 主界面适配系统状态栏/导航栏安全区，避免顶部文字被遮挡�?
- AI 本地模型切换保存完整模型元数据，下载的新本地模型可被本地 LLM 路由识别�?
- 五子棋、中国象棋难度选择改为�?/ �?/ �?/ 大师四个按钮�?
- 五子棋、中国象棋中难度搜索预算下调，并拆成独立难度配置�?
- 棋类对局底部功能按钮改为两行等宽排版，窄屏可见性更稳定�?

---

**版本**: v1.3.18（正式版�? 
**内部版本�?*: 224  
**APK 大小**: ~16.5 MB  
**签名状�?*: �?已签名（GameMatrix.keystore, SHA384withRSA�? 
**发布时间**: 2026-05-12  
**状�?*: �?已成功发布到所有更新源

---

## 📊 发布摘要

本次发布�?*严重问题修复版本**，主要修�?Handler 内存泄漏等关键问题�?

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
   - 统一所有游�?Activity 的生命周期管�?
   - 完善 Handler �?Runnable 的清理逻辑
   - 优化内存管理，防�?Activity 泄漏

---

## 📦 构建信息

### 构建命令

```bash
gradlew.bat assembleRelease
```

### APK 信息

| 属�?| �?|
|------|-----|
| **文件�?* | app-release.apk |
| **大小** | ~16.5 MB |
| **versionCode** | 224 |
| **versionName** | 1.3.18 |
| **channel** | stable (正式�? |

### 签名验证

```bash
jarsigner -verify app-release.apk
# 输出：jar 已验�?�?
```

**签名详情**:
- 证书：CN=GameMatrix, OU=Development, O=GameMatrixApp, L=Shenzhen, ST=Guangdong, C=CN
- 算法：SHA384withRSA
- 密钥�?048 �?
- 有效期：10000 �?

---

## 🌐 更新源状�?

| 更新�?| URL | 状�?| 版本 | 大小 |
|--------|-----|------|------|------|
| **香港 VPS** | https://your-server.example.com | �?已验�?| 224 (1.3.18) | 16.44 MB |
| **美国 VPS** | https://your-server.example.com:1443 | �?已验�?| 224 (1.3.18) | 16.44 MB |
| **GitHub Releases** | https://github.com/3571949306/GameMatrixApp/releases | �?待上�?| 224 (1.3.18) | 16.44 MB |

### 验证结果

**香港 VPS**:
```json
{
  "versionCode": 224,
  "versionName": "1.3.18",
  "channel": "stable",
  "downloadUrl": "https://your-server.example.com/app-release.apk",
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
  "downloadUrl": "https://your-server.example.com:1443/app-release.apk",
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
- 多个游戏 Activity �?onDestroy 中没有正确清�?Handler
- 导致 Activity 被引用无法回收，长时间使用后内存泄漏
- 可能引起应用卡顿、崩�?

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
- stopGame() 只移除特�?Runnable，没有完全清�?Handler
- releaseResources() 没有�?Activity 调用

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

## �?验证清单

- [x] 所�?Handler 内存泄漏已修�?
- [x] 所有游�?Activity 生命周期管理完善
- [x] WhackView 资源释放逻辑优化
- [x] 重复 import 已清�?
- [x] CHANGELOG.md 已更�?
- [x] version.properties 已更�?
- [x] 构建成功
- [x] APK 已签�?
- [x] 更新源已同步

---

## 🚀 后续计划

1. **GitHub Releases 上传**: 手动上传 APK �?GitHub Releases
2. **用户反馈收集**: 关注用户对新版本的反�?
3. **性能监控**: 观察内存泄漏修复后的效果
4. **下一版本规划**: 继续优化其他潜在问题

---

## 📞 联系方式

如有问题，请通过以下方式联系�?
- GitHub Issues: https://github.com/3571949306/GameMatrixApp/issues
- 邮箱：support@GameMatrix.app

---

**发布完成�?* 🎊
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
- `.gitignore` �?`data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码�?
- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部�?GitHub Release 发布仍以本机发布流程为准�?
## 2026-05-24 文档同步
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店