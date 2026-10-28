package com.gamecenter.app.game2048;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 2048 模块 Fragment（五星级即开即玩现代化升级版本）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场：首屏秒入，自动恢复上次未完成对局。</li>
 *   <li>标准统一导航栏：防误触返回、标题、多档难度切换胶囊、规则说明、快捷设置。</li>
 *   <li>三档难度模式：休闲模式（4 概率 5%）、经典挑战（10% 标准）、大师硬核（30% 极速压迫）。</li>
 *   <li>战况 HUD 看板：当前得分、历史最高分、盘面最大方块与难度指示。</li>
 *   <li>控制功能：重新开局、单步撤回（Undo）。</li>
 *   <li>沉浸式对局结算：终局弹窗与自动持久化。</li>
 * </ul>
 * </p>
 */
public class Game2048Fragment extends Fragment {

    private static final String TAG = "Game2048Fragment";
    private static final String GAME_ID = "2048";
    private static final String SLOT_AUTO = "auto";
    private static final String PREF_NAME = "game2048_prefs";
    private static final String KEY_DIFFICULTY = "last_difficulty";

    private Game2048View game2048View;
    private Game2048Game game;
    private GestureDetector gestureDetector;

    private TextView tvDifficultyBadge;
    private TextView tvScore;
    private TextView tvHighScore;
    private Button btnRestart;
    private Button btnUndo;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private int highScore;
    private int currentDifficulty = Game2048Game.DIFFICULTY_NORMAL;

    private int colorBg;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorCardBg;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        resolveThemeColors(ctx);

        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        usageStore.recordLaunch(GAME_ID);
        highScore = loadHighScore();

