package com.gamecenter.app.games.gomoku;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 五子棋人机对战Activity。
 * <p>
 * 负责管理五子棋单局对战的完整生命周期，包括：
 * <ul>
 *   <li>难度选择（1~6级，对应不同AI思考时间）</li>
 *   <li>玩家（黑方）落子交互</li>
 *   <li>AI（白方）异步计算与落子</li>
 *   <li>悔棋功能（同时撤销玩家和AI各一手）</li>
 *   <li>胜负统计记录</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>AI计算在单独线程池中执行，通过Handler延迟300ms回传结果</li>
 *   <li>难度通过SeekBar选择，影响AI的搜索时间限制</li>
 *   <li>悔棋按"一手"为单位，每次撤销玩家+AI共两手棋</li>
 * </ul>
 */
public class GomokuActivity extends AppCompatActivity {

    /** 游戏标识，用于胜负统计 */
    private static final String GAME_ID = "gomoku";

    /** 棋盘视图组件 */
    private GomokuView gomokuView;

    /** 难度选择面板 */
    private LinearLayout difficultyPanel;

    /** 游戏控制面板（悔棋、重开等按钮） */
    private LinearLayout controlPanel;

    /** 难度标签文本 */
    private TextView tvDifficultyLabel;

    /** 难度选择滑块 */
    private SeekBar seekDifficulty;

    /** 五子棋游戏逻辑对象 */
    private GomokuGame game;

    /** AI决策引擎 */
    private GomokuAI ai;

    /** AI执子颜色（固定为白方） */
    private int aiPlayer = GomokuGame.WHITE;

    /** 当前AI难度等级（1~6） */
    private int aiDifficulty = 3;

    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;

    /** 主线程Handler */
    private Handler mainHandler;

    /** AI计算专用线程池 */
    private ExecutorService aiExecutor;

    /** AI是否正在思考的标志位 */
    private volatile boolean aiThinking = false;

    /** 难度名称数组，与SeekBar进度对应 */
    private static final String[] DIFFICULTY_NAMES = {
        "初识五子棋", "初级棋手", "入门学生", "中等棋力", "高手水平", "精湛大师"
    };

    /**
     * Activity创建时的初始化入口。
     * <p>
     * 初始化游戏对象、AI引擎、视图绑定和事件监听器。
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gomoku);

        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        gomokuView = findViewById(R.id.gomoku_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        seekDifficulty = findViewById(R.id.seek_difficulty);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);

        game = new GomokuGame();
        ai = new GomokuAI(aiDifficulty);
        gomokuView.setGame(game);
        usageStore = new GameUsageStore(this);

        gomokuView.setOnCellClickListener((x, y) -> handleCellClick(x, y));
        gomokuView.setOnGameOverListener(this::handleGameOver);

        seekDifficulty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                aiDifficulty = progress + 1;
                if (tvDifficultyLabel != null) {
                    tvDifficultyLabel.setText("难度：" + DIFFICULTY_NAMES[progress]
                            + " (" + aiDifficulty + "/6)");
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame(aiDifficulty));
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, GomokuOnlineActivity.class);
            startActivity(intent);
        });
    }

    /**
     * 开始游戏，根据选择的难度创建AI引擎。
     *
     * @param difficulty AI难度等级（1~6）
     */
    private void startGame(int difficulty) {
        aiDifficulty = difficulty;
        ai = new GomokuAI(difficulty);
        game.reset();
        gomokuView.setGame(game);
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        gomokuView.invalidate();
    }

    /**
     * 处理玩家点击棋盘的落子操作。
     * <p>
     * 仅在黑方回合且AI未思考时响应。玩家落子后检查胜负，
     * 若未结束则异步触发AI计算，AI完成后延迟300ms落子。
     *
     * @param x 横坐标（列索引）
     * @param y 纵坐标（行索引）
     */
    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        // 仅允许黑方（玩家）手动落子
        if (game.getCurrentPlayer() != GomokuGame.BLACK) return;
        if (aiThinking) return;
        if (!game.isValidMove(x, y)) return;

        game.makeMove(x, y, GomokuGame.BLACK);
        game.switchPlayer();
        gomokuView.invalidate();

        // 玩家落子后检查是否已获胜
        if (game.checkGameOver()) {
            gomokuView.invalidate();
            return;
        }

        aiThinking = true;
        gomokuView.clearHover();
        gomokuView.invalidate();
        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(game, aiPlayer);
            // 延迟300ms落子，模拟AI思考过程
            mainHandler.postDelayed(() -> {
                if (bestMove != null) {
                    game.makeMove(bestMove[0], bestMove[1], GomokuGame.WHITE);
                    game.switchPlayer();
                    game.checkGameOver();
                }
                aiThinking = false;
                gomokuView.invalidate();
            }, 300);
        });
    }

    /**
     * 处理悔棋操作。
     * <p>
     * 每次悔棋撤销玩家和AI各一手（共两手棋）。
     * AI思考期间禁止悔棋。
     */
    private void handleUndo() {
        if (aiThinking) return;
        int undoCount = game.undoLastMoves(1);
        if (undoCount > 0) {
            gomokuView.invalidate();
        }
    }

    /**
     * 处理重新开始操作。
     * <p>
     * 重置游戏状态，返回难度选择界面。
     */
    private void handleRestart() {
        aiThinking = false;
        game.reset();
        gomokuView.clearHover();
        gomokuView.setGame(game);
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        gomokuView.invalidate();
    }

    /**
     * 游戏结束回调，记录胜负统计。
     *
     * @param winner 获胜方（BLACK/WHITE），null表示平局
     */
    private void handleGameOver(Integer winner) {
        if (winner != null && winner == GomokuGame.BLACK) {
            usageStore.recordWin(GAME_ID);
        } else if (winner != null && winner == GomokuGame.WHITE) {
            usageStore.recordLoss(GAME_ID);
        }
    }

    /**
     * 返回键处理：游戏中返回难度选择，否则正常退出。
     */
    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            handleRestart();
        } else {
            super.onBackPressed();
        }
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
