/**
 * 游戏公共接口包 — 定义所有游戏应遵循的统一契约。
 *
 * <p>本包提供游戏逻辑的抽象层，使不同游戏模块可以共享统一的接口规范，
 * 便于扩展新游戏、支持联机对战、以及与 UI 层解耦。
 *
 * <p>推荐的游戏模块架构（三层分离）：
 * <pre>
 * Activity (UI 层) — 负责界面展示和用户交互
 *   └── GameController (控制层) — 协调 UI 和逻辑，处理事件分发
 *         └── GameLogic (逻辑层) — 纯游戏状态和规则，不依赖 UI
 * </pre>
 *
 * <p>核心接口：
 * <ul>
 *   <li>{@link com.gamecenter.app.games.common.GameLogic} — 所有游戏的基础接口，
 *       定义状态查询、动作执行、游戏结束判定和重置等方法</li>
 *   <li>{@link com.gamecenter.app.games.common.OnlineGameLogic} — 联机游戏扩展接口，
 *       在 GameLogic 基础上增加动作序列化/反序列化和远程动作验证</li>
 * </ul>
 *
 * <p>新游戏应实现 GameLogic 接口，联机游戏应实现 OnlineGameLogic 接口。
 * 现有游戏可逐步迁移，不强制要求立即实现。
 */
package com.gamecenter.app.games.common;
