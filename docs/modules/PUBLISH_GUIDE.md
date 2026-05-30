# GameMatrixApp 发布指南

## 2026-05-20 GitHub 上传网络说明

- 本机已配�?GitHub-only Git 代理：`git config --global http.https://github.com.proxy http://127.0.0.1:10808`�?- 该配置只影响 `https://github.com`，用于在不开�?xray TUN/虚拟网卡模式时完�?`git push` �?GitHub Release 上传前的 Git 操作�?- 如本地代理端口变化，运行 `powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Apply` 重新检测并写入配置�?
本文档说明如何将 GameMatrixApp APK 自动发布到所有更新源�?
## 重要更新�?026-05-12�?

### 双版本分发架构重�?🎯

�?v1.3.19 开始，发布系统已重构为**双版本分发架�?*�?

#### 核心变化
- **测试版和正式版完全分�?*：VPS 上同时维�?`app-beta.apk` �?`app-release.apk`
- **上传脚本修复**：`upload_to_vps.py` �?`cleanup_remote` 函数现在保护两个通道的文�?
- **更新逻辑优化**�?
  - 用户开�?接收测试�? �?检�?version-beta.json
  - 用户关闭"接收测试�? �?只检�?version-release.json
  - 旧版 APP 使用 /api/update/check API 自动兼容

#### 发布命令

**发布测试�?*�?
```bash
python 工具/upload_to_vps.py --apk app/build/outputs/apk/release/app-release.apk \
    --version app/build/outputs/apk/release/version.json --channel beta
```

**发布正式�?*�?
```bash
python 工具/upload_to_vps.py --apk app/build/outputs/apk/release/app-release.apk \
    --version app/build/outputs/apk/release/version.json --channel release
```

### APK 签名问题已修�?

之前的版本存�?APK 签名配置问题，导致安装包提示"开发者签名异�?。现已修复：

1. **修复 keystore 路径错误**
   - 错误：`storeFile file(props['STORE_FILE'])`
   - 正确：`storeFile rootProject.file(props['STORE_FILE'])`

2. **启用 V1 �?V2 签名方案**
   ```groovy
   signingConfigs {
       release {
           enableV1Signing = true
           enableV2Signing = true
       }
   }
   ```

3. **验证签名**
   ```bash
   cd app\build\outputs\apk\release
   jarsigner -verify app-release.apk
   # 输出：jar 已验�?�?
   ```

### VPS 文件结构

```
/var/www/update/app/
├── app-beta.apk         # 测试版安装包
├── version-beta.json     # 测试版元数据
├── app-release.apk      # 正式版安装包
└── version-release.json  # 正式版元数据
```

---

## 更新源列�?

| 序号 | 更新�?| URL | 类型 | 用�?|
|------|--------|-----|------|------|
| 1 | 香港 VPS | https://your-server.example.com | SFTP | 主要更新源，低延�?|
| 2 | 美国 VPS | https://your-server.example.com:1443 | SFTP | 备用更新�?|
| 3 | GitHub Releases | https://github.com/3571949306/GameMatrixApp/releases | HTTPS API | 公开分发 |

## 前置准备

### 1. 安装依赖

```bash
# 安装 Python 依赖
pip install paramiko requests
```

### 2. 配置签名

在项目根目录创建 `keystore.properties` 文件�?

```properties
# GameMatrixApp 签名配置
STORE_FILE=GameMatrix.keystore
STORE_PASSWORD=<your-store-password>
KEY_ALIAS=GameMatrix
KEY_PASSWORD=<your-key-password>
```

确保 `GameMatrix.keystore` 文件存在于项目根目录�?

### 3. 配置 VPS 凭证

VPS 配置文件位于 `local_private/服务器部�?` 目录（已排除在版本控制外）：

- `upload_config_hk.json` - 香港 VPS 配置
- `upload_config_hk.json` - 美国 VPS 配置

配置示例�?

```json
{
  "host": "your-vps-ip",
  "port": 22,
  "user": "root",
  "authMethod": "password",
  "password": "your-password",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://your-update-domain.com",
  "postUploadCommands": [
    "systemctl restart GameMatrix-update"
  ]
}
```

### 4. 获取 GitHub Token

1. 访问 https://github.com/settings/tokens
2. 创建�?Token，勾�?`repo` 权限
3. 复制 Token 并保存（只显示一次）

## 发布方式

