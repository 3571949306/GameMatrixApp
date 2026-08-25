package com.gamecenter.app.games.tetris;

import android.content.pm.ActivityInfo;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 现代俄罗斯方块 Activity。
 *
 * <ul>
 *   <li>横屏强制（landscape），充分利用屏幕显示完整 HUD</li>
 *   <li>启动时弹难度选择对话框（简单/普通/困难/大师）</li>
 *   <li>右上角浮动按钮：暂停 / 重开 / 设置</li>
 *   <li>消行 / Tetris / T-Spin 触发触觉反馈</li>
 *   <li>Game Over 时弹窗：再来一局 / 退出</li>
 *   <li>支持中断续玩存档</li>
 * </ul>
 */
public class TetrisActivity extends BaseGameActivity {

    private static final String GAME_ID_VALUE = "tetris";
    private static final String TAG = "TetrisActivity";

    private TetrisView tetrisView;

    private SoundPool soundPool;
    private int soundIdRotate = 0;
    private int soundIdLand = 0;
    private int soundIdClear = 0;
    private int soundIdSpecial = 0;

    private int totalLinesCleared = 0;

    private com.gamecenter.app.games.save.GameSaveManager saveManager;
    private android.content.SharedPreferences tetrisPrefs;
    private long elapsedMs = 0L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 俄罗斯方块是天然竖屏游戏（10×20 高板），强制竖屏以获得最佳可视面积
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 全屏沉浸（可选）
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}

        super.onCreate(savedInstanceState);
        // 必须在 BaseGameActivity 把 TetrisView 加入容器后再加浮动按钮，否则会被棋盘遮住
        addHudButtons();
    }

    @NonNull
    @Override
    protected String getGameId() { return GAME_ID_VALUE; }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_tetris_name);
    }

    @Nullable
    @Override
    protected View getGameContentView() {
        return tetrisView;
    }

    @Override
    protected void initGame() {
        tetrisView = new TetrisView(this);

        // 加载历史最高分
        int high = usageStore != null ? usageStore.getHighScore(GAME_ID_VALUE) : 0;
        tetrisView.setHighScore(high);

        // 音效（复用现有 ui_turn 资源；后续可加专用 sfx）
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(4).build();
            soundIdRotate = soundPool.load(this, R.raw.ui_turn, 1);
            soundIdLand = soundPool.load(this, R.raw.ui_turn, 1);
            soundIdClear = soundPool.load(this, R.raw.ui_turn, 1);
            soundIdSpecial = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception e) {
            soundPool = null;
        }

        // 事件回调
        tetrisView.setOnScoreChangeListener(score -> {
            updateScore(score);
            checkAchievementThreshold("score", score, 1000);
            checkAchievementThreshold("score", score, 5000);
            checkAchievementThreshold("score", score, 10000);
        });

        tetrisView.setOnLinesClearedListener((lines, combo) -> {
            totalLinesCleared += lines;
            checkAchievementThreshold("lines", totalLinesCleared, 10);
            checkAchievementThreshold("lines", totalLinesCleared, 50);
            if (lines >= 4) {
                unlockAchievement("tetris_perfect");
                vibrate(HapticFeedbackConstants.LONG_PRESS);
            } else if (lines >= 2) {
                vibrate(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            playSound(soundIdClear);
            float pitch = lines >= 4 ? 1.3f : 1f + 0.1f * lines;
            playSoundPitched(soundIdClear, pitch);
        });

        tetrisView.setOnLevelChangeListener(level -> {
            checkAchievementThreshold("level", level, 5);
            checkAchievementThreshold("level", level, 10);
        });

        tetrisView.setOnGameOverListener(finalScore -> {
            usageStore.recordLoss(GAME_ID_VALUE);
            isGameRunning = false;
            if (saveManager != null) saveManager.clear(GAME_ID_VALUE);
            if (finalScore > high) {
                recordHighScore(finalScore);
            }
            submitScoreToLeaderboard(finalScore, elapsedMs);
            showGameOverDialog(finalScore);
        });

        tetrisView.setOnPieceLockListener(this::saveProgress);

        tetrisView.setOnActionEventListener(actionName -> {
            playSound(soundIdClear);
            if (actionName.startsWith("Tetris") || actionName.startsWith("T-Spin")) {
                vibrate(HapticFeedbackConstants.LONG_PRESS);
            } else if (actionName.startsWith("Combo x")) {
                vibrate(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });

        // 操控音效（区分旋转 / 落子 / 软降 / Hold，使用不同 pitch 增强反馈）
        tetrisView.setOnSfxListener(type -> {
            int id;
            float pitch;
            switch (type) {
                case "rotate": id = soundIdRotate; pitch = 1.10f; break;
                case "drop":   id = soundIdLand;   pitch = 0.80f; break;
                case "soft":   id = soundIdLand;   pitch = 1.30f; break;
                case "hold":   id = soundIdRotate; pitch = 0.90f; break;
                default:       id = soundIdRotate; pitch = 1.0f;
            }
            playSoundPitched(id, pitch);
        });

        // 暂停时点击棋盘请求恢复（同步图标状态）
        tetrisView.setOnRequestResumeListener(() -> {
            if (isGamePaused) togglePause();
        });

        // 记忆上次难度
        tetrisPrefs = getSharedPreferences("tetris_prefs", MODE_PRIVATE);

        // 添加视图
        if (gameContentContainer != null) {
            ((FrameLayout) gameContentContainer).addView(tetrisView);
        }

        // 初始化存档
        saveManager = new com.gamecenter.app.games.save.GameSaveManager(this);

        // 浮动按钮由 onCreate() 在 super.onCreate() 之后添加，确保在棋盘上方

        // 启动入口
        tetrisView.post(this::showDifficultyDialogOrRestore);
    }

    private android.widget.ImageButton pauseBtn;

    private void addHudButtons() {
        // 在 gameContentContainer 顶部居中放置一行功能按钮（返回 / 暂停 / 重开 / 设置），
        // 位于 HOLD 与 NEXT 之间的空白区，避免遮挡竖屏 HUD。
        if (gameContentContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        int btnSize = (int) (40 * density);

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);

        pauseBtn = makeIconButton(android.R.drawable.ic_media_pause, btnSize);
        android.widget.ImageButton btnRestart = makeIconButton(android.R.drawable.ic_menu_revert, btnSize);
        android.widget.ImageButton btnSettings = makeIconButton(android.R.drawable.ic_menu_preferences, btnSize);
        android.widget.ImageButton btnBack = makeIconButton(android.R.drawable.ic_menu_close_clear_cancel, btnSize);

        pauseBtn.setOnClickListener(v -> togglePause());
        btnRestart.setOnClickListener(v -> confirmRestart());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnBack.setOnClickListener(v -> finish());

        android.widget.ImageButton[] arr = {btnBack, pauseBtn, btnRestart, btnSettings};
        for (android.widget.ImageButton b : arr) {
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins((int) (8 * density), 0, (int) (8 * density), 0);
            row.addView(b, lp);
        }

        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        flp.topMargin = (int) (10 * density);
        row.setLayoutParams(flp);
        gameContentContainer.addView(row);
    }

    private void togglePause() {
        if (tetrisView == null) return;
        if (tetrisView.isGameOver()) return;
        if (tetrisView.isPaused()) {
            tetrisView.resumeGame();
            isGamePaused = false;
            if (pauseBtn != null) pauseBtn.setImageResource(android.R.drawable.ic_media_pause);
            Toast.makeText(this, R.string.game_tetris_resumed, Toast.LENGTH_SHORT).show();
        } else {
            tetrisView.pauseGame();
            isGamePaused = true;
            if (pauseBtn != null) pauseBtn.setImageResource(android.R.drawable.ic_media_play);
            Toast.makeText(this, R.string.game_tetris_paused, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmRestart() {
        if (tetrisView == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_tetris_restart_confirm_title)
                .setMessage(R.string.game_tetris_restart_confirm_msg)
                .setPositiveButton(R.string.game_tetris_restart, (d, w) -> {
                    tetrisView.pauseGame();
                    if (saveManager != null) saveManager.clear(GAME_ID_VALUE);
                    totalLinesCleared = 0;
                    showDifficultyDialog();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private android.widget.ImageButton makeIconButton(int drawableRes, int size) {
        android.widget.ImageButton btn = new android.widget.ImageButton(this);
        btn.setImageResource(drawableRes);
        btn.setBackgroundColor(0x66000000);
        btn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(8, 8, 8, 8);
        return btn;
    }

    /** 启动：检测存档 vs 弹难度选择 */
    private void showDifficultyDialogOrRestore() {
        if (isFinishing() || isDestroyed()) return;
        if (saveManager != null && saveManager.hasSave(GAME_ID_VALUE)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.game_tetris_resume_title)
                    .setMessage(R.string.game_tetris_resume_msg)
                    .setPositiveButton(R.string.game_tetris_resume, (d, w) -> restoreFromSave())
                    .setNegativeButton(R.string.game_tetris_new_game, (d, w) -> {
                        saveManager.clear(GAME_ID_VALUE);
                        showDifficultyDialog();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            showDifficultyDialog();
        }
    }

    private void showDifficultyDialog() {
        // 难度列表（中文 + 描述）
        final String[] names = {
                getString(R.string.game_tetris_diff_easy),
                getString(R.string.game_tetris_diff_normal),
                getString(R.string.game_tetris_diff_hard),
                getString(R.string.game_tetris_diff_master)
        };
        final String[] descs = {
                getString(R.string.game_tetris_diff_easy_desc),
                getString(R.string.game_tetris_diff_normal_desc),
                getString(R.string.game_tetris_diff_hard_desc),
                getString(R.string.game_tetris_diff_master_desc)
        };
        final int last = tetrisPrefs != null ? tetrisPrefs.getInt("last_difficulty", 1) : 1;
        final int[] chosen = {Math.max(0, Math.min(3, last - 1))};
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_tetris_difficulty_title)
                .setSingleChoiceItems(names, chosen[0], (d, w) -> chosen[0] = w)
                .setPositiveButton(R.string.game_tetris_start, (d, w) -> {
                    int diffIndex = chosen[0];
                    int difficulty = diffIndex + 1; // 1..4
                    if (tetrisPrefs != null) {
                        tetrisPrefs.edit().putInt("last_difficulty", difficulty).apply();
                    }
                    // 同时更新 BaseGameActivity 的难度索引
                    setDifficulty(diffIndex);
                    if (tetrisView != null) {
                        tetrisView.setDifficultyLevel(difficulty);
                    }
                    startGame();
                })
                .setCancelable(false)
                .show();
    }

    private void showSettingsDialog() {
        final String[] items = {
                getString(R.string.game_tetris_settings_sound),
                getString(R.string.game_tetris_settings_vibrate),
                getString(R.string.game_tetris_settings_ghost),
                getString(R.string.tetris_rules_title)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_tetris_settings_title)
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        // 音效总开关（走 SettingsManager.setSoundEnabled）
                        boolean cur = SettingsManager.getInstance(this).isSoundEnabled();
                        SettingsManager.getInstance(this).setSoundEnabled(!cur);
                        Toast.makeText(this, !cur ? R.string.game_tetris_sound_on : R.string.game_tetris_sound_off, Toast.LENGTH_SHORT).show();
                    } else if (w == 1) {
                        // 振动开关（走 SettingsManager.setVibrationEnabled — 与全局振动开关联动）
                        boolean cur = SettingsManager.getInstance(this).isVibrationEnabled();
                        SettingsManager.getInstance(this).setVibrationEnabled(!cur);
                        Toast.makeText(this, !cur ? R.string.game_tetris_vibrate_on : R.string.game_tetris_vibrate_off, Toast.LENGTH_SHORT).show();
                    } else if (w == 2) {
                        // 落点预览 (Ghost) 开关
                        boolean cur = tetrisView != null && tetrisView.isGhostEnabled();
                        if (tetrisView != null) tetrisView.setGhostEnabled(!cur);
                        Toast.makeText(this, !cur ? R.string.game_tetris_ghost_on : R.string.game_tetris_ghost_off, Toast.LENGTH_SHORT).show();
                    } else if (w == 3) {
                        // 规则
                        showRulesDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRulesDialog() {
        String body = getString(R.string.tetris_rules_basic)
                + "\n\n" + getString(R.string.tetris_rules_victory)
                + "\n\n" + getString(R.string.tetris_rules_scoring)
                + "\n\n" + getString(R.string.tetris_rules_special)
                + "\n\n" + getString(R.string.tetris_rules_modern);
        new AlertDialog.Builder(this)
                .setTitle(R.string.tetris_rules_title)
                .setMessage(body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showGameOverDialog(int finalScore) {
        // 短暂延迟确保 Game Over 动画到位
        tetrisView.postDelayed(() -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.game_tetris_game_over)
                    .setMessage(getString(R.string.game_tetris_game_over_msg, finalScore,
                            Math.max(finalScore, tetrisView.getHighScore())))
                    .setPositiveButton(R.string.game_tetris_restart, (d, w) -> {
                        totalLinesCleared = 0;
                        showDifficultyDialog();
                    })
                    .setNegativeButton(R.string.game_tetris_back, (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }, 600);
    }

    private void restoreFromSave() {
        if (saveManager == null) {
            startGame();
            return;
        }
        JSONObject state = saveManager.load(GAME_ID_VALUE);
        if (state == null) {
            startGame();
            return;
        }
        try {
            JSONArray rows = state.getJSONArray("grid");
            int[][] savedGrid = new int[ROWS_TETRIS()][COLS_TETRIS()];
            for (int r = 0; r < rows.length(); r++) {
                JSONArray row = rows.getJSONArray(r);
                savedGrid[r] = new int[row.length()];
                for (int c = 0; c < row.length(); c++) {
                    savedGrid[r][c] = row.getInt(c);
                }
            }
            int savedPiece = state.optInt("currentPiece", -1);
            int savedRotation = state.optInt("currentRotation", 0);
            int savedX = state.optInt("pieceX", 0);
            int savedY = state.optInt("pieceY", 0);
            int savedScore = state.optInt("score", 0);
            int savedLines = state.optInt("lines", 0);
            int savedLevel = state.optInt("level", 1);
            int savedHold = state.optInt("hold", -1);

            // next 队列（兼容存档中只存单个 nextPiece 的情况，自动补齐）
            int[] savedNext = new int[5];
            JSONArray nextArr = state.optJSONArray("nextQueue");
            int idx = 0;
            if (nextArr != null) {
                for (int i = 0; i < nextArr.length() && idx < 5; i++) {
                    savedNext[idx++] = nextArr.getInt(i);
                }
            } else {
                int nxt = state.optInt("nextPiece", 0);
                if (nxt >= 0 && nxt < 7) {
                    savedNext[0] = nxt;
                    idx = 1;
                }
            }
            while (idx < 5) {
                // 用 piece=0 填空，让 View 内部 refillBagIfNeeded 重排
                savedNext[idx++] = 0;
            }
            // next=0 重新随机洗牌保险 — restoreSnapshot 接受 -1
            boolean savedHoldUsed = state.optBoolean("holdUsed", false);
            int savedCombo = state.optInt("combo", 0);
            boolean savedB2B = state.optBoolean("lastDifficult", false);

            if (!tetrisView.restoreSnapshot(savedGrid, savedPiece, savedRotation, savedX, savedY,
                    savedNext, savedHold, savedScore, savedLines, savedLevel,
                    savedHoldUsed, savedCombo, savedB2B)) {
                startGame();
                return;
            }
            updateScore(savedScore);
            totalLinesCleared = savedLines;
            isGameRunning = true;
            isGamePaused = false;
            gameStartTime = System.currentTimeMillis();
        } catch (Exception e) {
            android.util.Log.w(TAG, "存档恢复失败，新开一局: " + e.getMessage());
            startGame();
        }
    }

    private int ROWS_TETRIS() { return 20; }
    private int COLS_TETRIS() { return 10; }

    private void saveProgress() {
        if (saveManager == null || !isGameRunning || tetrisView == null) return;
        try {
            JSONObject state = new JSONObject();
            JSONArray rows = new JSONArray();
            int[][] grid = tetrisView.getGrid();
            for (int r = 0; r < grid.length; r++) {
                JSONArray row = new JSONArray();
                for (int c = 0; c < grid[r].length; c++) {
                    row.put(grid[r][c]);
                }
                rows.put(row);
            }
            state.put("grid", rows);
            state.put("currentPiece", tetrisView.getCurrentPiece());
            state.put("currentRotation", tetrisView.getCurrentRotation());
            state.put("pieceX", tetrisView.getPieceX());
            state.put("pieceY", tetrisView.getPieceY());
            // 兼容：旧存档用 nextPiece，新版用 nextQueue
            state.put("nextPiece", tetrisView.getNextQueue() != null && !tetrisView.getNextQueue().isEmpty()
                    ? tetrisView.getNextQueue().peek() : 0);
            JSONArray nextArr = new JSONArray();
            if (tetrisView.getNextQueue() != null) {
                for (int p : tetrisView.getNextQueue()) {
                    nextArr.put(p);
                    if (nextArr.length() >= 5) break;
                }
            }
            state.put("nextQueue", nextArr);
            state.put("hold", tetrisView.getHoldPiece());
            state.put("holdUsed", tetrisView.isHoldUsedThisTurn());
            state.put("score", tetrisView.getScore());
            state.put("lines", tetrisView.getLines());
            state.put("level", tetrisView.getLevel());
            state.put("combo", tetrisView.getCombo());
            state.put("lastDifficult", tetrisView.isBackToBack());
            saveManager.save(GAME_ID_VALUE, state);
        } catch (Exception ignored) {}
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        totalLinesCleared = 0;
        if (tetrisView != null) tetrisView.startGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        if (tetrisView != null) tetrisView.pauseGame();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (tetrisView != null) tetrisView.resumeGame();
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (tetrisView != null) tetrisView.stopGame();
    }

    // ==================== 难度 / 成就（兼容 BaseGameActivity） ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_easy), 1, getString(R.string.game_tetris_diff_easy_desc),
                0, 0, 1.5f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_normal), 2, getString(R.string.game_tetris_diff_normal_desc),
                0, 0, 1.0f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_hard), 3, getString(R.string.game_tetris_diff_hard_desc),
                0, 0, 0.65f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_master), 4, getString(R.string.game_tetris_diff_master_desc),
                0, 0, 0.45f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        if (tetrisView != null) tetrisView.setDifficultyLevel(newLevel.level);
        // 不在切换时重置 game
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        switch (eventType) {
            case "score":
                int s = (int) params[0];
                checkAchievementThreshold("score", s, 1000);
                checkAchievementThreshold("score", s, 5000);
                checkAchievementThreshold("score", s, 10000);
                break;
        }
    }

    // ==================== 音效 / 振动 ====================

    private void playSound(int id) {
        if (soundPool == null || id == 0) return;
        if (!SettingsManager.getInstance(this).shouldPlayGameSound()) return;
        try {
            soundPool.play(id, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception ignored) {}
    }

    private void playSoundPitched(int id, float pitch) {
        if (soundPool == null || id == 0) return;
        if (!SettingsManager.getInstance(this).shouldPlayGameSound()) return;
        try {
            soundPool.play(id, 0.55f, 0.55f, 1, 0, pitch);
        } catch (Exception ignored) {}
    }

    private void vibrate(int hapticConstant) {
        if (!SettingsManager.getInstance(this).isVibrationEnabled()) return;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int ms = hapticConstant == HapticFeedbackConstants.LONG_PRESS ? 50 : 20;
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(hapticConstant == HapticFeedbackConstants.LONG_PRESS ? 50 : 20);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            try { soundPool.release(); } catch (Exception ignored) {}
            soundPool = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 暂停所有动画
    }
}
