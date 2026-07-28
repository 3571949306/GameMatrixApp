<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# VPN 模块改造设计 (track-vpn) — Summary

> Track: track-vpn  
> 状态: 调研完成 / 改造设计 v1  
> 日期: 2026-06-04  
> 最后更新: 2026-07-06 (循环24 Netty 安全修复复核)  
> 完整版: `d:\Developmment\GameMatrixApp\docs\refactor\track-vpn.md`

## TL;DR

VPN 模块目前**只有 UI 骨架 + 协议桩**，端到端**不可用**：

- 4 个协议 (VMess/VLESS/Trojan/Shadowsocks) 全部为桩 (`Socket(host, port)` 拿裸流即返回)
- 缺 VpnService 子类；AndroidManifest 没声明 service / 权限
- `VpnFragment` 启动 `VpnServiceProxy` Intent，目标类**根本不存在** → 必崩
- 节点 host/port 永远 "未解析"（parseAndAddNode 只设 type）
- 订阅 URL 存了不取
- HK VPS x-ui (41370) 跟 App 完全无 API 集成

## P0 必修

> **2026-07-06 实际进度复核**：以下 4 项 P0 在循环 19-24 期间**均未启动**，VPN 模块仍为桩实现。循环 24 仅完成宿主侧 Netty 4.1.135 安全升级（见下文「Netty 升级影响评估」），未触及 `module-store/feature/tools/vpn/` 内部代码。

1. 实现真正的 `VpnService` (管 tun/DNS/路由) — ⚠️ **未启动**
2. 接入 sing-box / v2rayNG 替换 4 个桩 — ⚠️ **未启动**（D-01 决策待用户确认）
3. AndroidManifest 补 `<service>` + BIND_VPN_SERVICE — ⚠️ **未启动**
4. Intent 目标改到 `com.gamecenter.app.vpn.service.GameVpnService`（新类） — ⚠️ **未启动**

### Netty 4.1.135 升级对 VPN 模块的影响评估 (2026-07-06 / 循环24)

循环 24 完成宿主侧 Netty 4.1.134 → 4.1.135.Final 升级，修复 7 CVE：

| CVE | 严重度 | 与 VPN 模块相关性 |
|-----|--------|------------------|
| CVE-2026-50010 | High | 间接相关 — sing-box outbound 未来若走 Netty 链路可受益 |
| CVE-2026-45416 | High | 间接相关 — 同上 |
| CVE-2026-44249 | High | 间接相关 — 同上 |
| CVE-2026-50560 | Medium | 间接相关 — 同上 |
| CVE-2026-50020 | Medium | 间接相关 — 同上 |
| CVE-2026-48043 | Medium | 间接相关 — 同上 |
| CVE-2026-47244 | Medium | 间接相关 — 同上 |

**结论**：
- VPN 模块当前 4 个协议桩（`Socket(host, port)` 裸 TCP）**不依赖 Netty**，本次升级对 VPN 模块运行时行为无直接影响
- 但 sing-box 接入后（D-01 决策落地），若 sing-box outbound 走 Netty 网络栈，则受益于本次升级
- 宿主侧 `OkHttpClientProvider` 与 WebSocket Relay 已受益于 Netty 升级，VPN Per-App 路由（P-15）未来跨模块感知 VPN 状态时网络链路更稳
- GitHub Dependabot 当前 0 open / 7 dismissed，VPN 模块依赖（含 sing-box AAR 引入后）应纳入 Dependabot 持续监控

## 改造阶段

- 短期 (1-2 周): sing-box 接入 + GameVpnService + ShareLinkParser + UI 美化
- 中期 (1-2 月): 智能选路 / 订阅管理 / Per-App 路由 / EncryptedSharedPrefs
- 长期 (3-6 月): Reality / Hysteria2 / 中转 / AI 选路

详细全文见同目录其他文档（`d:\Developmment\GameMatrixApp\docs\refactor\track-vpn.md`）。

---

## 1. 现状

### 1.1 模块位置与定位

