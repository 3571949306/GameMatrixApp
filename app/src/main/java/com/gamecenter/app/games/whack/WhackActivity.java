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
import android.util.Log;
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
 * <p>管理打地鼠游戏的 UI 交互、游戏状态回调和设置功能。
 * 作为 MVC 中的 Controller，协调 {@link WhackView}（视图+逻辑）与 UI 控件。</p>
 *
 * <p>功能：
 * <ul>
 *   <li>30 秒打地鼠游戏，实时显示得分和剩余时间</li>
 *   <li>设置对话框：自定义地鼠颜色、查看历史分数</li>
 *   <li>教程按钮：展示游戏玩法说明</li>
 *   <li>游戏结束时自动记录分数到历史</li>
 * </ul>
 * </p>
 */
public class WhackActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于统计 */
    private static final String GAME_ID = "whack";
    /** 日志标签 */
    private static final String TAG = "WhackActivity";
    /** 打地鼠自定义视图 */
    private WhackView whackView;
    /** 得分文本显示 */
    private TextView tvScore;
    /** 剩余时间文本显示 */
    private TextView tvTime;
    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;

    /**
     * Activity 创建回调
     *
     * <p>初始化视图、游戏状态监听器和所有按钮事件。
     * 游戏状态监听器负责实时更新得分、时间和游戏结束的 UI 显示。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
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
        // 设置游戏状态回调：得分变化、时间变化、游戏结束
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

        // 重新开始按钮
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            whackView.stopGame();
            whackView.startGame();
        });

        // 教程按钮
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showWhackTutorial(this));

        // 设置按钮（地鼠颜色、历史分数）
        ImageButton btnSettings = findViewById(R.id.btn_whack_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        whackView.startGame();
    }

    /**
     * 显示打地鼠设置对话框
     *
     * <p>提供两个选项：
     * <ul>
     *   <li>改变地鼠颜色：打开颜色选择器</li>
     *   <li>历史分数：查看历史得分记录</li>
     * </ul>
     * </p>
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
     * 显示地鼠颜色选择对话框
     *
     * <p>提供 10 种预设颜色供选择，当前选中的颜色会被预选。
     * 选择后立即更新地鼠颜色并持久化保存到 SharedPreferences。</p>
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

        // 查找当前颜色在选项中的索引
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
     * 显示历史分数对话框
     *
     * <p>从 SharedPreferences 读取历史分数记录，解析后按时间倒序显示。
     * 每条记录格式为 "分数,时间戳"，用分号分隔。
     * 最多显示最近 20 条记录，并提供清除历史功能。</p>
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

        // 解析历史记录：格式为 "分数,时间戳;分数,时间戳;..."
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
                } catch (NumberFormatException ignored) { Log.w(TAG, "Invalid number format: " + ignored.getMessage()); }
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

    /**
     * Activity 销毁回调
     *
     * <p>停止游戏并释放视图资源，避免内存泄漏。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        whackView.stopGame();
        whackView.releaseResources();
        whackView = null;
    }
}
