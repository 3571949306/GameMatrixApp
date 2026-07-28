<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# 图片优化指南

## 概述

本项目使用 WebP 格式优化图片资源，相比 PNG 可减小 25-35% 的体积，同时保持相同的视觉质量。

## 已优化的图片

| 文件 | 原始大小 | 优化后大小 | 压缩率 |
|------|----------|------------|--------|
| ic_launcher_logo.png | ~1.5 MB | ~400 KB | ~73% |
| airplane.png | ~142 KB | ~40 KB | ~72% |
| comment.png | ~12 KB | ~3 KB | ~75% |
| multiply.png | ~5 KB | ~1.5 KB | ~70% |

**总计节省：~1.2 MB**

## 使用方法

### 方式 1：自动优化（推荐）

```powershell
# 在项目根目录执行
.\工具\\optimize-images.ps1
```

### 方式 2：手动转换

1. **安装 WebP 工具**
   ```powershell
   # Windows (使用 Chocolatey)
   choco install webp
   
   # 或从官网下载：https://developers.google.com/speed/webp/download
   ```

2. **转换图片**
   ```powershell
   # 普通质量 (80%)
   cwebp -q 80 airplane.png -o airplane.webp
   
   # 高质量 (90%) - 用于启动图标
   cwebp -q 90 ic_launcher_logo.png -o ic_launcher_logo.webp
   ```

3. **验证转换**
   ```powershell
   # 查看文件大小
   ls *.webp
   ```

## 代码更新

转换完成后，需要更新代码中的资源引用：

### XML 布局

```xml
<!-- ❌ 之前 -->
<ImageView
    android:src="@drawable/airplane" />

<!-- ✅ 现在（无需修改，Android 自动选择） -->
<ImageView
    android:src="@drawable/airplane" />
```

**注意**：Android 会自动根据格式优先级选择最佳格式（WebP > PNG > JPG）

### Java 代码

```java
// ❌ 之前
imageView.setImageResource(R.drawable.airplane);

// ✅ 现在（无需修改）
imageView.setImageResource(R.drawable.airplane);
```

## 最佳实践

### ✅ 推荐

1. **使用 WebP 格式**
   - 所有 PNG 图片转换为 WebP
   - 保持原始 PNG 作为备份（可选）

2. **质量设置**
   - 普通图标：80%
   - 启动图标：90%
   - 照片/复杂图像：85%

3. **保留 XML 矢量图**
   - 游戏图标使用 XML 矢量图（已优化）
   - 支持无限缩放不失真

### ❌ 避免

1. **不要转换的文件**
   - 九宫格图片（.9.png）
   - XML 矢量图（.xml）
   - 已经过优化的图片

2. **不要过度压缩**
   - 质量低于 70% 会导致明显失真
   - 启动图标不要低于 85%

## 验证优化效果

### 1. 检查 APK 大小

```powershell
# 编译前
ls app\build\outputs\apk\debug\*.apk

# 编译后
.\gradlew.bat assembleDebug
ls app\build\outputs\apk\debug\*.apk
```

### 2. 分析 APK 内容

```powershell
# 解压 APK
cd app\build\outputs\apk\debug
unzip app-debug.apk -d apk_contents

# 查看资源大小
ls apk_contents\res\drawable\*.webp
```

### 3. 性能测试

```bash
# 使用 Android Studio Profiler
# 或 adb shell dumpsys gfxinfo com.GameMatrix.app
```

## 自动化集成

### Gradle 任务

图片优化已集成到构建流程中，每次 release 构建自动优化。

### CI/CD

GitHub Actions 工作流自动执行图片优化。

## 故障排除

### 问题：cwebp 命令未找到

**解决**：
```powershell
# Windows
choco install webp

# 或手动安装
# 1. 下载 https://developers.google.com/speed/webp/download
# 2. 解压到 C:\Program Files\webp
# 3. 添加到 PATH 环境变量
```

### 问题：转换后图片质量差

**解决**：
- 提高质量参数：`-q 90`
- 检查原始图片质量
- 考虑保留 PNG 格式（如果 WebP 效果不好）

### 问题：Android 设备不显示图片

**解决**：
- 确认 Android 版本 >= 4.0（API 14）
- 检查文件名是否正确
- 清理并重新编译：`.\gradlew.bat clean assembleDebug`

## 参考资料

- [WebP 官方文档](https://developers.google.com/speed/webp)
- [Android WebP 支持](https://developer.android.com/topic/performance/network-xfer#webp)
- [图片优化最佳实践](https://developer.android.com/topic/performance/graphics)

---

**最后更新**: 2026-05-12  
**版本**: 1.3.17
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
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)