```
d:\Developmment\GameMatrixApp\module-store\feature\tools\vpn
├── build.gradle                       (独立 applicationId com.gamecenter.app.vpn)
├── AndroidManifest.xml                (极简，无 service / permission)
├── detekt-baseline.xml
└── src/main/
    ├── AndroidManifest.xml            (只有 <application>，无 service 声明)
    └── java/com/gamecenter/app/vpn/
        ├── VpnFragment.kt             (166 行，纯代码 UI)
        ├── VpnModuleEntryPoint.kt     (72 行，模块入口/委托)
        ├── adapter/NodeAdapter.kt     (72 行，列表 adapter)
        ├── model/Node.kt              (42 行，数据模型)
        ├── protocol/ProtocolFactory.kt     (37 行，含 DexClassLoader 兜底)
        ├── protocol/ProtocolModule.kt      (29 行，接口)
        ├── protocol/VmessModule.kt         (28 行，桩)
        ├── protocol/VlessModule.kt         (28 行，桩)
        ├── protocol/TrojanModule.kt        (28 行，桩)
        ├── protocol/ShadowsocksModule.kt   (41 行，桩)
        └── repository/NodeRepository.kt    (59 行，SharedPreferences + Gson)
```

**关键设计**：
- `build.gradle` 把 vpn 声明为**独立 APK**（`com.android.application`），applicationId `com.gamecenter.app.vpn`，所有依赖 `compileOnly` → 不打包进主 APK / R8 不参与 / 签名跟主 APK 解耦。
- `VpnModuleEntryPoint` 同时实现 `ModuleInterface` / `FeatureModule` / `VpnDelegate` 三个接口，是模块被加载后唯一对外暴露的能力。
- `VpnFragment` 是入口 UI，**纯代码**构建，**零 XML 布局依赖**——这是模块商店下载的 dex 类可以安全运行的必要条件。
### 1.2 协议能力 (桩实现)

四个协议实现逻辑**完全相同**：

```kotlin
// 摘 ShadowsocksModule.kt:27-33
override fun connect(): Pair<InputStream, OutputStream> {
    if (connected) throw IllegalStateException("already connected")
    val node = currentNode ?: throw IllegalStateException("not prepared")
    socket = Socket(node.address, node.port)       // ← 裸 TCP
    connected = true
    return Pair(socket!!.getInputStream(), socket!!.getOutputStream())
}
```

**问题清单**：
1. `Socket(host, port)` 后**未做**任何协议握手 — VMess 要发 `0x01 + IV + payload`，VLESS 要发 UUID+命令，Trojan 要先 TLS 握手再发密码+hex(SHA224)，SS 要做 SIP002 协议头 + AEAD 加密。
2. `connect()` 返回 `InputStream/OutputStream` —— 意味着调用方（VpnServiceProxy）需要自己处理 SOCKS5/HTTP CONNECT 解析、TCP 分流转发。**但项目中根本不存在 VpnServiceProxy**。
3. 没有 TLS 实现（要 OkHttp/Conscrypt/BoringSSL 走 SSLSocket）。
4. 没有重试 / 超时 / keepalive / 多路复用。
5. `init(Context)` 全是空实现，没有任何加载 native 库或缓存证书的逻辑。
6. `getStatus()` 只返回 "connected"/"disconnected" 二态，不含延迟、丢包、流量。

### 1.3 节点管理

`NodeRepository` 用 **SharedPreferences + Gson** 存节点 JSON 数组和订阅 URL Set：

```kotlin
// NodeRepository.kt:16-17
private val prefs: SharedPreferences =
    context.getSharedPreferences("vpn_nodes", Context.MODE_PRIVATE)
```

**问题**：
- **明文存**：UUID / 密码 / 订阅 URL 直接进 SP，root 设备可见。生产应用应使用 `EncryptedSharedPreferences`（androidx.security）或 Tink。
- **无订阅解析**：`addSubscriptionUrl()` 把链接存进 Set，但**没有 `fetchSubscription()` 之类的方法** —— UI 里说"粘贴订阅链接"也只是一个空操作。
- **无去重**：`upsertNode()` 只按 `id` 比对，没有按 `(host, port)` 去重。
- **无版本 / 更新时间**：节点变更无法追溯。
- **无分组 / 标签**：所有节点扁平放在一个 List。

