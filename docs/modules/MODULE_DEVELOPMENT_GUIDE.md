# 模块开发指南 / Module Development Guide

> **当前参考（最后核验：2026-07-27）**  
> 本文约束新模块的开发与发布。当前发布事实、Catalog 差异、签名门槛与 Runtime 边界见 [`../CURRENT_STATE.md`](../CURRENT_STATE.md) 和 [`../DOCUMENTATION_GOVERNANCE.md`](../DOCUMENTATION_GOVERNANCE.md)。
>
> 当前 Catalog 的正式记录以 `deliveryType=apk` 为主；旧 ZIP/资源包说明仅作为兼容能力，不能假定所有游戏模块都是 ZIP，或假定 ZIP 是新模块的默认生产交付格式。模块作者应以当前 Catalog、`ModuleDownloader`、`ModuleLoader`、`ModuleCoreFacade` 和发布任务为最终合同。

> **文档版本**: v1.1  
> **创建日期**: 2026-05-26  
> **最后更新**: 2026-07-22 (Flutter-first 与宿主导航扩展同步)  
> **维护者**: GameCenterApp Team  
> **版本语义**: 工作版本和最近稳定版均以 `version.properties` 为准；发布模块前必须以当次 APK、Catalog、签名和真机验证为准。  
> **项目根**: d:\Developmment\GameMatrixApp

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

GameCenterApp 采用**模块化架构**，支持动态加载模块。当前平台可处理多种 Runtime/交付方式；**新模块必须以当前 Catalog 和宿主 Runtime 的实际支持为准**。面向用户的模块详情应描述能力、权限、离线性和数据去向，不暴露不必要的交付实现细节。

| 类型 | 当前用途 | 发布要求 |
|---|---|---|
| **Android APK 模块** | 当前 Catalog 的主要交付方式，适用于游戏与包含逻辑/权限/交互的能力 | HTTPS、多源下载信息、SHA-256、签名者验证、兼容性、更新/回滚与卸载语义 |
| **受控资源/内容包** | 仅限宿主 Runtime 已登记并验证的 Web、Asset、Unity 或资源型能力 | 必须经 Catalog 信任、内容 manifest、路径安全与事务安装；不得把外部内容当作任意代码加载 |
| **Flutter 能力** | 仅允许宿主已编译并登记的 Flutter route | 不下载或执行外部 Dart 源码；由宿主声明路由、权限和生命周期 |

### 模块化优势

- **减小初始下载量**：用户只安装自己需要的能力
- **独立更新**：模块可以独立发布更新，无需更新整个 App
- **用户可控**：安装前可了解用途、权限、网络和数据影响，之后可禁用或卸载
- **灵活扩展**：模块在满足平台安全、隐私、生命周期和发布合同后可接入

---

## 兼容交付格式（仅在 Runtime 已支持时使用）

> 这部分描述兼容能力，不代表新游戏模块的默认发布方式。当前 Catalog 以 APK 记录为主；发布前必须按当前 Catalog、签名和 Runtime 验证路径复核。

### 1. 资源/内容包（ZIP 格式）

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

### 2. Android APK 模块

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

> **2026-07-06 复核**：模块源码目录已统一到英文路径 `module-store/feature/`（不再使用中文路径 `模块商店/功能模块/`）。当前实际目录结构：
> - `module-store/feature/games/games/{hall,chinesechess,game2048,klotski,tts}`
> - `module-store/feature/tools/{ai,browser,tools,vpn,wrongbook}`

在 `module-store/feature/tools/` 或 `module-store/feature/games/games/` 目录下创建模块文件夹：

```bash
# 示例：创建新的 tools 子模块
mkdir -p module-store/feature/tools/my_module
cd module-store/feature/tools/my_module
```

### 步骤 2：创建模块入口类

> **2026-07-06 复核**：模块入口接口有两套并存：
> - **`IModule`**（`com.gamecenter.app.interfaces.IModule`）：旧接口，Java 友好，本指南原始示例使用
> - **`ModuleInterface` + `FeatureModule`**（`com.gamecenter.app.core.common`）：新接口，Kotlin 友好，推荐新模块使用
> 
> 当前 9 个动态 APK 模块均使用 `ModuleInterface + FeatureModule` 模式（如 `ToolsModuleEntryPoint : ModuleInterface, FeatureModule`）。新模块**强烈推荐**使用 Kotlin + 新接口。

#### 2a. Kotlin 推荐写法（ModuleInterface + FeatureModule）

