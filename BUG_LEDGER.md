# BUG LEDGER — 缺陷账本（回归防护专项 §六）

> 规则（verify_ratchet.py 机器执行）：**每个真人可发现的 bug 必须登记，且守卫字段不得为空**。
> 守卫 = 防止同类回归的机器检查（测试名 / verify 脚本 / 服务端配置）。
> `PENDING(...)` 表示守卫待补——计入"无守卫"基线，补上后计数下降（棘轮奖励改进）。
> 本文件必须留在仓库根：`docs/` 整目录被 .gitignore 排除，CI 读不到。

## 条目格式

```
## BL-NNN 一句话症状
- 日期: YYYY-MM-DD
- 类别: 静默失败 | 并发时序 | 窄修复回归 | 配置 | 设备现实
- 根因: 为什么发生（不是"哪里改了"，是"为什么会错"）
- 守卫: <测试/检查名>（待补的写 PENDING 加计划项；不可测类别注明原因）
```

---

## BL-001 parseModulesArray 对 Catalog V2 对象格式静默返回 null
- 日期: 2026-08-30
- 类别: 静默失败 + 窄修复回归
- 根因: 为兼容一种清单格式加解析分支时，未测另一种格式；`JSONArray(jsonStr)` 抛异常被吞→返回 null，调用方（`bundledVersionCodeOf`/`loadModuleList`/SP 缓存）全部静默降级，不崩溃不报错，真人升级模块时才暴露
- 守卫: PENDING(§六 接缝契约测试——catalog 双格式 golden fixtures)

## BL-002 Splash 预装安装与核心预加载并发竞态
- 日期: 2026-08-30
- 类别: 并发时序
- 根因: 同一线程池并发跑 install（提取+事务安装 31 模块）与 preload（load 核心模块），同目录读写窗口期竞态。**单测原理上测不到**，只有真机首启时序才踩
- 守卫: PENDING(§六 真机冒烟套件——冷启动黄金路径 + logcat ERROR 扫描)

## BL-003 /admin/feedback 公网暴露
- 日期: 2026-08-30
- 类别: 配置
- 根因: nginx location 未加访问控制，token 轮换后入口仍公网可达
- 守卫: 服务端 nginx `07-hk-update-uk.conf` allow 127.0.0.1/::1/100.64.0.0/10 + deny all（公网 curl 实测 403）；PENDING(§六 冒烟——公网 403 探针)

## BL-004 SourceTestStore.append 非原子写
- 日期: 2026-08-30
- 类别: 静默失败
- 根因: 测速中断可留截断 JSON，下次读取解析失败静默丢数据
- 守卫: tmp+rename 实现（含 rename 失败兜底）；PENDING(§六 契约测试——截断注入用例)

## BL-005 verify_security_clauses 因 release_signer.cer 不入库而 CI 假红
- 日期: 2026-08-30
- 类别: 配置（门禁自身缺陷）
- 根因: 机检检查了凭据类文件实体，而该文件被 .gitignore 排除（设计如此）——检查器假设与仓库策略冲突
- 守卫: 9e17bd4 改为检查证书资源接线（ModuleSignatureVerifier 引用存在性），verify_security_clauses.py §8.3 项

## BL-006 客户端 feedback.url 仍指死域 tcp0053.shop
- 日期: 2026-08-30
- 类别: 配置
- 根因: 死域清理只做了 catalog 产物，local.properties 的 feedback.url 与后端 9011 监听是独立链路，未随清
- 守卫: PENDING(§六 真人反馈回路修复)
