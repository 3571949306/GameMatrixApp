# 小游戏 AI 响应优化交接说明

更新时间：2026-05-21

## 1. 任务背景

本轮原始目标有两部分：

1. 先解决 `项目改进建议书.md` 在用户本机查看时显示乱码的问题。
2. 按既定顺序优化小游戏里“人机反应慢”的问题，之后更新 Markdown、上传 GitHub、构建并发布正式版安装包。

当前状态：

- `项目改进建议书.md` 已处理为 Windows 侧可正常显示的编码形式。
- AI 响应优化**已完成分析，但代码改造未完成**。
- 用户已明确要求：**先不要继续修复代码，只输出中文交接文档，供另一个 AI 接手。**

## 2. 关键结论

小游戏“AI 反应慢”并不全是搜索算法慢，至少有一部分是 UI 层**人为补的固定延迟**。

### 五子棋

文件：

- `app/src/main/java/com/gamecenter/app/games/gomoku/GomokuActivity.java`
- `app/src/main/java/com/gamecenter/app/games/gomoku/GomokuAI.java`

定位结果：

- `GomokuActivity` 在 AI 算完后，又 `postDelayed(..., 300)`，会额外固定等待 `300ms`。
- `GomokuAI` 已经有战术早停：
  - `findImmediateWin(...)`
  - `findMajorThreat(...)`
- 当前难度时间预算：
  - 低：`450ms`
  - 中：`1200ms`
  - 高：`3500ms`
  - 大师：`8000ms`

结论：

- 五子棋第一优先不是改搜索框架，而是先去掉或缩短 UI 假延迟。
- 引擎本身已有一部分战术早停，收益重点在响应链路，不在先重写算法。

### 中国象棋

文件：

- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessActivity.java`
- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java`

定位结果：

- `ChineseChessActivity` 中：
  - `long delay = Math.max(1000 - elapsed, 100);`
  - 这表示 AI 至少要“思考”约 1 秒。
- `ChineseChessAI` 已经是较完整的搜索栈：
  - Iterative Deepening
  - PVS
  - Aspiration Window
  - Transposition Table
  - Null Move Pruning
  - Futility Pruning
  - LMR
  - Quiescence
  - Killer / History Heuristics

结论：

- 象棋“慢”的第一来源就是固定 1 秒最小展示时间。
- 不建议优先上深层并行搜索，先把 UI 层硬延迟改掉，收益最高，风险最低。

### 围棋

文件：

- `app/src/main/java/com/gamecenter/app/games/go/GoActivity.java`
- `app/src/main/java/com/gamecenter/app/games/go/GoGame.java`

定位结果：

- `GoActivity` 中 AI 算完后额外 `postDelayed(..., 400)`。
- `GoGame` 常量：
  - `AI_TIME_LIMIT_MS = 1800`
  - `MAX_ROOT_SIMULATIONS = 22000`
- `GoGame.getBestMove()` 当前策略：
  - 先对合法着法打先验分
  - 只保留前 `28` 个候选
  - 在时间上限内做根节点 Monte Carlo 模拟
- `randomPlayout(...)` / `simulateRootMove(...)` 使用复制棋盘，天然适合做根并行。

结论：

- 围棋适合做两层优化：
  1. 去掉 `400ms` 固定展示延迟。
  2. 在 `GoGame` 做动态时间预算 + 根并行模拟。

## 3. 已确定的优化顺序

这是本对话中已经明确确认过的顺序，后续接手 AI 应按此顺序执行：

1. 去掉或缩短 UI 假延迟
2. 加即时战术早停
3. 改为动态时间预算，而不是硬等固定时长
4. 围棋做根并行 Monte Carlo
5. 五子棋做根层并行搜索
6. 中国象棋最后再考虑并行，先做缓存、排序、响应链路优化

## 4. 建议的具体改法

### 第一阶段：只改 UI 响应链路

目标：先让用户“感觉慢”的问题明显下降。

建议：

- 五子棋：
  - 把固定 `300ms` 改成按难度的最小响应延迟，例如 `80/120/170/230ms`
- 中国象棋：
  - 把固定“至少 1 秒”改成按难度的最小响应延迟，例如 `140/220/340/480ms`
- 围棋：
  - 把固定 `400ms` 改成统一 `80~150ms` 量级，建议先取 `120ms`

原则：

- AI 如果本身已经算得更久，就不再额外补时。
- AI 如果算得非常快，只补一个很短的最小展示延迟，避免“瞬移落子”。

### 第二阶段：引擎优化

#### 围棋

优先级最高。

建议：

1. 在 `GoGame.getBestMove()` 中引入动态时间预算
   - 不要固定 `1800ms`
   - 可按 `moveCount`、候选数、局面阶段动态调整
   - 建议控制在约 `900ms ~ 1500ms`

2. 增加快速战术短路
   - 优先处理明显提子
   - 优先处理收益很高的即时吃子点
   - 只做保守短路，不要做大规模规则改写