### 1.4 UI 详情

`VpnFragment.createRootView()` 用纯代码搭出来一个 `FrameLayout`：

```
┌──────────────────────────────────┐
│  RecyclerView (节点列表)         │
│  ── 节点1 (类型·host:port)       │
│  ── 节点2 (类型·host:port)       │
│  ── 节点N (类型·host:port)       │
│  [未连接] [未连接] ...            │
│                                  │
│                           [ + ]  │  ← 绿色 FAB
└──────────────────────────────────┘
```

- 每个 Item 三行：名字（粗）、`类型·host:port`（灰）、固定文字"未连接"（绿）。
- 列表右侧有红色删除按钮（ImageView + `ic_menu_delete` + 红色滤镜）。
- "+" 按钮弹 AlertDialog，让用户粘贴 `vmess://`/`vless://`/`trojan://`/`ss://`/订阅链接。
- **没有**：搜索框、刷新按钮、连接/断开大按钮、流量统计、错误提示美化、空状态引导。

`parseAndAddNode(input: String)` 只识别协议前缀，**完全不解析 URL**：

```kotlin
// VpnFragment.kt:124-143
val type = when {
    input.startsWith("vmess://") -> ProtocolType.VMess
    input.startsWith("vless://") -> ProtocolType.VLESS
    input.startsWith("trojan://") -> ProtocolType.Trojan
    input.startsWith("ss://") -> ProtocolType.Shadowsocks
    input.startsWith("http") -> { nodeRepo.addSubscriptionUrl(input); return }
    else -> ProtocolType.Shadowsocks
}
nodeRepo.upsertNode(Node(
    name = "节点 ${nodes.size + 1}",
    type = type,
    address = "未解析",   // ← 永远是"未解析"
    port = 443
))
```

### 1.5 架构（关键断裂点）

调用链：

```
[主 APK MainActivity]
  → 点底部 "科学上网" 标签
  → ModuleManager.load("vpn")
  → VpnModuleEntryPoint.start()        // 跳到 MainActivity extra_nav_tab=vpn
  → MainActivity 显示 VpnFragment
  → 用户点节点
  → VpnFragment.startVpnConnection()
    → VpnService.prepare(this)          // 系统弹 VPN 授权
    → VpnFragment.connectToNode()
      → Intent:
          action = "com.gamecenter.app.vpn.CONNECT"
          setClassName("com.gamecenter.app.vpn.service.VpnServiceProxy")
          putExtra("node_json", ...)
      → context.startService(intent)    // ← 目标类不存在
  → AndroidRuntime: ClassNotFoundException → 闪退
```

**核心断裂**：
- `setClassName(requireContext(), "com.gamecenter.app.vpn.service.VpnServiceProxy")` 指向的类**在这个模块里不存在**（已 grep 整个仓库，0 命中）。
- 就算能补一个 `VpnServiceProxy` 类，它也得有 `onStartCommand` 解析 `node_json`，然后 `VpnModuleEntryPoint.connect(nodeJson)` 拿到 `Pair<InputStream, OutputStream>`——但**VpnService 必须自己 `establish()` 一条 tun** 才能把 InputStream 接到应用层流量上。
- `VpnService.Builder().addAddress(...).addRoute(...).establish()` 这段代码**完全缺失**。
- DNS 转发（VpnService 必须劫持 DNS 请求到节点）也**没有**。
- `VpnDelegate.Tunnel(input, output)` 是简单数据类，意味着**设计者原本计划**用 VpnService 内部的 Proxy 协程做 SOCKS5 解析 + 流量透传——但实现是 0。

### 1.6 服务器现状

`docs/PROJECT_CONTEXT.md` 1.1 节记录：

| 服务 | 端口 | 域名 |
| --- | --- | --- |
| x-ui 面板 | 127.0.0.1:41370 | `hk-xui.<YOUR_DOMAIN>` (Cloudflare Flexible) |

