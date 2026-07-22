# GameMatrixApp 棋类人机对战 AI 实现分析

> 分析对象：夹层 App（GameMatrixApp，包名 `com.gamecenter.app`）中**棋类游戏的人机对战 AI**（五子棋 / 中国象棋 / 围棋 / 井字棋 / 跳棋），对应前一份《棋类游戏 AI 实现原理调研报告》的"原理 → 工程落地"映射。
> 代码根：`D:\Developmment\GameMatrixApp\app\src\main\java\com\gamecenter\app\games\`
> 生成日期：2026-07-20

---

## 0. 总览：棋类游戏与 AI 能力矩阵

所有棋类游戏在 `games/` 下按子包分包，并在 `GameRegistry.kt`（约 277–377 行 `buildStaticCategories`）统一注册。

| 游戏 | gameId | 主类 | 部署 AI 算法 | 难度档 | 后台线程 |
|---|---|---|---|---|---|
| 五子棋 | `gomoku` | `GomokuActivity` / `GomokuGame` / `GomokuAI` / `GomokuView` | Minimax + α-β + 迭代加深 + VCF 算杀 | 4 档 | ✅ 单线程 Executor |
| 中国象棋 | `chinesechess` | `ChineseChessActivity` / `ChineseChessView` / `ChineseChessAI` | Minimax + α-β | 4 档 | ✅ 单线程 Executor |
| 围棋 | `go` | `GoActivity` / `GoGame` / `GoAI` / `GoView` | 随机/贪心/Minimax/MCTS(UCT) 按档切换 | 4 档 | ❌ 主线程 |
| 井字棋 | `tic` | `TicTacToeActivity` / `TicTacToeView` | 随机 / 完整 Minimax（3×3 可穷举） | 2 档 | ❌ 主线程 |
| 跳棋 | `checkers` | `CheckersActivity` / `CheckersView` | 随机 / 启发式优先跳吃（无搜索） | 2 档 | ❌ 主线程 |

**未实现**：黑白棋/翻转棋（reversi/othello）、国际象棋（chess）。围棋以 `go` 命名实现。中国象棋另在 `module-store/feature/games/games/chinesechess/.../ChineseChessAI.java` 有一份**外置热更新副本**（实现与主模块一致）。

**核心结论**：五子棋 AI 是工程最完整、最贴近第一份调研报告理论范式的实现；中国象棋次之；围棋引入了真正的 MCTS（对应报告第 4 节现代技术）；井字棋/跳棋属于教学级/启发式实现。

---

## 1. 核心搜索算法实现（原理 → 落地对照）

### 1.1 五子棋 —— α-β Minimax + 迭代加深 + VCF

文件：`gomoku/GomokuAI.java`

- **难度档位**（第 36–41 行 `DIFFICULTY_PROFILES`）：时间与深度成档。
  | 档 | 名称 | 时间上限 | 搜索深度 |
  |---|---|---|---|
  | 1 | 低 | 450 ms | 3 |
  | 2 | 中 | 1200 ms | 5 |
  | 3 | 高 | 3500 ms | 8 |
  | 4 | 大师 | 8000 ms | 10 |

- **α-β 剪枝递归**（`minimax(...)`，第 518–567 行）：经典 max/min 交替，每搜索 256 个节点检查一次超时（`TIME_CHECK_INTERVAL=256`，第 45、522 行）。胜利分值乘以 `(depth+1)` 以**偏好更快取胜**。

- **着法生成**（`getCandidateMoves`，第 414–454 行）：仅取"已有棋子周围 2 格内"的空位作为候选，大幅剪枝分支因子；空盘从天元附近随机开局。

- **迭代加深**（`getBestMove`，第 686–711 行）：
  ```java
  for (int depth = 1; depth <= maxDepth; depth++) {
      // 超时即返回上一轮已完成的最佳着法
  }
  ```
  这是工程上的关键容错：时间耗尽时不返回"半截搜索"，而是回退到上一完整深度的结果。

- **VCF 算杀**（`findVcfMove` / `vcfSearch`，第 761–852 行）：仅大师档启用（`vcfEnabled`，第 74 行），`VCF_MAX_DEPTH=6`（第 51 行），占用最多 40% 搜索时间（`VCF_TIME_RATIO=0.4`，第 54 行）。VCF = "连续冲四"（Victory by Continuous Four），专门在残局/中盘寻找必胜连杀路径——正是第一份报告所述"五子棋对一手制胜极敏感 → 威胁优先搜索"的工程体现。

- **强制着法兜底**（`findImmediateWin`，第 660–671 行）：搜索前先处理"立即取胜 / 阻挡对手取胜 / 重大威胁"，即使搜索未完成也能正确攻防。

### 1.2 中国象棋 —— α-β Minimax

文件：`chinesechess/ChineseChessAI.java`（第 133–166 行）
```java
private int minimax(int[][] board, int depth, int alpha, int beta, boolean isMax) {
    if (cancelled) return 0;
    if (depth == 0) return evaluateBoard(board);
    ...
    if (beta <= alpha) break; // 剪枝
}
```
- 搜索深度档位：`SEARCH_DEPTHS = {2, 4, 6, 8}`（第 47 行）。
- 着法生成：`generateMoves` → `generatePieceMoves`（第 229–359 行）为车/马/炮/兵/将/士/象分别生成走法。

### 1.3 围棋 —— Minimax + MCTS(UCT)

文件：`go/GoAI.java`
- `minimax`（第 98–122 行）：α-β + `MAX_NODES=80000` 节点上限。
- **MCTS**（`mctsMove`，第 169–218 行）+ UCT 选择（`selectChild`，第 220–235 行）：
  ```java
  double uct = child.totalReward / child.visits
          + 1.41 * Math.sqrt(Math.log(node.visits) / child.visits);
  ```
  时间上限 `MCTS_TIME_LIMIT_MS = 1500`（第 10 行）。这正是第一份报告第 4 节所述"蒙特卡洛树搜索四步循环（Selection→Expansion→Simulation→Backpropagation）"的最小可运行实现：循环体内 `while` 选子 → 扩展未探索着法 → `playout` 随机模拟 → 反向传播 `visits`/`totalReward`。

### 1.4 井字棋 / 跳棋

- 井字棋（第 335–366 行）：完整 Minimax，终局分 `10 - depth`（越快赢越高），3×3 可穷举故无需剪枝。
- 跳棋（第 388–400 行）：困难档仅为"优先跳吃否则随机"的贪心，**未实现搜索算法**。

---

## 2. 评估函数设计（对应报告第 2 节）

### 2.1 五子棋 —— 棋型价值表 + 中心偏置（最完整）

文件：`gomoku/GomokuAI.java`

- **棋型评分表**（`addWindowScore`，第 335–361 行）：

  | 棋型 | 分值 |
  |---|---|
  | 五连 | 10,000,000 |
  | 活四 | 900,000 |
  | 冲四（一端封） | 180,000 |
  | 活三 | 35,000 |
  | 眠三（一端封） | 4,000 |
  | 死三 | 800 |
  | 活二 | 1,500 / 眠二 250 |
  | 活一 | 80 / 眠一 10 |

- **间隔棋型识别**（`evaluateGapPatterns`，第 247–313 行）：专门识别"跳活三(25,000)""跳冲四(120,000)"，捕捉非连续连线威胁。
- **局面评估**（`evaluate`，第 388–402 行）：累加己方威胁分，对手分乘 `defenseBias`（第 68 行，低档 1.40 重防守 → 大师档 1.12 重进攻）。
- **中心偏置**（`centerBias`，第 942–946 行）：越靠中心得分越高（最多 +40），体现"开局占中"的棋理。
- **组合威胁加成**（第 219–221、624–627 行）：双四、双活三等叠加额外分。

### 2.2 中国象棋 —— 子力价值表 + 简化位置加分

文件：`chinesechess/ChineseChessAI.java`（第 35–44 行）
```java
// 将/士/象/马/车/炮/兵
{10000, 200, 200, 400, 900, 450, 100}
```
- `evaluateBoard`（第 173–190 行）：子力求和。
- `getPositionBonus`（第 195–218 行）：仅兵/马/车/炮有简单位置奖励，**未使用完整 PST 位置价值表**（对比专业引擎差距所在）。

### 2.3 围棋 / 井字棋 —— 极简评估

- 围棋 `evaluateBoard`（第 124–133 行）：仅按子数 `白*10 - 黑*10`，**不计算围空/目**；`evaluatePosition`（第 135–151 行）加微弱中心距与邻接己方子加分。
- 井字棋：仅终局胜负分（见 1.4），无棋型表。

---

## 3. 完整链路：玩家落子 → AI 思考 → AI 落子

### 3.1 五子棋（最规范，后台线程 + 思考态）

`GomokuActivity.handleCellClick`（第 384–401 行） → `game.makeMove` + `switchPlayer` → `triggerAiMove()`：
```java
aiThinking = true; gomokuView.setAiThinking(true);
aiExecutor.execute(() -> {                         // Executors.newSingleThreadExecutor()
    int[] bestMove = ai.getBestMove(game, aiPlayer);
    mainHandler.post(applyMove);                  // 回主线程刷新
});
```
- `aiExecutor = Executors.newSingleThreadExecutor()`（第 173 行），**后台线程计算、主线程刷新**，不阻塞 UI。
- **"思考中"状态**：`gomokuView.setAiThinking(true)` + `aiThinking` 标志（第 128、387 行）。
- **拟真延迟**：`AI_MIN_RESPONSE_DELAYS_MS = {80,120,170,230}`（第 67 行），让 AI 落子更像人类。
- **代际防错**：`aiGeneration` 计数（第 129、418 行）防止"悔棋/重开"后陈旧结果回写。

### 3.2 中国象棋（同范式）

`ChineseChessView.setOnPlayerMoveListener` → `ChineseChessActivity.handlePlayerMove`（第 381–434 行）：`aiExecutor.execute(...)` → `mainHandler.post(applyMove)` → `chessView.applyAIMove(...)`；同样有 `aiThinking` / `aiGeneration` 防错。

### 3.3 围棋 / 井字棋 / 跳棋（⚠️ 主线程）

- 围棋 `GoActivity.onCellClick`（第 197–211 行）→ `handler.postDelayed(this::aiMove, 300)` → `aiMove`（第 213–240 行）**直接在主线程调用 `ai.findBestAiMove(game)`**。大师档 MCTS 最多 1.5s，**期间 UI 卡顿**。
- 井字棋/跳棋规模小，`handler.postDelayed` 在主线程同步计算无感知。

---

## 4. 五子棋 vs 象棋 AI 实现差异（呼应报告第 3 节）

| 维度 | 五子棋 | 中国象棋 |
|---|---|---|
| 评估来源 | 纯棋型威胁链（无子力概念） | 子力价值表 + 简单位置加分 |
| 搜索重点 | 对"一手制胜"极敏感 → VCF 算杀优先 | α-β + 深度 + 着法生成（车马炮兵） |
| 难度差异化 | 防守偏置 + 概率随机化让新手能赢 | 仅调搜索深度 |
| 理论弱点 | 未实现禁手规则（无禁手自由规则） | 缺将死（checkmate）判定，仅 `isInCheck` 提示 |
| 工程完整度 | 完整（剪枝+迭代加深+算杀+兜底） | 完整但评估简化（无 PST） |

**要点**：五子棋 AI 印证了报告所述"五子棋是从纯威胁链出发、对一手制胜极敏感"的特性（用 VCF 与显式强制着法兜底）；象棋 AI 印证了"依赖子力价值 + 位置表 + α-β 深度搜索"的范式，但本项目未上 PST/NNUE，评估偏朴素。

---

## 5. 现代 AI 技术落地（呼应报告第 4 节）

**围棋 MCTS 是本项目唯一真正落地的"现代"技术**：`GoAI.mctsMove` 实现了 UCT 版蒙特卡洛树搜索（探索常数 1.41），按难度档切换"随机 → 贪心 → Minimax → MCTS"。这与报告所述"MCTS 攻克高分支因子游戏"一致——只是围棋评估函数仍极简（不计围空），故强度有限。

**未落地**：深度学习/神经网络评估（NNUE、AlphaZero 范式）在项目中**未使用**。五子棋、象棋均为纯手工评估 + 搜索。这与报告结论一致——中小项目以可解释、可调试的 α-β + 手工评估为主，NNUE/深度强化为专业引擎（如皮卡鱼、Katago）专属。

---

## 6. 棋盘状态与对局管理

| 游戏 | 数据结构 | 胜负判定 | 悔棋 | 状态持久化 |
|---|---|---|---|---|
| 五子棋 | `int[15][15]`（第 52 行），四方向常量 | `checkWinAt` 数连≥5（第 267–302 行）；满盘平局（第 323 行） | `undoLastMoves`（第 240–253 行） | `onSaveInstanceState`（第 594–686 行）旋转恢复 |
| 中国象棋 | `int[10][9]`（`getBoardState`，第 181 行） | `checkGameEnd`/监听器 | `undoMove`（第 294 行） | — |
| 围棋 | `int[9][9]`，`KOMI=6.5` | 连续两次 pass（第 86 行） | **未实现** | — |
| 井字棋/跳棋 | `int[3][3]` / `int[8][8]` | 行列对角 / 空盘判负 | 跳棋无；井字棋无 | — |

围棋提子/气：`removeCapturedStones` + `countLiberties`（DFS，第 137–192 行）；禁着：`isValidMove`（第 97–110 行）禁止自杀与打劫复形（全局同形）。

---

## 7. 错误处理与边界（已知缺陷）

| 项目 | 处理 | 备注 |
|---|---|---|
| 平局 | 五子棋满盘 / 围棋双 pass / 井字棋满盘 / 跳棋无子 | 已实现 |
| 五子棋禁手 | **未实现**（仅判界内且空，第 189–191 行） | 无三三/四四/长连禁手，属无禁手自由规则 |
| 中国象棋将死 | **缺判定**，`minimax` 仅"无合法走法"终止，`isInCheck` 仅 UI 提示 | 潜在逻辑漏洞 |
| AI 返回 null | 五子棋/围棋有兜底（判 pass/取上一深度）；**中国象棋 Activity 未显式处理 `getBestMove` 返回 null** | 潜在崩溃隐患 |
| 超时 | 五子棋迭代加深回退 + 节点超时检查；围棋 MCTS 时间循环 | 已实现 |
| 非法着法 | 五子棋 `makeMove` 非空返回 null（第 202 行）+ UI 前置校验 | 已实现 |
| 线程卡顿 | 五子棋/象棋后台线程；**围棋 MCTS 主线程 1.5s 卡 UI** | 建议迁移后台线程 |

---

## 8. 小结与优化建议

**已做对**：
1. 五子棋 AI 是教科书级落地——α-β + 迭代加深 + 棋型价值表 + VCF 算杀 + 超时兜底 + 后台线程 + 思考态 + 代际防错，完整印证第一份调研报告理论。
2. 围棋 MCTS 是"现代技术"的最小可运行验证。
3. 难度分级以"时间 + 深度 + 防守偏置 + 概率随机化"组合，体验分层合理。

**待优化（按风险排序）**：
1. **围棋 AI 主线程 MCTS 卡顿** → 迁移到 `ExecutorService`（参照五子棋）。
2. **中国象棋 `getBestMove` 返回 null 无兜底** → Activity 层加空判断与"认输/重开"分支。
3. **中国象棋缺将死判定** → `minimax` 终局加入被将死检测，否则残局逻辑失真。
4. **五子棋未实现禁手** → 若需"有禁手标准规则"，在 `isValidMove` 增加三三/四四/长连判定（当前为无禁手自由规则，可作为可选项）。
5. **评估函数升级空间** → 象棋引入 PST 位置表；围棋引入围空/目数评估，可显著提升强度。

**与第一份报告的关系**：本报告验证了调研报告中的核心原理（Minimax/α-β 剪枝、棋型/子力评估、MCTS）在真实 Android 项目中的工程化形态；同时暴露了"理论完备"与"工程简化"之间的差距（禁手、将死、PST、NNUE 均未实现），为后续增强指明路径。

---

## 附：关键文件索引

- 五子棋 AI：`games/gomoku/GomokuAI.java`、`GomokuGame.java`、`GomokuActivity.java`、`GomokuView.java`
- 中国象棋 AI：`games/chinesechess/ChineseChessAI.java`、`ChineseChessView.java`、`ChineseChessActivity.java`（外置副本见 `module-store/feature/games/games/chinesechess/.../ChineseChessAI.java`）
- 围棋 AI：`games/go/GoAI.java`、`GoGame.java`、`GoActivity.java`
- 井字棋：`games/tic/TicTacToeActivity.java`
- 跳棋：`games/checkers/CheckersActivity.java`
- 游戏注册：`GameRegistry.kt`（约 277–377 行）
