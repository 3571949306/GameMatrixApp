package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict parser for versioned, data-only TD campaign content. */
public final class TdLevelJsonParser {
    public static final int SCHEMA_VERSION = 1;

    public static final class Manifest {
        public final int contentVersion;
        public final List<ChapterRef> chapters;
        Manifest(int contentVersion, List<ChapterRef> chapters) {
            this.contentVersion = contentVersion;
            this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
        }
    }

    public static final class ChapterRef {
        public final String id, file;
        public final int levelCount;
        ChapterRef(String id, String file, int levelCount) {
            this.id = id; this.file = file; this.levelCount = levelCount;
        }
    }

    public static final class Chapter {
        public final String id, name;
        public final List<TdLevelDefinition> levels;
        Chapter(String id, String name, List<TdLevelDefinition> levels) {
            this.id = id; this.name = name;
            this.levels = Collections.unmodifiableList(new ArrayList<>(levels));
        }
    }

    private TdLevelJsonParser() {}

    public static Manifest parseManifest(String text) {
        Map<String, Object> root = object(document(text), "manifest");
        exact(root, "schema", "contentVersion", "gameId", "chapters");
        schema(root);
        if (!"td".equals(requiredString(root, "gameId", 2, 32))) throw bad("manifest gameId");
        List<Object> rawChapters = requiredArray(root, "chapters", 1, 100);
        List<ChapterRef> chapters = new ArrayList<>();
        Set<String> ids = new HashSet<>(), files = new HashSet<>();
        for (Object raw : rawChapters) {
            Map<String, Object> chapter = object(raw, "manifest chapter");
            exact(chapter, "id", "file", "levelCount");
            String id = id(chapter, "id");
            String file = requiredString(chapter, "file", 1, 96);
            if (!file.matches("chapters/[a-z0-9_]+\\.json")) throw bad("unsafe chapter file");
            int count = requiredInt(chapter, "levelCount", 1, 200);
            if (!ids.add(id) || !files.add(file)) throw bad("duplicate manifest chapter");
            chapters.add(new ChapterRef(id, file, count));
        }
        return new Manifest(requiredInt(root, "contentVersion", 1, Integer.MAX_VALUE), chapters);
    }

    public static Chapter parseChapter(String text) {
        Map<String, Object> root = object(document(text), "chapter");
        exact(root, "schema", "id", "name", "levels");
        schema(root);
        List<Object> rawLevels = requiredArray(root, "levels", 1, 200);
        List<TdLevelDefinition> levels = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (Object raw : rawLevels) {
            TdLevelDefinition level = level(object(raw, "level"));
            if (!ids.add(level.id) || !orders.add(level.order)) throw bad("duplicate level id/order");
            levels.add(level);
        }
        return new Chapter(id(root, "id"), requiredString(root, "name", 1, 64), levels);
    }

    private static TdLevelDefinition level(Map<String, Object> value) {
        exact(value, "id", "order", "name", "subtitle", "theme", "rows", "cols", "egg",
                "startCoin", "mascotHp", "routes", "waves");
        String levelId = id(value, "id");
        int order = requiredInt(value, "order", 1, 9999);
        String name = requiredString(value, "name", 1, 64);
        String subtitle = requiredString(value, "subtitle", 0, 120);
        TdLevelDefinition.Theme theme;
        try {
            theme = TdLevelDefinition.Theme.valueOf(requiredString(value, "theme", 3, 16)
                    .toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            throw bad("unknown theme");
        }
        int rows = requiredInt(value, "rows", 3, 64);
        int cols = requiredInt(value, "cols", 3, 64);
        List<Object> egg = requiredArray(value, "egg", 2, 2);
        int eggRow = integer(egg.get(0), "egg row", 0, rows - 1);
        int eggCol = integer(egg.get(1), "egg col", 0, cols - 1);
        List<int[][]> routes = routes(requiredArray(value, "routes", 1, 8), rows, cols, eggRow, eggCol);
        requireBuildableArea(routes, rows, cols);
        List<TdLevelDefinition.Wave> waves = waves(requiredArray(value, "waves", 1, 100), routes.size());
        return new TdLevelDefinition(levelId, order, name, subtitle, theme, rows, cols, eggRow, eggCol,
                requiredInt(value, "startCoin", 0, 100000),
                requiredInt(value, "mascotHp", 1, 100), routes, waves);
    }

    private static List<int[][]> routes(List<Object> values, int rows, int cols, int eggRow, int eggCol) {
        List<int[][]> result = new ArrayList<>();
        for (Object rawRoute : values) {
            List<Object> points = array(rawRoute, "route", 2, 256);
            int[][] route = new int[points.size()][2];
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < points.size(); i++) {
                List<Object> point = array(points.get(i), "route point", 2, 2);
                int row = integer(point.get(0), "route row", 0, rows - 1);
                int col = integer(point.get(1), "route col", 0, cols - 1);
                if (!seen.add(row * cols + col)) throw bad("route revisits a cell");
                if (i > 0 && Math.abs(row - route[i - 1][0]) + Math.abs(col - route[i - 1][1]) != 1) {
                    throw bad("route is not contiguous");
                }
                route[i][0] = row;
                route[i][1] = col;
            }
            int[] end = route[route.length - 1];
            if (end[0] != eggRow || end[1] != eggCol) throw bad("route must end at egg");
            result.add(route);
        }
        return result;
    }