- 服务器侧跑 x-ui，提供 VLESS/VMess/Trojan 节点（端口 443 / 8443 等）。
- **App 端没有任何 x-ui API 集成**：
  - 没有 `x-ui` 关键字命中 grep 结果（除 PROJECT_CONTEXT 之外）。
  - 没有"流量统计"接口调用。
  - 没有"通过订阅链接拉节点"功能。
  - 用户只能**手动复制粘贴** vmess:// 链接。

**这意味着**：HK VPS 上的节点是**孤岛**——服务器在跑，App 端用不了（也用不上，因为协议模块是桩）。

### 1.7 模块化状态

| 维度 | 状态 |
| --- | --- |
| 编译产物 | 独立 APK（applicationId `com.gamecenter.app.vpn`） |
| 依赖方式 | `compileOnly`（主 APK 不打包 vpn 代码） |
| 模块接口 | 已实现 `ModuleInterface` / `FeatureModule` / `VpnDelegate` 三个 |
| 资源隔离 | UI 纯代码（无 R 文件），可放进 dex 加载 |
| 签名 | 没看到独立签名配置（build.gradle 没 `signingConfigs`，release minifyEnabled=false 但没签名 → 上传模块商店前必须补） |
| 分发 | 跟主 APK 走同样的两级下载源（HK VPS → GitHub Releases）；2026-06-19 起 US 备用源已下线 |
| 升级 | 走 modules.json 里的 `versionCode` 比对，跟主 APK 同步更新 |

**模块化本身做对了**：纯代码 UI + 三个接口都齐了。**没做对的是模块内部就是个空壳**。

---

## 2. 用户痛点

| # | 痛点 | 严重度 | 来源 |
| --- | --- | --- | --- |
| P-01 | 闪退：点节点必崩（VpnServiceProxy 不存在） | P0 | 代码验证 |
| P-02 | 连接无效：4 个协议模块是 Socket 桩 | P0 | 代码验证 |
| P-03 | 节点导入后 host/port 全是"未解析" | P0 | VpnFragment.kt:124-143 |
| P-04 | 订阅链接存了不取 | P1 | VpnFragment.kt:131 |
| P-05 | 没有 Vpn 权限申请 | P0 | AndroidManifest.xml |
| P-06 | 没有连接状态/进度 | P2 | NodeAdapter.kt:64 |
| P-07 | 没有流量统计 | P2 | 全模块无 traffic counter |
| P-08 | 没有延迟/丢包测试 | P1 | 无 ping/probe 代码 |
| P-09 | 无错误提示（catch 块吞错） | P1 | VpnFragment.kt:142 |
| P-10 | 节点明文存储 | P2 | NodeRepository.kt:16 |
| P-11 | 没有断开 UI | P1 | VpnFragment 全文无 disconnect 入口 |
| P-12 | 没有通知栏图标 | P1 | 无前台服务 / 通知代码 |
| P-13 | 节点过多时无分组/搜索 | P2 | UI 无搜索框 |
| P-14 | 没有"测试所有节点"按钮 | P2 | UI 无该入口 |
| P-15 | AI/浏览器无法感知 VPN 状态 | P2 | core/common VpnDelegate 接口无 callback |

---

## 3. 改造设计

> 优先级：P1。短期 UI + 接入真内核，中长期再补订阅/选路/新协议。

### 3.1 短期（1-2 周）

**目标**：让 VPN 模块真的能连上一个 HK 节点。

**3.1.1 内核选择（D-01）**

候选：v2rayNG / **sing-box SDK**（推荐）/ SagerNet / 自行实现。推荐 sing-box 因为同时支持 Reality + Hysteria2。

**3.1.2 重写 4 个协议 Module**

所有协议统一转发到 sing-box outbound，不再各自 Socket.connect()。新接口加入 traffic/stats 字段。

**3.1.3 实现真正的 VpnService**

新建 `service/GameVpnService.kt`（替换不存在的 VpnServiceProxy）：

- 继承 `android.net.VpnService`
- `onStartCommand` 解析 `node_json` → 走 `VpnModuleEntryPoint.connect()`
- `Builder().addAddress("10.0.0.2", 32).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1").establish()`
- 启动前台服务 + 通知栏图标
- 内部用常驻协程跑 tun ↔ sing-box 转发
- 提供 `Binder` 给 UI 查询流量 / 状态