```kotlin
package com.gamecenter.app.modules.my_module

import android.content.Context
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.FeatureModule

class MyModuleEntryPoint : ModuleInterface, FeatureModule {
    override val moduleId: String = "my_module"
    override val versionName: String = "1.0.0"
    override val versionCode: Int = 100

    private var loaded: Boolean = false

    override fun onLoad(context: Context) {
        loaded = true
        // 初始化模块
    }

    override fun onUnload() {
        loaded = false
        // 清理资源
    }

    /**
     * FeatureModule: 返回模块主 Fragment 的全限定类名
     * 宿主用 DynamicGameActivity 承载此 Fragment
     */
    override fun createFragmentClassName(): String? =
        "com.gamecenter.app.modules.my_module.MyModuleFragment"
}
```

#### 2b. Java 兼容写法（IModule，旧接口）

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

> **2026-07-06 复核**：构建命令需使用 `module-store:feature:tools:<module>` 或 `module-store:feature:games:games:<module>` 格式（与 `settings.gradle` 中的 include 一致）。

```bash
# 构建 tools 子模块 Release APK
.\gradlew.bat :module-store:feature:tools:my_module:assembleRelease

# 构建 games 子模块 Release APK
.\gradlew.bat :module-store:feature:games:games:my_module:assembleRelease

# 预安装模块场景（需同时重建宿主 APK）
.\gradlew.bat :module-store:feature:tools:wrongbook:assembleDebug -PautoBumpVersion=false --stacktrace
.\gradlew.bat :app:bundlePreinstalledModules -PautoBumpVersion=false --stacktrace
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace

# APK 输出路径
# module-store/feature/tools/my_module/build/outputs/apk/release/my_module-release.apk
# 或预安装到 app/src/main/assets/modules/feature_my_module_v100.apk
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

### 底部导航贡献（Android 功能模块）

需要成为 App 一级入口的 Android APK 模块，可以在已签名 Catalog/`modules.json` 中声明：

```json
"navigationContribution": {
  "slot": "bottom_nav",
  "title": "新专区",
  "icon": "extension",
  "order": 15,
  "enabled": true
}
```

- `order` 越小越靠前；游戏大厅默认是 `10`，因此 `15` 默认位于游戏大厅之后。
- `icon` 只能使用宿主白名单键：`games/browser/tools/ai/vpn/profile/extension`。动态 APK 的 `R.drawable` ID 不得传给宿主。
- 模块入口仍需实现 `ModuleInterface + FeatureModule`，且 `entryClass` 必须进入权威清单；点击标签时由 `ModuleShellFragment` 加载 `FeatureModule.createFragment()`。
- 只有已安装、启用、版本兼容且声明为 `bottom_nav` 的模块会参与导航。普通小游戏应贡献到 `games_hall`，不要占用稀缺的一级标签。
- 用户可以在“设置 → 数据与导航 → 底部导航”中重新排序或隐藏入口；游戏大厅不能隐藏，最多显示 6 项。
- 新的 Flutter route 仍必须预编译进宿主，不能用此字段远程注入 Dart 代码。

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

> **2026-07-06 复核**：当前实际模块 ID 已扩展，新增 `hall`/`tts`/`wrongbook` 等不带前缀的 ID（与 `modules.json` 实际字段一致）。

| 类型 | 前缀 | 示例 |
|------|------|------|
| 游戏大厅 | `games_hall` 或 `hall` | `games_hall`（动态 APK）/ `hall`（模块 ID） |
| 游戏 | `game_` 或无前缀 | `game_2048`、`game_snake`、`chinesechess`、`klotski`、`game2048` |
| TTS 语音 | `tts` | `tts` |
| 工具 | `tool_` 或 `tools` | `tool_qrcode`、`tool_hash`、`tools`（整包） |
| 浏览器 | `browser` | `browser` |
| AI | `ai` | `ai` |
| VPN | `vpn` | `vpn` |
| 错题本 | `wrongbook` | `wrongbook`（循环 20 新增） |

### B. 目录结构参考

> **2026-07-06 复核**：模块源码目录已统一到英文路径 `module-store/feature/`，预安装模块 APK 位于 `app/src/main/assets/modules/`。

```
GameMatrixApp/
├── module-store/feature/        # 动态 APK 模块源码（2026-07-06 实际结构）
│   ├── games/games/             # 游戏模块
│   │   ├── hall/                # 游戏大厅
│   │   ├── chinesechess/        # 中国象棋
│   │   ├── game2048/            # 2048
│   │   ├── klotski/             # 华容道
│   │   ├── tts/                 # TTS 语音合成
│   │   ├── snake/               # 贪吃蛇（2026-07-24 新增）
│   │   ├── tic/                 # 井字棋（2026-07-24 新增）
│   │   ├── whack/               # 打地鼠（2026-07-24 新增）
│   │   ├── reaction/            # 反应力（2026-07-24 新增）
│   │   └── rock/                # 猜拳（2026-07-24 新增）
│   └── tools/                   # 工具模块
│       ├── ai/                  # AI 助手
│       ├── browser/             # 浏览器
│       ├── tools/               # 工具箱
│       ├── vpn/                 # VPN
│       └── wrongbook/           # 错题本（循环 20 新增）
├── app/src/main/assets/modules/ # 预安装模块 APK
│   ├── feature_games_hall_v100.apk
│   ├── feature_chinesechess_v200.apk
│   ├── feature_game2048_v100.apk
│   ├── feature_klotski_v200.apk
│   ├── feature_tts_v100.apk
│   ├── feature_ai_v100.apk
│   ├── feature_browser_v100.apk
│   ├── feature_tools_v100.apk
│   ├── feature_vpn_v100.apk
│   └── feature_wrongbook_v100.apk  # 循环 20 新增
├── core/                        # 核心库模块
│   ├── common/                  # 通用库（ModuleInterface / FeatureModule / IModule）
│   ├── moduleloader/            # 模块加载器（DexClassLoader + AssetManager.addAssetPath）
│   ├── module-host/             # ClassLoader 池
│   ├── modulestore/             # 模块商店
│   ├── network/                 # 网络库
│   ├── update/                  # 更新库
│   └── security/                # 安全库
└── app/src/main/assets/modules.json  # 兜底模块清单
```

### C. 参考资料

1. [Android Dynamic Feature Modules 官方文档](https://developer.android.com/studio/projects/dynamic-delivery)
2. [DexClassLoader 官方文档](https://developer.android.com/reference/dalvik/system/DexClassLoader)
3. [OkHttp 官方文档](https://square.github.io/okhttp/)

---

## D. ModuleContextHelper 与 ModuleShellFragment (2026-07-06 新增)

> **循环 19-24 复核**：宿主提供了 `ModuleContextHelper` 与 `ModuleShellFragment` 两个工具类，模块开发者应优先复用，避免重复造轮子。

### D.1 ModuleContextHelper

位于 `core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/ModuleContextHelper.kt`（或 `core/module-host`，以实际仓库为准）。

**职责**：

- 包装模块 Context（通过 `AssetManager.addAssetPath()` 反射 + `ContextWrapper`）
- 提供模块资源访问入口（`getDrawable` / `getString` / `getLayout` / `getIdentifier`）
- 解决跨 APK 边界的 R 类引用问题（模块不能用宿主 `R.layout.xx` 引用宿主资源）

**使用示例**：

```kotlin
// 在模块入口类中
val moduleContext = ModuleContextHelper.createModuleContext(hostContext, apkFile)
val layoutId = moduleContext.resources.getIdentifier(
    "activity_my_module", "layout", moduleContext.packageName
)
val view = LayoutInflater.from(moduleContext).inflate(layoutId, null)
```

### D.2 ModuleShellFragment

宿主提供的 `ModuleShellFragment` 用于承载模块 Fragment，解决动态 APK 内 Fragment 无法直接通过 `FragmentManager` 加载的问题。

**使用示例**：

```kotlin
// 宿主侧：加载模块 Fragment
val fragmentClassName = moduleEntryPoint.createFragmentClassName()
val fragment = ModuleShellFragment.instantiate(hostContext, fragmentClassName, apkFile)
supportFragmentManager.beginTransaction()
    .replace(R.id.container, fragment)
    .commit()
