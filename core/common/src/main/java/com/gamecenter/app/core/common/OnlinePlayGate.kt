package com.gamecenter.app.core.common

/**
 * 联机对战可用性总闸（P2 · 明确下线）。
 *
 * 背景：联机基础设施（`core:online` 的房管/中继与历史 `modules:online-core`）尚未接入
 * 生产环境，动态模块侧仍为空存根（`com.gamecenter.app.network.GameSocketClient/Server`）。
 * 为避免用户进入"永远连不上"的空实现，所有游戏模块的联机入口统一经本闸门下线，
 * 改为提示"即将上线"。
 *
 * 恢复联机的步骤：
 * 1. 将本闸门 [ENABLED] 置回 true；
 * 2. 动态模块的 `network.GameSocketClient/Server` 由存根切换为 `core:online` 真实实现；
 * 3. 配置中继服务地址并完成双设备 + 服务端验收（含 AGENTS.md §3 的 commitMove 落子闸门不变量）。
 */
object OnlinePlayGate {

    /** 联机可用状态：当前统一下线。 */
    const val ENABLED: Boolean = false

    /** 下线提示文案（模块可能无资源上下文，使用常量避免资源依赖）。 */
    const val COMING_SOON_MESSAGE: String = "联机功能即将上线，敬请期待"
}