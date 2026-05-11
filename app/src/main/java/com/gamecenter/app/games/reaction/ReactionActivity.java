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
 *
 * 玩法：
 * 1. 屏幕变红后等待随机时间
 * 2. 变绿后立即点击
 * 3. 查看你的反应时间(ms)
 * 4. 完成5轮后显示平均成绩
 *
 * 布局：res/layout/activity_reaction.xml
 */
public class ReactionActivity extends AppCompatActivity {

    private static final String PREFS = "reaction_prefs";
    private static final String KEY_BEST = "best_time";

    private ReactionView reactionView;
    private ReactionGame game;
    private Handler handler;
    private Runnable timeoutRunnable;
    private TextView tvScore;
    private TextView tvBest;

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
        timeoutRunnable = game::onTimeout;

        game.setOnStateChangeListener(state -> {
            reactionView.refresh();
            updateScore();
            if (state == ReactionGame.State.WAITING) {
                scheduleTimeout();
            }
        });

        reactionView.setOnStateChangeListener(() -> {
            updateScore();
            if (game.getState() == ReactionGame.State.WAITING) {
                scheduleTimeout();
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            cancelTimeout();
            reactionView.refresh();
            updateScore();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showReactionTutorial(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void scheduleTimeout() {
        cancelTimeout();
        long delay = game.getWaitingDelay();
        handler.postDelayed(timeoutRunnable, delay);
    }

    private void cancelTimeout() {
        if (handler != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
    }

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
                if (ms < getBestTime()) {
                    saveBestTime((int) ms);
                }
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

    private int getBestTime() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_BEST, 0);
    }

    private void saveBestTime(int time) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_BEST, time)
                .apply();
    }
}
