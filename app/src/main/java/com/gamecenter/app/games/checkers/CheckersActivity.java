// 同步声明：此文件的 AI 算法逻辑与 module-store/feature/games/games/checkers/src/main/java/com/gamecenter/app/checkers/CheckersGame.java 保持同步
// 结构差异：app 版 AI 逻辑内联在 Activity 中；module-store 版提取到独立的 CheckersGame 类。修改 AI 算法时请同步修改对方文件
package com.gamecenter.app.games.checkers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 跳棋（国际跳棋）游戏 Activity。
 *
 * <p>8×8 棋盘，黑色棋子 vs 白色棋子（AI）。
 * 支持跳过吃子和升王规则。</p>
 *
 * <p>难度梯度：
 * <ul>
 *   <li>简单（0）：随机走子，新手友好</li>
 *   <li>中等（1）：Minimax depth=2 + α-β 剪枝，可被战胜</li>
 *   <li>困难（2）：Minimax depth=4 + α-β 剪枝 + 评估（棋子差+王棋加权+升王行位置），首手随机化</li>
 * </ul>
 * </p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>连胜 3 局</li>
 *   <li>吃子大师（单局吃子超过5个）</li>
 *   <li>击败困难 AI</li>
 *   <li>全吃胜利（吃掉所有对手棋子）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.1
 * @since 2026-06-20
 */
public class CheckersActivity extends BaseGameActivity {

    private static final int BOARD_SIZE = 8;
    private static final int EMPTY = CheckersView.EMPTY;
    private static final int BLACK = CheckersView.BLACK;
    private static final int BLACK_KING = CheckersView.BLACK_KING;
    private static final int WHITE = CheckersView.WHITE;
    private static final int WHITE_KING = CheckersView.WHITE_KING;

    /** 玩家为黑色，AI 为白色 */
    private static final int PLAYER_COLOR = BLACK;
    private static final int AI_COLOR = WHITE;

    /** AI 思考最大节点数（防卡顿） */
    private static final int AI_MAX_NODES = 20000;

    // 游戏状态
    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private boolean isPlayerTurn = true;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean[][] validMoves = new boolean[BOARD_SIZE][BOARD_SIZE];
    private List<int[]> jumpMoves = new ArrayList<>();
    private int playerCaptures = 0;
    private int totalWins = 0;
    private int winStreak = 0;
    /** AI 难度：0=简单, 1=中等, 2=困难 */
    private int aiLevel = 1;
    /** AI 搜索节点计数 */
    private int aiNodesSearched;

