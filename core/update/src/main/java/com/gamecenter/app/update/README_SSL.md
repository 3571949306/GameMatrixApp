<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# SSL/TLS 网络配置说明

## 当前实现

App 端通过 `SSLHelper.trustUpdateServer(baseUrl)` 只对更新服务器域名禁用证书验证，不再全局禁用所有 HTTPS 连接。

```java
SSLHelper.trustUpdateServer("http://<YOUR_DOMAIN>"); // 只信任更新服务器
```

`UpdateManager` 初始化时会自动从默认 URL 提取域名并信任：

```java
SSLHelper.trustUpdateServer(DEFAULT_BASE_URL); // 提取 <YOUR_DOMAIN>
```

用户自定义更新 URL 时也会自动添加信任：

```java
SSLHelper.trustUpdateServer(baseUrl); // setBaseUrl() 时调用
```

## HTTPS 配置状态

VPS 端已在 nginx 1443 端口配置了 HTTPS（使用 Let's Encrypt 证书），配置文件位于 `/etc/nginx/conf.d/update-https.conf`。

由于 443 端口被 Xray 占用，使用 1443 端口。HTTPS 已启用，APP 默认更新地址为 `https://<YOUR_DOMAIN>:1443`。

HTTP 80 端口仍保持向后兼容，旧版 APP 仍可通过 HTTP 检查更新。

## 服务端 HTTPS 参考配置

当前 nginx 配置（已部署）：

```nginx
server {
    listen 1443 ssl;
    listen [::]:1443 ssl;
    server_name <YOUR_DOMAIN>;

    ssl_certificate /etc/letsencrypt/live/<YOUR_DOMAIN>/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/<YOUR_DOMAIN>/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    client_max_body_size 200m;

    location / {
        proxy_pass http://127.0.0.1:9000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

验证命令：

```bash
# VPS 本地测试
curl -k -sI https://localhost:1443/version-beta.json

# 公网测试（需要云防火墙开放 1443）
curl -k -sI https://<YOUR_DOMAIN>:1443/version-beta.json
```

## 检查清单

- 确认客户端 `DEFAULT_BASE_URL` 与服务端协议一致
- 确认公网 `/version.json` 和 `/app-beta.apk` 可通过 HTTP 访问
- 确认 APK 下载支持足够大的 `client_max_body_size`
- 确认 Android 9+ 明文 HTTP 策略已经在 `network_security_config.xml` 中允许
- 在模拟器或真机上触发一次检查更新，查看 `UpdateManager` 日志

## 常见错误

| 错误信息 | 常见原因 | 处理方式 |
| --- | --- | --- |
| `SSLV3_ALERT_HANDSHAKE_FAILURE` | TLS 协议或证书配置不兼容 | 使用 `"TLS"` 自动协商，并检查 nginx SSL 配置 |
| `Failed to connect` | 端口不可达或反向代理未启动 | 检查防火墙、nginx 和服务进程 |
| `CLEARTEXT communication not permitted` | Android 禁止明文 HTTP | 检查 `network_security_config.xml` |
| `Certificate not trusted` | 证书链不被系统信任 | 当前方案只对更新服务器域名禁用验证，不影响其他连接 |

## 相关文件

- [UpdateManager.java](/core/update/src/main/java/com/gamecenter/app/update/UpdateManager.java): 更新检查、下载和安装流程
- [SSLHelper.java](/core/update/src/main/java/com/gamecenter/app/update/SSLHelper.java): TLS 信任管理
- [network_security_config.xml](/app/src/main/res/xml/network_security_config.xml): Android 网络安全配置
- [AndroidManifest.xml](/app/src/main/AndroidManifest.xml): 应用清单与网络配置引用
- VPS 更新服务模板不在当前仓库中，部署时必须以受控服务器仓库/配置为准。

---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)