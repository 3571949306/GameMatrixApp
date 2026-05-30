package com.gamecenter.app.games.breakout;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * 打砖块游戏的 Activity 控制器。
 * <p>
 * 职责：
 * <ul>
 *   <li>初始化游戏视图和 UI 控件</li>
 *   <li>监听游戏状态变化（分数、生命、游戏结束/通关）并更新 UI</li>
 *   <li>在游戏结束时记录分数到使用统计存储</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>游戏逻辑和渲染完全封装在 BreakoutView 中，Activity 仅负责 UI 状态同步</li>
 *   <li>通过 OnGameStateListener 回调接口实现 View → Activity 的单向通信</li>
 * </ul>
 */
public class BreakoutActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于分数记录 */
    private static final String GAME_ID = "breakout";

    /** 游戏自定义视图 */
    private BreakoutView breakoutView;

    /** 分数显示文本 */
    private TextView tvScore;

    /** 生命值显示文本 */
    private TextView tvLives;

    /** 游戏使用统计存储，用于记录分数 */
    private GameUsageStore usageStore;

    /**
     * Activity 创建时的初始化入口。
     * <p>
     * 绑定视图、设置游戏状态监听器、配置重启按钮，
     * 延迟 500ms 后自动开始游戏（等待视图布局完成）。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breakout);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_breakout);

        tvScore = findViewById(R.id.tv_game_score);
        tvLives = findViewById(R.id.tv_game_lives);
        usageStore = new GameUsageStore(this);

        breakoutView = findViewById(R.id.breakout_view);
        breakoutView.setOnGameStateListener(new BreakoutView.OnGameStateListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("得分: " + score);
            }

            @Override
            public void onLivesChanged(int lives) {
                tvLives.setText("生命: " + lives);
            }

            @Override
            public void onGameOver(int score) {
                tvScore.setText("游戏结束! 得分: " + score);
                usageStore.recordScore(GAME_ID, score);
            }

            @Override
            public void onGameWon(int score) {
                Toast.makeText(BreakoutActivity.this, "恭喜通关!", Toast.LENGTH_LONG).show();
                usageStore.recordScore(GAME_ID, score);
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> breakoutView.resetGame());

        // 延迟启动游戏，确保视图已完成布局测量
        breakoutView.postDelayed(() -> breakoutView.startGame(), 500);
    }
}
