package com.gamecenter.app.brotato.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Brotato 内容（manifest / enemies / waves）的严格解析器。
 *
 * <p>纯 JVM、零 Android 依赖，自带一个极简 JSON reader（不支持注释、脚本、反射与类型强转），
 * 因此可以在普通单元测试里直接跑。所有失败路径统一抛
 * {@link IllegalStateException}（消息以 {@code "invalid brotato content: "} 开头），
 * 也就是说：宁可整包拒绝装载，也不带着半残内容进战场（fail-closed）。</p>
 *
 * <p>字段名与生产资产逐字对齐：enemies.json 用 {@code name_en}/{@code sizeDp}/
 * {@code contactDamage}/{@code behaviors}，waves.json 用 {@code totalWaves}/
 * {@code spawnCount}/{@code spawnIntervalMs}/{@code composition}[{@code kind},{@code weight}]/
 * {@code bossGroup}[{@code kind},{@code count},{@code intervalMs},{@code delayMs}]。
 * 少量同义拼写（snake_case）作为兼容别名接受，未知字段忽略但不报错。</p>
 */
public final class BrotatoContentParser {

    /** 当前支持的内容 schema。 */
    public static final int SCHEMA_VERSION = 1;
    /** 内容包必须声明的 gameId，防止串用别家内容。 */
    public static final String GAME_ID = "brotato";

    private static final String PREFIX = "invalid brotato content: ";

    /** manifest.json 的解析结果。 */
    public static final class Manifest {
        private final int contentVersion;
        private final String gameId;
        private final String enemiesFile;
        private final String wavesFile;

        Manifest(int contentVersion, String gameId, String enemiesFile, String wavesFile) {
            this.contentVersion = contentVersion;
            this.gameId = gameId;
            this.enemiesFile = enemiesFile;
            this.wavesFile = wavesFile;
        }

        /** 内容版本（决定成长/数值口径），必须 ≥ 1。 */
        public int contentVersion() {
            return contentVersion;
        }

        /** 归属游戏 id，恒为 {@link #GAME_ID}。 */
        public String gameId() {
            return gameId;
        }

        /** enemies 文件的相对路径声明（装载方按它去 assets 取文本）。 */
        public String enemiesFile() {
            return enemiesFile;
        }

        /** waves 文件的相对路径声明。 */
        public String wavesFile() {
            return wavesFile;
        }

        @Override
        public String toString() {
            return "Manifest(contentVersion=" + contentVersion + ",gameId=" + gameId
                    + ",enemies=" + enemiesFile + ",waves=" + wavesFile + ")";
        }
    }

    private BrotatoContentParser() {
    }

    // ==== 对外解析口 ==============================================================

    /** 解析 manifest：contentVersion、gameId 与 files 映射全部必需。 */
    public static Manifest parseManifest(String text) {
        Map<String, Object> root = objectValue(read(text, "manifest"), "manifest");
        requireSchema(root, "manifest");
        int contentVersion = requiredInt(root, "contentVersion", "content_version", 1,
                Integer.MAX_VALUE, "manifest");
        String gameId = requiredString(root, "gameId", "game_id", 1, 32, "manifest");
        if (!GAME_ID.equals(gameId.trim().toLowerCase(Locale.US))) {
            throw fail("manifest gameId must be \"" + GAME_ID + "\" but was \"" + gameId + "\"");
        }
        Map<String, Object> files = objectValue(requiredValue(root, "files", "manifest"), "manifest.files");
        String enemies = requiredString(files, "enemies", null, 1, 96, "manifest.files");
        String waves = requiredString(files, "waves", null, 1, 96, "manifest.files");
        return new Manifest(contentVersion, gameId.trim(), enemies.trim(), waves.trim());
    }