```

---

## E. 预安装模块流程 (2026-07-06 新增)

> **循环 20 引入 wrongbook 模块后**，预安装模块流程需要规范化。预安装模块 = 动态 APK 编译后拷贝到 `app/src/main/assets/modules/`，随宿主 APK 一起分发。

### E.1 何时使用预安装

- 模块需要在首次启动时立即可用（不依赖网络下载）
- 模块作为基础框架的一部分（`isBaseFramework: true`）
- 模块作为核心入口（如 games_hall、tools、ai）

### E.2 预安装构建命令

```powershell
# 1. 构建动态模块 APK
.\gradlew.bat :module-store:feature:tools:wrongbook:assembleDebug -PautoBumpVersion=false --stacktrace

# 2. 同步到 app assets（bundlePreinstalledModules 任务会拷贝并重命名）
.\gradlew.bat :app:bundlePreinstalledModules -PautoBumpVersion=false --stacktrace

# 3. 重建宿主 APK（必须，否则 assets 不会更新）
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

### E.3 Feature Flag 控制

预安装模块应在 `app/build.gradle` 中添加 feature flag，便于紧急禁用：

```groovy
android {
    defaultConfig {
        buildConfigField "boolean", "ENABLE_WRONGBOOK", "true"
    }
}
```

宿主代码通过 `BuildConfig.ENABLE_WRONGBOOK` 判断是否注册模块入口与显示导航 tab。

