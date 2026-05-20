package com.gamecenter.app.games.dice;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 骰子对战游戏的 Activity 控制器。
 * <p>
 * 职责：
 * <ul>
 *   <li>初始化游戏视图（DiceView）和游戏逻辑（DiceGame）</li>
 *   <li>处理重启和教程按钮事件</li>
 *   <li>同步显示对战比分信息</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用通用布局 activity_game_simple，通过 FrameLayout 动态添加 DiceView，
 *       而非在布局文件中静态声明，以复用通用模板</li>
 *   <li>DiceView 和 DiceGame 分离，Activity 作为中间协调层</li>
 * </ul>
 */
public class DiceActivity extends AppCompatActivity {

    /** 骰子游戏自定义视图 */
    private DiceView diceView;

    /** 骰子游戏逻辑对象 */
    private DiceGame game;

    /** 比分显示文本 */
    private TextView tvScore;

    /**
     * Activity 创建时的初始化入口。
     * <p>
     * 使用通用布局模板，动态替换游戏视图占位符为 DiceView 实例，
     * 绑定重启和教程按钮，初始化比分显示。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_dice);

        tvScore = findViewById(R.id.tv_game_score);

        // 动态创建 DiceView 并替换布局中的占位符
        diceView = new DiceView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(diceView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new DiceGame();
        diceView.setGame(game);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            diceView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showDiceTutorial(this));

        updateScore();
    }

    /**
     * 更新比分显示文本。
     * <p>
     * 显示格式：玩家胜场、AI 胜场、平局数、当前局数。
     */
    private void updateScore() {
        if (tvScore != null && game != null) {
            tvScore.setText("你 " + game.getPlayerWins() + "胜  "
                    + game.getAiWins() + "负  " + game.getDraws() + "平  第" + (game.getRound() + 1) + "局");
        }
    }
}