3. 做根节点并行模拟
   - 候选着法仍然先排序
   - 根候选共享 deadline
   - 多线程分别做 `simulateRootMove(...)`
   - 合并 visits / values

4. 把 `Math.random()` 换成线程局部随机源
   - 并行时建议改为 `ThreadLocalRandom`

#### 五子棋

建议：

1. 先保留现有 `findImmediateWin` / `findMajorThreat`
2. 在根层候选上做有限并行
3. 不要粗暴并行整棵 alpha-beta 树，容易导致剪枝效率下降

#### 中国象棋

建议：

1. 先只改最小响应延迟
2. 不急着做深层并行
3. 后续优先关注：
   - move ordering
   - transposition table 利用率
   - 不必要的拷贝/重复评估

## 5. 当前工作区里的半成品状态

这部分很重要，接手 AI 需要先看。

### 已发生的未完成修改

#### 1. `ChineseChessActivity.java`

当前已经有一部分改动，方向是对的：

- 新增了 `AI_MIN_RESPONSE_DELAYS_MS`
- 把
  - `Math.max(1000 - elapsed, 100)`
  改成了
  - `Math.max(getAiMinResponseDelayMs() - elapsed, 0L)`
- 已增加 `getAiMinResponseDelayMs()`

但仍需要接手 AI 自行复核：

- 代码风格和注释排版是否需要整理
- 是否在 `delay == 0` 时改成 `post(...)` 而不是 `postDelayed(..., 0)`

#### 2. `GomokuActivity.java`

当前**处于破损状态**，不能直接当完成品使用。

现状：

- 已加入 `AI_MIN_RESPONSE_DELAYS_MS`
- `handleCellClick(...)` 部分逻辑已经改成最小响应延迟思路
- 但是 `getAiMinResponseDelayMs()` 的插入位置和注释块被污染，文件中出现了字面量形式的 `` `r`n `` 和乱码注释拼接

结论：

- `GomokuActivity.java` 需要先修结构，再继续优化。
- 接手 AI 应优先修复该文件的方法边界和注释块，再继续后续改造。

#### 3. `GoActivity.java`

当前**基本还没改成功**。

仍然保留：

- `postDelayed(..., 400)` 两处

结论：

- 围棋 Activity 需要重新按方案改。

#### 4. `GoGame.java`

当前尚未开始引擎改造：

- 仍然是 `AI_TIME_LIMIT_MS = 1800`
- 仍然是 `MAX_ROOT_SIMULATIONS = 22000`
- 尚未加入动态时间预算
- 尚未加入根并行模拟
- 尚未加入线程局部随机源

## 6. 建议接手步骤

建议按下面顺序执行：

1. 先修 `GomokuActivity.java` 的破损注释和方法结构
2. 完成 `GomokuActivity.java` 的最小响应延迟改造
3. 检查并整理 `ChineseChessActivity.java`
4. 重做 `GoActivity.java` 的最小响应延迟改造
5. 在 `GoGame.java` 做动态时间预算
6. 在 `GoGame.java` 做保守战术短路
7. 在 `GoGame.java` 做根并行模拟
8. 跑验证
9. 更新 Markdown
10. 再做正式版构建和发布

## 7. 验证命令

仓库里已有验证经验，优先使用下面两条：

```powershell
.\gradlew.bat :app:test -PautoBumpVersion=false
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
```

如果准备正式版，再跑：

```powershell
.\gradlew.bat :app:assembleRelease -PautoBumpVersion=false -PupdateChannel=stable
```

或按仓库既有发布任务：

```powershell
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

## 8. 发布相关说明

正式版发布相关入口：

- 发布指南：`docs/PUBLISH_GUIDE.md`
- Gradle 任务：`app/build.gradle`
- VPS 上传脚本：`tools/upload_to_vps.py`
- GitHub Release 上传脚本：`tools/upload_to_github_release.py`

注意：

- 仓库工作区当前不是干净状态，存在用户自己的未提交改动。
- 接手 AI 不应回滚无关文件。
- 正式版上传前，必须先确认当前工作树中哪些改动属于本次 AI 优化，哪些是用户已有改动。

## 9. 文档更新范围

如果优化完成，建议同步更新：

- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`
- `CODE_WIKI.md`
- `项目改进建议书.md`

建议在 `项目改进建议书.md` 中单列一条：

- 小游戏 AI 响应链路优化：去除固定假延迟，改为按难度或阶段的最小响应延迟；围棋改为动态时间预算并补根并行模拟。

## 10. 接手注意事项

- 当前对话要求是“生成中文 md 给另一个 AI 修复”，不是继续本轮编码。
- 因此本文件是交接文档，不代表当前代码已经完成。
- 接手 AI 的第一步应是检查 `GomokuActivity.java` 的当前破损状态，再决定是局部修复还是回到 `HEAD` 后重新改。
