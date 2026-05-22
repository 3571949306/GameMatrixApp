# 项目AI接手说明

## 1. 当前项目概况

- 项目名称：`GameMatrixApp`
- GitHub 仓库：`https://github.com/3571949306/GameMatrixApp.git`
- 主维护分支：`main`
- Android 构建核心目录：`app/`
- 发布相关脚本目录：`tools/`、`docs/`、`local_private/`

建议 AI 或新维护者接手时，先读这几份文档：

- `README.md`：项目总览、更新分发架构、文档入口
- `PROJECT_CONTEXT.md`：维护约束、仓库规则、接手背景
- `CODE_WIKI.md`：代码结构与模块说明
- `docs/LOCAL_GITHUB_NETWORK.md`：本机 GitHub 访问与代理说明
- `docs/PUBLISH_GUIDE.md`：发布链路历史说明

## 2. 当前发布架构

当前项目的安装包分发是三路结构：

1. 香港 VPS：主更新源
2. GitHub Releases：正式版公开分发源
3. 美国 VPS：备用更新源

版本策略分两类：

- `beta`：上传到香港 VPS 和美国 VPS
- `stable/release`：上传到香港 VPS、美国 VPS、GitHub Releases

VPS 上的更新目录约定为：

```text
/var/www/update/app/
├── app-beta.apk
├── version-beta.json
├── app-release.apk
└── version-release.json
```

本地构建产物默认使用：

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/release/version.json
```

## 3. 如何连接 VPS

### 3.1 配置文件位置

VPS 凭证不进 Git，放在：

```text
local_private/vps/
```

脚本会自动读取：

- `upload_config_hk.json`
- `upload_config_us.json`
- 其他匹配 `upload_config_*.json` 的文件

每个配置至少应包含这些字段：

```json
{
  "host": "服务器地址",
  "port": 22,
  "user": "root",
  "authMethod": "password 或 key",
  "password": "仅本地保存",
  "identityFile": "可选，私钥路径",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://对应公网下载地址",
  "postUploadCommands": []
}
```

### 3.2 实际上传脚本

仓库当前用于连接 VPS 的脚本是：

```text
tools/upload_to_vps.py
```

它使用 `paramiko` 通过 SSH/SFTP 连接 VPS，默认会：

1. 读取 `local_private/vps/upload_config_*.json`
2. 上传 `app-release.apk` 和 `version.json`
3. 在远端重命名为通道文件名
4. 清理旧的 `.apk` / `.json` 文件，但保留 `beta` 和 `release` 两套当前文件
5. 按 `publicBaseUrl` 校验公网可访问性

### 3.3 常用命令

先安装依赖：

```powershell
python -m pip install paramiko
```

上传测试版到所有已配置 VPS：

```powershell
python tools\upload_to_vps.py --channel beta
```

上传正式版到所有已配置 VPS：

```powershell
python tools\upload_to_vps.py --channel release
```

如果要指定文件路径：

```powershell
python tools\upload_to_vps.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version app\build\outputs\apk\release\version.json `
  --channel release
```

### 3.4 VPS 上传后的检查

至少检查这几个地址是否可访问：

- 香港测试版：`https://hk-update.tcp0053.shop/version-beta.json`
- 美国测试版：`https://tcp0053.shop:1443/version-beta.json`
- 香港正式版：`https://hk-update.tcp0053.shop/version-release.json`
- 美国正式版：`https://tcp0053.shop:1443/version-release.json`

如果只需要看美国备用更新源是否仍然正常，优先检查：

- `https://tcp0053.shop:1443/version-beta.json`

## 4. 如何上传到 GitHub

### 4.1 远程仓库

当前 `origin` 为：

```text
https://github.com/3571949306/GameMatrixApp.git
```

普通提交命令：

```powershell
git add .
git commit -m "你的提交说明"
git push origin main
```

### 4.2 本机 GitHub 连接方式