**3.1.4 补全 AndroidManifest**

需要 `<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />` + `<service>` 声明（`foregroundServiceType="vpn"`）。

**3.1.5 UI 美化 + 3.1.6 ShareLinkParser**

- 大圆连接按钮（100dp，未连灰/连接中蓝脉冲/已连绿稳）
- 节点 Item 三列：name | type 小图标 | 三点菜单
- 搜索框（EditText + TextWatcher）
- "导入订阅" + "扫码导入" 按钮
- "测试所有节点" 按钮 + 进度条
- ShareLinkParser 支持 vmess://、vless://、trojan://、ss://

**3.1.7 短期交付物**

- 协议实现换成 sing-box libbox aar
- 补 VpnService + Manifest
- ShareLinkParser
- UI 视觉升级
- 单测：Parser、Repository 加密、State 转换

### 3.2 中期（1-2 月）

**3.2.1 智能选路**

启动时并发测所有节点（HTTP GET `https://www.google.com/generate_204`），缓存 24h，WorkManager 每 6h 复测。选路策略：手动 / 自动低延迟 / 自动低丢包 / 自动均衡。切路时机：当前节点连续 3 次 probe 失败 → 自动切到次优。

**3.2.2 订阅管理**

`addSubscription(url, name, userAgent)` 替代 `addSubscriptionUrl`。`SubscriptionManager` 用 OkHttpClientProvider 拉取，支持 base64/plain 两种格式，WorkManager 每天 1 次刷新，按订阅源分组显示。

**3.2.3 按 App 路由**

`VpnService.Builder.addAllowedApplication()` / `addDisallowedApplication()`。设置页让用户选"仅代理这些"或"全部代理但排除这些"。默认白名单：AI / Browser，斗地主等游戏走直连。

**3.2.4 模块化真正落地**

补 vpn 模块独立 keystore、独立 version.properties、Auto-Publish `modules` 子命令、主 APK 通过 ModuleDownloader 走 SHA-256 校验下载、主 APK 删除 vpn 的 `compileOnly` 依赖。

**3.2.5 加密存储**

NodeRepository 切到 EncryptedSharedPreferences（`androidx.security:security-crypto:1.1.0-alpha06`），主密钥放 Android Keystore，一次性 migration。

### 3.3 长期（3-6 月）

**3.3.1 VLESS Reality**

Reality (XTLS) 是当前唯一能稳定抗 GFW 主动探测的协议。接入 sing-box Reality outbound（destination = `www.apple.com` 之类），支持 uTLS（chrome / firefox / safari 指纹），不需要域名证书，用户配置成本低。

**3.3.2 Hysteria2**

基于 QUIC，拥塞算法 Brutal，**高丢包网络下带宽优势大**。适合非洲/南美/中东用户。sing-box native 支持。

**3.3.3 自建中转（Relay）**

服务器 A → 中转 B（HK/JP） → 落地 C（US/SG）。配置格式：`(outbound) → (chain detour) → (final)`。UI 给节点选"中转"标签，自动写入 chain。服务器端用 iptables / socat / xray route 做中转。

**3.3.4 节点池 + 自动选路（AI 化）**

收集 30 天延迟/丢包/带宽数据，简单线性回归预测"未来 1h 最可能稳定的节点"，写进 VpnDelegate 的 `recommendNode(): Node?` 接口，AI 路由时优先走推荐节点。

**3.3.5 模块化生态扩张**

vpn 从"工具"升级为"网络基础能力"。`core:network` 抽 `ProxyStateProvider` / `VpnStateProvider` 接口，AI / Browser / Update 三个模块都注入，感知 VPN 状态后切换 fallback 策略。

---

## 4. 关键决策点

