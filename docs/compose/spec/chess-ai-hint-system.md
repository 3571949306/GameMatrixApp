---
feature: chess-ai-hint-system
status: delivered
updated: 2026-07-23
branch: feature/chess-ai-hint
commits: # filled at delivery
---

# 中国象棋AI提示系统

## Report

**What was built** — 完整的中国象棋AI提示系统，包含9个功能模块：
1. 提示次数限制系统（每局10次，每步1次，3秒冷却）
2. 提示视觉反馈系统（棋盘高亮、箭头指示、提示弹窗）
3. 错误分析系统（战术/战略/位置错误识别）
4. 异步计算系统（后台线程计算，不阻塞UI）
5. 提示缓存机制（LRU缓存，最多100条）
6. 对局记录系统（Room持久化，PGN导出）
7. 复盘功能（每步分析，好棋坏棋标记）
8. 主题学习系统（5个战术主题，21道练习题）
9. 新手引导系统（7步引导流程）

**Verification** — 编译验证通过：`:app:compileDebugJavaWithJavac` BUILD SUCCESSFUL

## [S1] Problem
用户在人机对战时缺乏指导，不知道该如何走棋。需要实现一个提示系统，既能帮助用户理解局面，又具有教学意义。同时AI不应因用户使用提示而降低强度，胜利必须是真实的。

## [S2] Design

### 核心设计原则
1. **AI强度保持**：AI不因用户使用提示而变弱
2. **教学意义**：提示不仅告诉用户走哪里，还解释为什么
3. **可击败性**：用户理解提示后可以凭借实力击败AI
4. **防滥用**：提示次数有限制，防止用户过度依赖

### 功能模块

#### P0：必须实现

**T1: 提示次数限制系统**
- 每局最多10次提示
- 每步最多1次提示
- 提示冷却时间3秒
- 次数用完后显示提示

**T2: 提示视觉反馈系统**
- 棋盘高亮显示目标位置（起点绿色、终点蓝色）
- 箭头指示走法方向
- 提示弹窗显示走法和解释
- 执行走法后清除高亮

**T3: 错误分析系统**
- 识别用户走法与最佳走法的差异
- 判断错误类型（战术/战略/位置）
- 生成针对性的错误解释
- 提供改进建议

#### P1：重要

**T4: 异步计算系统**
- 提示计算在后台线程执行
- 避免阻塞UI线程
- 计算完成后更新UI
- 显示加载状态

**T5: 提示缓存机制**
- 缓存已计算的提示结果
- 相同局面重复提示时直接返回缓存
- 缓存过期策略（局面变化后失效）
- 内存限制（最多缓存100条）

**T6: 对局记录系统**
- 保存每步走法和评分
- 记录提示使用情况
- 支持对局回放
- 导出为PGN格式

#### P2：建议

**T7: 复盘功能**
- 对局结束后分析每步走法
- 标记好棋和坏棋
- 提供改进建议
- 生成复盘报告

**T8: 主题学习系统**
- 按战术主题分类（将军、抽将、杀棋等）
- 提供主题练习模式
- 进度追踪
- 成就解锁

**T9: 新手引导系统**
- 首次使用提示时的引导
- 逐步介绍功能
- 示例演示
- 跳过选项

### 技术架构

```
ChineseChessActivity
    ├── HintButton (UI)
    ├── HintDialog (UI)
    ├── HintSystem (核心逻辑)
    │   ├── MoveAnalyzer (走法分析)
    │   ├── TacticalAnalyzer (战术分析)
    │   ├── ExplanationGenerator (解释生成)
    │   └── HintLimiter (次数限制)
    ├── VisualFeedbackManager (视觉反馈)
    ├── MistakeAnalyzer (错误分析)
    ├── HintCache (缓存)
    ├── GameRecorder (对局记录)
    └── ReviewSystem (复盘)
```

### 接口定义

```java
// 提示结果
public class HintResult {
    public int[] move;           // 推荐走法
    public String explanation;   // 解释文本
    public TacticalPattern pattern; // 战术类型
    public int score;            // 评分
}

// 错误分析结果
public class MistakeResult {
    public MistakeType type;     // 错误类型
    public int scoreDiff;        // 评分差异
    public String explanation;   // 解释文本
    public int[] betterMove;     // 更好的走法
}

// 对局记录
public class GameRecord {
    public List<int[]> moves;    // 走法列表
    public List<Integer> scores; // 评分列表
    public List<HintResult> hints; // 提示列表
    public long startTime;       // 开始时间
    public long endTime;         // 结束时间
    public GameResult result;    // 对局结果
}
```

## [S3] Out of Scope

1. **AI强度调整**：AI不因用户使用提示而变弱
2. **网络功能**：提示计算完全本地化
3. **多语言支持**：仅支持中文
4. **付费功能**：提示完全免费
5. **社交功能**：不涉及分享、排行等

## Tasks

### P0 Tasks

- [x] T1: 实现提示次数限制系统 — acceptance: 每局最多10次，每步最多1次，冷却3秒 (covers: S2-P0)
- [x] T2: 实现提示视觉反馈系统 — acceptance: 棋盘高亮显示目标位置，箭头指示方向 (covers: S2-P0)
- [x] T3: 实现错误分析系统 — acceptance: 能识别战术/战略/位置错误并给出解释 (covers: S2-P0)

### P1 Tasks

- [x] T4: 实现异步计算系统 — acceptance: 提示计算不阻塞UI，有加载状态 (covers: S2-P1; depends: T1)
- [x] T5: 实现提示缓存机制 — acceptance: 相同局面重复提示时直接返回缓存 (covers: S2-P1; depends: T4)
- [x] T6: 实现对局记录系统 — acceptance: 能保存走法、评分、提示，支持回放 (covers: S2-P1; depends: T4)

### P2 Tasks

- [x] T7: 实现复盘功能 — acceptance: 对局结束后能分析每步走法，标记好棋坏棋 (covers: S2-P2; depends: T6)
- [x] T8: 实现主题学习系统 — acceptance: 能按战术主题分类练习，有进度追踪 (covers: S2-P2; depends: T3)
- [x] T9: 实现新手引导系统 — acceptance: 首次使用时有引导，介绍功能 (covers: S2-P2; depends: T2)
