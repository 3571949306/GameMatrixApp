package com.gamecenter.app.sudoku;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 数独当前对局存档。
 *
 * <p>存档使用模块作用域 SharedPreferences，内容采用带 schemaVersion 的 JSON，
 * 便于后续增加统计字段而不破坏已经进行中的对局。</p>
 */
public final class SudokuSaveManager {

    private static final String TAG = "SudokuSaveManager";
    private static final String MODULE_ID = "sudoku";
    private static final String PREFS_NAME = "sudoku_module";
    private static final String KEY_ACTIVE_GAME = "active_game";
    private static final int SCHEMA_VERSION = 1;

    private final SharedPreferences preferences;

    public SudokuSaveManager(Context context) {
        Context appContext = context.getApplicationContext();
        // 保留旧扁平文件的兼容迁移，但之后只访问 mod_sudoku__sudoku_module。
        ModuleScopedPreferences.migrateFrom(appContext, MODULE_ID, PREFS_NAME);
        preferences = ModuleScopedPreferences.get(appContext, MODULE_ID, PREFS_NAME);
    }

    public void save(SudokuGame game, long elapsedMs) {
        if (game == null || !game.isStarted() || game.isBoardComplete()) return;
        try {
            SudokuGame.State state = game.getState();
            JSONObject payload = new JSONObject();
            payload.put("schemaVersion", SCHEMA_VERSION);
            payload.put("difficultyIndex", state.getDifficultyIndex());
            payload.put("seed", state.getSeed());
            payload.put("elapsedMs", Math.max(0L, elapsedMs));
            payload.put("hintsUsed", state.getHintsUsed());
            payload.put("mistakes", state.getMistakes());
            payload.put("solution", encodeIntMatrix(state.getSolution()));
            payload.put("board", encodeIntMatrix(state.getBoard()));
            payload.put("given", encodeBooleanMatrix(state.getGiven()));
            payload.put("hinted", encodeBooleanMatrix(state.getHinted()));
            payload.put("notes", encodeIntMatrix(state.getNotes()));
            preferences.edit().putString(KEY_ACTIVE_GAME, payload.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "无法写入数独存档", e);
        }
    }

    public SavedGame load() {
        String encoded = preferences.getString(KEY_ACTIVE_GAME, null);
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            JSONObject payload = new JSONObject(encoded);
            if (payload.getInt("schemaVersion") != SCHEMA_VERSION) return null;
            SudokuGame.State state = new SudokuGame.State(
                    payload.getInt("difficultyIndex"),
                    payload.getLong("seed"),
                    readIntMatrix(payload, "solution"),
                    readIntMatrix(payload, "board"),
                    readBooleanMatrix(payload, "given"),
                    readBooleanMatrix(payload, "hinted"),
                    readIntMatrix(payload, "notes"),
                    payload.getInt("hintsUsed"),
                    payload.getInt("mistakes"));
            long elapsedMs = Math.max(0L, payload.getLong("elapsedMs"));

            // restoreState 会再次执行完整的题面、冲突与草稿不变量校验。
            SudokuGame validator = new SudokuGame();
            if (!validator.restoreState(state)) return null;
            return new SavedGame(state, elapsedMs);
        } catch (JSONException | RuntimeException e) {
            Log.w(TAG, "忽略损坏的数独存档", e);
            return null;
        }
    }

    public boolean hasSavedGame() {
        return load() != null;
    }

    public void clear() {
        preferences.edit().remove(KEY_ACTIVE_GAME).apply();
    }

    public static final class SavedGame {
        private final SudokuGame.State state;
        private final long elapsedMs;

        private SavedGame(SudokuGame.State state, long elapsedMs) {
            this.state = state;
            this.elapsedMs = elapsedMs;
        }

        public SudokuGame.State getState() {
            return state;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    private static JSONArray encodeIntMatrix(int[][] matrix) throws JSONException {
        JSONArray rows = new JSONArray();
        for (int[] row : matrix) {
            JSONArray values = new JSONArray();
            for (int value : row) values.put(value);
            rows.put(values);
        }
        return rows;
    }

    private static JSONArray encodeBooleanMatrix(boolean[][] matrix) throws JSONException {
        JSONArray rows = new JSONArray();
        for (boolean[] row : matrix) {
            JSONArray values = new JSONArray();
            for (boolean value : row) values.put(value);
            rows.put(values);
        }
        return rows;
    }

    private static int[][] readIntMatrix(JSONObject object, String key) throws JSONException {
        JSONArray rows = object.getJSONArray(key);
        if (rows.length() != SudokuGame.GRID_SIZE) {
            throw new JSONException("matrix row count mismatch: " + key);
        }
        int[][] matrix = new int[SudokuGame.GRID_SIZE][SudokuGame.GRID_SIZE];
        for (int r = 0; r < SudokuGame.GRID_SIZE; r++) {
            JSONArray values = rows.getJSONArray(r);
            if (values.length() != SudokuGame.GRID_SIZE) {
                throw new JSONException("matrix column count mismatch: " + key);
            }
            for (int c = 0; c < SudokuGame.GRID_SIZE; c++) {
                matrix[r][c] = values.getInt(c);
            }
        }
        return matrix;
    }

    private static boolean[][] readBooleanMatrix(JSONObject object, String key) throws JSONException {
        JSONArray rows = object.getJSONArray(key);
        if (rows.length() != SudokuGame.GRID_SIZE) {
            throw new JSONException("matrix row count mismatch: " + key);
        }
        boolean[][] matrix = new boolean[SudokuGame.GRID_SIZE][SudokuGame.GRID_SIZE];
        for (int r = 0; r < SudokuGame.GRID_SIZE; r++) {
            JSONArray values = rows.getJSONArray(r);
            if (values.length() != SudokuGame.GRID_SIZE) {
                throw new JSONException("matrix column count mismatch: " + key);
            }
            for (int c = 0; c < SudokuGame.GRID_SIZE; c++) {
                matrix[r][c] = values.getBoolean(c);
            }
        }
        return matrix;
    }
}
