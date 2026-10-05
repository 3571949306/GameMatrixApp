package com.gamecenter.app.sudoku;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义谜题存储（谜题编辑器产出）。
 *
 * <p>沿用模块作用域偏好（mod_sudoku__sudoku_custom_puzzles），谜题以
 * {@link SudokuPuzzleCodec} 81 字符谜题码文本保存：每题一行 {@code id|name|code}，
 * 行间用换行分隔。用 String 而非 StringSet，避免跨实例缓存问题（工程约定）。</p>
 */
final class SudokuCustomPuzzles {

    private static final String MODULE_ID = "sudoku";
    private static final String PREFS_NAME = "sudoku_custom_puzzles";
    private static final String KEY_PUZZLES = "custom_puzzles_v1";
    /** name 约束：不换行、不含分隔符、限长。 */
    static final int MAX_NAME_LEN = 12;

    /** 自定义谜题条目。 */
    static final class CustomPuzzle {
        final int id;
        final String name;
        /** {@link SudokuPuzzleCodec} 谜题码（81 字符，'0'=空）。 */
        final String code;

        CustomPuzzle(int id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        /** 解码为 9×9 盘面，非法码返回 null。 */
        int[][] decodeMap() {
            return SudokuPuzzleCodec.decode(code);
        }
    }

    private SudokuCustomPuzzles() {}

    /** 全部自定义谜题（按保存顺序）。 */
    static List<CustomPuzzle> loadAll(Context context) {
        List<CustomPuzzle> result = new ArrayList<>();
        String raw = preferences(context).getString(KEY_PUZZLES, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            CustomPuzzle puzzle = parseLine(line);
            if (puzzle != null) result.add(puzzle);
        }
        return result;
    }

    /**
     * 新增谜题。
     *
     * @return 新谜题 id；盘面非法（编码失败）返回 -1
     */
    static int add(Context context, String name, int[][] grid) {
        String code = SudokuPuzzleCodec.encode(grid);
        if (code == null) return -1;
        List<CustomPuzzle> all = loadAll(context);
        int maxId = 0;
        for (CustomPuzzle puzzle : all) {
            if (puzzle.id > maxId) maxId = puzzle.id;
        }
        int id = maxId + 1;
        all.add(new CustomPuzzle(id, sanitizeName(name), code));
        saveAll(context, all);
        return id;
    }

    /** 删除指定 id 的谜题。 */
    static void remove(Context context, int id) {
        List<CustomPuzzle> all = loadAll(context);
        List<CustomPuzzle> next = new ArrayList<>();
        for (CustomPuzzle puzzle : all) {
            if (puzzle.id != id) next.add(puzzle);
        }
        saveAll(context, next);
    }

    static CustomPuzzle find(Context context, int id) {
        for (CustomPuzzle puzzle : loadAll(context)) {
            if (puzzle.id == id) return puzzle;
        }
        return null;
    }

    /** 谜题总数。 */
    static int count(Context context) {
        return loadAll(context).size();
    }

    private static void saveAll(Context context, List<CustomPuzzle> puzzles) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < puzzles.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(puzzles.get(i).id).append('|')
                    .append(puzzles.get(i).name).append('|')
                    .append(puzzles.get(i).code);
        }
        preferences(context).edit().putString(KEY_PUZZLES, sb.toString()).apply();
    }

    private static CustomPuzzle parseLine(String line) {
        int s1 = line.indexOf('|');
        int s2 = line.indexOf('|', s1 + 1);
        if (s1 <= 0 || s2 <= s1 + 1) return null;
        try {
            int id = Integer.parseInt(line.substring(0, s1));
            String name = line.substring(s1 + 1, s2);
            String code = line.substring(s2 + 1);
            if (id < 1 || name.isEmpty() || code.isEmpty()) return null;
            return new CustomPuzzle(id, name, code);
        } catch (NumberFormatException e) {
            // 棘轮门禁：catch 吞异常必须留痕，禁止静默 return（verify_ratchet silent_return）。
            Log.w("SudokuCustomPuzzles", "dropping malformed custom puzzle line", e);
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