    /** P2-7: 对局回放录制器 */
    private com.gamecenter.app.games.replay.ReplayRecorder replayRecorder;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    // UI 组件
    private CheckersView checkersView;
    private TextView tvStatus;
    private TextView tvDifficulty;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "checkers";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_checkers_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_checkers_color_bg));

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_checkers_color_status_text));
        tvStatus.setPadding(0, 24, 0, 8);

        tvDifficulty = new TextView(this);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(ContextCompat.getColor(this, R.color.game_checkers_color_diff_label));
        tvDifficulty.setPadding(0, 4, 0, 16);

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        MaterialButton btnEasy = new MaterialButton(this);
        btnEasy.setText(R.string.difficulty_easy);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 0, 8, 0);
        btnEasy.setLayoutParams(lp);
        btnEasy.setOnClickListener(v -> startNewGame(0));

        MaterialButton btnMedium = new MaterialButton(this);
        btnMedium.setText(R.string.game_checkers_difficulty_medium);
        LinearLayout.LayoutParams lpMed = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMed.setMargins(8, 0, 8, 0);
        btnMedium.setLayoutParams(lpMed);
        btnMedium.setOnClickListener(v -> startNewGame(1));

        MaterialButton btnHard = new MaterialButton(this);
        btnHard.setText(R.string.difficulty_hard);
        btnHard.setOnClickListener(v -> startNewGame(2));

        btnRow.addView(btnEasy);
        btnRow.addView(btnMedium);
        btnRow.addView(btnHard);
        menuPanel.addView(btnRow);

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        checkersView = new CheckersView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        checkersView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        checkersView.setOnCellClickListener(this::onCellClick);

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.btn_restart);
        btnRestart.setOnClickListener(v -> showMenu());
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = 16;
        btnRestart.setLayoutParams(restartLp);

        gamePanel.addView(checkersView);
        gamePanel.addView(btnRestart);

        root.addView(tvStatus);
        root.addView(tvDifficulty);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_checkers_select_difficulty);
        tvDifficulty.setText("");
    }

    /**
     * 开始新游戏
     */
    private void startNewGame(int level) {
        aiLevel = level;
        isPlayerTurn = true;
        selectedRow = -1;
        selectedCol = -1;
        playerCaptures = 0;
        initBoard();

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficulty.setText(level == 0
                ? getString(R.string.difficulty_easy)
                : (level == 1 ? getString(R.string.game_checkers_difficulty_medium)
                              : getString(R.string.difficulty_hard)));
        tvStatus.setText(R.string.game_checkers_your_turn);

        checkersView.setBoard(board);
        checkersView.clearSelection();

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();

        // P2-7: 开始回放录制
        replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, getGameId());
        replayRecorder.startRecording(level);
    }

    /**
     * 初始化棋盘
     */
    private void initBoard() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        // 白色棋子（AI）在上方 3 行
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = WHITE;
                }
            }
        }
        // 黑色棋子（玩家）在下方 3 行
        for (int r = 5; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = BLACK;
                }
            }
        }
    }

    /**
     * 处理棋盘点击
     */
    private void onCellClick(int row, int col) {
        if (!isPlayerTurn || !isGameRunning) return;

        // 如果已有选中棋子，尝试移动
        if (selectedRow >= 0) {
            if (validMoves[row][col]) {
                // 执行移动
                performMove(selectedRow, selectedCol, row, col);
                checkersView.clearSelection();
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
        }

        // 选择新棋子
        int piece = board[row][col];
        if (piece == BLACK || piece == BLACK_KING) {
            selectedRow = row;
            selectedCol = col;
            checkersView.setSelected(row, col);

            // 计算合法移动
            validMoves = calculateValidMoves(row, col);
            checkersView.setValidMoves(validMoves);
        }
    }

    /**
     * 计算指定棋子的合法移动
     */
    private boolean[][] calculateValidMoves(int row, int col) {
        boolean[][] moves = new boolean[BOARD_SIZE][BOARD_SIZE];
        int piece = board[row][col];

        // 跳吃优先
        List<int[]> jumps = getJumpMoves(row, col, piece);
        if (!jumps.isEmpty()) {
            for (int[] jump : jumps) {
                moves[jump[0]][jump[1]] = true;
            }
            return moves;
        }

        // 普通移动
        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            if (isValidPosition(newRow, newCol) && board[newRow][newCol] == EMPTY) {
                moves[newRow][newCol] = true;
            }
        }

        return moves;
    }

    /**
     * 获取跳吃移动
     */
    private List<int[]> getJumpMoves(int row, int col, int piece) {
        List<int[]> jumps = new ArrayList<>();
        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int midRow = row + dir[0];
            int midCol = col + dir[1];
            int landRow = row + 2 * dir[0];
            int landCol = col + 2 * dir[1];
            if (isValidPosition(landRow, landCol) && board[landRow][landCol] == EMPTY
                    && isOpponent(board[midRow][midCol], piece)) {
                jumps.add(new int[]{landRow, landCol});
            }
        }
        return jumps;
    }

    /**
     * 获取棋子移动方向
     */
    private int[][] getDirections(int piece) {
        switch (piece) {
            case BLACK:      return new int[][]{{-1, -1}, {-1, 1}};
            case BLACK_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            case WHITE:      return new int[][]{{1, -1}, {1, 1}};
            case WHITE_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            default:         return new int[0][];
        }
    }

    /**
     * 检查是否为对手棋子
     */
    private boolean isOpponent(int piece, int myPiece) {
        if (myPiece == BLACK || myPiece == BLACK_KING) {
            return piece == WHITE || piece == WHITE_KING;
        } else {
            return piece == BLACK || piece == BLACK_KING;
        }
    }

    /**
     * 检查位置是否有效
     */
    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    /**
     * 执行移动
     */
    private void performMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        board[fromRow][fromCol] = EMPTY;

        // 检查是否跳吃
        if (Math.abs(toRow - fromRow) == 2) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            board[midRow][midCol] = EMPTY;
            playerCaptures++;
        }

        // 升王检查
        if (piece == BLACK && toRow == 0) {
            piece = BLACK_KING;
        }
        board[toRow][toCol] = piece;

        checkersView.setBoard(board);

        // P2-7: 记录玩家走法
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(
                    fromRow, fromCol, toRow, toCol, PLAYER_COLOR));
        }

        // 检查游戏结束
        if (checkGameEnd()) return;

        // AI 回合
        isPlayerTurn = false;
        tvStatus.setText(R.string.game_checkers_ai_turn);
        handler.postDelayed(this::aiMove, 500);
    }

    /**
     * AI 移动
     */
    private void aiMove() {
        List<int[]> allMoves = getAllMoves(AI_COLOR);
        if (allMoves.isEmpty()) {
            // AI 无棋可走，玩家获胜
            onPlayerWin(true);
            return;
        }

        int[] move;
        if (aiLevel == 0) {
            // 简单 AI：随机走
            move = allMoves.get(random.nextInt(allMoves.size()));
        } else {
            // 中等/困难 AI：Minimax + α-β 剪枝
            int depth = (aiLevel == 1) ? 2 : 4;
            aiNodesSearched = 0;
            move = findBestMoveByMinimax(AI_COLOR, depth);
            // 兜底：Minimax 没找到走法（罕见，如超节点上限），用第一个
            if (move == null) {
                move = allMoves.get(0);
            }
        }

        // 执行 AI 移动
        int piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = EMPTY;

        if (Math.abs(move[2] - move[0]) == 2) {
            int midRow = (move[0] + move[2]) / 2;
            int midCol = (move[1] + move[3]) / 2;
            board[midRow][midCol] = EMPTY;
        }

        if (piece == WHITE && move[2] == BOARD_SIZE - 1) {
            piece = WHITE_KING;
        }
        board[move[2]][move[3]] = piece;

        checkersView.setBoard(board);

        // P2-7: 记录 AI 走法
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(
                    move[0], move[1], move[2], move[3], AI_COLOR));
        }

        if (checkGameEnd()) return;

        isPlayerTurn = true;
        tvStatus.setText(R.string.game_checkers_your_turn);
    }

    /**
     * Minimax + α-β 剪枝搜索最佳走法。
     * <p>
     * 遍历所有合法走法，模拟执行后调用 minimax 评估，选择评分最高的走法。
     * 困难档首手若有多条等价走法，加权随机选一条，增加开局多样性。
     *
     * @param color AI 颜色
     * @param depth 搜索深度
     * @return 最佳走法 [fromRow, fromCol, toRow, toCol]
     */
    private int[] findBestMoveByMinimax(int color, int depth) {
        List<int[]> moves = getAllMoves(color);
        if (moves.isEmpty()) return null;

        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();

        for (int[] move : moves) {
            int[][] saved = copyBoard(board);
            applyMove(move);
            int score = minimax(depth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
            restoreBoard(saved);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        // 困难档若有多条等价最佳走法，随机选一条增加开局多样性
        // 中等档始终选第一条（更稳定）
        if (aiLevel == 2 && bestMoves.size() > 1) {
            return bestMoves.get(random.nextInt(bestMoves.size()));
        }
        return bestMoves.get(0);
    }

    /**
     * Minimax 递归搜索。
     *
     * @param depth         剩余深度
     * @param isMaximizing  当前是否为 AI（最大化）方
     * @param alpha         α 剪枝下界
     * @param beta          β 剪枝上界
     * @return 评估分数（AI 视角）
     */
    private int minimax(int depth, boolean isMaximizing, int alpha, int beta) {
        if (++aiNodesSearched > AI_MAX_NODES) return evaluateBoard();
        if (depth == 0) return evaluateBoard();

        int color = isMaximizing ? AI_COLOR : PLAYER_COLOR;
        List<int[]> moves = getAllMoves(color);
        if (moves.isEmpty()) {
            // 无棋可走判负
            return isMaximizing ? -10000 : 10000;
        }

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int[] move : moves) {
                int[][] saved = copyBoard(board);
                applyMove(move);
                int val = minimax(depth - 1, false, alpha, beta);
                restoreBoard(saved);
                best = Math.max(best, val);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int[] move : moves) {
                int[][] saved = copyBoard(board);
                applyMove(move);
                int val = minimax(depth - 1, true, alpha, beta);
                restoreBoard(saved);
                best = Math.min(best, val);
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    /**
     * 评估当前棋盘（AI 视角，正值=AI 优势）。
     * <p>
     * 评估项：
     * <ul>
     *   <li>普通棋子：+10（AI）/ -10（玩家）</li>
     *   <li>王棋：+25 / -25（王棋价值更高）</li>
     *   <li>位置加权：靠近升王行的普通棋子加分（白棋靠近 row=7，黑棋靠近 row=0）</li>
     * </ul>
     */
    private int evaluateBoard() {
        int score = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int piece = board[r][c];
                switch (piece) {
                    case WHITE:
                        // AI 普通棋子 +10，靠近升王行（row=7）再加分
                        score += 10 + (r * 1);
                        break;
                    case WHITE_KING:
                        score += 25;
                        break;
                    case BLACK:
                        // 玩家普通棋子 -10，靠近升王行（row=0）再减分
                        score -= 10 + ((BOARD_SIZE - 1 - r) * 1);
                        break;
                    case BLACK_KING:
                        score -= 25;
                        break;
                }
            }
        }
        return score;
    }

    /** 复制棋盘 */
    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(src[r], 0, copy[r], 0, BOARD_SIZE);
        }
        return copy;
    }

    /** 还原棋盘 */
    private void restoreBoard(int[][] saved) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(saved[r], 0, board[r], 0, BOARD_SIZE);
        }
    }

    /** 在棋盘上应用走法（不更新 UI） */
    private void applyMove(int[] move) {
        int piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = EMPTY;
        if (Math.abs(move[2] - move[0]) == 2) {
            int midRow = (move[0] + move[2]) / 2;
            int midCol = (move[1] + move[3]) / 2;
            board[midRow][midCol] = EMPTY;
        }
        if (piece == WHITE && move[2] == BOARD_SIZE - 1) {
            piece = WHITE_KING;
        }
        if (piece == BLACK && move[2] == 0) {
            piece = BLACK_KING;
        }
        board[move[2]][move[3]] = piece;
    }

    /**
     * 获取指定颜色所有合法移动
     */
    private List<int[]> getAllMoves(int color) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    // 优先跳吃
                    for (int[] dir : directions) {
                        int midR = r + dir[0];
                        int midC = c + dir[1];
                        int landR = r + 2 * dir[0];
                        int landC = c + 2 * dir[1];
                        if (isValidPosition(landR, landC) && board[landR][landC] == EMPTY
                                && isOpponent(board[midR][midC], board[r][c])) {
                            moves.add(new int[]{r, c, landR, landC});
                        }
                    }
                }
            }
        }
        if (!moves.isEmpty()) return moves;

        // 无跳吃，普通移动
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    for (int[] dir : directions) {
                        int newR = r + dir[0];
                        int newC = c + dir[1];
                        if (isValidPosition(newR, newC) && board[newR][newC] == EMPTY) {
                            moves.add(new int[]{r, c, newR, newC});
                        }
                    }
                }
            }
        }
        return moves;
    }

    /**
     * 检查游戏是否结束
     */
    private boolean checkGameEnd() {
        int blackCount = countPieces(BLACK) + countPieces(BLACK_KING);
        int whiteCount = countPieces(WHITE) + countPieces(WHITE_KING);

        if (blackCount == 0) {
            onPlayerLose();
            return true;
        }
        if (whiteCount == 0) {
            onPlayerWin(false);
            return true;
        }
        if (isPlayerTurn && getAllMoves(PLAYER_COLOR).isEmpty()) {
            onPlayerLose();
            return true;
        }
        if (!isPlayerTurn && getAllMoves(AI_COLOR).isEmpty()) {
            onPlayerWin(false);
            return true;
        }
        return false;
    }

    /**
     * 统计棋子数量
     */
    private int countPieces(int pieceType) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == pieceType) count++;
            }
        }
        return count;
    }

    /**
     * P2-7: 结束回放录制并弹出回放查看对话框。
     *
     * @param result 对局结果（{@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_WIN} /
     *               {@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_LOSS} /
     *               {@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_DRAW}）
     */
    private void finishReplay(String result) {
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.endRecording(result);
        }
        if (replayRecorder != null && replayRecorder.hasHistory()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("对局结束")
                    .setMessage("是否查看回放？")
                    .setPositiveButton("查看回放", (d, w) -> {
                        com.gamecenter.app.games.replay.ReplayRecord rec = replayRecorder.loadLatest();
                        com.gamecenter.app.games.replay.ReplayDialog.show(this, rec,
                                new com.gamecenter.app.games.replay.ReplayPlayer.Listener() {
                                    @Override
                                    public void onBoardUpdated(int step,
                                                                List<com.gamecenter.app.games.replay.ReplayMove> played) {
                                        // 重置棋盘并按已播放走法重建局面
                                        initBoard();
                                        for (com.gamecenter.app.games.replay.ReplayMove m : played) {
                                            applyMove(new int[]{m.fromRow, m.fromCol, m.toRow, m.toCol});
                                        }
                                        checkersView.setBoard(board);
                                    }

                                    @Override
                                    public void onReplayFinished() {}

                                    @Override
                                    public void onReplayReset() {}
                                });
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        }
    }

    /**
     * 玩家获胜
     */
    private void onPlayerWin(boolean allEaten) {
        isGameRunning = false;
        totalWins++;
        winStreak++;

        tvStatus.setText(R.string.game_checkers_you_win);
        usageStore.recordWin(getGameId());

        checkAchievement("win", totalWins);
        checkAchievement("streak", winStreak);
        checkAchievement("score", playerCaptures);
        // 困难档（aiLevel==2）胜利才触发"击败困难AI"成就
        if (aiLevel == 2) {
            checkAchievement("special", true);
        }
        if (allEaten || countPieces(WHITE) + countPieces(WHITE_KING) == 0) {
            checkAchievement("special", true);
        }

        updateScore(currentScore + 200);
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        // P2-7: 结束录制并提供回放
        finishReplay(com.gamecenter.app.games.replay.ReplayRecorder.RESULT_WIN);
    }

    /**
     * 玩家失败
     */
    private void onPlayerLose() {
        isGameRunning = false;
        winStreak = 0;
        tvStatus.setText(R.string.game_checkers_ai_win);
        usageStore.recordLoss(getGameId());
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        // P2-7: 结束录制并提供回放
        finishReplay(com.gamecenter.app.games.replay.ReplayRecorder.RESULT_LOSS);
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