### E.4 验证清单

- [ ] 模块 APK 已生成到 `module-store/feature/tools/<module>/build/outputs/apk/debug/`
- [ ] 模块 APK 已同步到 `app/src/main/assets/modules/feature_<module>_v<version>.apk`
- [ ] `app/src/main/assets/modules.json` 中模块元数据正确（`sha256` / `fileSize` / `versionCode`）
- [ ] 宿主 APK 已重建（`app-debug.apk` 中包含最新 assets）
- [ ] Feature Flag 已配置（如需要）
- [ ] 浅色/深色主题下 UI 正常
- [ ] 中英文环境下字符串正常显示

---

## F. Feature Flag / 主题适配 / 本地化 / 资源构建链路 (2026-07-06 新增)

> **循环 19-24 复核**：新增模块必须遵守以下规范，避免破坏宿主一致性。

### F.1 Feature Flag 规范

- 所有新增功能**必须**有 feature flag（用户规则 #17）
- 在 `app/build.gradle` 的 `defaultConfig.buildConfigField` 中声明
- 命名：`ENABLE_<MODULE_NAME>`（如 `ENABLE_WRONGBOOK`、`GOMOKU_ENHANCED`）
- 默认值为 `"true"`，发布前可根据测试结果调整
- 宿主代码通过 `BuildConfig.ENABLE_XXX` 引用

### F.2 主题适配规范

- 所有新增 UI **必须**支持浅色和深色主题（用户规则 #18）
- 颜色资源放 `app/src/main/res/values/colors.xml`（浅色）+ `values-night/colors.xml`（深色）
- 优先使用 Material 3 token：`?attr/colorPrimary`、`?attr/colorSurface`、`?attr/colorOnSurface` 等
- 避免硬编码颜色（如 `0xFF9E9E9E`），改用 `?attr/colorSurfaceVariant`
- 圆角/间距/字号使用 `dimens.xml` token：`@dimen/spacing_*`、`@dimen/corner_radius_*`、`@dimen/font_size_*`
- 启用 Edge-to-Edge：`EdgeToEdge.enable()`（硬约束）

### F.3 本地化规范

- 所有新增字符串**必须**考虑本地化（用户规则 #19）
- 默认中文：`app/src/main/res/values/strings.xml`
- 英文翻译：`app/src/main/res/values-en/strings.xml`
- 命名：`<module>_<feature>_<purpose>`（如 `wrongbook_ocr_recognizing`）
- 避免硬编码文本到 layout XML，统一走 strings.xml
- 游戏专用字符串可放 `values/strings_game_<game>.xml` + `values-en/strings_game_<game>.xml`

### F.4 资源构建链路规范

- 所有新增资源**必须**进入正确的资源构建链路（用户规则 #20）
- 模块内资源放 `module-store/feature/<category>/<module>/src/main/res/`
- 宿主共享资源放 `app/src/main/res/` 或 `core/common/src/main/res/`
- 命名规范：`activity_*`、`fragment_*`、`dialog_*`、`item_*`、`ic_*`、`bg_*`
- 图标优先用 vector drawable（`ic_*.xml`），启动器图标用 `mipmap-anydpi-v26`
- 新增 drawable 需检查 `app/build.gradle` 的 `resConfigs "zh-rCN", "en"` 是否影响密度拆分

---

**文档结束 / End of Document**


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
# Flutter-first Multi-runtime 补充（2026-07-21）

新增模块必须先选择 `runtimeType`（`flutter/web/asset/android/native_service/unity`）与 `deliveryType`（`builtin/apk/zip/content`），并遵循 `docs/flutter-store/CATALOG_V2.md`。生命周期只能通过 `ModuleCoreFacade` 和对应 `ModuleRuntimeHandler`，不得在 Flutter 中直接操作 APK、DEX、服务、Unity 或原生文件路径。

- Flutter：只登记宿主已编译 route，不下载 Dart 源码。
- Web/Asset：ZIP 必须有 HTTPS、SHA-256、正式包签名并通过隔离解压；Web 默认禁用 JavaScript，禁止任意 bridge/外域。
- Android：继续使用现有宿主证书校验和宿主代理/入口，动态 APK Activity/资源不能按普通 in-process 组件假设。
- Native Service：`serviceType` 必须加入原生能力白名单并单独审计权限。
- Unity：使用 launcher ID 和既有 Unity manager；content 与代码生命周期分开。

提交模块前至少增加 Catalog 解析/兼容测试、handler 测试以及受控包安装/回滚测试，并在真机检查目标流 logcat。Flutter 商店稳定前不要删除旧 `ModuleStoreActivity` 或旧目录适配器。