        currentDifficulty = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DIFFICULTY, Game2048Game.DIFFICULTY_NORMAL);

        game = new Game2048Game();
        game.setDifficulty(currentDifficulty);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (14 * dp));

        // Child 0: 标准统一顶部导航栏
        root.addView(createTopBar(ctx, dp));

        // Child 1: 当前得分 HUD
        tvScore = new TextView(ctx);
        tvScore.setTextSize(17);
        tvScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvScore.setTextColor(colorAccent);
        tvScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.gravity = Gravity.CENTER;
        scoreLp.topMargin = (int) (6 * dp);
        tvScore.setLayoutParams(scoreLp);
        root.addView(tvScore);

        // Child 2: 历史最高分 HUD
        tvHighScore = new TextView(ctx);
        tvHighScore.setTextSize(15);
        tvHighScore.setTextColor(colorTextSecondary);
        tvHighScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams highScoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        highScoreLp.gravity = Gravity.CENTER;
        highScoreLp.topMargin = (int) (2 * dp);
        tvHighScore.setLayoutParams(highScoreLp);
        root.addView(tvHighScore);

        // Child 3: 游戏核心棋盘容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.setMargins(0, (int) (8 * dp), 0, (int) (8 * dp));
        gameContainer.setLayoutParams(containerLp);

        game2048View = new Game2048View(ctx);
        gameContainer.addView(game2048View);
        root.addView(gameContainer);

        // Child 4: 底部控制栏（按键索引 0 为 btnRestart，严格保证测试用例契约）
        LinearLayout buttonBar = new LinearLayout(ctx);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins(0, 0, 0, (int) (8 * dp));
        buttonBar.setLayoutParams(barLp);

        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        styleButton(btnRestart, dp, colorAccent, Color.WHITE);
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                0, (int) (46 * dp), 1f);
        restartLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnRestart.setLayoutParams(restartLp);
        buttonBar.addView(btnRestart);

        btnUndo = new Button(ctx);
        btnUndo.setText("↩ 撤回一步");
        styleButton(btnUndo, dp, colorCardBg, colorTextPrimary);
        LinearLayout.LayoutParams undoLp = new LinearLayout.LayoutParams(
                0, (int) (46 * dp), 1f);
        undoLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnUndo.setLayoutParams(undoLp);
        buttonBar.addView(btnUndo);

        root.addView(buttonBar);

        return root;
    }

    private void resolveThemeColors(Context ctx) {
        boolean dark = isDarkTheme(ctx);
        colorBg = dark ? 0xFF121418 : 0xFFFAF8EF;
        colorTextPrimary = dark ? 0xFFEEEEEE : 0xFF776E65;
        colorTextSecondary = dark ? 0xFF9E9E9E : 0xFF8F7A66;
        colorAccent = dark ? 0xFFFFA726 : 0xFFEDC22E;
        colorCardBg = dark ? 0xFF1E222B : 0xFFEDE0C8;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (6 * dp));

        // 返回防误触按钮
        TextView tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        // 标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("2048");
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        bar.addView(tvTitle);

        // 难度下拉胶囊
        tvDifficultyBadge = new TextView(ctx);
        updateDifficultyBadgeText();
        tvDifficultyBadge.setTextSize(12f);
        tvDifficultyBadge.setTextColor(colorAccent);
        tvDifficultyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyBadge.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(isDarkTheme(ctx) ? 0x22FFA726 : 0x1FEDC22E);
        badgeBg.setCornerRadius(14 * dp);
        badgeBg.setStroke((int) (1 * dp), colorAccent);
        tvDifficultyBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams diffLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffLp.setMargins((int) (10 * dp), 0, 0, 0);
        tvDifficultyBadge.setLayoutParams(diffLp);
        tvDifficultyBadge.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvDifficultyBadge);

        // 弹性占位
        View spacer = new View(ctx);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // 规则说明按钮
        TextView tvRules = new TextView(ctx);
        tvRules.setText("ℹ");
        tvRules.setTextSize(18f);
        tvRules.setTextColor(colorTextSecondary);
        tvRules.setPadding((int) (8 * dp), (int) (6 * dp), (int) (8 * dp), (int) (6 * dp));
        tvRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(tvRules);

        // 设置 / 重开按钮
        TextView tvSettings = new TextView(ctx);
        tvSettings.setText("⚙");
        tvSettings.setTextSize(18f);
        tvSettings.setTextColor(colorTextSecondary);
        tvSettings.setPadding((int) (8 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
        tvSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(tvSettings);

        return bar;
    }

    private void styleButton(Button btn, float dp, int bgCol, int textCol) {
        btn.setTextSize(14f);
        btn.setTextColor(textCol);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgCol);
        bg.setCornerRadius(12 * dp);
        btn.setBackground(bg);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();

        loadSavedState();
        game2048View.setGame(game);
        updateScore();
        updateUndoButton();

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null || game.isGameOver()) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 50) {
                        game.moveRight();
                    } else if (dx < -50) {
                        game.moveLeft();
                    }
                } else {
                    if (dy > 50) {
                        game.moveDown();
                    } else if (dy < -50) {
                        game.moveUp();
                    }
                }
                game2048View.invalidate();
                updateScore();
                updateUndoButton();
                return true;
            }
        });

        btnRestart.setOnClickListener(v -> {
            game.reset();
            game2048View.invalidate();
            updateScore();
            updateUndoButton();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        });

        btnUndo.setOnClickListener(v -> {
            if (game.undo()) {
                game2048View.invalidate();
                updateScore();
                updateUndoButton();
                Toast.makeText(ctx, "已撤回一步", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "无法再撤回", Toast.LENGTH_SHORT).show();
            }
        });

        requireView().setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void updateScore() {
        int currentScore = game.getScore();
        tvScore.setText(getString(R.string.game_score_alt_format, currentScore) + "  |  最大: " + game.getMaxTile());
        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        tvHighScore.setText(getString(R.string.game_high_score_format, highScore));
        if (game.isGameOver()) {
            game2048View.invalidate();
            usageStore.recordScore(GAME_ID, highScore);
            showGameOverDialog();
        }
    }

    private void updateUndoButton() {
        if (btnUndo != null) {
            btnUndo.setEnabled(game.canUndo());
            btnUndo.setAlpha(game.canUndo() ? 1.0f : 0.5f);
        }
    }

    private void showDifficultyDialog() {
        Context ctx = requireContext();
        new AlertDialog.Builder(ctx)
                .setTitle("选择游戏难度")
                .setSingleChoiceItems(Game2048Game.DIFFICULTY_NAMES, currentDifficulty, (dialog, which) -> {
                    currentDifficulty = which;
                    ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_DIFFICULTY, currentDifficulty)
                            .apply();
                    updateDifficultyBadgeText();
                    game.setDifficulty(currentDifficulty);
                    game.reset();
                    game2048View.invalidate();
                    updateScore();
                    updateUndoButton();
                    saveManager.deleteSave(GAME_ID, SLOT_AUTO);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge != null) {
            tvDifficultyBadge.setText(Game2048Game.DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        }
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("2048 游戏规则")
                .setMessage("【基本规则】\n" +
                        "• 上下左右滑动棋盘，所有方块会向滑动的方向靠拢\n" +
                        "• 两个相同数值的方块相撞时会合并为一个数值翻倍的新方块（如 2+2=4，4+4=8）\n" +
                        "• 每次滑动后，棋盘空位处会随机生成一个新的数字方块\n\n" +
                        "【获胜与结束】\n" +
                        "• 当成功合成 2048 方块时达成胜利（可继续挑战 4096 等更高分）\n" +
                        "• 当棋盘填满且相邻格子无法再合并时，游戏结束！\n\n" +
                        "【难度说明】\n" +
                        "• 休闲模式：新生成 4 的概率仅 5%，支持自由撤回\n" +
                        "• 经典挑战：官方标准 10% 概率生成 4\n" +
                        "• 大师硬核：新生成 4 的概率高达 30%，空间极速压迫")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(new String[]{"切换难度", "重新开局", "清空最高分纪录"}, (dialog, which) -> {
                    if (which == 0) {
                        showDifficultyDialog();
                    } else if (which == 1) {
                        btnRestart.performClick();
                    } else if (which == 2) {
                        highScore = 0;
                        saveHighScore(0);
                        updateScore();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showGameOverDialog() {
        if (!isAdded()) return;
        boolean isNewRecord = game.getScore() >= highScore && game.getScore() > 0;
        String title = isNewRecord ? "🎉 创造新纪录！" : "对局结算";
        String msg = "最终得分: " + game.getScore() + "\n" +
                "盘面最大方块: " + game.getMaxTile() + "\n" +
                "有效滑动步数: " + game.getMovesCount() + "\n" +
                "历史最高分: " + highScore + "\n" +
                "当前模式: " + Game2048Game.DIFFICULTY_NAMES[currentDifficulty];

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("再来一局", (dialog, which) -> {
                    btnRestart.performClick();
                })
                .setNegativeButton("返回大厅", (dialog, which) -> {
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .show();
    }

    private void handleBackPress() {
        if (game != null && !game.isGameOver() && game.getScore() > 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("退出将自动保存当前棋盘进度，确定返回大厅？")
                    .setPositiveButton("确定退出", (dialog, which) -> {
                        saveGameState();
                        if (getActivity() != null) getActivity().onBackPressed();
                    })
                    .setNegativeButton("继续游戏", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().onBackPressed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (game != null) {
            saveGameState();
        }
    }

    private void loadSavedState() {
        String saved = saveManager.load(GAME_ID, SLOT_AUTO);
        if (saved == null) return;
        try {
            JSONObject obj = new JSONObject(saved);
            int score = obj.getInt("score");
            boolean gameOver = obj.optBoolean("gameOver", false);
            int[][] board = new int[4][4];
            String boardStr = obj.getString("board");
            String[] values = boardStr.split(",");
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    board[y][x] = Integer.parseInt(values[y * 4 + x]);
                }
            }
            game.restoreState(board, score, gameOver);
        } catch (Exception e) {
            Log.w(TAG, "存档损坏", e);
        }
    }

    private void saveGameState() {
        try {
            StringBuilder boardStr = new StringBuilder();
            int[][] board = game.getBoardSnapshot();
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (boardStr.length() > 0) boardStr.append(",");
                    boardStr.append(board[y][x]);
                }
            }
            JSONObject obj = new JSONObject();
            obj.put("board", boardStr.toString());
            obj.put("score", game.getScore());
            obj.put("gameOver", game.isGameOver());
            saveManager.save(GAME_ID, SLOT_AUTO, obj.toString());
        } catch (Exception e) {
            Log.w(TAG, "存档错误", e);
        }
    }

    private int loadHighScore() {
        String progressJson = saveManager.loadProgress(GAME_ID);
        if (progressJson != null) {
            try {
                JSONObject obj = new JSONObject(progressJson);
                return obj.optInt("highScore", 0);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception e) {
            Log.w(TAG, "存档操作失败", e);
        }
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
