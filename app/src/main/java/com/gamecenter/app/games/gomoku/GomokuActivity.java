package com.gamecenter.app.games.gomoku;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
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
 *   <li>难度选择（低 / 中 / 高 / 大师，对应不同AI思考时间）</li>
 *   <li>玩家（黑方）落子交互</li>
 *   <li>AI（白方）异步计算与落子</li>
 *   <li>悔棋功能（同时撤销玩家和AI各一手）</li>
 *   <li>胜负统计记录</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>AI计算在单独线程池中执行，通过Handler延迟300ms回传结果</li>
 *   <li>难度通过按钮直接选择，影响AI的搜索时间限制</li>
 *   <li>悔棋按"一手"为单位，每次撤销玩家+AI共两手棋</li>
 * </ul>
 *
 * 【初学者指南】
 * Activity是Android中的"页面"，你可以把它理解为游戏的一个屏幕。
 * 这个类就是五子棋人机对战的主屏幕，负责把棋盘、按钮、难度选择等组合在一起。
 * 它就像一个"指挥官"：自己不下棋，但协调棋盘视图(GomokuView)、游戏规则(GomokuGame)和AI大脑(GomokuAI)协同工作。
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

    /** 五子棋游戏逻辑对象 */
    private GomokuGame game;

    /** AI决策引擎 */
    private GomokuAI ai;

    /** AI执子颜色（固定为白方） */
    private int aiPlayer = GomokuGame.WHITE;

    /** 当前AI难度等级（1~4） */
    private int aiDifficulty = 2;

    private static final int MAX_AI_DIFFICULTY = 4;

    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;

    // 主线程Handler：就像一个"信使"，负责把AI的计算结果从后台线程送到主线程（UI线程）
    // 因为Android规定：只有主线程才能修改界面，后台线程不能直接改界面
    private Handler mainHandler;

    // AI计算专用线程池：就像给AI单独开了一个"办公室"，在里面专心计算不会卡住界面
    // 如果AI在主线程计算，界面就会冻住（卡顿），用户体验很差
    private ExecutorService aiExecutor;

    // AI是否正在思考的标志位
    // volatile关键字保证：一个线程改了这个值，其他线程能立刻看到最新值
    // 就像一块"公共黑板"，谁都能看到上面的内容，而且修改后立刻生效
    private volatile boolean aiThinking = false;

    /** 难度名称数组，与 1-4 档按钮对应 */
    private static final String[] DIFFICULTY_NAMES = {
        "低", "中", "高", "大师"
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

        // 创建主线程信使和AI专用线程池
        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        // 找到布局中的各个UI组件（就像从图纸上找到各个零件的位置）
        gomokuView = findViewById(R.id.gomoku_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);

        // 创建游戏逻辑对象和AI引擎
        game = new GomokuGame();
        ai = new GomokuAI(aiDifficulty);
        gomokuView.setGame(game);
        usageStore = new GameUsageStore(this);

        // 设置棋盘点击监听器：玩家点击棋盘时调用handleCellClick方法
        gomokuView.setOnCellClickListener((x, y) -> handleCellClick(x, y));
        // 设置游戏结束监听器：游戏结束时调用handleGameOver方法
        gomokuView.setOnGameOverListener(this::handleGameOver);

        setupDifficultyButtons();

        // 绑定各个按钮的点击事件
        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame(aiDifficulty));
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_hint).setOnClickListener(v -> handleHint());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        // 跳转到联机对战界面
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, GomokuOnlineActivity.class);
            startActivity(intent);
        });
    }

    private void setupDifficultyButtons() {
        int[] ids = {
                R.id.btn_difficulty_1,
                R.id.btn_difficulty_2,
                R.id.btn_difficulty_3,
                R.id.btn_difficulty_4
        };
        for (int i = 0; i < ids.length; i++) {
            final int difficulty = i + 1;
            View button = findViewById(ids[i]);
            if (button != null) {
                button.setOnClickListener(v -> selectDifficulty(difficulty));
            }
        }
        selectDifficulty(aiDifficulty);
    }

    private void selectDifficulty(int difficulty) {
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        if (tvDifficultyLabel != null) {
            tvDifficultyLabel.setText("难度：" + DIFFICULTY_NAMES[aiDifficulty - 1]
                    + " (" + aiDifficulty + "/" + MAX_AI_DIFFICULTY + ")");
        }
    }

    /**
     * 开始游戏，根据选择的难度创建AI引擎。
     *
     * @param difficulty AI难度等级（1~4）
     */
    private void startGame(int difficulty) {
        aiDifficulty = difficulty;
        // 每次开始新游戏都重新创建AI，因为难度可能变了
        ai = new GomokuAI(difficulty);
        game.reset();
        gomokuView.setGame(game);
        // 切换界面：隐藏难度选择面板，显示游戏控制面板
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
        // 一系列前置检查：游戏是否结束？是否轮到玩家？AI是否在思考？落子是否合法？
        if (game.isGameOver()) return;
        // 仅允许黑方（玩家）手动落子
        if (game.getCurrentPlayer() != GomokuGame.BLACK) return;
        if (aiThinking) return;
        if (!game.isValidMove(x, y)) return;

        // 玩家落子，然后切换到AI回合
        game.makeMove(x, y, GomokuGame.BLACK);
        game.switchPlayer();
        gomokuView.invalidate();

        // 玩家落子后检查是否已获胜
        if (game.checkGameOver()) {
            gomokuView.invalidate();
            return;
        }

        // 标记AI正在思考，此时玩家不能再操作
        aiThinking = true;
        gomokuView.clearHover();
        gomokuView.invalidate();
        // 把AI计算任务提交到专用线程池（不在主线程计算，避免界面卡顿）
        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(game, aiPlayer);
            // 延迟300ms落子，模拟AI思考过程
            // 就像真人下棋需要思考一会儿，AI也"假装"想了一下
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
        // AI正在思考时不允许悔棋，避免状态混乱
        if (aiThinking) return;
        // undoLastMoves(1)表示撤销1"手"（包含玩家和AI各一手）
        int undoCount = game.undoLastMoves(1);
        if (undoCount > 0) {
            gomokuView.invalidate();
        }
    }

    private void handleHint() {
        if (game.isGameOver() || aiThinking || game.getCurrentPlayer() != GomokuGame.BLACK) return;
        aiExecutor.execute(() -> {
            int[] hint = ai.getBestMove(game, GomokuGame.BLACK);
            mainHandler.post(() -> {
                if (hint != null && !game.isGameOver() && game.getCurrentPlayer() == GomokuGame.BLACK) {
                    gomokuView.showHint(hint[0], hint[1]);
                }
            });
        });
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
        // 切换界面：显示难度选择面板，隐藏游戏控制面板
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
            // 玩家（黑方）赢了，记录一次胜利
            usageStore.recordWin(GAME_ID);
        } else if (winner != null && winner == GomokuGame.WHITE) {
            // AI（白方）赢了，记录一次失败
            usageStore.recordLoss(GAME_ID);
        }
    }

    /**
     * 返回键处理：游戏中返回难度选择，否则正常退出。
     */
    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            // 如果正在游戏中，按返回键回到难度选择界面（而不是退出）
            handleRestart();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Activity销毁时关闭AI线程池，防止线程泄漏。
     * 就像离开房间要关灯一样，不用了就要关掉，否则会浪费资源
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiExecutor.shutdownNow();
    }
}
