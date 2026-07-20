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
        return "华容道";
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
        root.setBackgroundColor(0xFFFFFFFF);
        root.setPadding(0, 16, 0, 0);

        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText("华容道");
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(26f);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 状态显示
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(15f);
        tvStatus.setTextColor(0xFF4CAF50);
        tvStatus.setPadding(16, 8, 16, 4);
        tvStatus.setText("滑动方块，帮助曹操逃出");
        root.addView(tvStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 步数显示
        tvMoves = new TextView(this);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(13f);
        tvMoves.setTextColor(0xFF757575);
        tvMoves.setText("步数: 0");
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
        btnRestart.setText("重开");
        btnRestart.setTextSize(13f);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(8, 0, 8, 0);
        btnRestart.setLayoutParams(btnParams);
        btnRestart.setOnClickListener(v -> startNewGame());
        btnRow.addView(btnRestart);

        MaterialButton btnHint = new MaterialButton(this);
        btnHint.setText("提示");
        btnHint.setTextSize(13f);
        btnHint.setLayoutParams(btnParams);
        btnHint.setOnClickListener(v -> showHint());
        btnRow.addView(btnHint);

        // 2026-06-23: 撤销按钮
        MaterialButton btnUndo = new MaterialButton(this);
        btnUndo.setText("↶ 撤销");
        btnUndo.setTextSize(13f);
        btnUndo.setLayoutParams(btnParams);
        btnUndo.setOnClickListener(v -> {
            if (game != null && game.undoMove()) {
                klotskiView.invalidate();
                if (tvMoves != null) tvMoves.setText("步数: " + game.getMoves());
                tvStatus.setText("已撤销");
            } else {
                Toast.makeText(this, "无可撤销", Toast.LENGTH_SHORT).show();
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
                tvStatus.setText("🎉 恭喜通关！");
                Toast.makeText(this, "恭喜通关！", Toast.LENGTH_SHORT).show();
                // 2026-06-23: 通关后弹游戏结束 Dialog（含步数+用时）
                usageStore.recordWin(getGameId());
                checkAchievement("win", game.getMoves());
                updateScore(currentScore + 100);
                showGameEndDialog(true, game.getMoves());
            });
            klotskiView.setOnMoveListener(() -> {
                if (tvMoves != null) {
                    tvMoves.setText("步数: " + game.getMoves());
                }
            });
        }

        if (tvStatus != null) {
            tvStatus.setText("滑动方块，帮助曹操逃出");
        }
        if (tvMoves != null) {
            tvMoves.setText("步数: 0");
        }
    }

    private void showHint() {
        if (isHintSearching) {
            Toast.makeText(this, "正在搜索中...", Toast.LENGTH_SHORT).show();
            return;
        }

        isHintSearching = true;
        tvStatus.setText("正在计算最优解...");
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
                            "提示搜索 " + hintMs + "ms",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
                if (hint != null) {
                    klotskiView.showHint(hint);
                    tvStatus.setText("提示: " + hint.totalSteps + "步可通关");
                } else {
                    tvStatus.setText("未找到解法");
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
        builder.setTitle(won ? "🎉 通关" : "本局结束");
        StringBuilder content = new StringBuilder();
        content.append(won ? "恭喜通关！\n\n" : "再接再厉\n\n");
        content.append("总步数: ").append(moves).append("\n");
        content.append("用时: ").append(formatDuration(elapsed));
        if (won) {
            content.append("\n\n得分 +100");
        }
        builder.setMessage(content.toString());
        builder.setPositiveButton("再来一局", (d, w) -> startNewGame());
        builder.setNegativeButton("返回主菜单", (d, w) -> finish());
        builder.setNeutralButton("排行榜", (d, w) -> showLeaderboard());
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
        StringBuilder content = new StringBuilder("最少步数通关记录（前 5）：\n\n");
        if (existing.isEmpty()) {
            content.append("暂无记录，赶快通关吧！");
        } else {
            int rank = 1;
            for (String line : existing.split("\\|")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                int moves = Integer.parseInt(parts[0]);
                long ms = Long.parseLong(parts[1]);
                content.append("#").append(rank++).append("  ")
                        .append(moves).append(" 步 / ")
                        .append(formatDuration(ms)).append("\n");
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("🏆 华容道排行榜")
                .setMessage(content.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    /** 格式化毫秒为 mm:ss */
    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L);
    }
}
