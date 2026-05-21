package com.gamecenter.app.games.go;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 围棋人机对战Activity。
 * <p>
 * 负责管理围棋单局对战的完整生命周期，包括：
 * <ul>
 *   <li>玩家（黑方）落子交互</li>
 *   <li>AI（白方）异步计算与落子</li>
 *   <li>虚手（Pass）操作与双方连续虚手判负</li>
 *   <li>对局状态显示与重新开始</li>
 * </ul>
 * <p>
 * 关键设计决策：AI计算在单独的线程池中执行，避免阻塞UI线程；
 * 通过 {@link Handler#postDelayed} 添加最小响应延迟，使AI落子有"思考"的视觉效果。
 *
 * 【初学者指南】
 * 这个类是围棋人机对战的主屏幕，和五子棋的GomokuActivity结构类似。
 * 主要区别：围棋有"虚手"（Pass）操作——当你觉得无棋可下时可以选择跳过，
 * 双方连续虚手则对局结束。围棋的AI更复杂，使用蒙特卡洛模拟来决策。
 */
public class GoActivity extends AppCompatActivity {

    private static final long AI_MIN_RESPONSE_DELAY_MS = 120L;

    /** 棋盘视图组件，负责绘制棋盘和棋子 */
    private GoView goView;

    /** 控制面板布局（包含虚手、重开等按钮） */
    private LinearLayout controlPanel;

    /** 状态文本，显示当前回合或对局结果 */
    private TextView tvStatus;

    /** 围棋游戏逻辑核心对象 */
    private GoGame game;

    /** UI线程Handler，用于将AI落子结果投递回主线程 */
    private Handler uiHandler;

    /** AI计算专用单线程执行器，确保AI计算串行执行不冲突 */
    private ExecutorService aiExecutor;

    /** AI是否正在思考的标志位，volatile保证多线程可见性 */
    private volatile boolean aiThinking = false;

    /**
     * Activity创建时的初始化入口。
     * <p>
     * 初始化游戏对象、视图绑定、事件监听器，并设置AI执行器。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        goView = findViewById(R.id.go_view);
        controlPanel = findViewById(R.id.control_panel);
        tvStatus = findViewById(R.id.tv_status);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_go);

        game = new GoGame();
        goView.setGame(game);
        goView.setOnCellClickListener(this::handleCellClick);

        findViewById(R.id.btn_pass).setOnClickListener(v -> handlePass());
        findViewById(R.id.btn_hint).setOnClickListener(v -> handleHint());
        findViewById(R.id.btn_restart).setOnClickListener(v -> restart());
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGoTutorial(this));
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGoTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, GoOnlineActivity.class);
            startActivity(intent);
        });

        updateStatus();
    }

    /**
     * 处理玩家点击棋盘交叉点的落子操作。
     * <p>
     * 仅在黑方回合且AI未思考时响应。落子后切换到白方，
     * 并异步触发AI计算。AI计算完成后按最小响应延迟执行落子。
     *
     * @param x 棋盘横坐标（列索引）
     * @param y 棋盘纵坐标（行索引）
     */
    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        // 仅允许黑方（玩家）手动落子
        if (game.getCurrentPlayer() != GoGame.BLACK) return;
        // AI思考期间禁止玩家操作，防止状态冲突
        if (aiThinking) return;

        if (!game.isValidMove(x, y)) {
            Toast.makeText(this, "此处不可落子", Toast.LENGTH_SHORT).show();
            return;
        }

        game.makeMove(x, y);
        goView.clearHint();
        game.switchPlayer();
        goView.invalidate();
        updateStatus();

        aiThinking = true;
        final long startMs = System.currentTimeMillis();
        aiExecutor.execute(() -> {
            int[] move = game.getBestMove();
            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(AI_MIN_RESPONSE_DELAY_MS - elapsed, 0L);
            Runnable applyMove = () -> {
                if (move != null) {
                    game.makeMove(move[0], move[1]);
                } else {
                    game.pass();
                    Toast.makeText(this, "AI 弃权一手", Toast.LENGTH_SHORT).show();
                }
                game.switchPlayer();
                aiThinking = false;
                goView.invalidate();
                updateStatus();
            };
            if (delay > 0L) {
                uiHandler.postDelayed(applyMove, delay);
            } else {
                uiHandler.post(applyMove);
            }
        });
    }

    /**
     * 处理玩家虚手（Pass）操作。
     * <p>
     * 玩家虚手后切换到AI回合，AI同样可能虚手。
     * 双方连续虚手时对局结束。
     */
    private void handlePass() {
        if (game.isGameOver()) return;
        if (aiThinking) return;
        game.pass();
        goView.clearHint();
        game.switchPlayer();
        goView.invalidate();
        updateStatus();

        // 双方连续虚手则对局结束，不再触发AI
        if (game.isGameOver()) return;

        aiThinking = true;
        final long startMs = System.currentTimeMillis();
        aiExecutor.execute(() -> {
            int[] move = game.getBestMove();
            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(AI_MIN_RESPONSE_DELAY_MS - elapsed, 0L);
            Runnable applyMove = () -> {
                if (move != null) {
                    game.makeMove(move[0], move[1]);
                } else {
                    game.pass();
                }
                game.switchPlayer();
                aiThinking = false;
                goView.invalidate();
                updateStatus();
            };
            if (delay > 0L) {
                uiHandler.postDelayed(applyMove, delay);
            } else {
                uiHandler.post(applyMove);
            }
        });
    }

    /**
     * 更新状态栏文本。
     * <p>
     * 对局中显示当前回合方，对局结束时显示双方吃子数。
     */
    private void updateStatus() {
        if (game.isGameOver()) {
            tvStatus.setText(game.getResultText());
            return;
        }
        tvStatus.setText(game.getCurrentPlayer() == GoGame.BLACK ? "黑方回合" : "白方回合 (AI)");
    }

    /**
     * 重新开始对局。
     * <p>
     * 重置游戏状态和AI思考标志，刷新视图。
     */
    private void restart() {
        aiThinking = false;
        game.reset();
        goView.setGame(game);
        goView.clearHint();
        goView.invalidate();
        updateStatus();
    }

    private void handleHint() {
        if (game.isGameOver() || aiThinking || game.getCurrentPlayer() != GoGame.BLACK) return;
        aiExecutor.execute(() -> {
            int[] hint = game.getBestMove();
            uiHandler.post(() -> {
                if (hint != null && !game.isGameOver() && game.getCurrentPlayer() == GoGame.BLACK) {
                    goView.showHint(hint[0], hint[1]);
                    Toast.makeText(this, "建议落子: " + (hint[0] + 1) + "," + (hint[1] + 1), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "当前建议虚手", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Activity销毁时关闭AI线程池，防止线程泄漏。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiExecutor.shutdownNow();
    }
}
