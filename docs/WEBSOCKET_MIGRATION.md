# 斗地主 Beta WebSocket 联机迁移文档

## 1. 概述

**目的**：将斗地主 Beta 联机从 HTTP 轮询迁移到 OkHttp WebSocket 长连接，实现低延迟实时通信。

**架构**：

```
Android (房主/客户端)
    │
    ▼
OkHttp WebSocket Client
    │
    ▼
Cloudflare (CDN/WAF) → 香港 VPS nginx (WSS 代理)
    │
    ▼
Node.js Relay Server (127.0.0.1:18080)
    │
    └── 房间管理 (内存 Map)
    └── 消息转发 (房主 ↔ 客户端)
```

**当前状态**：v21 (vc=167) - 游戏状态同步已验证成功 ✅

**最新修复**：
- Relay 转发使用 `data.toString('utf8')` 确保文本帧
- Android 端新增 `onMessage(WebSocket, ByteString)` 处理二进制帧
- 3 次冗余广播 + STATE_ACK 确认机制
- 禁止在没有 REMOTE 座位时开始游戏

---

## 2. 联机架构总览

### 2.1 三种联机模式

```
┌─────────────────────────────────────────────────────────────┐
│                    斗地主 Beta 联机架构                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  模式 1     │    │  模式 2     │    │  模式 3     │     │
│  │  LAN TCP    │    │  WebSocket  │    │  HTTP Relay │     │
│  │  (局域网)   │    │  (主方案)   │    │  (Fallback) │     │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘     │
│         │                  │                  │             │
│         ▼                  ▼                  ▼             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │ TCP Server  │    │  WSS Relay  │    │ HTTP Relay  │     │
│  │  Socket     │    │  Server     │    │  Server     │     │
│  └─────────────┘    └──────┬──────┘    └─────────────┘     │
│                            │                                │
│                            ▼                                │
│                     ┌─────────────┐                         │
│                     │ Cloudflare  │                         │
│                     │   + nginx   │                         │
│                     │  (WSS 代理) │                         │
│                     └──────┬──────┘                         │
│                            │                                │
│                     ┌──────┴──────┐                         │
│                     │  美国 VPS   │                         │
│                     │  <YOUR_VPS_IP>  │                        │
│                     └─────────────┘                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模式对比

| 特性 | LAN TCP | WebSocket | HTTP Relay |
|------|---------|-----------|------------|
| 适用场景 | 同一 WiFi | 远程联机 | 远程 fallback |
| 延迟 | < 10ms | 50-200ms | 1000-3000ms |
| 消息方向 | 双向实时 | 双向实时 | 单向轮询 |
| 服务器依赖 | 无 | 需要 Relay | 需要 Relay |
| 稳定性 | 高 | 高 | 中 |
| 跨境支持 | 不支持 | 支持 | 支持 |

---

## 3. 服务器架构

### 3.1 部署拓扑

```
┌────────────────────────────────────────┐
│           Cloudflare CDN               │
│         (Full Strict SSL)              │
│            <YOUR_DOMAIN>                │
└───────────────┬────────────────────────┘
                │
                ▼ WSS (WebSocket Secure)
┌────────────────────────────────────────┐
│              nginx                     │
│         (Port 2083)                    │
│  ┌─────────────┐  ┌─────────────┐     │
│  │ /ddz-ws     │  │ /health     │     │
│  │ WebSocket   │  │ Health Check│     │
│  │ Upgrade     │  │             │     │
│  └──────┬──────┘  └─────────────┘     │
│         │                              │
│         ▼ proxy_pass                   │
│  ┌─────────────────────────────┐      │
│  │  localhost:18080            │      │
│  │  Node.js WebSocket Relay    │      │
│  └─────────────────────────────┘      │
└────────────────────────────────────────┘
```

### 3.2 服务器组件

| 组件 | 技术 | 端口 | 路径 | 状态 |
|------|------|------|------|------|
| nginx | nginx | 2083 | /ddz-ws, /health | ✅ 运行中 |
| WebSocket Relay | Node.js + ws | 18080 | / | ✅ 运行中 |
| SSL 证书 | Cloudflare Origin | - | - | ✅ Full Strict |

### 3.3 关键配置

**nginx 配置** (`/etc/nginx/conf.d/ws-ssl.conf`):
```nginx
server {
    listen 2083 ssl;
    server_name ws.<YOUR_DOMAIN>;
    
    ssl_certificate /etc/nginx/ssl/cloudflare_origin.pem;
    ssl_certificate_key /etc/nginx/ssl/cloudflare_origin.key;
    
    location /ddz-ws {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
