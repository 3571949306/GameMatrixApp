package com.gamecenter.app.games.reaction;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 反应力挑战 Activity
 * <p>
 * 玩法：
 * 1. 屏幕变红后等待随机时间
 * 2. 变绿后立即点击
 * 3. 查看你的反应时间(ms)
 * 4. 完成5轮后显示平均成绩
 * <p>
 * 职责：
 * - 管理 ReactionGame 和 ReactionView 的生命周期与交互
 * - 通过 Handler 调度延迟任务（等待→变绿的超时机制）
 * - 使用 SharedPreferences 持久化最佳反应时间记录
 * - 根据游戏状态更新 UI 文字（得分、提示、结果）
 * <p>
 * 布局：res/layout/activity_reaction.xml
 */
public class ReactionActivity extends AppCompatActivity {

    /** SharedPreferences 文件名，用于存储最佳反应时间 */
    private static final String PREFS = "reaction_prefs";
    /** 最佳反应时间的存储键 */
    private static final String KEY_BEST = "best_time";

    private ReactionView reactionView;
    private ReactionGame game;
    /** 主线程 Handler，用于调度延迟变绿的 Runnable */
    private Handler handler;
    /** 延迟变绿的 Runnable，到达等待时间后将游戏状态切换为 READY */
    private Runnable timeoutRunnable;
    /** 当前轮次得分文字 */
    private TextView tvScore;
    /** 最佳记录文字 */
    private TextView tvBest;

    /**
     * Activity 创建时初始化视图、游戏逻辑和事件监听
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reaction);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_reaction);

        tvScore = findViewById(R.id.tv_game_score);
        tvBest = findViewById(R.id.tv_best_score);
        reactionView = findViewById(R.id.reaction_view);

        game = new ReactionGame();
        reactionView.setGame(game);

        handler = new Handler(Looper.getMainLooper());
        // 超时回调：等待时间结束后将状态切换为 READY（变绿）
        timeoutRunnable = game::onTimeout;

        // 监听游戏状态变化，更新视图和得分显示
        game.setOnStateChangeListener(state -> {
            reactionView.refresh();
            updateScore();
            if (state == ReactionGame.State.WAITING) {
                // 进入等待状态时，调度延迟变绿任务
                scheduleTimeout();
            }
        });

        // 监听视图触摸事件后的状态变化
        reactionView.setOnStateChangeListener(() -> {
            updateScore();
            if (game.getState() == ReactionGame.State.WAITING) {
                scheduleTimeout();
            }
        });

        // 重新开始按钮：重置游戏状态并取消待执行的延迟任务
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            cancelTimeout();
            reactionView.refresh();
            updateScore();
        });

        // 教程按钮：显示反应力游戏玩法说明
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showReactionTutorial(this));
    }

    /**
     * Activity 销毁时清理 Handler 回调，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * 调度延迟变绿任务
     * <p>
     * 先取消之前的延迟任务，再根据游戏逻辑返回的随机等待时间重新调度。
     * 等待时间到达后，timeoutRunnable 会调用 game.onTimeout() 将状态切换为 READY。
     */
    private void scheduleTimeout() {
        cancelTimeout();
        long delay = game.getWaitingDelay();
        handler.postDelayed(timeoutRunnable, delay);
    }

    /**
     * 取消待执行的延迟变绿任务
     */
    private void cancelTimeout() {
        if (handler != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
    }

    /**
     * 根据当前游戏状态更新得分和提示文字
     * <p>
     * 各状态显示内容：
     * - IDLE：提示点击开始
     * - WAITING：提示等待变绿
     * - READY：提示立即点击
     * - TAPPED：显示本轮反应时间，5轮完成后显示平均和最佳成绩
     * - TOO_SOON：提示点击过早
     */
    private void updateScore() {
        int round = game.getRound();
        ReactionGame.State state = game.getState();

        switch (state) {
            case IDLE:
                tvScore.setText("点击屏幕开始");
                break;
            case WAITING:
                tvScore.setText("等待... 准备反应!");
                break;
            case READY:
                tvScore.setText("🟢 点击!!!");
                break;
            case TAPPED:
                long ms = game.getCurrentResult();
                tvScore.setText("第" + round + "轮: " + ms + " ms");
                // 如果本轮成绩优于历史最佳，则更新记录
                if (ms < getBestTime()) {
                    saveBestTime((int) ms);
                }
                // 5轮完成后显示汇总统计
                if (round >= 5) {
                    tvScore.setText("5轮完成! 平均: " + Math.round(game.getAverage()) + " ms | 最好: " + Math.round(game.getBest()) + " ms");
                }
                break;
            case TOO_SOON:
                tvScore.setText("⚠️ 太早了! 等绿色再点");
                break;
        }

        int best = getBestTime();
        tvBest.setText("最好记录: " + (best > 0 ? best + " ms" : "暂无"));
    }

    /**
     * 从 SharedPreferences 读取历史最佳反应时间
     *
     * @return 最佳反应时间（毫秒），无记录时返回 0
     */
    private int getBestTime() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_BEST, 0);
    }

    /**
     * 将新的最佳反应时间保存到 SharedPreferences
     *
     * @param time 反应时间（毫秒）
     */
    private void saveBestTime(int time) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_BEST, time)
                .apply();
    }
}
