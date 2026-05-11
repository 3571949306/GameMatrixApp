package com.gamecenter.app.games.doudizhu.network;

/**
 * 类名：RemoteP2PUtil
 * 职责：远程联机辅助工具类
 *        1. 房间码格式化与规范化（6位字母数字）
 *        2. 从剪贴板文本中提取房间码
 *        3. P2P 邀请地址格式化与解析（p2p://host:port）
 *        4. IP/端口合法性验证
 * 关联类：
 *   - 被 DouDiZhuOnlineActivity 调用：formatRelayInvite()、normalizeRoomCode()、findRoomCode()、formatInviteAddress()、parseEndpoint()
 *   - 被 GameSocketClient 调用：normalizeRoomCode()
 * 生命周期：无状态工具类，所有方法均为 static，无需实例化
 * 注意事项：
 *   - 房间码规范：6位大写字母+数字，normalizeRoomCode() 会自动过滤非法字符并转大写
 *   - P2P 地址格式：p2p://host:port，支持 IPv4 和 IPv6
 *   - 127.x.x.x 本地地址被 isValidIPv4() 排除，防止误连本机
 */
public final class RemoteP2PUtil {

    /**
     * 房间码标准长度（6位字母数字）
     */
    private static final int ROOM_CODE_LENGTH = 6;

    /**
     * 斗地主自定义协议前缀（用于分享链接）
     */
    private static final String PROTOCOL_PREFIX = "DDZ://";

    /**
     * P2P 地址协议前缀
     */
    private static final String P2P_PREFIX = "p2p://";

    private RemoteP2PUtil() {
        // 工具类禁止实例化
    }

    /**
     * 方法作用：生成云联机房间邀请文本（用于复制到剪贴板分享）
     * @param roomCode 原始房间码（任意格式，会自动规范化）
     * @return 格式化后的邀请文本，包含房间码和加入方式说明；如果房间码无效返回空字符串
     * 调用时机：DouDiZhuOnlineActivity 中房主点击"复制邀请"按钮时调用
     * 副作用：无
     */
    public static String formatRelayInvite(String roomCode) {
        String code = normalizeRoomCode(roomCode);
        if (code.isEmpty()) return "";
        return "斗地主云房间 " + code + "\n加入方式：斗地主 → 云联机 → 输入房间码 " + code;
    }

