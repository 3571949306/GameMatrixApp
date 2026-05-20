package com.gamecenter.app.games.tic;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * 井字棋游戏 Activity
 *
 * <p>职责：作为井字棋游戏的入口界面，负责初始化游戏视图、绑定交互事件、
 * 管理胜负记录以及提供重新开始和教程功能。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>玩家先手（执X），电脑后手（执O），通过 TicGame.computerMove() 实现AI落子</li>
 *   <li>使用 FrameLayout 动态替换占位 View 为自定义 TicView，实现游戏画面自适应</li>
 *   <li>胜负记录通过 GameUsageStore 持久化，用于统计玩家表现</li>
 * </ul>
 *
 * <p>布局：res/layout/activity_game_simple.xml</p>
 */
public class TicActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于 GameUsageStore 记录胜负统计 */
    private static final String GAME_ID = "tic";

    /** 自定义游戏视图，负责绘制棋盘和处理触摸输入 */
    private TicView ticView;

    /** 游戏逻辑核心，管理棋盘状态和AI决策 */
    private TicGame game;

    /** 顶部得分/提示文本 */
    private TextView tvScore;

    /** 游戏使用统计存储，记录玩家胜负 */
    private GameUsageStore usageStore;

    /**
     * Activity 创建回调
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置布局并绑定标题</li>
     *   <li>创建 TicView 并替换布局中的占位 View</li>
     *   <li>创建 TicGame 并关联到视图</li>
     *   <li>注册游戏结束监听器，记录胜负</li>
     *   <li>绑定重新开始和教程按钮</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_tic);

        tvScore = findViewById(R.id.tv_game_score);

        ticView = new TicView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(ticView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new TicGame();
        ticView.setGame(game);
        usageStore = new GameUsageStore(this);

        ticView.setOnGameOverListener(winner -> {
            if (winner == TicGame.PLAYER) {
                usageStore.recordWin(GAME_ID);
            } else if (winner == TicGame.COMPUTER) {
                usageStore.recordLoss(GAME_ID);
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            ticView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showTicTutorial(this));

        updateScore();
    }

    /**
     * 更新顶部得分/提示文本
     *
     * <p>当前固定显示"你先下 X"作为提示，
     * 后续可扩展为显示累计胜场等统计信息。</p>
     */
    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText("你先下 X");
        }
    }
}
