# T08: 框架 APK 体积优化 - 优化指南

## 当前状态

- **当前 APK 大小**: 72.54 MB
- **目标大小**: ≤15 MB
- **差距**: 需要减少约 57 MB

## 已完成的优化

### 1. ✅ 移除嵌入的 APK（节省 ~17.3 MB）
- 移除了 `app/src/main/assets/com.injoy.games.crazy.poker-14.apk` (9.0 MB)
- 移除了 `app/src/main/assets/com.injoy.games.crazy.poker-config.arm64_v8a-14.apk` (8.3 MB)
- **注意**: 这些游戏应作为模块从模块商店下载，不应嵌入框架 APK

### 2. ✅ 启用 ABI 拆分（预计节省 ~39 MB）
- 在 `app/build.gradle` 中添加了 `splits` 配置
- 仅保留 `arm64-v8a` 架构（现代设备占比 >95%）
- **效果**: Native 库体积从 ~52 MB (4 架构) 降至 ~13 MB (1 架构)

## 待完成的优化

### 3. 🔴 优化启动图标 `ic_launcher_logo.png`（当前 1.6 MB → 目标 <100 KB）
**当前状态**: 1254x1254 像素，1.6 MB（过大！）

**优化方法**（任选其一）:
1. **使用 Android Studio**:
   - 右键点击 `ic_launcher_logo.png`
   - 选择 "Convert to WebP"
   - 质量设置为 85-90%
   - 预计可减少 70-80%

2. **手动调整大小**:
   - 标准启动图标尺寸: 512x512（xxxhdpi）
   - 使用图像编辑工具调整大小并导出为 WebP

3. **使用命令行工具**（如果可用）:
   ```bash
   # 使用 ImageMagick
   convert ic_launcher_logo.png -resize 512x512 -quality 85 ic_launcher_logo.webp
   
   # 使用 ffmpeg
   ffmpeg -i ic_launcher_logo.png -vf scale=512:512 ic_launcher_logo.webp
   ```

**预计节省**: ~1.5 MB

### 4. 🔴 压缩 `raw/` 文件夹中的音频文件（当前 2.4 MB）
**当前状态**: 包含斗地主游戏的音效文件

**优化方法**:
1. 使用音频压缩工具（如 Audacity、ffmpeg）压缩 MP3 文件
2. 降低比特率（如从 320kbps 降至 128kbps）
3. 对于短音效，可以考虑使用 OGG 格式（更好的压缩）

**预计节省**: ~1-1.5 MB

### 5. 🔴 转换图片为 WebP 格式
**当前状态**: `res/drawable/` 中包含多个 PNG 文件

**优化方法**:
- 使用 Android Studio 批量转换: `Select all PNGs → Right click → Convert to WebP`
- 或在 `build.gradle` 中启用自动 WebP 转换:
  ```groovy
  android {
      buildTypes {
          release {
              // 已经在 aaptOptions 中设置了 noCompress 'webp'
              // Android 会自动将 PNG 转换为 WebP（需要 Android Studio 4.0+）
          }
      }
  }
  ```

**预计节省**: ~30-50% 图片大小

### 6. 🟡 运行 Lint 查找未使用的资源
**命令**:
```bash
.\gradlew.bat :app:lint
```

**操作后**:
- 查看 Lint 报告（`app/build/reports/lint/lint.html`）
- 删除未使用的资源（图片、布局、字符串等）

**预计节省**: 视项目而定，通常 0.5-2 MB

### 7. 🟡 考虑将 MediaPipe LLM 功能模块化为可选模块
**当前状态**: `libllm_inference_engine_jni.so` (arm64-v8a) 大小为 13 MB

**优化方案**:
- 如果 AI 功能不是核心功能，可以将其作为可选模块
- 用户从模块商店下载 AI 模块（13 MB）
- 框架 APK 减少 13 MB

**预计节省**: 13 MB（如果改为可选模块）

## 优化优先级

| 优先级 | 优化项 | 预计节省 | 难度 |
|--------|---------|----------|------|
| P0 | 移除嵌入 APK | 17.3 MB | ✅ 已完成 |
| P0 | ABI 拆分 | 39 MB | ✅ 已完成 |
| P1 | 优化启动图标 | 1.5 MB | 中 |
| P1 | 压缩音频文件 | 1-1.5 MB | 中 |
| P2 | 转换图片为 WebP | 0.5-1 MB | 低 |
| P2 | 删除未使用资源 | 0.5-2 MB | 低 |
| P3 | MediaPipe 模块化 | 13 MB | 高 |

## 下一步操作

1. **手动优化启动图标**（需要 Android Studio 或图像编辑工具）
2. **压缩音频文件**（使用 Audacity 或 ffmpeg）
3. **运行 Lint** 并删除未使用的资源
4. **构建 Release APK** 并检查大小
5. **如果仍然超过 15 MB**，考虑将 MediaPipe LLM 功能模块化为可选模块

## 构建命令

```bash
# 构建 Release APK（仅 arm64-v8a）
.\gradlew.bat :app:assembleRelease

# 查看 APK 大小
ls -lh app/build/outputs/apk/release/app-release.apk

# 使用 Android Studio APK Analyzer 分析体积
# Build → Analyze APK → 选择 app-release.apk
```

## 检查清单

- [x] 移除嵌入的 APK 文件
- [x] 启用 ABI 拆分（仅 arm64-v8a）
- [ ] 优化启动图标（1.6 MB → <100 KB）
- [ ] 压缩 `raw/` 中的音频文件
- [ ] 转换图片为 WebP 格式
- [ ] 运行 Lint 并删除未使用资源
- [ ] 构建并检查 APK 大小（目标 ≤15 MB）
- [ ] 如果超过 15 MB，考虑 MediaPipe 模块化


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