### 方式一：一键发布脚本（推荐�?

#### Windows (批处�?

```bash
# 发布 Beta �?
publish-all.bat beta

# 发布正式�?
publish-all.bat release
```

#### Python 脚本（跨平台�?

```bash
# 发布 Beta �?
python 工具/publish-all.py --channel beta --github-token YOUR_GITHUB_TOKEN

# 发布正式�?
python 工具/publish-all.py --channel release --github-token YOUR_GITHUB_TOKEN

# 只上传到特定更新�?
python 工具/publish-all.py --channel beta --github-token YOUR_TOKEN --sources hk_vps github

# 跳过验证
python 工具/publish-all.py --channel release --github-token YOUR_TOKEN --skip-verify
```

### 方式二：分步执行

#### 步骤 1: 编译 APK

```bash
# 编译 Beta 版（带签名）
gradlew assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 编译正式版（带签名）
gradlew assembleRelease -x lintVitalReportRelease -x lintVitalRelease
```

**注意**：现�?APK 会自动签名，无需手动签名步骤�?

#### 步骤 2: 生成 version.json

```bash
gradlew generateVersionJson
```

version.json 会自动生成到�?
- `app/build/outputs/apk/debug/version.json`
- `app/build/outputs/apk/release/version.json`

#### 步骤 3: 验证签名

```bash
cd app/build/outputs/apk/release
jarsigner -verify app-release.apk
# 输出：jar 已验�?�?
```

#### 步骤 4: 上传�?VPS

```bash
# 上传到香�?VPS + 美国 VPS
python 工具/upload_to_vps.py --apk app/build/outputs/apk/release/app-release.apk \
    --version app/build/outputs/apk/release/version.json \
    --channel beta
```

**注意**：现在使用已签名�?`app-release.apk`，而非 `app-release-unsigned.apk`�?

#### 步骤 5: 上传�?GitHub Releases

```bash
python 工具/upload_to_github_release.py \
    app/build/outputs/apk/release/app-release.apk \
    "v1.3.17"
```

## 发布流程

```mermaid
graph TD
    A[开始] --> B[清理构建缓存]
    B --> C[编译 Release APK]
    C --> D[生成 version.json]
    D --> E{选择发布渠道}
    E -->|Beta| F[上传�?HK VPS]
    E -->|Release| F
    F --> G[上传�?US VPS]
    G --> H[上传�?GitHub Releases]
    H --> I[验证所有更新源]
    I --> J[发布完成]
```

## 验证发布

### 1. 检�?VPS 更新�?

访问以下 URL 确认文件已上传：

- 香港 VPS: https://your-server.example.com/version-beta.json
- 美国 VPS: https://your-server.example.com:1443/version-beta.json

### 2. 检�?GitHub Releases

访问：https://github.com/3571949306/GameMatrixApp/releases

### 3. 应用内检查更�?

在应用设置中切换到对应更新源，点�?检查更�?�?

## 常见问题

### Q: 上传�?VPS 失败

**A:** 检查以下项目：
1. VPS 配置文件是否存在且正�?
2. 网络连接是否正常
3. VPS �?SSH 服务是否运行
4. 防火墙是否允�?SSH 连接

### Q: GitHub Releases 上传失败

**A:** 确认�?
1. GitHub Token 是否有效
2. Token 是否�?`repo` 权限
3. 仓库名称是否正确

### Q: 版本号不匹配

**A:** 确保�?
1. `version.properties` 中的版本号已更新
2. 使用正确�?`-PupdateChannel` 参数
3. 清理旧的构建文件后重新编�?

## 自动化发布（CI/CD�?

项目已配�?GitHub Actions 工作�?(`.github/workflows/ci.yml`)，推送代码时自动构建和上传�?

如需手动触发发布，可以使用：

```bash
# 本地一键发�?
python 工具/publish-all.py --channel beta --github-token ${{ secrets.GITHUB_TOKEN }}
```

## 发布记录

发布记录会自动更新到 `CHANGELOG.md` �?`RELEASE_NOTES_*.md` 文件�?

---

**最后更�?*: 2026-05-11  
**版本**: v1.11.0
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

## 2026-05-19 Modularization Note

The build now includes `:core:common`, `:core:network`, and `:core:update`. Release and upload commands should continue to target `:app` tasks, but maintainers must keep module-level BuildConfig generation in sync when adding new release fields to `local.properties` or `version.properties`.

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店