    private static List<TdLevelDefinition.Wave> waves(List<Object> values, int routeCount) {
        List<TdLevelDefinition.Wave> result = new ArrayList<>();
        for (Object rawWave : values) {
            Map<String, Object> wave = object(rawWave, "wave");
            exact(wave, "types", "route", "count", "interval", "delay", "hpMul", "speedMul");
            List<MonsterType> types = new ArrayList<>();
            for (Object rawType : requiredArray(wave, "types", 1, 8)) {
                try {
                    types.add(MonsterType.valueOf(string(rawType, "monster type", 1, 32)
                            .toUpperCase(Locale.US)));
                } catch (IllegalArgumentException ex) {
                    throw bad("unknown monster type");
                }
            }
            result.add(new TdLevelDefinition.Wave(
                    requiredInt(wave, "route", 0, routeCount - 1), types,
                    requiredInt(wave, "count", 1, 1000),
                    requiredFloat(wave, "interval", 0f, 60f),
                    requiredFloat(wave, "delay", 0f, 600f),
                    requiredFloat(wave, "hpMul", .01f, 100f),
                    requiredFloat(wave, "speedMul", .01f, 100f)));
        }
        return result;
    }

    /** A long path must not consume the entire board: players need enough strategic build space. */
    private static void requireBuildableArea(List<int[][]> routes, int rows, int cols) {
        Set<Integer> occupied = new HashSet<>();
        for (int[][] route : routes) {
            for (int[] point : route) occupied.add(point[0] * cols + point[1]);
        }
        int minimum = Math.max(6, (rows * cols) / 5);
        if (rows * cols - occupied.size() < minimum) {
            throw bad("insufficient buildable cells");
        }
    }

    private static Object document(String text) {
        if (text == null) throw bad("null JSON");
        return new Reader(text).document();
    }

    private static void schema(Map<String, Object> object) {
        if (requiredInt(object, "schema", SCHEMA_VERSION, SCHEMA_VERSION) != SCHEMA_VERSION) {
            throw bad("unsupported schema");
        }
    }

    private static void exact(Map<String, Object> object, String... fields) {
        Set<String> expected = new HashSet<>(Arrays.asList(fields));
        if (!object.keySet().equals(expected)) throw bad("unknown or missing field");
    }

    private static String id(Map<String, Object> object, String key) {
        String value = requiredString(object, key, 3, 32);
        if (!value.matches("[a-z][a-z0-9_]{2,31}")) throw bad("invalid stable id");
        return value;
    }

    private static String requiredString(Map<String, Object> object, String key, int min, int max) {
        if (!object.containsKey(key)) throw bad("missing " + key);
        return string(object.get(key), key, min, max);
    }

    private static String string(Object value, String key, int min, int max) {
        if (!(value instanceof String)) throw bad("expected string " + key);
        String result = (String) value;
        if (result.length() < min || result.length() > max) throw bad("invalid string " + key);
        return result;
    }

