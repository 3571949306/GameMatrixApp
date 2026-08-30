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
