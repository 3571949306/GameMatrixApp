<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# 游戏模块全面评估报告（2026-07-22）

本报告基于对 27 款游戏核心源码的逐行调研，给出每款游戏的测试结论、优化建议及保留/淘汰/改造判定。

## 判定统计

| 判定 | 数量 | 游戏 |
|------|------|------|
| 保留（含小修） | 8 | gomoku、game2048、minesweeper、breakout、tic、tetris、snake、whack |
| 改造 | 12 | chinesechess、go、doudizhu、checkers、sudoku、klotski、sokoban、match、memory、flappy、brotato、reaction |
| 待淘汰（仅标记，暂不删除） | 7 | blackjack、pipeline、tiles、plane、guess、dice、rock |

## 待淘汰游戏清单（暂不删除，仅标记）

以下 7 款游戏因完成度低、玩法深度不足或存在根本性缺陷，标记为"待淘汰"。当前保留代码与注册，后续迭代决定是否删除。

| 游戏 | 核心问题 | 淘汰原因 |
|------|----------|----------|
| blackjack | recordWin 所有结果都调用(bug)；无AI类；UI字符串展示卡牌 | 完成度低，与旗舰棋类工程标准差距巨大 |
| pipeline | 验证逻辑根本错误(只比对目标旋转非连通性)；直线管4状态bug | 当前不是管道工玩法，需重写核心逻辑 |
| tiles | 名实不符(无连线玩法)；累计分bug；四张同牌；与Match/Memory三胞胎 | 名实不符+多处bug+严重同质化 |
| plane | 计分bug；仅一种敌机无弹幕；currentWave死逻辑；深度极浅 | 内容深度过低，竞争力最低 |
| guess | 二分查找必胜；深度上限低；无变种 | 教育意义大于娱乐意义，深度太浅 |
| dice | 加倍双重结算bug；不调recordWin；纯随机无策略 | 有功能性bug且玩法无策略深度 |
| rock | AI未实现(注释说有策略实为纯随机)；不调recordWin；maxWinStreak死代码 | 核心卖点缺失，无留存价值 |

> **处理方式**：暂不从 modules.json/GameRegistry 移除注册，仅在本文档标记。后续若确认淘汰，按规则22记入 `pending_delete_files.md` 并在任务结束后统一删除。

## 本次改造内容（GAME_REVAMP_2026）

### P1 基础设施修复
- **AchievementManager 重写**：成就键按 gameId 隔离，修正跨游戏串扰；引入阈值判定，修正无条件解锁 bug
- **BaseGameActivity 增强**：新增 `unlockAchievement`、`checkAchievementThreshold`、`recordHighScore`、`getHighScore` 方法

### P0 功能性 Bug 修复
- klotski 撤销 Bug（shuffle 后清空 moveHistory）
- game2048 canContinue 永不 true（合成 2048 后锁死）
- tiles 累计分丢失 Bug
- flappy/brotato/plane 计分膨胀 Bug（改覆盖式）
- dice 加倍双重结算 Bug + 补 recordWin
- reaction 失败轮计入统计 Bug + 最高分持久化

### P2 难度系统补全
- 为 flappy、brotato、plane、whack、match、memory、guess、go、doudizhu 补难度系统
- 为上述游戏补最高分持久化（recordHighScore）

### P4 深度改造
- sudoku：补唯一解验证（中心对称挖洞+回溯计数）+ 笔记/草稿功能
- sokoban：补充 7 个新关卡（共 10 关）+ 撤销功能（每关最多 10 次）
- chinesechess：修复棋谱展示（完整记录每步）+ 修复 getNotation 记谱法 bug

### P5 主题/本地化合规（27 款游戏全面重构）
- **范围**：全部 27 款游戏的 Java 文件，按 4 组并行重构（街机/棋类/益智/休闲）
- **字符串提取**：347 条硬编码中文字符串 → 各游戏 `strings_game_<name>.xml`（setText/Toast/Dialog）
- **颜色提取**：240 条硬编码 UI 颜色 → 新建 `colors_game_group_{a,b,c,d}.xml`（浅色）+ `values-night/` 对应深色变体
- **规则**：Canvas onDraw 中的 paint.setColor 保留（游戏视觉标识）；UI 元素颜色全部提取并配套深色主题
- **验证**：BUILD SUCCESSFUL，真机 smoke 测试无 FATAL EXCEPTION

### 待完成项（后续迭代）
- brotato 成长线（局间升级选择）
- checkers 规则补全（连跳+强制跳吃）+ Minimax AI
- go 棋盘尺寸选项（13×13/19×19）+ MCTS 性能优化
- match/memory 差异化或合并
- 英文翻译（values-en/）尚未创建，仅完成中文资源提取

---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
