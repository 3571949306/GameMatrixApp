package com.gamecenter.app.chinesechess;

import java.util.HashMap;
import java.util.Map;

/**
 * 局域网双机对战行协议编解码（纯 Java，可脱离 Android 运行时单测）。
 *
 * <p>线格式：每行一条扁平 JSON 对象，UTF-8 编码，行尾以 '\n' 分隔。
 * 字段值仅允许字符串与整数（无嵌套对象/数组/浮点/布尔），本类内置极简解析器，
 * 不依赖 org.json——动态模块的纯 Java 回归测试环境（javac 直接编译）没有 Android 运行时。</p>
 *
 * <p>消息类型（type 字段）：
 * <ul>
 *   <li>HELLO{name?}        —— GUEST 握手请求，name 为可选的玩家展示名；</li>
 *   <li>WELCOME{color, hostName} —— HOST 回复，color 为分配给 GUEST 的阵营；</li>
 *   <li>MOVE{fromX,fromY,toX,toY,ply} —— 走子，ply 为双方共识的着法序号（从 1 递增）；</li>
 *   <li>RESIGN / LEAVE / PING / PONG —— 认输 / 离开 / 保活，无附加字段。</li>
 * </ul></p>
 *
 * <p>容错契约：decode 对非 JSON、未知类型、字段缺失/类型不符一律返回 null，
 * 绝不抛出异常（调用方运行在 Socket 读线程，异常会炸线程）。</p>
 *
 * <p>线程约定：本类无共享可变状态，全部方法线程安全。</p>
 */
public final class LanChessProtocol {

    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_WELCOME = "WELCOME";
    public static final String TYPE_MOVE = "MOVE";
    public static final String TYPE_RESIGN = "RESIGN";
    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_PONG = "PONG";

    /** 阵营编码（与 ChineseChessGame.Side 对应，协议层只认字符串）。 */
    public static final String COLOR_RED = "RED";
    public static final String COLOR_BLACK = "BLACK";

    private LanChessProtocol() {
    }

    /** 已解码的协议消息：类型 + 扁平字段集（值为 String 或 Long）。 */
    public static final class Message {
        private final String type;
        private final Map<String, Object> fields;

        private Message(String type, Map<String, Object> fields) {
            this.type = type;
            this.fields = fields;
        }

        public String type() {
            return type;
        }

        public boolean has(String key) {
            return fields.containsKey(key);
        }

        /** 读取字符串字段；不存在或类型不符时返回 fallback。 */
        public String getString(String key, String fallback) {
            Object value = fields.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        /** 读取整数字段；不存在、类型不符或超出 int 范围时返回 fallback。 */
        public int getInt(String key, int fallback) {
            Object value = fields.get(key);
            if (value instanceof Long) {
                long v = (Long) value;
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) return fallback;
                return (int) v;
            }
            return fallback;
        }
    }

    // ==================== 编码 ====================

    /** HELLO：GUEST 握手请求。name 为可选展示名（null 则省略字段）。 */
    public static String encodeHello(String name) {
        if (name == null) {
            return "{\"type\":\"" + TYPE_HELLO + "\"}";
        }
        return "{\"type\":\"" + TYPE_HELLO + "\",\"name\":" + encodeString(name) + "}";
    }

    /** WELCOME：HOST 分配给 GUEST 的阵营 color（RED/BLACK）+ 主机展示名。 */
    public static String encodeWelcome(String color, String hostName) {
        return "{\"type\":\"" + TYPE_WELCOME + "\",\"color\":" + encodeString(color)
                + ",\"hostName\":" + encodeString(hostName == null ? "" : hostName) + "}";
    }

    /** MOVE：走子（坐标为游戏层 [x, y] = [col, row] 契约，ply 从 1 递增）。 */
    public static String encodeMove(int fromX, int fromY, int toX, int toY, int ply) {
        return "{\"type\":\"" + TYPE_MOVE + "\",\"fromX\":" + fromX
                + ",\"fromY\":" + fromY
                + ",\"toX\":" + toX
                + ",\"toY\":" + toY
                + ",\"ply\":" + ply + "}";
    }

    public static String encodeResign() {
        return "{\"type\":\"" + TYPE_RESIGN + "\"}";
    }

    public static String encodeLeave() {
        return "{\"type\":\"" + TYPE_LEAVE + "\"}";
    }

    public static String encodePing() {
        return "{\"type\":\"" + TYPE_PING + "\"}";
    }

    public static String encodePong() {
        return "{\"type\":\"" + TYPE_PONG + "\"}";
    }

    /** JSON 字符串字面量：转义引号、反斜杠与控制字符。 */
    static String encodeString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ==================== 解码 ====================

