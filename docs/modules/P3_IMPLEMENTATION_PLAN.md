# P3 实施计划：目录签名 + 事务安装

**日期**: 2026-07-20  
**分支**: `feature/hybrid-module-store-phase1`  
**目标**: 实现Ed25519目录签名验证和事务性模块安装系统

## 实施完成报告

**完成时间**: 2026-07-20  
**状态**: ✅ 已完成并通过真机部署测试

### 已实现内容
1. `CatalogSignatureVerifier` 接口与 `Ed25519CatalogSignatureVerifier` 实现（Tink 1.10.0）
2. `StoreCatalogRepository` 集成签名验证，从 `X-Catalog-Signature` 响应头读取签名
3. `TransactionInstaller`：`staging/current/last_good/quarantine` 目录结构与原子提升
4. `ModuleDownloader` 下载到 `staging/`
5. `ModuleManager` 集成事务安装
6. `ModuleLoader` 优先从 `current/` 加载，失败时自动回滚 `last_good/`
7. Feature Flags：`ENABLE_CATALOG_SIGNATURE`（false，兼容模式）、`ENABLE_TRANSACTIONAL_INSTALL`（true）

### 验证结果
- `\.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace` **BUILD SUCCESSFUL**
- 真机测试（小米 ares M2012K10C）：模块商店启动/分类切换/搜索/详情/主入口均正常
- logcat 无 FATAL EXCEPTION

### 遗留问题
- 目录签名公钥当前为占位符，`ENABLE_CATALOG_SIGNATURE=false` 处于兼容模式
- 安装状态仍使用 SharedPreferences，未迁移到 Room
- 单元测试待补充（`CatalogSignatureVerifierTest`、`TransactionInstallerTest`）

## 一、P3 目标

### P3.1 目录签名（Ed25519）
- 实现`CatalogSignatureVerifier`接口
- 使用Ed25519算法验证目录签名
- 私钥仅保存在发布环境
- 公钥内置主APK
- 不允许从服务器动态下载公钥
- 明确当前处于兼容模式
- 为正式启用预留BuildConfig开关

### P3.2 事务安装
- 实现`staging/current/last_good/quarantine`目录结构
- 下载完成后先进入`staging`
- 验证通过后原子提升到`current`
- 保留上一版本到`last_good`
- 加载失败时自动回滚到`last_good`
- 严重问题模块移入`quarantine`

### P3.3 测试验证
- 编译测试
- 单元测试
- 真机测试

### P3.4 文档更新
- 更新MODULE_STORE_REDESIGN_PLAN.md
- 更新修改记录.md

### P3.5 输出最终报告

## 二、现有代码分析

### 2.1 现有签名验证机制
- `ModuleVerifier.kt`：只实现了SHA-256校验，没有Ed25519签名验证
- `ModuleSignatureVerifier.kt`：APK签名验证，但不是目录签名

### 2.2 现有模块安装流程
- `ModuleManager.downloadModule()`：下载模块
- `ModuleLoader.loadModule()`：加载模块
- 没有staging/current/last_good/quarantine目录结构
- 没有回滚机制

### 2.3 现有模块存储路径
- 模块文件存储在`context.filesDir/modules/`
- 优化后的DEX存储在`context.cacheDir/modules_opt/`
- 库文件存储在`context.filesDir/modules_lib/`

## 三、实施步骤

### 步骤1：创建目录签名验证器

**文件**: `app/src/main/java/com/gamecenter/app/modules/store/CatalogSignatureVerifier.kt`

**功能**:
- 定义`CatalogSignatureVerifier`接口
- 实现`Ed25519CatalogSignatureVerifier`
- 使用Tink库进行Ed25519签名验证
- 公钥硬编码在代码中
- 当前处于兼容模式（不强制验证）
- BuildConfig开关控制是否启用

**关键代码**:
```kotlin
interface CatalogSignatureVerifier {
    fun verify(catalog: String, signature: String): Boolean
}

class Ed25519CatalogSignatureVerifier : CatalogSignatureVerifier {
    private val publicKey = "..." // 硬编码公钥
    
    override fun verify(catalog: String, signature: String): Boolean {
        // 使用Tink验证Ed25519签名
    }
}
```

### 步骤2：修改StoreCatalogRepository支持签名验证

**文件**: `app/src/main/java/com/gamecenter/app/modules/store/StoreCatalogRepository.kt`

