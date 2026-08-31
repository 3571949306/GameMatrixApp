# GameMatrixApp Agent 工程规范

本文件适用于仓库根目录及全部子目录。任何 Agent 在修改代码前必须先阅读本文件；若子目录存在更具体的 `AGENTS.md`，两者同时生效，以更具体者为准。

## 五条铁律

1. **用户资产保护**：开工先跑 `git status --short`，已有改动一律视为用户资产；禁止覆盖、回滚或顺手格式化无关文件。禁止 `git reset --hard`、`git checkout --` 或删除用户未提交工作来制造"干净"状态。
2. **单一真相源**：动态游戏/功能的运行时真源在 `module-store/feature/.../src/main/`；宿主侧副本已全部删除（0c52cb9），禁止复制粘贴回宿主或新增第三份实现。`scripts/verify_ai_parity.py` 守卫此条。
3. **受保护文件**：`app/src/main/assets/catalog.json`、`modules.json`、`modules/*.apk`、`version.properties` 是发布产物，除非任务明确要求构建/发布否则不得改动；`autoBumpVersion=true` 时 `assembleDebug` 会自动递增 versionCode 并回写，交付必须记录当前 versionCode。
4. **小补丁 + 回归测试**：修 bug 必须加"修复前失败、修复后通过"的回归测试；完成前至少运行 `python scripts/verify_agent_contract.py`、相关专项 verify 脚本与 scoped `git diff --check`；未验证项必须明示，不得以"应该可以"代替证据。
5. **分支保护**：`main` 受保护，CI（lint-and-test / build / gitleaks）全绿才可合入；改动先推 `feature/**` 或 `hotfix/**`。完整规范见本地 `docs/开发规范-分支与发布.md`（不上传 GitHub，必读）。

## 子目录规范路由表

进入以下目录工作时，根规范 + 对应子目录 AGENTS.md 同时生效：

| 工作目录 | 子规范 |
|---|---|
| `module-store/feature/games/games/chinesechess/**` | [中国象棋不变量与实机验收](module-store/feature/games/games/chinesechess/AGENTS.md) |
| `module-store/feature/games/games/go/**` | [围棋规则、AI 与实机验收](module-store/feature/games/games/go/AGENTS.md) |
| `core/module-host/**`、模块装载/资源/目录信任 | [模块隔离与目录信任策略](core/module-host/AGENTS.md) |

## 专项 verify 脚本速查

| 场景 | 脚本 |
|---|---|
| 象棋/围棋/构建链契约 | `verify_agent_contract.py`、`verify_chinese_chess.py`、`verify_go.py` |
| 单一真相源守卫 | `verify_ai_parity.py` |
| 模块隔离 | `verify_isolation.py` |
| 预装模块 | `verify_preinstalled_modules.py` |
| 安全条款（exported/SHA/钉扎/降级/目录签名） | `verify_security_clauses.py` |
| 只降不增棘轮（空 catch / boolean flag / SOFT） | `verify_ratchet.py` |
| 发布资产哈希 | `verify_protected_assets.py snapshot` / `verify` |
| ADB 相关 | `verify_adb_{discovery,entries,operations,process,protocol}.py` |
| 浏览器 / scrcpy | `verify_browser.py`、`verify_scrcpy.py` |

以上脚本已接入 `.github/workflows/ci.yml`；改到对应域时本地先行运行。

## Agent 专属规则

通用规则（上方铁律）对所有 agent 生效；本节按工具分类，**各工具只执行自己名下的小节**，其他小节跳过。新工具（Codex/Claude 等）有专属约定时在下方追加 `###` 小节，不要写入通用部分。

### 多实例并行避让（所有 agent 生效）

用户宣告另一 agent 软件正在同一工作树施工时（以用户宣告为准，未宣告不适用）：

1. 后到侧**冻结仓库**：不写仓库内任何文件（含 BUG_LEDGER.md）、不跑 gradle 构建、不做任何 git 写操作（commit/stash/checkout/branch）。
2. 只允许只读操作（grep/读文件/`git log`）；产出写仓库外（如 `D:\Developmment\<主题>.md`）。
3. 用户宣告完成/解除后，先 `git fetch` + 对齐 `origin/main`，再恢复写操作。

### ZCode 专用（其他工具跳过本节）

GitHub 操作（本机实测要点）：

- `gh` 未登录，token 内联提取：`export GH_TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')`；shell 状态不跨调用保留，每条命令都要重新 export。
- push/fetch 直连不稳：`git -c http.proxy=socks5h://127.0.0.1:10808 <cmd>`（v2rayN SOCKS，偶发 TLS 超时重试即可）；`gh` 的 GraphQL 同理。
- main 保护分支流程：feature 分支 → PR → CI 绿 → merge。**必需检查必须落在最新 head 上**：串行合并多个 PR 时，后合的会 BEHIND，先 `gh pr update-branch N` 重跑 CI，`--admin` 绕不过（"3 of 3 required status checks are expected"）。
- `Instrumented Tests (emulator)` 腿在 main 长期红（存量问题），非必需检查，`UNSTABLE` 状态可合并。
- 修复纪律门禁：生产 `.kt/.java` diff 必须伴随测试/守卫变更（`*/src/test/**`、`scripts/verify_*`、`config/**`、`BUG_LEDGER.md` 任一），或打 label `no-test-justified` 并在 PR 描述写修复报告单。BUG_LEDGER 新条目写 `PENDING` 守卫会抬 guardless 计数、违反 ratchet 只降不增基线——不可测修复走 label 逃生门。

构建副作用还原（配合铁律 3）：

- 本地 `assembleDebug` 会自动 bump `version.properties` 并重打包 `assets/modules/*` + 重写 `catalog.json`/`modules.json`；验证类构建完成后执行 `git checkout -- version.properties app/src/main/assets/` 还原，发布类构建除外。

仓库外产出约定：

- 跨会话文档、问题台账、预备补丁写仓库外（`D:\Developmment\<主题>.md`、`D:\Developmment\fix_prep\`），不混入仓库工作树；等窗口期一次性入账（BUG_LEDGER/PR）。
