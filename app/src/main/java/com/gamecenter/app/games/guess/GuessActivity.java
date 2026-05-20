package com.gamecenter.app.games.guess;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 猜数字游戏的Activity
 *
 * <p>负责初始化游戏界面、绑定GuessView和GuessGame、
 * 处理分数/难度显示、最佳记录、重新开始和教程按钮逻辑。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用通用布局activity_game_simple，通过代码动态添加GuessView和最佳记录文本框</li>
 *   <li>GuessView不在XML布局中声明，而是运行时动态添加到FrameLayout中，
 *       替换占位的game_view_stub</li>
 *   <li>最佳记录TextView通过代码动态插入到分数文本框下方</li>
 * </ul>
 */
public class GuessActivity extends AppCompatActivity {

    /** 猜数字游戏自定义视图 */
    private GuessView guessView;
    /** 猜数字游戏逻辑对象 */
    private GuessGame game;
    /** 分数/难度显示文本框 */
    private TextView tvScore;
    /** 最佳记录显示文本框 */
    private TextView tvBest;

    /**
     * Activity创建时的初始化
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置通用布局并更新标题</li>
     *   <li>动态创建最佳记录文本框并插入到分数文本框下方</li>
     *   <li>动态创建GuessView并替换布局中的占位ViewStub</li>
     *   <li>创建GuessGame实例并绑定到GuessView</li>
     *   <li>设置重新开始和教程按钮</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_guess);

        tvScore = findViewById(R.id.tv_game_score);

        // 动态创建最佳记录文本框，插入到分数文本框下方
        tvBest = new TextView(this);
        tvBest.setTextSize(16);
        tvBest.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        android.widget.LinearLayout parent = (android.widget.LinearLayout) tvScore.getParent();
        parent.addView(tvBest, parent.indexOfChild(tvScore) + 1);

        // 动态创建GuessView，替换布局中的占位ViewStub
        guessView = new GuessView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(guessView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new GuessGame();
        guessView.setGame(game);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            guessView.resetInput();
            guessView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showGuessTutorial(this));

        updateScore();
    }

    /**
     * 更新分数和最佳记录显示
     *
     * <p>分数文本框显示当前难度和猜测次数，
     * 最佳记录文本框显示历史最少猜测次数。</p>
     */
    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText(game.getDifficultyName() + " — 第" + (game.getAttempts() + 1) + "次");
        }
        if (tvBest != null) {
            int best = game.getBestScore();
            tvBest.setText("最佳记录: " + (best > 0 ? best + "次" : "暂无"));
        }
    }
}
