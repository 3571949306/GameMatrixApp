# GameMatrixApp — 云编译 & VPS 部署指南

> **本项目** : GameMatrixApp — Android 多模块游戏中心，含 DDZ、WebSocket Relay、模块化（ClassLoader + DexClassLoader）框架
> **本指南** : 用 GitHub Actions 在云端编译 APK，再用 openclaw / VPS 接收 artifact

---

## 1. 项目一句话总结

- **类型**: Android (Kotlin + Java + Hilt + KSP + detekt + ktlint + JaCoCo)
- **构建工具**: Gradle 8.13 (wrapper) + AGP 8.13.2 + Kotlin 2.0.21
- **模块数**: 22 个 (`:app` + 6 个 `:core:*` + 1 个 `:modules:*` + 14 个 `:module-store:feature:*`)
- **JDK**: 17
- **JVM target**: 17

---

## 2. 编译环境需求

| 工具 | 最低版本 | 说明 |
|---|---|---|
| JDK | 17 | Gradle 8.13 + AGP 8.13.2 要求 |
| Android SDK | API 35 | `compileSdk` 看 `app/build.gradle` |
| Build Tools | 35.0.0 | (与 compileSdk 同步) |
| Gradle | 8.13 | 由 `gradlew` wrapper 自带 |
| 磁盘 | ≥ 5 GB | Gradle 缓存 + 编译产物 |

**本地编译不推荐**: 见 `HK VPS 5.2 GB free, 装 SDK 后只剩 ~1.7 GB` — **本地编译会挤爆**。

**云编译 (GitHub Actions)**: 编译在 GitHub 跑（macOS/Linux runner 都有预装 SDK），产物（APK/AAB）作为 artifact 上传，VPS 只下载产物即可。**强烈推荐**。

---

## 3. GitHub Actions 云编译 (本项目已配)

`.github/workflows/ci.yml` **已经包含** 3 个 job：

| Job | 内容 | 触发 |
|---|---|---|
| `lint-and-test` | detekt + ktlint + 单元测试 + JaCoCo 覆盖率 | push / PR |
| `build` | `:app:assembleDebug` + `:app:assembleRelease` (unsigned) | push / PR |
| `gitleaks` | secret 扫描 (基于 `.gitleaks.toml`) | push / PR |

**artifact 保留策略**:
- `app-debug` APK: 保留 7 天
- `coverage-report`: 保留 14 天
- release APK: **未上传**（CI 不签名）

### 3.1 触发云编译

任一即可:

```bash
# 1. 推到 main 分支 (自动触发)
git push origin main

# 2. 在 GitHub 网页手动触发
#    → Actions 页面 → "CI" workflow → "Run workflow"

# 3. 提 PR
gh pr create --base main --head your-branch --title "feat: ..."
```

### 3.2 下载编译产物

**方法 A — GitHub CLI (推荐)**:

```bash
# 列出最近 build
gh run list --workflow=ci.yml --limit=5

# 下载最新 debug APK
gh run download --name app-debug

# 解压得到 app-debug.apk
ls *.apk
```

**方法 B — 用 curl 直接拉 artifact (VPS 用)**:

```bash
# 1. 在 GitHub 设置一个 PAT (Settings → Developer settings → Personal access tokens)
#    权限: repo (含 actions:read)
# 2. 用 API 下载
RUN_ID=$(gh run list --workflow=ci.yml --limit 1 --json databaseId -q '.[0].databaseId')
curl -L -H "Authorization: token $GITHUB_TOKEN" \
  -o app-debug.zip \
  "https://api.github.com/repos/3571949306/GameMatrixApp/actions/runs/$RUN_ID/artifacts"
unzip app-debug.zip
```

**方法 C — VPS 上的自动化脚本** (推荐给持续集成):

见 `scripts/fetch-latest-apk.sh` (在 VPS `/root/` 下):

```bash
#!/bin/bash
# fetch-latest-apk.sh
# 从 GitHub Actions 拉取最新 debug APK 到 /root/apks/

set -e
TOKEN="${GITHUB_TOKEN:?must set GITHUB_TOKEN env var}"
REPO="3571949306/GameMatrixApp"
WORKFLOW="ci.yml"
DEST="/root/apks"
mkdir -p "$DEST"

# 找最近成功的 build
RUN_ID=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/$REPO/actions/workflows/$WORKFLOW/runs?status=success&per_page=1" \
  | jq -r '.workflow_runs[0].id')

if [ -z "$RUN_ID" ]; then
  echo "no successful build found" >&2
  exit 1
fi

echo "latest run: $RUN_ID"

# 列 artifacts 找 debug APK
ARTIFACT_ID=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/$REPO/actions/runs/$RUN_ID/artifacts" \
  | jq -r '.artifacts[] | select(.name=="app-debug") | .id')

curl -L -H "Authorization: token $TOKEN" \
  -o "$DEST/app-debug-${RUN_ID}.zip" \
  "https://api.github.com/repos/$REPO/actions/artifacts/$ARTIFACT_ID/zip"

cd "$DEST" && unzip -o "app-debug-${RUN_ID}.zip"
echo "✓ APK ready: $DEST/app/build/outputs/apk/debug/app-debug.apk"
```

VPS 上用法:

```bash
# VPS: 加到 PATH，定期跑 (e.g. cron)
chmod +x /root/fetch-latest-apk.sh
echo '0 */6 * * * /root/fetch-latest-apk.sh' | sudo crontab -

# 或 openclaw agent 触发
openclaw exec /root/fetch-latest-apk.sh
```

---

## 4. Release 签名 (高级)

