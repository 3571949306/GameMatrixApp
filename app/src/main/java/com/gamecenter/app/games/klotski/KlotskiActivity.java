package com.gamecenter.app.games.klotski;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * 华容道游戏 Activity。
 *
 * <p>经典华容道滑块拼图，曹操（2×2）需移至底部中央出口。
 * 支持自动打乱和最优解提示。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 2.0
 * @since 2026-06-21
 */
public class KlotskiActivity extends BaseGameActivity {

    private KlotskiView klotskiView;
    private KlotskiGame game;
    private TextView tvStatus;
    private TextView tvMoves;
    private Handler mainHandler;
    private boolean isHintSearching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "klotski";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_klotski_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
        startNewGame();
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_klotski_color_bg));
        root.setPadding(0, 16, 0, 0);

        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.game_klotski_name));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(26f);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.game_klotski_color_title));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 状态显示
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(15f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_klotski_color_status));
        tvStatus.setPadding(16, 8, 16, 4);
        tvStatus.setText(getString(R.string.game_klotski_slide_hint));
        root.addView(tvStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 步数显示
        tvMoves = new TextView(this);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(13f);
        tvMoves.setTextColor(ContextCompat.getColor(this, R.color.game_klotski_color_moves));
        tvMoves.setText(getString(R.string.game_klotski_moves_label, 0));
        root.addView(tvMoves, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 华容道视图
        klotskiView = new KlotskiView(this);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        viewParams.setMargins(16, 8, 16, 8);
        klotskiView.setLayoutParams(viewParams);
        root.addView(klotskiView);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(16, 0, 16, 8);

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.game_klotski_restart);
        btnRestart.setTextSize(13f);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(8, 0, 8, 0);
        btnRestart.setLayoutParams(btnParams);
        btnRestart.setOnClickListener(v -> startNewGame());
        btnRow.addView(btnRestart);

        MaterialButton btnHint = new MaterialButton(this);
        btnHint.setText(R.string.game_klotski_hint);
        btnHint.setTextSize(13f);
        btnHint.setLayoutParams(btnParams);
        btnHint.setOnClickListener(v -> showHint());
        btnRow.addView(btnHint);

        // 2026-06-23: 撤销按钮
        MaterialButton btnUndo = new MaterialButton(this);
        btnUndo.setText(R.string.game_klotski_undo);
        btnUndo.setTextSize(13f);
        btnUndo.setLayoutParams(btnParams);
        btnUndo.setOnClickListener(v -> {
            if (game != null && game.undoMove()) {
                klotskiView.invalidate();
                if (tvMoves != null) tvMoves.setText(getString(R.string.game_klotski_moves_label, game.getMoves()));
                tvStatus.setText(R.string.game_klotski_undone);
            } else {
                Toast.makeText(this, R.string.game_klotski_no_undo, Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(btnUndo);

        root.addView(btnRow);

        return root;
    }

    private void startNewGame() {
        game = new KlotskiGame();
        game.reset();
        game.shuffle();

        if (klotskiView != null) {
            klotskiView.setGame(game);
            klotskiView.setOnWinListener(() -> {
                tvStatus.setText(R.string.game_klotski_win_status);
                Toast.makeText(this, R.string.game_klotski_win_toast, Toast.LENGTH_SHORT).show();
                // 2026-06-23: 通关后弹游戏结束 Dialog（含步数+用时）
                usageStore.recordWin(getGameId());
                checkAchievement("win", game.getMoves());
                updateScore(currentScore + 100);
                showGameEndDialog(true, game.getMoves());
            });
            klotskiView.setOnMoveListener(() -> {
                if (tvMoves != null) {
                    tvMoves.setText(getString(R.string.game_klotski_moves_label, game.getMoves()));
                }
            });
        }

        if (tvStatus != null) {
            tvStatus.setText(R.string.game_klotski_slide_hint);
        }
        if (tvMoves != null) {
            tvMoves.setText(getString(R.string.game_klotski_moves_label, 0));
        }
    }

    private void showHint() {
        if (isHintSearching) {
            Toast.makeText(this, R.string.game_klotski_searching, Toast.LENGTH_SHORT).show();
            return;
        }

        isHintSearching = true;
        tvStatus.setText(R.string.game_klotski_calculating);
        hintStartMs = System.currentTimeMillis();  // 2026-06-23: 性能监控起点

        new Thread(() -> {
            KlotskiGame.HintResult hint = game.getHint();
            mainHandler.post(() -> {
                isHintSearching = false;
                // 2026-06-23: 性能监控 + Toast 提示（华容道 BFS 搜索）
                long hintMs = System.currentTimeMillis() - hintStartMs;
                android.util.Log.i("KlotskiAI", "提示搜索耗时=" + hintMs + "ms");
                if (hintMs > 500) {
                    android.widget.Toast.makeText(this,
                            getString(R.string.game_klotski_hint_search_ms, hintMs),
                            android.widget.Toast.LENGTH_SHORT).show();
                }
                if (hint != null) {
                    klotskiView.showHint(hint);
                    tvStatus.setText(getString(R.string.game_klotski_hint_steps, hint.totalSteps));
                } else {
                    tvStatus.setText(R.string.game_klotski_no_solution);
                }
            });
        }).start();
    }

    /** 2026-06-23: 提示搜索开始时间 */
    private long hintStartMs = 0L;

    /**
     * 2026-06-23：游戏结束 Dialog（华容道通关后显示战绩）。
     */
    private void showGameEndDialog(boolean won, int moves) {
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(won ? R.string.game_klotski_dialog_win_title : R.string.game_klotski_dialog_end_title);
        StringBuilder content = new StringBuilder();
        content.append(won ? getString(R.string.game_klotski_dialog_win_msg) : getString(R.string.game_klotski_dialog_lose_msg));
        content.append(getString(R.string.game_klotski_dialog_total_moves, moves));
        content.append(getString(R.string.game_klotski_dialog_time, formatDuration(elapsed)));
        if (won) {
            content.append(getString(R.string.game_klotski_dialog_score));
        }
        builder.setMessage(content.toString());
        builder.setPositiveButton(R.string.game_klotski_play_again, (d, w) -> startNewGame());
        builder.setNegativeButton(R.string.game_klotski_back_to_main, (d, w) -> finish());
        builder.setNeutralButton(R.string.game_klotski_leaderboard, (d, w) -> showLeaderboard());
        builder.setCancelable(false);
        builder.show();

        // 2026-06-23: 通关时保存记录到排行榜
        if (won) {
            saveLeaderboardRecord(moves, elapsed);
        }
    }

    /**
     * 2026-06-23: 华容道步数排行榜（本地 SharedPreferences 存储）。
     * 保存最少 5 条记录（按步数升序）。
     */
    private void saveLeaderboardRecord(int moves, long elapsedMs) {
        android.content.SharedPreferences prefs = getSharedPreferences("klotski_leaderboard", MODE_PRIVATE);
        String existing = prefs.getString("records", "");
        java.util.List<String> records = new java.util.ArrayList<>();
        if (!existing.isEmpty()) {
            for (String line : existing.split("\\|")) {
                if (!line.isEmpty()) records.add(line);
            }
        }
        String newRecord = moves + "," + elapsedMs;
        records.add(newRecord);
        // 按步数升序
        records.sort((a, b) -> {
            int am = Integer.parseInt(a.split(",")[0]);
            int bm = Integer.parseInt(b.split(",")[0]);
            return Integer.compare(am, bm);
        });
        // 保留前 5
        if (records.size() > 5) records = records.subList(0, 5);
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            if (sb.length() > 0) sb.append("|");
            sb.append(r);
        }
        prefs.edit().putString("records", sb.toString()).apply();
    }

    /**
     * 2026-06-23: 显示华容道步数排行榜。
     */
    private void showLeaderboard() {
        android.content.SharedPreferences prefs = getSharedPreferences("klotski_leaderboard", MODE_PRIVATE);
        String existing = prefs.getString("records", "");
        StringBuilder content = new StringBuilder(getString(R.string.game_klotski_leaderboard_header));
        if (existing.isEmpty()) {
            content.append(getString(R.string.game_klotski_no_records));
        } else {
            int rank = 1;
            for (String line : existing.split("\\|")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                int moves = Integer.parseInt(parts[0]);
                long ms = Long.parseLong(parts[1]);
                content.append(getString(R.string.game_klotski_leaderboard_record, rank++, moves, formatDuration(ms)));
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.game_klotski_leaderboard_title)
                .setMessage(content.toString())
                .setPositiveButton(R.string.game_klotski_close, null)
                .show();
    }

    /** 格式化毫秒为 mm:ss */
    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L);
    }
}
