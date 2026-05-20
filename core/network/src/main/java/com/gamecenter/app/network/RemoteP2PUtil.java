package com.gamecenter.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.util.regex.Pattern;

/**
 * 远程 P2P 连接工具类 — 提供房间码管理、端点解析、邀请链接生成、令牌安全存储等静态工具方法。
 * <p>
 * 职责：
 * <ul>
 *   <li>房间码的规范化、校验、生成和从文本中提取</li>
 *   <li>P2P 连接端点（host:port）的格式化与解析，支持 IPv4/IPv6</li>
 *   <li>邀请信息的格式化输出（纯文本邀请和协议链接）</li>
 *   <li>WebSocket URL 的构建</li>
 *   <li>对等端令牌（peer token）的加密存储与读取，使用 EncryptedSharedPreferences</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>房间码字母表排除了容易混淆的字符（I/O/0/1），降低用户输入错误率</li>
 *   <li>令牌存储采用加密 SharedPreferences，降级时回退到明文存储并自动迁移</li>
 *   <li>所有方法均为静态方法，无状态依赖，便于在各处直接调用</li>
 *   <li>主线程 Handler 用于回调切换，确保 UI 操作在主线程执行</li>
 * </ul>
 */
public class RemoteP2PUtil {

    private static final String TAG = "RemoteP2PUtil";

    /**
     * 房间码可用字符集。
     * <p>
     * 排除了 I、O、0、1 等容易与数字/字母混淆的字符，减少用户手动输入时的错误率。
     */
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 房间码固定长度 */
    private static final int ROOM_CODE_LENGTH = 6;

    /** 自定义协议前缀，用于邀请链接中标识房间码 */
    private static final String PROTOCOL_PREFIX = "DDZ://";

    /** P2P 直连协议前缀 */
    private static final String P2P_PREFIX = "p2p://";

    /** 合法房间码的正则校验模式：恰好6个合法字符 */
    private static final Pattern VALID_CODE = Pattern.compile("^[" + ROOM_CODE_ALPHABET + "]{6}$");

    /** 加密 SharedPreferences 文件名前缀，用于区分加密和明文存储 */
    private static final String ENCRYPTED_PREFS_PREFIX = "enc_";