**修改**:
- 在`refresh()`方法中添加签名验证
- 如果BuildConfig.ENABLE_CATALOG_SIGNATURE=true，强制验证
- 如果BuildConfig.ENABLE_CATALOG_SIGNATURE=false，记录警告但不阻止
- 验证失败时降级到缓存

### 步骤3：实现事务性安装系统

**文件**: `app/src/main/java/com/gamecenter/app/modules/ModuleInstallManager.kt`

**功能**:
- 定义目录结构：
  - `staging/`: 下载中的模块
  - `current/`: 当前使用的模块
  - `last_good/`: 上一个稳定版本
  - `quarantine/`: 有问题的模块
- 实现安装事务：
  1. 下载到`staging/`
  2. 验证SHA-256和签名
  3. 原子移动到`current/`
  4. 备份旧版本到`last_good/`
- 实现回滚机制：
  - 加载失败时自动回滚到`last_good/`
  - 严重问题移入`quarantine/`

### 步骤4：修改ModuleManager使用事务安装

**文件**: `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt`

**修改**:
- `downloadModule()`使用`ModuleInstallManager`
- `loadModule()`添加失败回滚逻辑
- 添加`rollbackModule()`方法

### 步骤5：修改ModuleLoader支持回滚

**文件**: `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt`

**修改**:
- `loadModule()`失败时调用`ModuleInstallManager.rollbackModule()`
- 添加加载状态跟踪

### 步骤6：添加BuildConfig开关

**文件**: `app/build.gradle`

**修改**:
```gradle
buildConfigField "boolean", "ENABLE_CATALOG_SIGNATURE", "false"
buildConfigField "boolean", "ENABLE_TRANSACTIONAL_INSTALL", "true"
```

### 步骤7：添加单元测试

**文件**: `app/src/test/java/com/gamecenter/app/modules/store/CatalogSignatureVerifierTest.kt`

**测试用例**:
- 有效签名验证通过
- 无效签名验证失败
- 空签名处理
- 兼容模式测试

**文件**: `app/src/test/java/com/gamecenter/app/modules/ModuleInstallManagerTest.kt`

**测试用例**:
- 安装事务流程
- 回滚机制
- 目录结构管理
- 并发安装处理

### 步骤8：编译和测试

- 执行`./gradlew :app:assembleDebug`
- 执行`./gradlew :app:testDebugUnitTest`
- 真机测试

### 步骤9：文档更新

- 更新MODULE_STORE_REDESIGN_PLAN.md
- 更新修改记录.md

### 步骤10：输出最终报告

## 四、依赖管理

### 4.1 Tink库依赖
需要在`app/build.gradle`中添加Tink依赖：
```gradle
implementation 'com.google.crypto.tink:tink-android:1.10.0'
```

### 4.2 公钥管理
- 生成Ed25519密钥对
- 私钥保存在VPS发布环境
- 公钥硬编码在代码中
- 后续可以实现密钥轮换机制

## 五、风险评估

### 5.1 技术风险
- Tink库兼容性：需要测试Android各版本兼容性
- 性能影响：签名验证可能增加加载时间
- 事务安装：原子操作需要谨慎处理

### 5.2 兼容性风险
- 现有模块需要迁移到新目录结构
- 需要保持向后兼容

### 5.3 安全风险
- 私钥泄露风险：需要严格保护VPS上的私钥
- 公钥硬编码：需要实现密钥轮换机制

## 六、验收标准

1. 目录签名验证通过
2. 安装失败可自动回滚
3. 现有测试全部通过
4. 新增事务安装测试
5. 真机验证无FATAL EXCEPTION

## 七、回滚方法

### 7.1 禁用功能
```gradle
buildConfigField "boolean", "ENABLE_CATALOG_SIGNATURE", "false"
buildConfigField "boolean", "ENABLE_TRANSACTIONAL_INSTALL", "false"
```

### 7.2 代码回滚
```bash
git revert <commit-sha>
```

## 八、时间估算

- 步骤1-2：2小时
- 步骤3-5：3小时
- 步骤6-7：1小时
- 步骤8-10：1小时
- **总计**: 7小时

## 九、下一步

P3完成后，进入P4阶段：
- 将商店UI迁移为可更新动态APK
- 实现动态导航贡献
- 实现动态模块更新
