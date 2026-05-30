package com.gamecenter.app.games.checkers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 跳棋（国际跳棋/Checkers）棋盘自定义视图。
 *
 * <p>职责：
 * <ul>
 *   <li>绘制 8×8 棋盘、棋子（普通和王棋）及选中/可移动位置高亮</li>
 *   <li>处理触摸事件，实现选子、移动、跳吃的完整交互流程</li>
 *   <li>维护棋盘状态、当前玩家、合法走法等核心游戏逻辑</li>
 *   <li>通过 {@link OnGameStateListener} 回调通知 Activity 玩家轮换和游戏结束</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>棋盘用二维数组 {@code board[row][col]} 表示，值含义：-1=空格, 0=黑子, 1=白子, 2=黑王, 3=白王</li>
 *   <li>普通棋子只能向前移动（黑方向上、白方向下），王棋可四方向移动</li>
 *   <li>跳吃后若仍可继续跳吃，则不切换玩家（强制连跳规则）</li>
 *   <li>棋子到达对方底线自动升变为王棋</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是跳棋（国际跳棋/Checkers）的棋盘画板+裁判，和其他棋类不同，
 * 它把游戏逻辑和视图绘制合在一个类里（没有单独的Game类）。
 * 跳棋的规则：
 * - 只能在深色格子上走棋（浅色格子永远为空）
 * - 普通棋子只能向前走，王棋可以前后走
 * - 跳吃：跳过对方的棋子把它吃掉，如果跳吃后还能继续跳，必须继续跳（强制连跳）
 * - 升变：棋子到达对方底线自动变成王棋（可以前后移动）
 * - 输的条件：一方没有棋子了，或者轮到你走但你无路可走
 */
public class CheckersView extends View {

    /** 棋盘边长（8×8） */
    private static final int SIZE = 8;

    /** 棋子类型常量：黑方普通棋子 */
    private static final int DARK = 0;

    /** 棋子类型常量：白方普通棋子 */
    private static final int LIGHT = 1;

    /** 棋子类型常量：黑方王棋 */
    private static final int DARK_KING = 2;

    /** 棋子类型常量：白方王棋 */
    private static final int LIGHT_KING = 3;

    /** 深色格子画笔（棕色） */
    private Paint darkPaint;

    /** 浅色格子画笔（米色） */
    private Paint lightPaint;

    /** 黑色棋子画笔 */
    private Paint darkPiecePaint;

    /** 白色棋子画笔 */
    private Paint lightPiecePaint;

    /** 选中格子高亮画笔（半透明绿色） */
    private Paint selectedPaint;

    /** 合法移动位置标记画笔（半透明绿色） */
    private Paint validMovePaint;

    /** 棋盘状态数组，board[row][col]，值为棋子类型常量或 -1（空）
     *  -1 = 空格（浅色格子或深色空格）
     *  0 = 黑方普通棋子（DARK）
     *  1 = 白方普通棋子（LIGHT）
     *  2 = 黑方王棋（DARK_KING）——到达对方底线后升变
     *  3 = 白方王棋（LIGHT_KING）——到达对方底线后升变
     */
    private int[][] board;

    /** 当前玩家编号：0=黑方，1=白方 */
    private int currentPlayer;

    /** 当前选中棋子的列坐标，-1 表示未选中 */
    private int selectedX = -1;

    /** 当前选中棋子的行坐标，-1 表示未选中 */
    private int selectedY = -1;

    /** 当前选中棋子的合法移动列表，每个元素为 {目标行, 目标列} 或 {目标行, 目标列, 1}（1表示跳吃） */
    private List<int[]> validMoves;

    /** 游戏状态监听器，用于通知 Activity 玩家轮换和游戏结束 */
    private OnGameStateListener listener;

    /**
     * 游戏状态监听接口。
     *
     * <p>Activity 实现此接口以响应游戏状态变化。
     */
    public interface OnGameStateListener {
        /**
         * 当玩家轮换时回调。
         *
         * @param player 新的当前玩家编号：0=黑方，1=白方
         */
        void onPlayerChanged(int player);

        /**
         * 当游戏结束时回调。
         *
         * @param winner 获胜方编号：0=黑方，1=白方
         */
        void onGameOver(int winner);
    }