CI 默认出 **unsigned release APK**。要签正式名版的 release APK:

### 4.1 准备签名 key

**在 Windows 本地** (一次性):

```powershell
keytool -genkey -v `
  -keystore gamematrix-release.jks `
  -alias gamematrix `
  -keyalg RSA -keysize 2048 -validity 10000
```

→ 产生 `gamematrix-release.jks` (~2 KB)

### 4.2 上传 keystore 到 GitHub Secrets

GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret 名 | 值 |
|---|---|
| `KEYSTORE_BASE64` | `base64 gamematrix-release.jks` (去掉换行) |
| `KEYSTORE_PASSWORD` | 创建 keystore 时设的 storepass |
| `KEY_ALIAS` | `gamematrix` |
| `KEY_PASSWORD` | 创建 keystore 时设的 keypass |

> ⚠️ **绝对不要** 把 `gamematrix-release.jks` 提交到 git！.gitignore 应该忽略 `*.jks` / `*.keystore`。

### 4.3 启用 release job

在 `ci.yml` 里加新 job（参考 `.github/workflows/cloud-build.yml` 里的 `signed-release` 实现）:

```yaml
signed-release:
  name: Signed Release APK
  runs-on: ubuntu-latest
  needs: lint-and-test
  if: github.ref == 'refs/heads/main'
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { java-version: 17, distribution: temurin }
    - name: Decode keystore
      run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > gamematrix.jks
    - name: Build signed release
      run: ./gradlew :app:assembleRelease
      env:
        KEYSTORE_PATH: gamematrix.jks
        KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
        KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
        KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
    - uses: actions/upload-artifact@v4
      with:
        name: app-release-signed
        path: app/build/outputs/apk/release/*.apk
        retention-days: 30
```

**App 端 `build.gradle` 要加 `signingConfigs.release`** 才能消费这些 env vars（细节见 Android 文档）。

---

## 5. 工作流文件说明

| 文件 | 用途 |
|---|---|
| `.github/workflows/ci.yml` | 主 CI（lint + test + debug build + gitleaks）— 已存在 |
| `.github/workflows/cloud-build.yml` | 云编译专用 workflow（debug + release artifact 上传）— 本次新增 |
| `.gitleaks.toml` | gitleaks 配置 — 已存在 |
| `CLOUD-BUILD.md` | 本文件 |

---

## 6. 跟 openclaw / VPS 协同

### 6.1 流程图

```
[Windows]                                                [GitHub]                                    [HK VPS / openclaw]
   │                                                       │                                              │
   │  git push origin main                                │                                              │
   ├──────────────────────────────────────────────────────►│                                              │
   │                                                       │  CI workflow 触发                             │
   │                                                       ├─► lint-and-test (5-7 min)                    │
   │                                                       ├─► build :app:assembleDebug (3-5 min)         │
   │                                                       │   └─► upload artifact: app-debug             │
   │                                                       │                                              │
   │                                                       │  openclaw agent / cron 拉取                   │
   │                                                       │◄─────────────────────────────────────────────┤
   │                                                       │  /root/fetch-latest-apk.sh                    │
   │                                                       │   └─► /root/apks/app-debug.apk               │
   │                                                       │                                              │
   │                                                       │  通过 WebSocket 通知 (可选)                  │
   │                                                       ├─────────────────────────────────────────────►│
   │                                                       │  weixin/qqbot push: "新 APK 来了"             │
```

### 6.2 openclaw 端最小集成 (伪代码)

openclaw 跑个 cron job 定期拉:

```yaml
# openclaw cron 配置
- name: fetch-latest-apk
  schedule: "0 */6 * * *"        # 每 6 小时
  command: /root/fetch-latest-apk.sh
  notify:
    - weixin: "✓ 新 APK 已就绪: /root/apks/app-debug.apk (build #${RUN_ID})"
```

---

## 7. 排错

| 现象 | 原因 | 修法 |
|---|---|---|
| `JAVA_HOME is not set` | workflow 没装 JDK | 检查 `setup-java@v4` step |
| `SDK location not found` | 没用 `reactivecircus/android-sdk-action` | 加上 |
| Build OOM | Gradle heap 不够 | `GRADLE_OPTS=-Xmx4g` (已设) |
| `signing config not found` | 没配 `signingConfigs.release` | 详见 [4. Release 签名](#4-release-签名-高级) |
| `Execution failed for task ':app:processDebugResources'` | SDK build-tools 旧 | GitHub 镜像 ubuntu-latest 自带 35.0.0 |
| VPS `fetch-latest-apk.sh` 报 401 | GITHUB_TOKEN 错 | 重新设 secret |
| 编译慢 (>15 min) | 第一次跑，没 cache | 第二次起会复用 `actions/cache` (通常 5-7 min) |

---

## 8. 资源消耗估算

GitHub Actions 公共仓库 **每月 2000 分钟免费** (private 看 plan)。

本项目单次 build 估算:

| Job | 时间 |
|---|---|
| lint-and-test (含 detekt + ktlint + 单元测试) | 5-8 分钟 |
| build (debug + unsigned release) | 3-5 分钟 |
| gitleaks | < 1 分钟 |
| **单次 push 合计** | **~9-14 分钟** |

**月 200 次 push** 内绰绰有余。

---

## 9. 链接

- GitHub 仓库: https://github.com/3571949306/GameMatrixApp
- GitHub Actions 文档: https://docs.github.com/en/actions
- 现有 workflow: `.github/workflows/ci.yml`
- 本指南: `CLOUD-BUILD.md` (本文件)
