package com.gamecenter.app.games.whack;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 打地鼠游戏 Activity
 *
 * 功能：
 * - 30秒打地鼠游戏
 * - 右上角设置：自定义地鼠颜色、查看历史分数
 * - 📖 教程按钮
 */
public class WhackActivity extends AppCompatActivity {

    private static final String GAME_ID = "whack";
    private WhackView whackView;
    private TextView tvScore;
    private TextView tvTime;
    private GameUsageStore usageStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whack);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_whack);

        tvScore = findViewById(R.id.tv_game_score);
        tvTime = findViewById(R.id.tv_game_time);
        usageStore = new GameUsageStore(this);

        whackView = findViewById(R.id.whack_view);
        whackView.setOnGameStateListener(new WhackView.OnGameStateListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("得分: " + score);
            }

            @Override
            public void onTimeChanged(int seconds) {
                tvTime.setText("时间: " + seconds + "秒");
            }

            @Override
            public void onGameOver(int finalScore) {
                tvScore.setText("游戏结束! 得分: " + finalScore);
                usageStore.recordScore(GAME_ID, finalScore);
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            whackView.stopGame();
            whackView.startGame();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showWhackTutorial(this));

        ImageButton btnSettings = findViewById(R.id.btn_whack_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        whackView.startGame();
    }

    /**
     * 显示打地鼠设置对话框
     * - 改变地鼠颜色
     * - 查看历史分数
     */
    private void showSettingsDialog() {
        String[] options = {"改变地鼠颜色", "历史分数"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("打地鼠设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showColorPicker();
                    } else {
                        showHistory();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 颜色选择对话框
     */
    private void showColorPicker() {
        final int[] colors = {
                0xFF8D6E63, // 棕色
                0xFF795548, // 深棕
                0xFFFF9800, // 橙色
                0xFF9C27B0, // 紫色
                0xFF2196F3, // 蓝色
                0xFF4CAF50, // 绿色
                0xFFFF5722, // 深橙
                0xFF607D8B, // 灰蓝
                0xFFE91E63, // 粉色
                0xFFFFEB3B, // 黄色
        };
        final String[] colorNames = {
                "棕色", "深棕", "橙色", "紫色", "蓝色",
                "绿色", "深橙", "灰蓝", "粉色", "黄色"
        };

        int currentColor = whackView.getMoleColor();
        int currentSelection = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == currentColor) {
                currentSelection = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择地鼠颜色")
                .setSingleChoiceItems(colorNames, currentSelection, (dialog, which) -> {
                    whackView.setMoleColor(colors[which]);
                    Toast.makeText(this, "颜色已更新: " + colorNames[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 历史分数对话框
     */
    private void showHistory() {
        SharedPreferences prefs = getSharedPreferences("whack_settings", Context.MODE_PRIVATE);
        String history = prefs.getString("score_history", "");

        if (history.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("历史分数")
                    .setMessage("暂无历史记录\n快去玩一局吧!")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        String[] entries = history.split(";");
        List<String> display = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length >= 2) {
                try {
                    int score = Integer.parseInt(parts[0]);
                    long time = Long.parseLong(parts[1]);
                    String dateStr = sdf.format(new Date(time));
                    display.add(dateStr + " — " + score + "分");
                } catch (NumberFormatException ignored) {}
            }
        }

        if (display.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("历史分数")
                    .setMessage("暂无有效历史记录")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        String[] items = display.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("历史分数 (最近20条)")
                .setItems(items, null)
                .setPositiveButton("确定", null)
                .setNeutralButton("清除历史", (dialog, which) -> {
                    prefs.edit().remove("score_history").apply();
                    Toast.makeText(this, "历史已清除", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        whackView.stopGame();
    }
}