    /**
     * 方法作用：规范化房间码（过滤非法字符、转大写、截取6位）
     * @param raw 原始输入（可能包含 DDZ:// 前缀、小写字母、特殊符号等）
     * @return 6位大写字母数字房间码；如果不足6位有效字符返回空字符串
     * 调用时机：
     *   - 用户输入房间码后调用（DouDiZhuOnlineActivity）
     *   - 从剪贴板/分享文本提取后调用（findRoomCode()）
     *   - 生成邀请文本前调用（formatRelayInvite()）
     * 副作用：无
     * 注意事项：
     *   - 自动去掉 DDZ:// 协议前缀
     *   - 只保留 A-Z 和 0-9，其他字符全部过滤
     *   - 截取前6个有效字符，不足6位返回空字符串
     */
    public static String normalizeRoomCode(String raw) {
        if (raw == null) return "";
        String upper = raw.trim().toUpperCase(java.util.Locale.US);
        if (upper.startsWith(PROTOCOL_PREFIX)) {
            upper = upper.substring(PROTOCOL_PREFIX.length());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                if (sb.length() == ROOM_CODE_LENGTH) {
                    break;
                }
            }
        }
        return sb.length() == ROOM_CODE_LENGTH ? sb.toString() : "";
    }

    /**
     * 方法作用：从任意文本中提取房间码（支持多种格式）
     * @param text 输入文本（如剪贴板内容、分享消息等）
     * @return 提取到的6位房间码；未找到返回空字符串
     * 调用时机：DouDiZhuOnlineActivity 中检测剪贴板内容、解析分享消息时调用
     * 副作用：无
     * 注意事项：
     *   - 支持 DDZ://ABC123 协议链接格式
     *   - 支持纯6位连续字母数字（如 ABC123）
     *   - 支持嵌入在文本中的6位token（如"房间码是 ABC123 快来加入"）
     *   - 优先匹配 DDZ:// 前缀，其次匹配连续6位，最后匹配文本中的6位token
     */
    public static String findRoomCode(String text) {
        if (text == null) return "";
        String upper = text.trim().toUpperCase(java.util.Locale.US);
        if (upper.startsWith(PROTOCOL_PREFIX)) {
            return normalizeRoomCode(upper);
        }

        // 策略1：整个文本去杂后刚好6位
        StringBuilder compact = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                compact.append(c);
            }
        }
        if (compact.length() == ROOM_CODE_LENGTH) {
            return compact.toString();
        }

        // 策略2：从文本中找连续的6位token
        StringBuilder token = new StringBuilder();
        for (int i = 0; i <= upper.length(); i++) {
            char c = i < upper.length() ? upper.charAt(i) : ' ';
            boolean codeChar = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (codeChar) {
                token.append(c);
            } else {
                if (token.length() == ROOM_CODE_LENGTH) {
                    return token.toString();
                }
                token.setLength(0);
            }
        }
        return "";
    }

    /**
     * 方法作用：格式化 P2P 邀请地址（用于局域网直连分享）
     * @param host 主机地址（IPv4、IPv6 或域名）
     * @param port 端口号
     * @return 格式化后的 p2p:// 地址；参数无效返回空字符串
     * 调用时机：DouDiZhuOnlineActivity 中生成局域网邀请地址时调用
     * 副作用：无
     * 注意事项：
     *   - IPv6 地址会自动加上方括号（如 [2001:db8::1]）
     *   - 会过滤掉本地地址（127.x.x.x）和无效端口
     */
    public static String formatInviteAddress(String host, int port) {
        if (host == null) return "";
        String value = host.trim();
        if (value.isEmpty() || !isValidPort(port) || !isPotentialRemoteHost(value)) {
            return "";
        }
        if (value.contains(":") && !value.startsWith("[") && !value.endsWith("]")) {
            value = "[" + value + "]";
        }
        return P2P_PREFIX + value + ":" + port;
    }

    /**
     * 方法作用：解析 P2P 地址字符串为 Endpoint 对象
     * @param rawHost    原始地址字符串（如 "p2p://192.168.1.100:8765" 或 "192.168.1.100:8765"）
     * @param fallbackPort 默认端口（当地址中未指定端口时使用）
     * @return 解析后的 Endpoint 对象；解析失败返回 null
     * 调用时机：DouDiZhuOnlineActivity 中解析用户输入的连接地址时调用
     * 副作用：无
     * 注意事项：
     *   - 支持 p2p:// 前缀，也支持裸地址
     *   - 支持 IPv4（如 192.168.1.100:8765）
     *   - 支持 IPv6（如 [2001:db8::1]:8765）
     *   - 会验证端口范围和主机合法性
     */
    public static Endpoint parseEndpoint(String rawHost, int fallbackPort) {
        if (rawHost == null) return null;
        String value = rawHost.trim();
        if (value.startsWith(P2P_PREFIX)) {
            value = value.substring(P2P_PREFIX.length());
        }
        if (value.isEmpty()) return null;

        String host = value;
        int port = fallbackPort;

        // IPv6 格式：[addr]:port
        if (value.startsWith("[") && value.contains("]")) {
            int end = value.indexOf(']');
            host = value.substring(1, end);
            if (value.length() > end + 2 && value.charAt(end + 1) == ':') {
                port = parsePort(value.substring(end + 2), fallbackPort);
            }
        } else {
            // IPv4 格式：addr:port（只有一个冒号）
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon > 0 && firstColon == lastColon) {
                host = value.substring(0, lastColon);
                port = parsePort(value.substring(lastColon + 1), fallbackPort);
            }
        }

        host = host.trim();
        if (host.isEmpty() || !isValidPort(port) || !isPotentialRemoteHost(host)) {
            return null;
        }
        return new Endpoint(host, port);
    }

    /**
     * 方法作用：验证端口号是否在有效范围内
     * @param port 端口号
     * @return true 表示 1-65535 范围内的有效端口
     * 调用时机：被 formatInviteAddress()、parseEndpoint() 内部调用
     * 副作用：无
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * 方法作用：解析端口字符串，失败时返回默认值
     * @param text         端口字符串
     * @param fallbackPort 解析失败时的回退端口
     * @return 解析后的端口号；无效时返回 fallbackPort
     * 调用时机：被 parseEndpoint() 内部调用
     * 副作用：无
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
     * 方法作用：判断主机地址是否可能是远程主机（排除本地地址）
     * @param host 主机地址字符串
     * @return true 表示可能是有效的远程主机地址
     * 调用时机：被 formatInviteAddress()、parseEndpoint() 内部调用
     * 副作用：无
     * 注意事项：
     *   - 接受有效的 IPv4 地址（排除 127.x.x.x）
     *   - 接受 IPv6 地址（包含冒号且长度>=3）
     *   - 接受符合 RFC 1123 的域名格式
     */
    private static boolean isPotentialRemoteHost(String host) {
        if (isValidIPv4(host)) return true;
        if (host.contains(":") && host.length() >= 3) return true;
        return host.matches("(?i)^[a-z0-9][a-z0-9.-]{0,252}[a-z0-9]$");
    }

    /**
     * 方法作用：验证 IPv4 地址格式并排除本地回环地址
     * @param ip IPv4 地址字符串
     * @return true 表示格式正确且不是 127.x.x.x 本地地址
     * 调用时机：被 isPotentialRemoteHost() 内部调用
     * 副作用：无
     * 注意事项：
     *   - 严格检查四段式格式，每段 0-255
     *   - 排除 127.0.0.0/8 本地回环地址，防止误连本机
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
            return !ip.startsWith("127.");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 类名：Endpoint
     * 职责：封装解析后的 P2P 主机地址和端口
     * 关联类：被 RemoteP2PUtil.parseEndpoint() 创建，被 DouDiZhuOnlineActivity 使用
     * 生命周期：作为值对象使用，创建后不可变
     */
    public static final class Endpoint {
        public final String host;
        public final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
