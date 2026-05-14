# GameCenterApp VPS Update And Feedback

当前 VPS 目录约定：

```text
/var/www/update/
├── app/                 # APK 和 version.json 上传目录
│   └── 反馈/             # 反馈 txt 镜像目录，不会被 App 上传清理
│       ├── bug/
│       └── 功能/
├── feedback/            # 用户反馈接收目录
│   ├── Bug反馈/          # Bug 类反馈落盘
│   └── 功能建议/         # 功能建议类反馈落盘
├── server.py            # 更新服务，监听 127.0.0.1:9000
└── downloads/           # 旧目录，仅兼容旧链接
```

`app` 和 `feedback` 是同一个父目录 `/var/www/update` 下的兄弟目录，便于备份和维护。

## 更新服务

部署文件：

```bash
sudo mkdir -p /var/www/update/app
sudo cp update_server.py /var/www/update/server.py
sudo chmod +x /var/www/update/server.py
sudo python3 -m py_compile /var/www/update/server.py
sudo systemctl restart update-server
```

上传 App 更新包时，把构建产物放到：

```text
/var/www/update/app/app-debug.apk
/var/www/update/app/version.json
```

以后只需要上传这两个文件作为 App 分发内容。`server.py`、`feedback_server.py`、nginx 配置和 systemd 服务只有在服务端代码或代理规则变化时才需要上传。

本地工程已提供自动上传脚本：

```bash
# 需要先安装依赖
pip install paramiko

# 上传 Beta 版
python tools/upload_to_vps.py --channel beta

# 上传 Release 版
python tools/upload_to_vps.py --channel release

# 指定 APK 和版本文件路径
python tools/upload_to_vps.py --apk app/build/outputs/apk/debug/app-debug.apk --version app/build/outputs/apk/debug/version.json --channel beta
```

配置文件在本机私有目录 `local_private/vps/upload_config.json`，当前使用 SSH 密码登录。不要把该目录提交到 GitHub。

公网检查：

```bash
curl http://<YOUR_DOMAIN>/version.json
curl -I http://<YOUR_DOMAIN>/app-debug.apk
curl 'http://<YOUR_DOMAIN>/api/update/check?versionCode=1'
```

`update_server.py` 同时兼容旧的 `/downloads/version.json`、`/downloads/app-debug.apk` 和 `/api/update/check`，但实际文件只从 `/var/www/update/app/` 读取。

`/var/www/update/api/update/check.py` 是早期旧接口脚本，现在不参与当前 nginx + systemd 的更新分发流程；当前 App 优先读取 `/version.json`，旧 `/api/update/check` 由 `/var/www/update/server.py` 兼容处理。

## Nginx

更新域名只使用 80 端口代理更新和反馈，避免影响同机已经存在的其他服务。不要把本文件合并到其他业务域名的 server 块里。

```bash
sudo cp nginx-update.conf /etc/nginx/conf.d/update.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 版本规则

VPS 永远只保留一个最新版 APK 和一份 `version.json`。这个最新版可以是 beta，也可以是 stable；只有明确发布正式版时才修改 `versionName` 并用 `-PupdateChannel=stable` 构建。普通测试打包只递增 `versionCode`，远端 `version.json` 通过 `channel` / `isBeta` 决定是正式版还是测试版。

自动上传脚本只会删除 `/var/www/update/app/` 下多余的普通 `.apk` / `.json` 文件，不会清理 `反馈/` 子目录。
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