| 编号 | 决策 | 候选 | 建议 |
| --- | --- | --- | --- |
| D-01 | VPN 内核 | sing-box / v2rayNG / SagerNet | **sing-box**（同时支持 Reality + Hysteria2） |
| D-02 | 模块形态 | 独立 APK / 合并主 APK | 独立 |
| D-03 | 节点加密 | EncryptedSharedPreferences / Tink / SQLCipher | EncryptedSharedPreferences |
| D-04 | UI 框架 | 纯代码 / XML / Compose | 纯代码（保模块商店兼容） |
| D-05 | Per-App 路由 | 做 / 不做 | 做 |
| D-06 | x-ui API 集成 | 是 / 否 | 否（走用户填订阅） |
| D-07 | 灰度发布 | 全量 / 10% | 全量 |

---

## 5. 落地清单 (TODO)

### 短期 (1-2 周)
- [ ] 决定内核（D-01）并下载 sing-box libbox.aar
- [ ] 引入 `io.nekohakekai:libbox:VERSION`
- [ ] 重写 `protocol/ProtocolModule.kt` 接口
- [ ] 新建 `service/GameVpnService.kt`
- [ ] 补 `AndroidManifest.xml` 的 `<service>` + 权限
- [ ] 改 `VpnFragment` Intent 目标
- [ ] 写 `parser/ShareLinkParser.kt`
- [ ] 写 `repository/NodeRepository` 加密版本
- [ ] UI 大圆按钮 + 搜索 + 测速
- [ ] 通知栏 + 前台服务
- [ ] 单测

### 中期 (1-2 月)
- [ ] SubscriptionManager + WorkManager
- [ ] Per-App 路由设置页
- [ ] 智能选路
- [ ] core:network 抽 ProxyStateProvider
- [ ] AI / Browser 注入 ProxyStateProvider
- [ ] vpn 模块独立 keystore + 签名
- [ ] Auto-Publish modules 子命令
- [ ] 主 APK 移除 vpn 的 compileOnly 依赖

### 长期 (3-6 月)
- [ ] sing-box Reality outbound
- [ ] uTLS 指纹
- [ ] Hysteria2 outbound
- [ ] 中转 chain UI
- [ ] 历史数据 + 线性回归推荐
- [ ] 模块商店页加"网络工具"分类
- [ ] 灰度发布 + 错误监控

---

## 6. 风险

| 风险 | 概率 | 影响 | 缓解 |
| --- | --- | --- | --- |
| sing-box AAR 在 minSdk 26 的 SO 兼容 | 中 | 高 | CI 早期发现，准备 fallback v2rayNG |
| EncryptedSharedPreferences Keystore 偶发失败 | 低 | 中 | 启动时检测，失败回退明文 |
| 前台服务被杀 | 中 | 中 | startForeground + FOREGROUND_SERVICE_TYPE_VPN + START_STICKY |
| Reality 服务器配置错误 | 高 | 中 | 测试节点先跑通 |
| 恶意 JSON 数据 | 中 | 高 | try-catch + 严格 schema 校验 |
| 模块商店 SHA-256 校验失败 | 中 | 高 | vpn APK 独立签名 + Auto-Publish modules 子命令 |

---

## 7. 参考

- `d:\Developmment\GameMatrixApp\module-store\feature\tools\vpn\src\main\java\com\gamecenter\app\vpn\**` (11 文件)
- `d:\Developmment\GameMatrixApp\module-store\feature\tools\vpn\build.gradle`
- `d:\Developmment\GameMatrixApp\module-store\feature\tools\vpn\src\main\AndroidManifest.xml`
- `d:\Developmment\GameMatrixApp\docs\PROJECT_CONTEXT.md` §1.1
- `d:\Developmment\GameMatrixApp\docs\NETWORK_LAYER.md`
- sing-box Android 集成: https://sing-box.sagernet.org/integration/
- v2rayNG Parser 参考: https://github.com/2dust/v2rayNG
- VpnService 官方文档: https://developer.android.com/reference/android/net/VpnService

---

> 调研人: coder
> 完成时间: 2026-06-04 16:30 (Asia/Shanghai)
> 评审建议: 与维护者/产品对一下 D-01（sing-box vs v2rayNG）决策后即可进入短期待办
> 同步位置: `d:\Developmment\GameMatrixApp\docs\refactor\track-vpn.md`


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)