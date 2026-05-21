package com.gamecenter.app.games.checkers;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * 跳棋（国际跳棋/Checkers）游戏的主界面 Activity。
 *
 * <p>职责：
 * <ul>
 *   <li>初始化棋盘视图（{@link CheckersView}）和界面控件</li>
 *   <li>监听游戏状态变化（玩家轮换、游戏结束），更新状态文本和弹出提示</li>
 *   <li>通过 {@link GameUsageStore} 记录胜负数据，用于统计和成就系统</li>
 *   <li>提供重新开始和教程入口</li>
 * </ul>
 *
 * <p>设计决策：
 * <ul>
 *   <li>棋盘逻辑和渲染全部委托给 {@link CheckersView}，本类仅负责 UI 编排和状态展示</li>
 *   <li>黑方（player=0）始终先手，与 {@link CheckersView} 的约定一致</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是跳棋人机对战的主屏幕，结构和其他棋类的Activity类似。
 * 跳棋（Checkers）是国际跳棋，和中国跳棋不同：
 * - 在8×8棋盘的深色格子上进行
 * - 每方12颗棋子，只能斜着走
 * - 跳过对方棋子可以吃掉它，连跳时必须继续跳
 * - 到达对方底线升变为王棋，可以前后移动
 */
public class CheckersActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于胜负记录和统计 */
    private static final String GAME_ID = "checkers";

    /** 棋盘自定义视图，承载全部游戏逻辑和绘制 */
    private CheckersView checkersView;

    /** 状态文本，显示当前回合或胜负信息 */
    private TextView tvStatus;

    /** 标记游戏是否已结束，防止结束后仍更新状态文本 */
    private boolean gameOver = false;

    /** 游戏使用记录存储，用于持久化胜负数据 */
    private GameUsageStore usageStore;

    /**
     * Activity 创建回调。初始化布局、视图引用、事件监听器。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkers);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_checkers);

        tvStatus = findViewById(R.id.tv_game_status);

        checkersView = findViewById(R.id.checkers_view);
        usageStore = new GameUsageStore(this);

        // 注册游戏状态监听器，响应玩家轮换和游戏结束事件
        checkersView.setOnGameStateListener(new CheckersView.OnGameStateListener() {
            /**
             * 玩家轮换回调。仅在游戏未结束时更新状态文本。
             *
             * @param player 当前玩家编号：0=黑方，1=白方
             */
            @Override
            public void onPlayerChanged(int player) {
                if (!gameOver) {
                    tvStatus.setText(player == 0 ? "黑方回合" : "白方回合");
                }
            }

            /**
             * 游戏结束回调。更新状态文本、弹出 Toast 提示，并记录胜负。
             *
             * @param winner 获胜方编号：0=黑方，1=白方
             */
            @Override
            public void onGameOver(int winner) {
                gameOver = true;
                String winnerName = (winner == 0) ? "黑方" : "白方";
                tvStatus.setText(winnerName + "获胜!");
                Toast.makeText(CheckersActivity.this, winnerName + "获胜!", Toast.LENGTH_LONG).show();

                // 黑方代表玩家，白方代表AI/对手，据此记录胜负
                if (winner == 0) {
                    usageStore.recordWin(GAME_ID);
                } else {
                    usageStore.recordLoss(GAME_ID);
                }
            }
        });

        // 重新开始按钮：重置游戏状态和界面
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            gameOver = false;
            checkersView.reset();
            tvStatus.setText("黑方回合");
        });

        // 教程按钮：显示跳棋游戏教程
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showCheckersTutorial(this));

        // 初始状态：黑方先手
        tvStatus.setText("黑方回合");
    }
}