    /**
     * 解码一行协议消息。
     *
     * @param line 一行原始文本（允许带尾随换行/空白）
     * @return 结构合法且类型已知的消息；非 JSON、未知类型、必填字段缺失或类型不符返回 null（不抛异常）
     */
    public static Message decode(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;
        Parser p = new Parser(s);
        Map<String, Object> fields = p.parseObject();
        if (fields == null) return null;
        p.skipWhitespace();
        if (!p.atEnd()) return null;
        Object typeValue = fields.get("type");
        if (!(typeValue instanceof String)) return null;
        String type = (String) typeValue;
        if (!isShapeValid(type, fields)) return null;
        return new Message(type, fields);
    }

    /** 按消息类型校验必填字段的存在与 JSON 类型。 */
    private static boolean isShapeValid(String type, Map<String, Object> fields) {
        switch (type) {
            case TYPE_HELLO:
                return true; // name 可选
            case TYPE_WELCOME:
                Object color = fields.get("color");
                Object hostName = fields.get("hostName");
                return (COLOR_RED.equals(color) || COLOR_BLACK.equals(color))
                        && hostName instanceof String;
            case TYPE_MOVE:
                return isInt(fields.get("fromX")) && isInt(fields.get("fromY"))
                        && isInt(fields.get("toX")) && isInt(fields.get("toY"))
                        && isInt(fields.get("ply"));
            case TYPE_RESIGN:
            case TYPE_LEAVE:
            case TYPE_PING:
            case TYPE_PONG:
                return true; // 无附加字段
            default:
                return false; // 未知类型
        }
    }

    private static boolean isInt(Object value) {
        return value instanceof Long;
    }

    /**
     * 极简扁平 JSON 解析器：{ "key": "string" | -?digits, ... }。
     * 失败语义：任何畸形输入返回 null（失败哨兵），不抛异常、不污染调用方。
     */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
                i++;
            }
        }

        private boolean expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) return false;
            i++;
            return true;
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        Map<String, Object> parseObject() {
            if (!expect('{')) return null;
            Map<String, Object> result = new HashMap<>();
            skipWhitespace();
            char c = peekOr('\0');
            if (c == '}') {
                i++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                if (key == null) return null;
                if (!expect(':')) return null;
                Object value = parseValue();
                if (value == null || result.containsKey(key)) return null; // 值失败/重复键=畸形
                result.put(key, value);
                skipWhitespace();
                char d = peekOr('\0');
                if (d == ',') {
                    i++;
                    continue;
                }
                if (d == '}') {
                    i++;
                    return result;
                }
                return null;
            }
        }

        /** 越界时返回 fallback 不推进（调用方随后的 expect 会失败收口）。 */
        private char peekOr(char fallback) {
            return i < s.length() ? s.charAt(i) : fallback;
        }

        /** 只接受字符串或整数两类值，其余 JSON 类型一律判为畸形。 */
        private Object parseValue() {
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '"') return parseString();
            if (c == '-' || isDigit(c)) return parseNumber();
            return null;
        }

        private Long parseNumber() {
            int start = i;
            boolean negative = false;
            if (i < s.length() && s.charAt(i) == '-') {
                negative = true;
                i++;
            }
            if (i >= s.length() || !isDigit(s.charAt(i))) return null;
            long value = 0;
            while (i < s.length() && isDigit(s.charAt(i))) {
                if (value > (Long.MAX_VALUE - 9) / 10) {
                    // 提前防溢出：协议坐标/序号远小于该量级，超大量级视为畸形。
                    return null;
                }
                value = value * 10 + (s.charAt(i) - '0');
                i++;
            }
            if (negative) value = -value;
            if (i < s.length()) {
                char next = s.charAt(i);
                if (next == '.' || next == 'e' || next == 'E') {
                    // 拒绝浮点/科学计数法
                    return null;
                }
            }
            if (s.charAt(start) == '0' && (i - start > (negative ? 2 : 1))) {
                return null; // 前导零（"007"）不符合 JSON 数字文法
            }
            return value;
        }

        private String parseString() {
            if (!expect('"')) return null;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) return null; // 未闭合字符串
                char c = s.charAt(i);
                i++;
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) return null;
                    char e = s.charAt(i);
                    i++;
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) return null;
                            int code = 0;
                            for (int k = 0; k < 4; k++) {
                                int d = Character.digit(s.charAt(i + k), 16);
                                if (d < 0) return null;
                                code = code * 16 + d;
                            }
                            sb.append((char) code);
                            i += 4;
                            break;
                        default:
                            return null; // 非法转义
                    }
                } else if (c < 0x20) {
                    return null; // 字符串内裸控制字符不符合 JSON 文法
                } else {
                    sb.append(c);
                }
            }
        }
    }
}