当前机器的 GitHub 访问依赖本地代理，只对 GitHub 生效：

```powershell
git config --global http.https://github.com.proxy http://127.0.0.1:10808
```

检查方式：

```powershell
git config --global --get http.https://github.com.proxy
git ls-remote https://github.com/3571949306/GameMatrixApp.git HEAD
```

如果代理失效，可重新执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools\network\Configure-GitHubProxy.ps1 -Apply
```

清理 GitHub 专用代理：

```powershell
powershell -ExecutionPolicy Bypass -File tools\network\Configure-GitHubProxy.ps1 -Clear
```

### 4.3 上传 GitHub Releases

当前正式版 APK 上传 GitHub Release 的脚本是：

```text
tools/upload_to_github_release.py
```

它会按以下顺序取 Token：

1. `--token`
2. 环境变量 `GITHUB_TOKEN`
3. `local_private/github/token.txt`

示例：

```powershell
python tools\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 1.3.27 `
  --changelog-file CHANGELOG.md
```

说明：

- `--version-name` 会作为 GitHub Release 的 tag
- 如果同 tag 已存在，脚本会更新 Release 信息并替换同名 APK 资产
- 该脚本主要用于正式版，不建议把测试包长期发到 GitHub Releases

## 5. 如何发布安装包

### 5.1 本地构建前提

- 使用 Android Studio / Gradle 环境
- 本机 Java 21 可用
- 根目录存在 `keystore.properties` 和签名文件
- 不要把 `local_private/` 内的私密配置提交到 GitHub

### 5.2 构建命令

生成 Release APK：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=beta
```

生成版本元数据：

```powershell
.\gradlew.bat generateVersionJson -PupdateChannel=beta
```

如果发布正式版，把 `beta` 改成 `stable`：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=stable
.\gradlew.bat generateVersionJson -PupdateChannel=stable
```

### 5.3 推荐发布方式

#### 方式一：分步执行，最稳妥

测试版：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=beta
.\gradlew.bat generateVersionJson -PupdateChannel=beta
python tools\upload_to_vps.py --channel beta
```

正式版：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=stable
.\gradlew.bat generateVersionJson -PupdateChannel=stable
python tools\upload_to_vps.py --channel release
python tools\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 版本号 `
  --changelog-file CHANGELOG.md
```

#### 方式二：直接走 Gradle 发布任务

正式版可直接执行：

```powershell
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

这个任务会串联：

1. `assembleRelease`
2. `generateVersionJson`
3. 上传 VPS
4. 上传 GitHub Releases

如果只是构建后上传 VPS：

```powershell
.\gradlew.bat :app:uploadReleaseToVps -PupdateChannel=beta
```

### 5.4 发版后验证

建议至少执行：

```powershell
.\gradlew.bat :app:test -PautoBumpVersion=false
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
```

再检查：

- VPS 上的 `version-beta.json` 或 `version-release.json`
- GitHub Releases 页面是否已有目标版本
- App 内实际检查更新是否能拿到对应通道

## 6. 接手时的注意事项

- `local_private/` 是本地私有配置区，不要提交。
- GitHub 网络问题先看 `docs/LOCAL_GITHUB_NETWORK.md`，不要直接改全局网络方案。
- 日常测试包优先走 `beta` 通道，不要误发正式版到 GitHub Releases。
- 正式版发布前确认 `CHANGELOG.md`、`version.properties`、`version.json` 一致。
- 美国 VPS 当前主要是备用更新源，不建议随意改公网入口。
- 如果上传脚本或发布任务异常，优先检查 Python 依赖、GitHub Token、本地代理、VPS 配置文件路径是否正确。

## 7. 一句话接手结论

这个项目当前已经具备完整的本地构建、双 VPS 上传、GitHub Releases 发布链路。AI 接手时，优先复用 `tools/upload_to_vps.py`、`tools/upload_to_github_release.py` 和 `app/build.gradle` 里的发布任务，不要重新发明一套发布流程。
