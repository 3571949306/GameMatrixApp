package com.gamecenter.app.games.chinesechess;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋人机对战主界面 Activity。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理游戏生命周期：难度选择 → 对局进行 → 胜负判定</li>
 *   <li>协调玩家交互与AI回合的切换，通过 {@link ExecutorService} 在后台线程执行AI搜索，
 *       避免阻塞UI线程</li>
 *   <li>维护选中棋子与合法走法的高亮状态</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>AI搜索使用单线程线程池（{@code Executors.newSingleThreadExecutor()}），
 *       保证同一时刻只有一个AI搜索任务在运行</li>
 *   <li>使用 {@code volatile boolean isProcessing} 标记防止玩家在AI思考期间操作棋盘</li>
 *   <li>棋子移动采用动画过渡（{@link ChineseChessView#animateMove}），
 *       动画完成后才真正执行走棋逻辑</li>
 * </ul>
 */
public class ChineseChessActivity extends AppCompatActivity {

    /** 游戏标识符，用于使用统计记录 */
    private static final String GAME_ID = "chinese_chess";

    /** 棋盘自定义视图，负责渲染棋盘、棋子及交互高亮 */
    private ChineseChessView chessView;

    /** 难度选择面板（开局前显示） */
    private LinearLayout difficultyPanel;

    /** 游戏控制面板（对局中显示，含悔棋/重开等按钮） */
    private LinearLayout controlPanel;

    /** 状态文本：显示当前回合、胜负等信息 */
    private TextView tvStatus;

    /** 难度滑块控件 */
    private SeekBar seekDifficulty;

    /** 难度标签文本 */
    private TextView tvDifficultyLabel;

    /** 游戏逻辑核心对象 */
    private ChineseChessGame game;

    /** AI引擎对象 */
    private ChineseChessAI ai;

    /** 当前AI难度等级（1~6） */
    private int aiDifficulty = 3;

    /** UI线程Handler，用于从AI后台线程切换回主线程更新界面 */
    private Handler uiHandler;

    /** AI搜索专用单线程线程池 */
    private ExecutorService aiExecutor;

    /**
     * 是否正在处理中（AI思考或走棋动画播放期间）。
     * 使用 volatile 保证多线程可见性。
     */
    private volatile boolean isProcessing = false;

    /** 当前选中的棋子坐标 [col, row]，null 表示未选中 */
    private int[] selectedPos = null;

    /** 当前选中棋子的合法走法列表，每个元素为 [toCol, toRow] */
    private List<int[]> currentValidMoves = null;

    /** 游戏使用统计存储，用于记录胜/负次数 */
    private GameUsageStore usageStore;

    /** 6档难度对应的中文标签名称 */
    private static final String[] DIFFICULTY_NAMES = {
        "初识象棋", "初级棋手", "入门学生", "中等棋力", "高手水平", "国家大师"
    };

    /**
     * Activity创建时的初始化入口。
     * <p>
     * 完成视图绑定、游戏对象创建、事件监听器注册等初始化工作。
     *
     * @param savedInstanceState 系统保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chinese_chess);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        chessView = findViewById(R.id.chess_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        tvStatus = findViewById(R.id.tv_status);
        seekDifficulty = findViewById(R.id.seek_difficulty);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);

        game = new ChineseChessGame();
        chessView.bindGame(game);
        usageStore = new GameUsageStore(this);
        chessView.setOnCellClickListener(this::onCellTap);

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

        findViewById(R.id.btn_start_game).setOnClickListener(v -> beginGame(aiDifficulty));
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> undoLastMove());
        findViewById(R.id.btn_restart).setOnClickListener(v -> restartGame());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChineseChessOnlineActivity.class);
            startActivity(intent);
        });
    }

    /**
     * 开始新一局游戏。
     * <p>
     * 根据指定难度创建AI实例，重置棋盘状态，切换界面从难度面板到游戏控制面板。
     *
     * @param difficulty AI难度等级（1~6），影响AI搜索时间上限
     */
    private void beginGame(int difficulty) {
        isProcessing = false;
        ai = new ChineseChessAI(difficulty);
        game.reset();

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearLastMove();

        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        showStatus("你的回合 - 红方先行");
    }

    /**
     * 在状态栏显示指定消息。
     *
     * @param msg 要显示的状态文本
     */
    private void showStatus(String msg) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 处理棋盘格子点击事件。
     * <p>
     * 交互逻辑：
     * <ol>
     *   <li>若正在处理中或游戏已结束，忽略点击</li>
     *   <li>仅允许红方（玩家）回合操作</li>
     *   <li>若已选中棋子且点击了合法目标位置，执行走棋</li>
     *   <li>若点击了己方其他棋子，切换选中</li>
     *   <li>若点击了无效位置，取消选中</li>
     * </ol>
     *
     * @param col 点击的列坐标（0~8）
     * @param row 点击的行坐标（0~9）
     */
    private void onCellTap(int col, int row) {
        if (isProcessing) return;
        if (game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;

        ChineseChessGame.Piece target = game.getBoard()[row][col];

        if (selectedPos != null) {
            // 已有选中棋子：检查点击位置是否为合法走法
            boolean isValidMove = false;
            if (currentValidMoves != null) {
                for (int[] mv : currentValidMoves) {
                    if (mv[0] == col && mv[1] == row) {
                        isValidMove = true;
                        break;
                    }
                }
            }

            if (isValidMove) {
                // 点击了合法目标位置，执行走棋
                performPlayerMove(selectedPos[0], selectedPos[1], col, row);
                return;
            }

            if (target != null && target.side == ChineseChessGame.Side.RED) {
                // 点击了己方另一颗棋子，切换选中
                selectedPos = new int[]{col, row};
                currentValidMoves = game.getLegalMoves(col, row);
                chessView.setSelected(col, row, currentValidMoves);
            } else {
                // 点击了无效位置，取消选中
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
            }
        } else {
            // 未选中棋子：点击己方棋子则选中
            if (target != null && target.side == ChineseChessGame.Side.RED) {
                selectedPos = new int[]{col, row};
                currentValidMoves = game.getLegalMoves(col, row);
                chessView.setSelected(col, row, currentValidMoves);
            }
        }
    }

    /**
     * 执行玩家的走棋操作。
     * <p>
     * 先播放走棋动画，动画完成后：
     * <ol>
     *   <li>在游戏逻辑中执行走棋</li>
     *   <li>切换到AI回合</li>
     *   <li>检查游戏是否结束</li>
     * </ol>
     *
     * @param fromX 起始列
     * @param fromY 起始行
     * @param toX   目标列
     * @param toY   目标行
     */
    private void performPlayerMove(int fromX, int fromY, int toX, int toY) {
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        isProcessing = true;

        chessView.animateMove(fromX, fromY, toX, toY, () -> {
            ChineseChessGame.MoveRecord rec = game.makeMoveSafe(fromX, fromY, toX, toY);
            if (rec == null) {
                isProcessing = false;
                return;
            }

            game.getMoveHistory().add(rec);
            chessView.setLastMove(fromX, fromY, toX, toY);
            chessView.invalidate();

            game.switchSide();
            game.checkGameOver();

            if (game.isGameOver()) {
                isProcessing = false;
                showStatus("🎉 恭喜获胜！");
                usageStore.recordWin(GAME_ID);
                return;
            }

            startAITurn();
        });
    }

    /**
     * 启动AI回合。
     * <p>
     * 锁定棋盘，在后台线程中执行AI搜索，搜索完成后通过UI Handler
     * 在主线程中应用AI走棋。为保证用户体验，AI思考至少耗时1秒（不足则补延时）。
     */
    private void startAITurn() {
        isProcessing = true;
        chessView.setLocked(true);
        showStatus("AI思考中...");
        final long startMs = System.currentTimeMillis();

        aiExecutor.execute(() -> {
            int[] result = ai.getBestMove(game);
            // AI搜索返回null时（极端情况），随机选择一个合法走法
            if (result == null) {
                List<int[]> all = game.getAllMoves(ChineseChessGame.Side.BLACK);
                if (!all.isEmpty()) result = all.get(0);
            }
            final int[] move = result;

            // 保证AI至少"思考"1秒，避免瞬间落子让用户困惑
            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(1000 - elapsed, 100);
            uiHandler.postDelayed(() -> applyAIMove(move), delay);
        });
    }

    /**
     * 应用AI计算出的走棋结果。
     * <p>
     * 播放AI走棋动画，动画完成后更新游戏状态、解锁棋盘、检查胜负。
     * 若AI无合法走法（move为null），直接跳过动画逻辑。
     *
     * @param move AI选择的走法 [fromX, fromY, toX, toY]，可能为null
     */
    private void applyAIMove(int[] move) {
        if (move != null) {
            chessView.animateMove(move[0], move[1], move[2], move[3], () -> {
                ChineseChessGame.MoveRecord rec = game.makeMoveSafe(move[0], move[1], move[2], move[3]);
                if (rec != null) {
                    game.getMoveHistory().add(rec);
                    chessView.setLastMove(move[0], move[1], move[2], move[3]);
                    game.switchSide();
                }

                isProcessing = false;
                chessView.setLocked(false);
                chessView.invalidate();
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
                showStatus("你的回合");

                game.checkGameOver();
                if (game.isGameOver()) {
                    showStatus("AI获胜！");
                    usageStore.recordLoss(GAME_ID);
                }
            });
        } else {
            // AI无合法走法的极端情况
            isProcessing = false;
            chessView.setLocked(false);
            chessView.invalidate();
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            showStatus("你的回合");

            game.checkGameOver();
            if (game.isGameOver()) {
                showStatus("AI获胜！");
                usageStore.recordLoss(GAME_ID);
            }
        }
    }

    /**
     * 悔棋操作。
     * <p>
     * 撤销最近一轮（玩家+AI各一步）的走棋，恢复到玩家上一次走棋前的状态。
     * 撤销后更新最后一步走棋的高亮标记。
     */
    private void undoLastMove() {
        if (isProcessing) return;
        int undone = game.undoLastMoves(1);
        if (undone > 0) {
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            chessView.clearLastMove();

            // 恢复上一步走棋的高亮标记
            List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
            if (history.size() > 0) {
                ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
                chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
            }

            showStatus("你的回合");
        }
    }

    /**
     * 重新开始游戏。
     * <p>
     * 取消进行中的动画，重置棋盘，切回难度选择面板。
     */
    private void restartGame() {
        isProcessing = false;
        chessView.cancelAnimation();
        game.reset();
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearLastMove();
        chessView.bindGame(game);
        chessView.setLocked(false);

        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
    }

    /**
     * 处理返回键事件。
     * <p>
     * 若当前在游戏中（难度面板已隐藏），返回到难度选择界面；
     * 若在难度选择界面，则执行默认的返回操作（退出Activity）。
     */
    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            restartGame();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Activity销毁时清理资源。
     * <p>
     * 立即关闭AI线程池，防止线程泄漏。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiExecutor.shutdownNow();
    }
}
