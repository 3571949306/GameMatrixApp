# 模块开发指南 / Module Development Guide

> **文档版本**: v1.0  
> **创建日期**: 2026-05-26  
> **维护者**: GameCenterApp Team

---

## 目录 / Table of Contents

1. [模块概述](#模块概述)
2. [模块类型](#模块类型)
3. [创建游戏模块 (ZIP)](#创建游戏模块-zip)
4. [创建功能模块 (APK)](#创建功能模块-apk)
5. [模块清单格式](#模块清单格式)
6. [发布模块到模块商店](#发布模块到模块商店)
7. [测试模块](#测试模块)
8. [常见问题](#常见问题)

---

## 模块概述 / Module Overview

GameCenterApp 采用**模块化架构**，支持动态加载模块。模块分为两类：

| 模块类型 | 格式 | 用途 | 示例 |
|----------|------|------|------|
| **游戏模块** | ZIP (资源包) | 纯资源游戏（无逻辑代码） | 2048、贪吃蛇、俄罗斯方块 |
| **功能模块** | APK (动态插件) | 包含逻辑代码的功能扩展 | 浏览器、工具箱、AI 助手、VPN |

### 模块化优势

- **减小初始 APK 体积**：框架 APK ≤15MB，其他功能按需下载
- **独立更新**：模块可以独立发布更新，无需更新整个 App
- **灵活扩展**：第三方开发者可以开发并发布模块

---

## 模块类型 / Module Types

### 1. 游戏模块 (ZIP 格式)

**适用场景**：
- 游戏逻辑已内置在框架中
- 只需要替换资源（图片、音效、布局）
- 简单的单机游戏

**文件结构**：
```
game_module.zip/
├── module.json           # 模块清单（必须）
├── res/                  # 资源文件夹（可选）
│   ├── drawable/        # 图片资源
│   ├── raw/             # 音频资源
│   └── layout/         # 布局文件（如果需要自定义 UI）
└── assets/              # 其他资源（可选）
```

### 2. 功能模块 (APK 格式)

**适用场景**：
- 需要独立的逻辑代码
- 需要新的 Activity/Service
- 需要访问系统权限
- 复杂的交互逻辑

**文件结构**：
```
FeatureModule/
├── app/                          # 模块 APK 项目
│   ├── src/main/
│   │   ├── java/com/gamecenter/app/modules/<module_id>/
│   │   │   ├── <ModuleName>Module.java   # 模块入口类（实现 IModule）
│   │   │   ├── activities/                 # Activity 类
│   │   │   └── services/                  # Service 类（可选）
│   │   ├── res/                          # 资源文件夹
│   │   └── AndroidManifest.xml            # 模块清单
│   ├── build.gradle                      # 模块构建配置
│   └── proguard-rules.pro               # 混淆规则（可选）
└── module.json                         # 模块元数据
```

---

## 创建游戏模块 (ZIP) / Create Game Module (ZIP)

### 步骤 1：创建模块清单

创建 `module.json`：

```json
{
  "moduleId": "game_2048",
  "moduleName": "2048",
  "versionName": "1.0.0",
  "versionCode": 100,
  "type": "game",
  "author": "Your Name",
  "description": "经典 2048 数字合并游戏",
  "downloadUrl": "https://your-server.com/modules/game_2048.zip",
  "fileSize": 1024000,
  "sha256": "<计算出的 SHA-256 值>",
  "minFrameworkVersion": 100,
  "targetSdk": 35
}
```

### 步骤 2：准备资源文件

将资源文件放入对应文件夹：

```
game_2048/
├── module.json
└── res/
    └── drawable/
        └── ic_2048.png   # 游戏图标（512x512 推荐）
```

### 步骤 3：打包成 ZIP

```bash
# 进入模块文件夹
cd game_2048/

# 打包成 ZIP
zip -r game_2048.zip *
```

### 步骤 4：计算 SHA-256

```bash
# Linux/Mac
shasum -a 256 game_2048.zip

# Windows (PowerShell)
Get-FileHash game_2048.zip -Algorithm SHA256
```

将计算出的 SHA-256 值更新到 `module.json` 的 `sha256` 字段。

---

## 创建功能模块 (APK) / Create Feature Module (APK)

### 步骤 1：创建模块项目

在 `模块商店/功能模块/` 目录下创建模块文件夹：

```bash
mkdir -p "模块商店/功能模块/my_module"
cd "模块商店/功能模块/my_module"
```

### 步骤 2：创建模块入口类

创建 `MyModule.java`（实现 `IModule` 接口）：

```java
package com.gamecenter.app.modules.my_module;

import android.content.Context;
import com.gamecenter.app.interfaces.IModule;

public class MyModule implements IModule {
    private Context context;
    private boolean loaded = false;
    
    @Override
    public String getModuleId() {
        return "my_module";
    }
    
    @Override
    public String getVersionName() {
        return "1.0.0";
    }
    
    @Override
    public int getVersionCode() {
        return 100;
    }
    
    @Override
    public void onLoad(Context context) {
        this.context = context;
        this.loaded = true;
        // 初始化模块
        initModule();
    }
    
    @Override
    public void onUnload() {
        // 清理资源
        cleanup();
        this.loaded = false;
    }
    
    @Override
    public void onUpdate(ModuleVersion newVersion) {
        // 处理模块更新
        Log.i("MyModule", "Updating to version: " + newVersion.getVersionName());
    }
    
    private void initModule() {
        // 初始化逻辑
    }
    
    private void cleanup() {
        // 清理逻辑
    }
}
```

### 步骤 3：创建 Activity（如果需要）

创建 `MyModuleActivity.java`：

```java
package com.gamecenter.app.modules.my_module;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MyModuleActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_module);
    }
}
```

### 步骤 4：配置 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.gamecenter.app.modules.my_module">

    <application>
        <meta-data
            android:name="module_id"
            android:value="my_module" />
        <meta-data
            android:name="module_version"
            android:value="1.0.0" />
        <meta-data
            android:name="module_type"
            android:value="tool" />
            
        <activity
            android:name=".MyModuleActivity"
            android:exported="true" />
    </application>

</manifest>
```

### 步骤 5：配置 build.gradle

```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace "com.gamecenter.app.modules.my_module"
    compileSdk 35
    
    defaultConfig {
        applicationId "com.gamecenter.app.modules.my_module"
        minSdk 24
        targetSdk 35
        versionCode 100
        versionName "1.0.0"
    }
    
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    // 依赖框架接口（必须）
    implementation project(':core:common')
    
    // 其他依赖
    implementation 'androidx.appcompat:appcompat:1.7.1'
}
```

### 步骤 6：构建模块 APK

```bash
# 构建 Release APK
./gradlew :modules:my_module:assembleRelease

# APK 输出路径
# app/build/outputs/apk/release/my_module-release.apk
```

---

## 模块清单格式 / Module Metadata Format

### modules.json（模块商店清单）

上传到 VPS 的 `modules.json` 格式：

```json
{
  "version": 11,
  "modules": [
    {
      "moduleId": "game_2048",
      "moduleName": "2048",
      "versionName": "1.0.0",
      "versionCode": 100,
      "type": "game",
      "builtIn": false,
      "author": "GameCenterApp Team",
      "description": "经典 2048 数字合并游戏",
      "downloadUrl": "https://your-vps.com/modules/game_2048.zip",
      "fileSize": 1024000,
      "sha256": "abc123...",
      "storeCategory": "puzzle",
      "minFrameworkVersion": 100,
      "targetSdk": 35,
      "permissions": [],
      "dependencies": []
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `moduleId` | String | 模块唯一 ID（格式：`类型_名称`） |
| `moduleName` | String | 模块显示名称 |
| `versionName` | String | 版本名称（语义化版本） |
| `versionCode` | int | 版本号（整数，用于比较） |
| `type` | String | 模块类型（`game`/`tool`/`browser`/`ai`/`vpn`） |
| `builtIn` | boolean | 是否内置（内置模块随 APK 发布） |
| `author` | String | 作者/开发者 |
| `description` | String | 模块描述 |
| `downloadUrl` | String | 下载地址 |
| `fileSize` | long | 文件大小（字节） |
| `sha256` | String | SHA-256 校验值 |
| `storeCategory` | String | 商店分类（`puzzle`/`arcade`/`tool` 等） |
| `minFrameworkVersion` | int | 最低框架版本要求 |
| `targetSdk` | int | 目标 SDK 版本 |
| `permissions` | String[] | 需要的权限列表 |
| `dependencies` | String[] | 依赖的模块 ID 列表 |

---

## 发布模块到模块商店 / Publish Module to Module Store

### 步骤 1：准备模块文件

- **游戏模块**：ZIP 文件
- **功能模块**：APK 文件

### 步骤 2：上传到 VPS

```bash
# 使用 SCP 上传
scp game_2048.zip user@your-vps.com:/var/www/modules/

# 使用 RSYNC 上传（推荐，支持断点续传）
rsync -avz --progress game_2048.zip user@your-vps.com:/var/www/modules/
```

### 步骤 3：更新 modules.json

在 VPS 上编辑 `/var/www/update/modules/modules.json`，添加新模块信息。

### 步骤 4：验证模块

在 App 中打开模块商店，检查：
- 新模块是否显示
- 下载是否成功
- 安装后是否能正常打开

---

## 测试模块 / Test Module

### 本地测试（功能模块）

1. **将模块 APK 推送到设备**：

```bash
adb push my_module-release.apk /sdcard/Download/
```

2. **在 App 中手动安装**：

- 打开 GameCenterApp
- 进入"设置" → "高级" → "安装本地模块"
- 选择 APK 文件

3. **查看日志**：

```bash
adb logcat | grep "ModuleLoader"
```

### 远程测试（模块商店）

1. **上传到测试服务器**
2. **在 App 中切换到测试服务器地址**（修改 `local.properties` 中的 `server.url`）
3. **下载并测试模块**

---

## 常见问题 / FAQ

### Q1：模块加载失败怎么办？

**可能原因**：
1. 模块 ID 不匹配
2. APK 签名验证失败
3. SHA-256 校验失败
4. 模块依赖未满足

**解决方法**：
- 检查 `moduleId` 是否一致
- 确认 APK 已签名
- 重新计算 SHA-256 并更新 `modules.json`
- 检查 `dependencies` 字段

### Q2：如何调试模块？

在模块代码中添加日志：

```java
import android.util.Log;

Log.d("MyModule", "Module loaded successfully");
```

查看日志：

```bash
adb logcat -s MyModule
```

### Q3：模块可以使用哪些权限？

模块可以声明并使用以下权限（需在 `module.json` 中声明）：

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CAMERA`
- `android.permission.READ_EXTERNAL_STORAGE`（已废弃，使用 SAF）
- 等等

**注意**：敏感权限（如 `READ_CONTACTS`）需要用户授权。

### Q4：如何在模块间通信？

使用事件总线或接口：

```java
// 定义接口（在 :core:common 中）
public interface IEventBus {
    void post(Event event);
    void register(EventListener listener);
}

// 在模块中使用
EventBus.getInstance().post(new MyEvent());
```

### Q5：模块 APK 体积太大怎么办？

优化建议：
1. 启用 R8 混淆（`minifyEnabled true`）
2. 启用资源收缩（`shrinkResources true`）
3. 使用 ABI 拆分（仅包含需要的架构）
4. 压缩图片资源（WebP 格式）
5. 删除未使用的依赖

---

## 附录 / Appendix

### A. 模块 ID 命名规范

| 类型 | 前缀 | 示例 |
|------|------|------|
| 游戏 | `game_` | `game_2048`、`game_snake` |
| 工具 | `tool_` | `tool_qrcode`、`tool_hash` |
| 浏览器 | `browser_` | `browser_main` |
| AI | `ai_` | `ai_assistant` |
| VPN | `vpn_` | `vpn_core` |

### B. 目录结构参考

```
GameCenterApp/
├── 模块商店/
│   ├── 压缩模块/              # ZIP 格式游戏模块
│   │   ├── game_2048.zip
│   │   └── game_snake.zip
│   └── 功能模块/              # APK 格式功能模块源码
│       ├── games/
│       │   ├── game_2048/
│       │   └── game_snake/
│       ├── browser/
│       ├── tools/
│       ├── ai/
│       └── vpn/
```

### C. 参考资料

1. [Android Dynamic Feature Modules 官方文档](https://developer.android.com/studio/projects/dynamic-delivery)
2. [DexClassLoader 官方文档](https://developer.android.com/reference/dalvik/system/DexClassLoader)
3. [OkHttp 官方文档](https://square.github.io/okhttp/)

---

**文档结束 / End of Document**