    /**
     * 单参数构造器（代码创建时使用）。
     *
     * @param context 上下文
     */
    public CheckersView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造器（XML 布局膨胀时使用）。
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public CheckersView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔、棋盘数组和棋子布局。
     *
     * <p>创建所有绘制所需的 {@link Paint} 对象，并调用 {@link #initBoard()} 设置初始棋子位置。
     */
    private void init() {
        darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(0xFF5D4037);
        darkPaint.setStyle(Paint.Style.FILL);

        lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPaint.setColor(0xFFBCAAA4);
        lightPaint.setStyle(Paint.Style.FILL);

        darkPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPiecePaint.setColor(0xFF212121);
        darkPiecePaint.setStyle(Paint.Style.FILL);

        lightPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPiecePaint.setColor(0xFFFFCDD2);
        lightPiecePaint.setStyle(Paint.Style.FILL);

        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(0x804CAF50);
        selectedPaint.setStyle(Paint.Style.FILL);

        validMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        validMovePaint.setColor(0x8000FF00);
        validMovePaint.setStyle(Paint.Style.FILL);

        board = new int[SIZE][SIZE];
        validMoves = new ArrayList<>();
        initBoard();
    }

    /**
     * 初始化棋盘上的棋子布局。
     *
     * <p>布局规则：
     * <ul>
     *   <li>仅在深色格子（(行+列)为奇数）上放置棋子</li>
     *   <li>前3行（行0-2）放置白方棋子</li>
     *   <li>后3行（行5-7）放置黑方棋子</li>
     *   <li>中间两行（行3-4）为空</li>
     * </ul>
     */
    private void initBoard() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    if (r < 3) board[r][c] = LIGHT;
                    else if (r > 4) board[r][c] = DARK;
                    else board[r][c] = -1;
                } else {
                    // 浅色格子始终为空，不可放置棋子
                    board[r][c] = -1;
                }
            }
        }
        // 黑方先手
        currentPlayer = DARK;
    }

    /**
     * 设置游戏状态监听器。
     *
     * @param listener 监听器实现
     */
    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 绘制棋盘、棋子、选中高亮和合法移动标记。
     *
     * <p>绘制顺序：
     * <ol>
     *   <li>棋盘格子（深浅交替）</li>
     *   <li>选中格子高亮</li>
     *   <li>合法移动位置标记（绿色圆点）</li>
     *   <li>棋子（黑/白普通棋子和王棋）</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 根据视图尺寸计算每个格子的大小，确保棋盘为正方形
        float cellSize = Math.min(getWidth(), getHeight()) / (float) SIZE;

        // 第一遍：绘制棋盘格子、选中高亮和合法移动标记
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                float left = c * cellSize;
                float top = r * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                // 交替绘制深浅格子
                Paint paint = ((r + c) % 2 == 0) ? lightPaint : darkPaint;
                canvas.drawRect(left, top, right, bottom, paint);

                // 选中格子叠加半透明绿色高亮
                if (r == selectedY && c == selectedX) {
                    canvas.drawRect(left, top, right, bottom, selectedPaint);
                }

                // 在合法移动目标位置绘制绿色圆点提示
                for (int[] move : validMoves) {
                    if (move[0] == r && move[1] == c) {
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 4, validMovePaint);
                    }
                }
            }
        }

        // 第二遍：绘制棋子（在格子之上），避免棋子被格子覆盖
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int piece = board[r][c];
                if (piece >= 0) {
                    float cx = c * cellSize + cellSize / 2;
                    float cy = r * cellSize + cellSize / 2;
                    float radius = cellSize * 0.35f;

                    // 根据棋子类型选择画笔
                    Paint piecePaint = (piece == DARK || piece == DARK_KING) ? darkPiecePaint : lightPiecePaint;
                    canvas.drawCircle(cx, cy, radius, piecePaint);

                    // 王棋额外绘制金色皇冠符号
                    if (piece == DARK_KING || piece == LIGHT_KING) {
                        Paint crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        crownPaint.setColor(0xFFFFD700);
                        crownPaint.setTextSize(cellSize * 0.3f);
                        crownPaint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("♔", cx, cy + cellSize * 0.1f, crownPaint);
                    }
                }
            }
        }
    }

    /**
     * 处理触摸事件，实现选子和移动的交互逻辑。
     *
     * <p>交互流程：
     * <ol>
     *   <li>若已选中棋子且触摸位置为合法移动目标，则执行移动</li>
     *   <li>若触摸位置为当前玩家的棋子，则选中该棋子并计算合法走法</li>
     *   <li>若触摸位置无效，则取消选中</li>
     * </ol>
     *
     * @param event 触摸事件
     * @return 始终返回 true，表示消费了触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float cellSize = Math.min(getWidth(), getHeight()) / (float) SIZE;
            int c = (int) (event.getX() / cellSize);
            int r = (int) (event.getY() / cellSize);

            // 边界检查，确保触摸点在棋盘范围内
            if (c >= 0 && c < SIZE && r >= 0 && r < SIZE) {
                // 已选中棋子时，检查是否点击了合法移动目标
                if (selectedX >= 0 && selectedY >= 0) {
                    for (int[] move : validMoves) {
                        if (move[0] == r && move[1] == c) {
                            // move[2]==1 表示跳吃移动
                            makeMove(selectedX, selectedY, c, r, move.length > 2 && move[2] == 1);
                            selectedX = selectedY = -1;
                            validMoves.clear();
                            invalidate();
                            return true;
                        }
                    }
                }

                // 点击当前玩家的棋子，选中并计算合法走法
                if (isValidPiece(r, c)) {
                    selectedX = c;
                    selectedY = r;
                    calculateValidMoves(c, r);
                    invalidate();
                } else {
                    // 点击无效位置，取消选中
                    selectedX = selectedY = -1;
                    validMoves.clear();
                    invalidate();
                }
            }
        }
        return true;
    }

    /**
     * 判断指定位置是否为当前玩家的棋子。
     *
     * @param r 行坐标
     * @param c 列坐标
     * @return 如果是当前玩家的棋子返回 true
     */
    private boolean isValidPiece(int r, int c) {
        int piece = board[r][c];
        if (currentPlayer == DARK && (piece == DARK || piece == DARK_KING)) return true;
        if (currentPlayer == LIGHT && (piece == LIGHT || piece == LIGHT_KING)) return true;
        return false;
    }

    /**
     * 计算指定棋子的所有合法移动（普通移动和跳吃）。
     *
     * <p>移动方向规则：
     * <ul>
     *   <li>黑方普通棋子：只能向上（行号递减方向）移动</li>
     *   <li>白方普通棋子：只能向下（行号递增方向）移动</li>
     *   <li>王棋：可向四个对角方向移动</li>
     * </ul>
     *
     * <p>跳吃规则：若相邻对角位置有敌方棋子，且其后方为空格，则可跳吃。
     * 跳吃移动以 {目标行, 目标列, 1} 格式存入 validMoves，第三个元素标记为跳吃。
     *
     * @param c 棋子列坐标
     * @param r 棋子行坐标
     */
    private void calculateValidMoves(int c, int r) {
        validMoves.clear();
        int piece = board[r][c];
        boolean isKing = (piece == DARK_KING || piece == LIGHT_KING);
        boolean isDark = (piece == DARK || piece == DARK_KING);

        // 根据棋子类型确定可移动方向
        int[][] dirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        if (isKing) {
            // 王棋可四方向移动
            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        } else if (isDark) {
            // 黑方普通棋子只能向上（行号递减）
            dirs = new int[][]{{-1, -1}, {-1, 1}};
        } else {
            // 白方普通棋子只能向下（行号递增）
            dirs = new int[][]{{1, -1}, {1, 1}};
        }

        for (int[] dir : dirs) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            if (nc >= 0 && nc < SIZE && nr >= 0 && nr < SIZE) {
                if (board[nr][nc] == -1) {
                    // 目标格为空，可普通移动
                    validMoves.add(new int[]{nr, nc});
                } else {
                    // 目标格有棋子，检查是否可跳吃
                    int captured = board[nr][nc];
                    boolean isEnemy = (currentPlayer == DARK && (captured == LIGHT || captured == LIGHT_KING)) ||
                                     (currentPlayer == LIGHT && (captured == DARK || captured == DARK_KING));
                    if (isEnemy) {
                        // 跳吃目标：被吃棋子后方的格子必须为空
                        int jr = nr + dir[0];
                        int jc = nc + dir[1];
                        if (jc >= 0 && jc < SIZE && jr >= 0 && jr < SIZE && board[jr][jc] == -1) {
                            // 第三个元素 1 标记此为跳吃移动
                            validMoves.add(new int[]{jr, jc, 1});
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行棋子移动，包括普通移动和跳吃。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>移动棋子到目标位置，清除原位置</li>
     *   <li>若为跳吃，移除被跳过的敌方棋子</li>
     *   <li>检查升变：普通棋子到达对方底线时升变为王棋</li>
     *   <li>若为跳吃且可继续跳吃，则不切换玩家（强制连跳）</li>
     *   <li>检查游戏是否结束</li>
     * </ol>
     *
     * @param fromC 起始列坐标
     * @param fromR 起始行坐标
     * @param toC   目标列坐标
     * @param toR   目标行坐标
     * @param jumped 是否为跳吃移动
     */
    private void makeMove(int fromC, int fromR, int toC, int toR, boolean jumped) {
        int piece = board[fromR][fromC];
        board[toR][toC] = piece;
        board[fromR][fromC] = -1;

        // 跳吃时移除被跳过的敌方棋子（位于起点和终点的中间位置）
        if (jumped) {
            int jr = (fromR + toR) / 2;
            int jc = (fromC + toC) / 2;
            board[jr][jc] = -1;
        }

        // 升变检查：黑方到达第0行（对方底线）或白方到达第7行时升变为王棋
        if ((piece == DARK && toR == 0) || (piece == LIGHT && toR == SIZE - 1)) {
            board[toR][toC] = (piece == DARK) ? DARK_KING : LIGHT_KING;
        }

        // 若非跳吃，或跳吃后无法继续跳吃，则切换玩家
        if (!jumped || !canJump(toC, toR)) {
            currentPlayer = 1 - currentPlayer;
            if (listener != null) listener.onPlayerChanged(currentPlayer);
        }
        // 否则保持当前玩家，允许继续跳吃（强制连跳规则）

        checkGameOver();
    }

    /**
     * 检查指定位置的棋子是否可以继续跳吃。
     *
     * <p>用于实现强制连跳规则：跳吃后若仍可跳吃，则当前玩家必须继续跳。
     *
     * @param c 棋子列坐标
     * @param r 棋子行坐标
     * @return 如果可以继续跳吃返回 true
     */
    private boolean canJump(int c, int r) {
        int piece = board[r][c];
        boolean isKing = (piece == DARK_KING || piece == LIGHT_KING);
        boolean isDark = (piece == DARK || piece == DARK_KING);

        // 根据棋子类型确定移动方向（与 calculateValidMoves 相同的方向规则）
        int[][] dirs;
        if (isKing) {
            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        } else if (isDark) {
            dirs = new int[][]{{-1, -1}, {-1, 1}};
        } else {
            dirs = new int[][]{{1, -1}, {1, 1}};
        }

        for (int[] dir : dirs) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            if (nc >= 0 && nc < SIZE && nr >= 0 && nr < SIZE) {
                int captured = board[nr][nc];
                boolean isEnemy = (isDark && (captured == LIGHT || captured == LIGHT_KING)) ||
                                 (!isDark && (captured == DARK || captured == DARK_KING));
                if (isEnemy) {
                    // 检查被吃棋子后方是否为空格（跳吃落点）
                    int jr = nr + dir[0];
                    int jc = nc + dir[1];
                    if (jc >= 0 && jc < SIZE && jr >= 0 && jr < SIZE && board[jr][jc] == -1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 检查游戏是否结束。
     *
     * <p>游戏结束条件：
     * <ul>
     *   <li>一方没有棋子剩余</li>
     *   <li>当前玩家没有任何合法移动（被堵死）</li>
     * </ul>
     *
     * <p>注意：此方法会临时修改 selectedX/selectedY 和 validMoves 来复用
     * {@link #calculateValidMoves(int, int)} 检查是否有合法走法，调用后会恢复这些字段。
     */
    private void checkGameOver() {
        boolean darkHasPiece = false;
        boolean lightHasPiece = false;
        boolean darkCanMove = false;
        boolean lightCanMove = false;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int piece = board[r][c];
                if (piece == DARK || piece == DARK_KING) {
                    darkHasPiece = true;
                    // 临时选中该棋子以计算其合法走法
                    selectedX = c;
                    selectedY = r;
                    calculateValidMoves(c, r);
                    if (!validMoves.isEmpty()) darkCanMove = true;
                } else if (piece == LIGHT || piece == LIGHT_KING) {
                    lightHasPiece = true;
                }
            }
        }

        // 恢复临时修改的选中状态
        validMoves.clear();
        selectedX = selectedY = -1;

        // 根据棋子存在性和可移动性判定胜负
        if (!darkHasPiece) {
            if (listener != null) listener.onGameOver(LIGHT);
        } else if (!lightHasPiece) {
            if (listener != null) listener.onGameOver(DARK);
        } else if (!darkCanMove && currentPlayer == DARK) {
            // 黑方无合法走法，白方获胜
            if (listener != null) listener.onGameOver(LIGHT);
        } else if (!lightCanMove && currentPlayer == LIGHT) {
            // 白方无合法走法，黑方获胜
            if (listener != null) listener.onGameOver(DARK);
        }
    }

    /**
     * 重置棋盘到初始状态，重新开始游戏。
     *
     * <p>重置后黑方先手，并通知监听器玩家轮换。
     */
    public void reset() {
        initBoard();
        if (listener != null) listener.onPlayerChanged(currentPlayer);
        invalidate();
    }
}