    private static int requiredInt(Map<String, Object> object, String key, int min, int max) {
        if (!object.containsKey(key)) throw bad("missing " + key);
        return integer(object.get(key), key, min, max);
    }

    private static int integer(Object value, String key, int min, int max) {
        if (!(value instanceof Number)) throw bad("expected integer " + key);
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max) {
            throw bad("invalid integer " + key);
        }
        return (int) number;
    }

    private static float requiredFloat(Map<String, Object> object, String key, float min, float max) {
        if (!object.containsKey(key) || !(object.get(key) instanceof Number)) throw bad("expected number " + key);
        float value = ((Number) object.get(key)).floatValue();
        if (!Float.isFinite(value) || value < min || value > max) throw bad("invalid number " + key);
        return value;
    }

    private static List<Object> requiredArray(Map<String, Object> object, String key, int min, int max) {
        if (!object.containsKey(key)) throw bad("missing " + key);
        return array(object.get(key), key, min, max);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String key, int min, int max) {
        if (!(value instanceof List)) throw bad("expected array " + key);
        List<Object> result = (List<Object>) value;
        if (result.size() < min || result.size() > max) throw bad("invalid array " + key);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String key) {
        if (!(value instanceof Map)) throw bad("expected object " + key);
        return (Map<String, Object>) value;
    }

    private static IllegalArgumentException bad(String detail) {
        return new IllegalArgumentException("invalid TD level content: " + detail);
    }

    /** A small JSON reader. It intentionally has no comment, script, reflection or coercion mode. */
    private static final class Reader {
        private final String source;
        private int index;
        Reader(String source) { this.source = source; }

        Object document() {
            whitespace();
            Object value = value();
            whitespace();
            if (index != source.length()) throw bad("trailing JSON");
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) throw bad("unexpected end");
            char token = source.charAt(index);
            if (token == '{') return object();
            if (token == '[') return array();
            if (token == '"') return text();
            if (token == '-' || Character.isDigit(token)) return number();
            if (source.startsWith("true", index)) { index += 4; return Boolean.TRUE; }
            if (source.startsWith("false", index)) { index += 5; return Boolean.FALSE; }
            if (source.startsWith("null", index)) { index += 4; return null; }
            throw bad("invalid token");
        }

        private Map<String, Object> object() {
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) return result;
            while (true) {
                whitespace();
                if (index >= source.length() || source.charAt(index) != '"') throw bad("object key");
                String key = text();
                whitespace();
                expect(':');
                if (result.containsKey(key)) throw bad("duplicate field");
                result.put(key, value());
                whitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            index++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (consume(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (consume(']')) return result;
                expect(',');
            }
        }

        private String text() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char value = source.charAt(index++);
                if (value == '"') return result.toString();
                if (value < 0x20) throw bad("control character in string");
                if (value != '\\') { result.append(value); continue; }
                if (index >= source.length()) throw bad("unterminated escape");
                char escape = source.charAt(index++);
                if (escape == '"' || escape == '\\' || escape == '/') result.append(escape);
                else if (escape == 'b') result.append('\b');
                else if (escape == 'f') result.append('\f');
                else if (escape == 'n') result.append('\n');
                else if (escape == 'r') result.append('\r');
                else if (escape == 't') result.append('\t');
                else if (escape == 'u') {
                    if (index + 4 > source.length()) throw bad("unicode escape");
                    try { result.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); }
                    catch (NumberFormatException ex) { throw bad("unicode escape"); }
                    index += 4;
                } else throw bad("invalid escape");
            }
            throw bad("unterminated string");
        }

        private Double number() {
            int start = index;
            if (source.charAt(index) == '-') index++;
            if (index >= source.length()) throw bad("number");
            if (source.charAt(index) == '0') index++;
            else {
                if (!Character.isDigit(source.charAt(index))) throw bad("number");
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                int decimal = index;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                if (index == decimal) throw bad("number fraction");
            }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                int exponent = index;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                if (index == exponent) throw bad("number exponent");
            }
            try { return Double.valueOf(source.substring(start, index)); }
            catch (NumberFormatException ex) { throw bad("number"); }
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }
        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }
        private void expect(char expected) {
            if (!consume(expected)) throw bad("expected '" + expected + "'");
        }
    }
}
