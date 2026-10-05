package com.gamecenter.app.sokoban;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义关卡存储（关卡编辑器产出）。
 *
 * <p>沿用模块作用域偏好（mod_sokoban__sokoban_custom_levels），关卡以
 * {@link SokobanLevelCodec} 关卡码文本保存：每关一行 {@code id|name|code}，
 * 行间用换行分隔。用 String 而非 StringSet，避免跨实例缓存问题（工程约定）。</p>
 */
final class SokobanCustomLevels {

    private static final String MODULE_ID = "sokoban";
    private static final String PREFS_NAME = "sokoban_custom_levels";
    private static final String KEY_LEVELS = "custom_levels_v1";
    /** name 约束：不换行、不含分隔符、限长。 */
    static final int MAX_NAME_LEN = 12;

    /** 自定义关卡条目。 */
    static final class CustomLevel {
        final int id;
        final String name;
        /** {@link SokobanLevelCodec} 关卡码。 */
        final String code;

        CustomLevel(int id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        /** 解码为地图，非法码返回 null。 */
        int[][] decodeMap() {
            return SokobanLevelCodec.decode(code);
        }
    }

    private SokobanCustomLevels() {}

    /** 全部自定义关卡（按保存顺序）。 */
    static List<CustomLevel> loadAll(Context context) {
        List<CustomLevel> result = new ArrayList<>();
        String raw = preferences(context).getString(KEY_LEVELS, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            CustomLevel level = parseLine(line);
            if (level != null) result.add(level);
        }
        return result;
    }

    /**
     * 新增关卡。
     *
     * @return 新关卡 id；地图非法（编码失败）返回 -1
     */
    static int add(Context context, String name, int[][] map) {
        String code = SokobanLevelCodec.encode(map);
        if (code == null) return -1;
        List<CustomLevel> all = loadAll(context);
        int maxId = 0;
        for (CustomLevel level : all) {
            if (level.id > maxId) maxId = level.id;
        }
        int id = maxId + 1;
        all.add(new CustomLevel(id, sanitizeName(name), code));
        saveAll(context, all);
        return id;
    }

    /** 删除指定 id 的关卡。 */
    static void remove(Context context, int id) {
        List<CustomLevel> all = loadAll(context);
        List<CustomLevel> next = new ArrayList<>();
        for (CustomLevel level : all) {
            if (level.id != id) next.add(level);
        }
        saveAll(context, next);
    }

    static CustomLevel find(Context context, int id) {
        for (CustomLevel level : loadAll(context)) {
            if (level.id == id) return level;
        }
        return null;
    }

    /** 关卡总数。 */
    static int count(Context context) {
        return loadAll(context).size();
    }

    private static void saveAll(Context context, List<CustomLevel> levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(levels.get(i).id).append('|')
                    .append(levels.get(i).name).append('|')
                    .append(levels.get(i).code);
        }
        preferences(context).edit().putString(KEY_LEVELS, sb.toString()).apply();
    }

    private static CustomLevel parseLine(String line) {
        int s1 = line.indexOf('|');
        int s2 = line.indexOf('|', s1 + 1);
        if (s1 <= 0 || s2 <= s1 + 1) return null;
        try {
            int id = Integer.parseInt(line.substring(0, s1));
            String name = line.substring(s1 + 1, s2);
            String code = line.substring(s2 + 1);
            if (id < 1 || name.isEmpty() || code.isEmpty()) return null;
            return new CustomLevel(id, name, code);
        } catch (NumberFormatException e) {
            // 棘轮门禁：catch 吞异常必须留痕，禁止静默 return（verify_ratchet silent_return）。
            Log.w("SokobanCustomLevels", "dropping malformed custom level line", e);
            return null;
        }
    }

    /** 清洗名字：去换行与分隔符，限长。 */
    private static String sanitizeName(String name) {
        String cleaned = name == null ? "" : name.replace("|", "").replace("\n", "").trim();
        if (cleaned.isEmpty()) cleaned = "未命名";
        if (cleaned.length() > MAX_NAME_LEN) cleaned = cleaned.substring(0, MAX_NAME_LEN);
        return cleaned;
    }

    private static SharedPreferences preferences(Context context) {
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, PREFS_NAME);
        return ModuleScopedPreferences.get(context, MODULE_ID, PREFS_NAME);
    }
}
