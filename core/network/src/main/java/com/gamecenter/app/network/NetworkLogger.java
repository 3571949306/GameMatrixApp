package com.gamecenter.app.network;

import android.util.Log;

import com.gamecenter.app.network.BuildConfig;

/**
 * 网络模块统一日志工具类。
 *
 * <p>为游戏中心的网络通信（主要是 WebSocket 相关）提供结构化的日志输出。
 * 所有日志方法均受 BuildConfig.DEBUG 开关控制，Release 构建下自动静默，
 * 避免在生产环境中泄露敏感通信信息。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 final 类 + 私有构造器，纯静态工具类，不可实例化</li>
 *   <li>日志前缀统一为 "[GAME-WS]"，便于 Logcat 过滤</li>
 *   <li>logWs 方法自动脱敏 URL 中的查询参数，防止 token 等敏感信息泄露到日志</li>
 * </ul>
 * </p>
 */
public final class NetworkLogger {

    /** 是否启用网络调试日志，仅在 DEBUG 构建时为 true */
    private static final boolean DEBUG_NETWORK = BuildConfig.DEBUG;

    /** 统一日志前缀，用于 Logcat 中快速过滤网络相关日志 */
    private static final String LOG_PREFIX = "[GAME-WS]";

    private NetworkLogger() {
    }

    /**
     * 记录网络事件日志（DEBUG 级别）。
     *
     * <p>输出格式：[GAME-WS] [事件名] room=房间号 player=玩家ID type=消息类型 t=时间戳</p>
     *
     * @param tag         日志标签，通常为调用类的简单名称
     * @param event       事件名称，如 "CONNECT"、"MESSAGE"、"DISCONNECT" 等
     * @param roomCode    房间代码，为 null 时显示 "-"
     * @param playerId    玩家 ID
     * @param messageType 消息类型标识，为 null 时显示 "-"
     */
    public static void logEvent(String tag, String event, String roomCode, int playerId, String messageType) {
        if (!DEBUG_NETWORK) return;
        Log.d(tag, LOG_PREFIX + " [" + event + "] room=" + (roomCode != null ? roomCode : "-")
                + " player=" + playerId + " type=" + (messageType != null ? messageType : "-")
                + " t=" + System.currentTimeMillis());
    }

    /**
     * 记录 WebSocket 相关日志（DEBUG 级别），自动脱敏 URL 中的查询参数。
     *
     * <p>URL 中的查询字符串（? 之后的部分）会被替换为 "?..."，防止 token 等敏感参数泄露到日志。</p>
     *
     * @param tag    日志标签
     * @param event  WebSocket 事件名称，如 "CONNECTING"、"OPEN"、"CLOSED" 等
     * @param wsUrl  WebSocket 连接 URL，查询参数将被自动脱敏
     * @param detail 附加详情信息
     */
    public static void logWs(String tag, String event, String wsUrl, String detail) {
        if (!DEBUG_NETWORK) return;
        // 正则替换 URL 中 "?" 之后的所有内容为 "?..."，实现参数脱敏
        Log.d(tag, LOG_PREFIX + " [WS_" + event + "] url=" + (wsUrl != null ? wsUrl.replaceAll("\\?.*", "?...") : "-")
                + " " + (detail != null ? detail : ""));
    }

    /**
     * 记录网络错误日志（ERROR 级别）。
     *
     * @param tag    日志标签
     * @param event  错误事件名称，如 "CONNECT_FAIL"、"SEND_ERROR" 等
     * @param detail 错误详情描述
     */
    public static void logError(String tag, String event, String detail) {
        if (!DEBUG_NETWORK) return;
        Log.e(tag, LOG_PREFIX + " [ERR_" + event + "] " + (detail != null ? detail : ""));
    }
}