    /**
     * 解析 enemies.json 为 kind 表：key 为小写规范 id，value 为不可变定义。
     * id 重复、behavior 未知、数值越界都会 fail-closed。
     */
    public static Map<String, EnemyKind> parseEnemyKinds(String text) {
        Map<String, Object> root = objectValue(read(text, "enemies"), "enemies document");
        requireSchema(root, "enemies document");
        requireOwnGameId(root, "enemies document");
        List<Object> raw = requiredArray(root, "enemies", "enemies", 1, 64);
        Map<String, EnemyKind> result = new LinkedHashMap<>();
        for (Object item : raw) {
            Map<String, Object> value = objectValue(item, "enemy");
            String id = EnemyKind.normalizeId(requiredString(value, "id", null, 1, 32, "enemy"));
            if (result.containsKey(id)) throw fail("duplicate enemy kind \"" + id + "\"");
            String name = requiredString(value, "name", null, 1, 64, "enemy \"" + id + "\"");
            String nameEn = optionalString(value, "name_en", "nameEn");
            float hp = requiredFloat(value, "hp", null, 0.01f, 100000f, "enemy \"" + id + "\"");
            float speed = requiredFloat(value, "speed", null, 0.01f, 50f, "enemy \"" + id + "\"");
            float sizeDp = requiredFloat(value, "sizeDp", "size_dp", 0.5f, 200f, "enemy \"" + id + "\"");
            int color = requiredInt(value, "color", null, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    "enemy \"" + id + "\"");
            int contactDamage = requiredInt(value, "contactDamage", "contact_damage", 0, 100,
                    "enemy \"" + id + "\"");
            int score = requiredInt(value, "score", null, 1, 100000, "enemy \"" + id + "\"");
            Set<String> behaviors = behaviors(value);
            result.put(id, new EnemyKind(id, name, nameEn, hp, speed, sizeDp, color, contactDamage,
                    score, behaviors));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 解析 waves.json 为按 wave 升序、1..N 连续的波表。
     *
     * @param kinds 已解析的 kind 表；composition / bossGroup 引用未定义的 id 会 fail-closed
     */
    public static List<WaveLevel> parseWaveLevels(String text, Map<String, EnemyKind> kinds) {
        Map<String, Object> root = objectValue(read(text, "waves"), "waves document");
        requireSchema(root, "waves document");
        requireOwnGameId(root, "waves document");
        List<Object> rawLevels = requiredArray(root, "levels", "waves document", 1, 200);
        int declaredTotal = optionalInt(root, "totalWaves", "total_waves");
        List<WaveLevel> levels = new ArrayList<>();
        Set<Integer> seenWaves = new LinkedHashSet<>();
        for (Object item : rawLevels) {
            Map<String, Object> value = objectValue(item, "level");
            int wave = requiredInt(value, "wave", null, 1, 4096, "level");
            if (!seenWaves.add(wave)) throw fail("duplicate wave number " + wave);
            int spawnCount = requiredInt(value, "spawnCount", "spawn_count", 1, 500, "wave " + wave);
            int interval = requiredInt(value, "spawnIntervalMs", "spawn_interval_ms", 16, 20000,
                    "wave " + wave);
            List<WaveLevel.CompEntry> composition =
                    composition(requiredArray(value, "composition", "wave " + wave, 1, 16), kinds, wave);
            WaveLevel.BossGroup boss = value.containsKey("bossGroup") || value.containsKey("boss_group")
                    ? bossGroup(firstPresent(value, "bossGroup", "boss_group"), kinds, wave)
                    : null;
            levels.add(new WaveLevel(wave, spawnCount, interval, composition, boss));
        }
        Collections.sort(levels, (a, b) -> Integer.compare(a.wave(), b.wave()));
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).wave() != i + 1) {
                throw fail("wave sequence must be contiguous 1.." + levels.size()
                        + " but position " + (i + 1) + " declares wave " + levels.get(i).wave());
            }
        }
        if (declaredTotal > 0 && declaredTotal != levels.size()) {
            throw fail("declared totalWaves " + declaredTotal + " but levels array holds "
                    + levels.size());
        }
        return Collections.unmodifiableList(levels);
    }

    // ==== 低级 JSON 读取 ===========================================================

    /** 把一段 JSON 文本读成 Map / List / String / Double / Boolean / null 的对象图。 */
    public static Object readJson(String text) {
        return read(text, "json");
    }

    /** 顶层必须是对象的便捷口。 */
    public static Map<String, Object> readJsonObject(String text) {
        return objectValue(read(text, "json"), "json document");
    }

    /** 统一的 fail-closed 异常：{@link IllegalStateException} + 可读原因。 */
    public static IllegalStateException fail(String detail) {
        return new IllegalStateException(PREFIX + detail);
    }

    // ==== 字段取值（全部 fail-closed） =============================================

    private static List<WaveLevel.CompEntry> composition(List<Object> values,
                                                         Map<String, EnemyKind> kinds, int wave) {
        List<WaveLevel.CompEntry> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object item : values) {
            Map<String, Object> value = objectValue(item, "wave " + wave + " composition entry");
            String id = EnemyKind.normalizeId(requiredString(value, "kind", null, 1, 32,
                    "wave " + wave + " composition entry"));
            if (!seen.add(id)) throw fail("wave " + wave + " lists kind \"" + id + "\" twice");
            if (kinds != null && !kinds.containsKey(id)) {
                throw fail("wave " + wave + " references unknown enemy kind \"" + id + "\"");
            }
            int weight = requiredInt(value, "weight", null, 1, 1000,
                    "wave " + wave + " composition entry \"" + id + "\"");
            result.add(new WaveLevel.CompEntry(id, weight));
        }
        return result;
    }

    private static WaveLevel.BossGroup bossGroup(Object raw, Map<String, EnemyKind> kinds, int wave) {
        if (raw == null) return null;
        Map<String, Object> value = objectValue(raw, "wave " + wave + " bossGroup");
        String id = EnemyKind.normalizeId(requiredString(value, "kind", null, 1, 32,
                "wave " + wave + " bossGroup"));
        if (kinds != null && !kinds.containsKey(id)) {
            throw fail("wave " + wave + " bossGroup references unknown enemy kind \"" + id + "\"");
        }
        int count = requiredInt(value, "count", null, 1, 50, "wave " + wave + " bossGroup");
        int intervalMs = requiredInt(value, "intervalMs", "interval_ms", 16, 20000,
                "wave " + wave + " bossGroup");
        int delayMs = requiredInt(value, "delayMs", "delay_ms", 0, 600000,
                "wave " + wave + " bossGroup");
        return new WaveLevel.BossGroup(id, count, intervalMs, delayMs);
    }

    private static Set<String> behaviors(Map<String, Object> value) {
        Object raw = firstPresent(value, "behaviors", "behaviours");
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        if (!(raw instanceof List)) throw fail("behaviors must be an array");
        for (Object item : (List<?>) raw) {
            if (!(item instanceof String)) throw fail("behavior entries must be strings");
            result.add(EnemyKind.normalizeBehavior((String) item));
        }
        return result;
    }

    private static void requireSchema(Map<String, Object> object, String subject) {
        if (!object.containsKey("schema")) return; // 早期内容可省略 schema，缺省即视为当前版本
        int schema = requiredInt(object, "schema", null, 1, SCHEMA_VERSION, subject);
        if (schema != SCHEMA_VERSION) throw fail(subject + " schema " + schema + " is not supported");
    }

    private static void requireOwnGameId(Map<String, Object> object, String subject) {
        if (!object.containsKey("gameId") && !object.containsKey("game_id")) return;
        String gameId = requiredString(object, "gameId", "game_id", 1, 32, subject);
        if (!GAME_ID.equals(gameId.trim().toLowerCase(Locale.US))) {
            throw fail(subject + " belongs to gameId \"" + gameId + "\", expected \"" + GAME_ID + "\"");
        }
    }

    private static Object requiredValue(Map<String, Object> object, String key, String subject) {
        if (!object.containsKey(key) || object.get(key) == null) {
            throw fail(subject + " is missing required field \"" + key + "\"");
        }
        return object.get(key);
    }

    private static String requiredString(Map<String, Object> object, String key, String alias,
                                         int min, int max, String subject) {
        Object raw = firstPresent(object, key, alias);
        if (raw == null) throw fail(subject + " is missing required field \"" + key + "\"");
        if (!(raw instanceof String)) throw fail(subject + " field \"" + key + "\" must be a string");
        String value = ((String) raw).trim();
        if (value.length() < min || value.length() > max) {
            throw fail(subject + " field \"" + key + "\" length must be " + min + ".." + max
                    + " but was " + value.length());
        }
        return (String) raw;
    }

    private static String optionalString(Map<String, Object> object, String key, String alias) {
        Object raw = firstPresent(object, key, alias);
        if (raw == null) return null;
        if (!(raw instanceof String)) throw fail("field \"" + key + "\" must be a string");
        String value = (String) raw;
        if (value.trim().isEmpty()) throw fail("field \"" + key + "\" must not be blank");
        return value;
    }

    private static int requiredInt(Map<String, Object> object, String key, String alias, int min,
                                   int max, String subject) {
        Object raw = firstPresent(object, key, alias);
        if (raw == null) throw fail(subject + " is missing required field \"" + key + "\"");
        return integer(raw, key, min, max, subject);
    }

    private static int optionalInt(Map<String, Object> object, String key, String alias) {
        Object raw = firstPresent(object, key, alias);
        if (raw == null) return -1;
        return integer(raw, key, 1, 4096, "content");
    }

    private static int integer(Object raw, String key, int min, int max, String subject) {
        if (!(raw instanceof Number)) throw fail(subject + " field \"" + key + "\" must be a number");
        double number = ((Number) raw).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)) {
            throw fail(subject + " field \"" + key + "\" must be an integer but was " + raw);
        }
        if (number < min || number > max) {
            throw fail(subject + " field \"" + key + "\" out of range " + min + ".." + max
                    + " but was " + raw);
        }
        return (int) number;
    }

    private static float requiredFloat(Map<String, Object> object, String key, String alias,
                                       float min, float max, String subject) {
        Object raw = firstPresent(object, key, alias);
        if (raw == null) throw fail(subject + " is missing required field \"" + key + "\"");
        if (!(raw instanceof Number)) throw fail(subject + " field \"" + key + "\" must be a number");
        float value = ((Number) raw).floatValue();
        if (!Float.isFinite(value) || value < min || value > max) {
            throw fail(subject + " field \"" + key + "\" out of range " + min + ".." + max
                    + " but was " + raw);
        }
        return value;
    }

    private static List<Object> requiredArray(Map<String, Object> object, String key, String subject,
                                              int min, int max) {
        Object raw = requiredValue(object, key, subject);
        if (!(raw instanceof List)) throw fail(subject + " field \"" + key + "\" must be an array");
        List<?> list = (List<?>) raw;
        if (list.size() < min || list.size() > max) {
            throw fail(subject + " field \"" + key + "\" must hold " + min + ".." + max
                    + " entries but holds " + list.size());
        }
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) list;
        return result;
    }

    private static Map<String, Object> objectValue(Object raw, String subject) {
        if (!(raw instanceof Map)) throw fail(subject + " must be a JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }

    private static Object firstPresent(Map<String, Object> object, String key, String alias) {
        if (object.containsKey(key)) return object.get(key);
        if (alias != null && object.containsKey(alias)) return object.get(alias);
        return null;
    }

    private static Object read(String text, String subject) {
        if (text == null) throw fail(subject + " json is null");
        if (text.trim().isEmpty()) throw fail(subject + " json is blank");
        return new Reader(text).document();
    }

    /**
     * 极简 JSON reader：只支持 RFC 8259 的严格子集（无注释、无 NaN/Infinity、无重复键、
     * 无尾随逗号、无 BOM 之外的前后噪声），并用深度上限防止恶意嵌套打爆栈。
     */
    private static final class Reader {
        private static final int MAX_DEPTH = 48;

        private final String source;
        private int index;
        private int depth;

        Reader(String source) {
            this.source = source;
        }

        Object document() {
            // 允许一个可选的 UTF-8 BOM，其余前后空白忽略。
            if (!source.isEmpty() && source.charAt(0) == 0xFEFF) index = 1;
            whitespace();
            Object value = value();
            whitespace();
            if (index != source.length()) throw fail("unexpected trailing JSON at offset " + index);
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) throw fail("unexpected end of JSON");
            char token = source.charAt(index);
            if (token == '{') return object();
            if (token == '[') return array();
            if (token == '"') return text();
            if (token == '-' || Character.isDigit(token)) return number();
            if (source.startsWith("true", index)) { index += 4; return Boolean.TRUE; }
            if (source.startsWith("false", index)) { index += 5; return Boolean.FALSE; }
            if (source.startsWith("null", index)) { index += 4; return null; }
            throw fail("invalid token '" + token + "' at offset " + index);
        }

        private Map<String, Object> object() {
            enter('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) { depth--; return result; }
            while (true) {
                whitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    throw fail("object key must be a string at offset " + index);
                }
                String key = text();
                whitespace();
                expect(':');
                if (result.containsKey(key)) throw fail("duplicate field \"" + key + "\"");
                result.put(key, value());
                whitespace();
                if (consume('}')) { depth--; return result; }
                expect(',');
            }
        }

        private List<Object> array() {
            enter('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (consume(']')) { depth--; return result; }
            while (true) {
                result.add(value());
                whitespace();
                if (consume(']')) { depth--; return result; }
                expect(',');
            }
        }

        private void enter(char open) {
            expect(open);
            if (++depth > MAX_DEPTH) throw fail("JSON nesting deeper than " + MAX_DEPTH);
        }

        private String text() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char value = source.charAt(index++);
                if (value == '"') return result.toString();
                if (value < 0x20) throw fail("control character inside string at offset " + (index - 1));
                if (value != '\\') {
                    result.append(value);
                    continue;
                }
                if (index >= source.length()) throw fail("unterminated escape sequence");
                char escape = source.charAt(index++);
                if (escape == '"' || escape == '\\' || escape == '/' || escape == '\'') {
                    result.append(escape);
                } else if (escape == 'b') {
                    result.append('\b');
                } else if (escape == 'f') {
                    result.append('\f');
                } else if (escape == 'n') {
                    result.append('\n');
                } else if (escape == 'r') {
                    result.append('\r');
                } else if (escape == 't') {
                    result.append('\t');
                } else if (escape == 'u') {
                    if (index + 4 > source.length()) throw fail("truncated unicode escape");
                    try {
                        result.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw fail("invalid unicode escape \\u" + source.substring(index));
                    }
                    index += 4;
                } else {
                    throw fail("invalid escape '\\" + escape + "'");
                }
            }
            throw fail("unterminated string");
        }

        private Double number() {
            int start = index;
            if (source.charAt(index) == '-') index++;
            if (index >= source.length()) throw fail("truncated number at offset " + start);
            if (source.charAt(index) == '0') {
                index++;
            } else {
                if (!Character.isDigit(source.charAt(index))) throw fail("invalid number at offset " + start);
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                int fraction = index;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                if (index == fraction) throw fail("number fraction is empty at offset " + start);
            }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length()
                        && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                int exponent = index;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                if (index == exponent) throw fail("number exponent is empty at offset " + start);
            }
            try {
                return Double.valueOf(source.substring(start, index));
            } catch (NumberFormatException ex) {
                throw fail("invalid number \"" + source.substring(start, index) + "\"");
            }
        }

        private void whitespace() {
            while (index < source.length()) {
                char value = source.charAt(index);
                if (value != ' ' && value != '\t' && value != '\n' && value != '\r') break;
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw fail("expected '" + expected + "' at offset " + index);
            }
        }
    }
}