    /** 主线程 Handler，用于将回调投递到主线程 */
    /**
     * 规范化房间码输入。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>去除首尾空白并转为大写</li>
     *   <li>移除 "DDZ://" 协议前缀（如果存在）</li>
     *   <li>过滤掉不在合法字符集中的字符</li>
     *   <li>截断超过6位的部分</li>
     * </ol>
     *
     * @param code 原始房间码输入（可能包含前缀、空格、非法字符等）
     * @return 规范化后的房间码；若输入为 null 则返回空字符串
     */
    public static String normalizeRoomCode(String code) {
        if (code == null) return "";
        String trimmed = code.trim().toUpperCase();
        // 移除协议前缀
        if (trimmed.startsWith("DDZ://")) {
            trimmed = trimmed.substring(6);
        }
        // 仅保留合法字符集中的字符
        StringBuilder sb = new StringBuilder();
        for (char c : trimmed.toCharArray()) {
            if (ROOM_CODE_ALPHABET.indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        // 截断超过6位的部分
        return sb.length() > 6 ? sb.substring(0, 6) : sb.toString();
    }

    /**
     * 校验房间码是否合法。
     * <p>
     * 合法条件：非空且恰好由6个合法字符组成。
     *
     * @param code 待校验的房间码
     * @return 合法返回 true，否则返回 false
     */
    public static boolean isValidRoomCode(String code) {
        if (code == null || code.isEmpty()) return false;
        return VALID_CODE.matcher(code).matches();
    }

    /**
     * 格式化中转服务器邀请文本。
     * <p>
     * 生成人类可读的邀请信息，包含房间码和加入方式说明，
     * 适合通过社交软件分享给对手。
     *
     * @param roomCode 房间码（会先进行规范化处理）
     * @return 格式化后的邀请文本；若房间码无效则返回空字符串
     */
    public static String formatRelayInvite(String roomCode) {
        String code = normalizeRoomCode(roomCode);
        if (!isValidRoomCode(code)) return "";
        return "斗地主云房间 " + code + "\n加入方式：斗地主 → 云联机 → 输入房间码 " + code;
    }

    /**
     * 从文本中提取房间码。
     * <p>
     * 提取策略（按优先级）：
     * <ol>
     *   <li>查找 "DDZ://" 协议前缀，提取其后的房间码</li>
     *   <li>扫描文本中连续的合法字符序列，找到第一个恰好6位的序列</li>
     *   <li>将整段文本规范化后尝试匹配</li>
     * </ol>
     * <p>
     * 此方法适用于从剪贴板、分享链接等非结构化文本中提取房间码。
     *
     * @param text 可能包含房间码的文本
     * @return 提取到的房间码；若未找到则返回空字符串
     */
    public static String findRoomCode(String text) {
        if (text == null) return "";
        String upper = text.trim().toUpperCase(java.util.Locale.US);
        // 优先查找协议前缀后的房间码
        int prefixIndex = upper.indexOf(PROTOCOL_PREFIX);
        if (prefixIndex >= 0) {
            String code = normalizeRoomCode(upper.substring(prefixIndex + PROTOCOL_PREFIX.length()));
            if (isValidRoomCode(code)) return code;
        }
        // 扫描连续合法字符序列，寻找恰好6位的房间码
        StringBuilder token = new StringBuilder();
        for (int i = 0; i <= upper.length(); i++) {
            // 末尾追加空格以确保最后一个 token 被处理
            char c = i < upper.length() ? upper.charAt(i) : ' ';
            boolean codeChar = ROOM_CODE_ALPHABET.indexOf(c) >= 0;
            if (codeChar) {
                token.append(c);
                if (token.length() == ROOM_CODE_LENGTH) {
                    return token.toString();
                }
            } else {
                // 遇到非法字符则重置，重新开始计数
                token.setLength(0);
            }
        }
        // 最后尝试将整段文本规范化后匹配
        String compact = normalizeRoomCode(upper);
        return isValidRoomCode(compact) ? compact : "";
    }

    /**
     * 格式化 P2P 直连邀请地址。
     * <p>
     * 生成 {@code p2p://host:port} 格式的地址字符串。
     * 对于 IPv6 地址，会自动添加方括号包裹（如 {@code p2p://[::1]:8080}）。
     *
     * @param host 主机地址（IPv4、IPv6 或域名）
     * @param port 端口号
     * @return 格式化后的 P2P 地址；若参数无效则返回空字符串
     */
    public static String formatInviteAddress(String host, int port) {
        if (host == null) return "";
        String value = host.trim();
        if (value.isEmpty() || !isValidPort(port) || !isPotentialRemoteHost(value)) {
            return "";
        }
        // IPv6 地址需要用方括号包裹，避免与端口分隔符冒号混淆
        if (value.contains(":") && !value.startsWith("[") && !value.endsWith("]")) {
            value = "[" + value + "]";
        }
        return P2P_PREFIX + value + ":" + port;
    }

    /**
     * 解析 P2P 端点地址。
     * <p>
     * 支持的格式：
     * <ul>
     *   <li>{@code p2p://host:port} — 带 P2P 协议前缀</li>
     *   <li>{@code host:port} — 不带前缀</li>
     *   <li>{@code [ipv6]:port} — IPv6 地址加端口</li>
     *   <li>{@code host} — 仅主机地址，使用 fallbackPort</li>
     * </ul>
     *
     * @param rawHost      原始主机地址字符串（可能包含 p2p:// 前缀和端口）
     * @param fallbackPort 当字符串中未指定端口时使用的默认端口
     * @return 解析成功的 {@link Endpoint} 对象；若解析失败则返回 null
     */
    public static Endpoint parseEndpoint(String rawHost, int fallbackPort) {
        if (rawHost == null) return null;
        String value = rawHost.trim();
        // 移除 P2P 协议前缀
        if (value.startsWith(P2P_PREFIX)) {
            value = value.substring(P2P_PREFIX.length());
        }
        if (value.isEmpty()) return null;
        String host = value;
        int port = fallbackPort;
        // 处理 IPv6 地址格式 [host]:port
        if (value.startsWith("[") && value.contains("]")) {
            int end = value.indexOf(']');
            host = value.substring(1, end);
            // 检查方括号后是否有端口（格式为 ]:port）
            if (value.length() > end + 2 && value.charAt(end + 1) == ':') {
                port = parsePort(value.substring(end + 2), fallbackPort);
            }
        } else {
            // 处理 IPv4/域名格式 host:port
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            // 仅当只有一个冒号时才视为端口分隔符（多个冒号说明是 IPv6 地址但未加方括号）
            if (firstColon > 0 && firstColon == lastColon) {
                host = value.substring(0, lastColon);
                port = parsePort(value.substring(lastColon + 1), fallbackPort);
            }
        }
        host = host.trim();
        // 校验解析结果的有效性
        if (host.isEmpty() || !isValidPort(port) || !isPotentialRemoteHost(host)) {
            return null;
        }
        return new Endpoint(host, port);
    }

    /**
     * 校验端口号是否在有效范围内（1-65535）。
     *
     * @param port 待校验的端口号
     * @return 有效返回 true，否则返回 false
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * 安全地解析端口号字符串。
     * <p>
     * 若解析失败或端口号不在有效范围内，则返回回退端口值。
     *
     * @param text         端口号字符串
     * @param fallbackPort 解析失败时的回退端口
     * @return 解析成功的端口号或回退端口
     */
    private static int parsePort(String text, int fallbackPort) {
        try {
            int port = Integer.parseInt(text.trim());
            return isValidPort(port) ? port : fallbackPort;
        } catch (Exception e) {
            return fallbackPort;
        }
    }

    /**
     * 判断主机地址是否可能是远程主机。
     * <p>
     * 判定逻辑：
     * <ul>
     *   <li>合法的 IPv4 地址（排除 127.x.x.x 回环地址）</li>
     *   <li>包含冒号的地址（IPv6 地址特征，长度至少3个字符）</li>
     *   <li>符合域名格式的字符串（字母数字开头和结尾，中间可含点和连字符）</li>
     * </ul>
     *
     * @param host 主机地址字符串
     * @return 可能是远程主机返回 true，否则返回 false
     */
    private static boolean isPotentialRemoteHost(String host) {
        if (isValidIPv4(host)) return true;
        if (isIPv4Literal(host)) return false;
        // 包含冒号且长度>=3，可能是 IPv6 地址
        if (host.contains(":") && host.length() >= 3) return true;
        // 域名格式校验：字母数字开头结尾，中间可含点和连字符
        return host.matches("(?i)^[a-z0-9][a-z0-9.-]{0,252}[a-z0-9]$");
    }

    private static boolean isIPv4Literal(String host) {
        return host != null && host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    }

    /**
     * 校验是否为合法的 IPv4 地址。
     * <p>
     * 合法条件：4段以点分隔的数字，每段 0-255，且排除 127.x.x.x 回环地址。
     *
     * @param ip 待校验的 IP 地址字符串
     * @return 合法且非回环地址返回 true，否则返回 false
     */
    private static boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
            // 排除回环地址，回环地址不适用于远程 P2P 连接
            return !ip.startsWith("127.");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 验证房间码并通过回调返回结果。
     * <p>
     * 先对房间码进行规范化处理，再校验其合法性。
     * 回调通过主线程 Handler 投递，确保在 UI 线程执行。
     *
     * @param context  上下文
     * @param code     待验证的房间码
     * @param callback 验证结果回调；合法时 errorMessage 为 null，非法时包含错误提示
     */
    public static void verifyRoomCode(Context context, String code, RoomCodeCallback callback) {
        String normalized = normalizeRoomCode(code);
        if (!isValidRoomCode(normalized)) {
            if (callback != null) postMain(() -> callback.onResult(false, "请输入6位房间码"));
            return;
        }
        if (callback != null) postMain(() -> callback.onResult(true, null));
    }

    /**
     * 获取加密的 SharedPreferences 实例。
     * <p>
     * 使用 AndroidX Security Crypto 库的 EncryptedSharedPreferences 对敏感数据进行加密存储。
     * 若加密存储不可用（如设备不支持、密钥损坏等），则降级到普通 SharedPreferences，
     * 并记录警告日志。
     *
     * @param context   上下文
     * @param prefsName 偏好设置文件名
     * @return 加密的 SharedPreferences 实例；降级时返回普通 SharedPreferences
     */
    private static SharedPreferences getEncryptedPrefs(Context context, String prefsName) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    ENCRYPTED_PREFS_PREFIX + prefsName,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // 加密存储不可用时降级到明文存储，保证功能可用性
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e);
            return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        }
    }

    /**
     * 安全保存对等端令牌（peer token）。
     * <p>
     * 将令牌保存到加密 SharedPreferences 中，同时记录保存时间戳。
     * 如果发现明文 SharedPreferences 中存在旧令牌，会清除明文数据，
     * 实现从明文到加密存储的自动迁移。
     *
     * @param context   上下文
     * @param prefsName 偏好设置文件名
     * @param token     对等端令牌
     */
    public static void savePeerToken(Context context, String prefsName, String token) {
        if (context == null || token == null) return;
        try {
            SharedPreferences encPrefs = getEncryptedPrefs(context, prefsName);
            SharedPreferences.Editor editor = encPrefs.edit();
            editor.putString("last_peer_token", token);
            editor.putString("peer_token_" + token, token);
            // 记录令牌保存时间，用于后续过期判断
            editor.putLong("peer_token_time", System.currentTimeMillis());
            editor.apply();

            // 清除明文存储中的旧令牌，实现向加密存储的迁移
            SharedPreferences plainPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            if (plainPrefs.contains("last_peer_token")) {
                plainPrefs.edit().clear().apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save peer token", e);
        }
    }

    /**
     * 获取上次保存的对等端令牌。
     * <p>
     * 优先从加密 SharedPreferences 读取令牌。若加密存储中无令牌，
     * 则尝试从明文 SharedPreferences 读取（兼容旧版本数据），
     * 并在读取成功后自动迁移到加密存储。
     *
     * @param context   上下文
     * @param prefsName 偏好设置文件名
     * @return 上次保存的令牌；若不存在或读取失败则返回 null
     */
    public static String getLastPeerToken(Context context, String prefsName) {
        if (context == null) return null;
        try {
            // 优先从加密存储读取
            SharedPreferences encPrefs = getEncryptedPrefs(context, prefsName);
            String token = encPrefs.getString("last_peer_token", null);
            if (token != null && !token.isEmpty()) return token;

            // 降级：从明文存储读取旧数据，并迁移到加密存储
            SharedPreferences plainPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            token = plainPrefs.getString("last_peer_token", null);
            if (token != null && !token.isEmpty()) {
                savePeerToken(context, prefsName, token);
                return token;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read peer token", e);
            return null;
        }
    }

    /**
     * 清除对等端令牌。
     * <p>
     * 同时清除加密存储和明文存储中的令牌数据，确保令牌被彻底移除。
     *
     * @param context   上下文
     * @param prefsName 偏好设置文件名
     */
    public static void clearPeerToken(Context context, String prefsName) {
        if (context == null) return;
        try {
            getEncryptedPrefs(context, prefsName).edit().remove("last_peer_token").apply();
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().remove("last_peer_token").apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear peer token", e);
        }
    }

    /**
     * 构建 WebSocket 连接 URL（不含玩家名称）。
     * <p>
     * 格式：{@code wss://server/ws?game=xxx&room=xxx&token=xxx}
     * 若服务器地址未指定协议前缀，默认使用 wss://（安全 WebSocket）。
     *
     * @param wsServer      WebSocket 服务器地址
     * @param gameProtocol  游戏协议标识（如 "ddz"）
     * @param roomCode      房间码
     * @param peerToken     对等端令牌
     * @return 构建好的 WebSocket URL
     */
    public static String buildWebSocketUrl(String wsServer, String gameProtocol, String roomCode, String peerToken) {
        // 默认使用安全 WebSocket 协议
        if (!wsServer.startsWith("ws://") && !wsServer.startsWith("wss://")) {
            wsServer = "wss://" + wsServer;
        }
        StringBuilder url = new StringBuilder(wsServer);
        if (!wsServer.endsWith("/")) url.append("/");
        url.append("ws?");
        if (gameProtocol != null && !gameProtocol.isEmpty()) url.append("game=").append(gameProtocol).append("&");
        if (roomCode != null && !roomCode.isEmpty()) url.append("room=").append(roomCode).append("&");
        if (peerToken != null && !peerToken.isEmpty()) url.append("token=").append(peerToken);
        return url.toString();
    }

    /**
     * 构建 WebSocket 连接 URL（含玩家名称）。
     * <p>
     * 在基础 URL 上追加 {@code name=playerName} 参数。
     *
     * @param wsServer      WebSocket 服务器地址
     * @param gameProtocol  游戏协议标识
     * @param roomCode      房间码
     * @param peerToken     对等端令牌
     * @param playerName    玩家名称
     * @return 构建好的 WebSocket URL
     */
    public static String buildWebSocketUrl(String wsServer, String gameProtocol, String roomCode, String peerToken, String playerName) {
        String base = buildWebSocketUrl(wsServer, gameProtocol, roomCode, peerToken);
        if (playerName != null && !playerName.isEmpty()) {
            // 确保 name 参数前有正确的连接符
            if (!base.endsWith("&")) base += "&";
            base += "name=" + playerName;
        }
        return base;
    }

    /**
     * 在主线程显示 Toast 提示。
     * <p>
     * 通过主线程 Handler 确保 Toast 在 UI 线程创建和显示，
     * 避免在非主线程调用 Toast 导致崩溃。
     *
     * @param context 上下文
     * @param message 提示消息
     */
    public static void showToast(Context context, String message) {
        if (context == null || message == null) return;
        postMain(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private static void postMain(Runnable runnable) {
        if (runnable == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(runnable);
        } catch (RuntimeException e) {
            runnable.run();
        }
    }

    /**
     * 房间码验证回调接口。
     */
    public interface RoomCodeCallback {
        /**
         * 验证结果回调。
         *
         * @param valid        房间码是否合法
         * @param errorMessage 错误提示信息；合法时为 null
         */
        void onResult(boolean valid, String errorMessage);
    }

    /**
     * P2P 端点数据类，封装主机地址和端口号。
     * <p>
     * 使用 final 字段确保不可变，解析完成后不应被修改。
     */
    public static final class Endpoint {
        /** 主机地址（IPv4、IPv6 或域名） */
        public final String host;
        /** 端口号 */
        public final int port;

        /**
         * 构造端点实例。
         *
         * @param host 主机地址
         * @param port 端口号
         */
        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
