package com.gamecenter.app.games.go;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 围棋棋盘自定义视图。
 * <p>
 * 负责9×9围棋棋盘的绘制和触摸交互，包括：
 * <ul>
 *   <li>棋盘网格线和星位绘制</li>
 *   <li>黑白棋子绘制（含边框）</li>
 *   <li>最后一手标记（红色圆点）</li>
 *   <li>吃子数信息显示</li>
 *   <li>对局结束遮罩层</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘尺寸自适应View大小，保持正方形</li>
 *   <li>触摸坐标通过四舍五入映射到最近的交叉点</li>
 *   <li>棋盘上方预留15%空间用于信息显示</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是围棋的棋盘画板，和五子棋的GomokuView类似但更简单。
 * 围棋棋盘是9×9（比标准19×19小，适合手机上快速对局），
 * 棋子没有3D渐变效果，用简单的圆形表示。
 * 围棋棋盘是木色背景，看起来像真实的木质棋盘。
 */
public class GoView extends View {

    /** 游戏逻辑对象 */
    private GoGame game;

    /** 每格像素大小 */
    private int cellSize;

    /** 棋盘绘制偏移量（水平方向），用于居中 */
    private float offsetX;

    /** 棋盘绘制偏移量（垂直方向），用于居中 */
    private float offsetY;

    /** 交叉点点击监听器 */
    private OnCellClickListener onCellClickListener;

    /** 背景画笔（木色） */
    private Paint bgPaint;

    /** 棋盘底色画笔 */
    private Paint boardPaint;

    /** 网格线画笔 */
    private Paint linePaint;

    /** 黑子填充画笔 */
    private Paint blackPaint;

    /** 白子填充画笔 */
    private Paint whitePaint;

    /** 黑子边框画笔 */
    private Paint blackBorderPaint;

    /** 白子边框画笔 */
    private Paint whiteBorderPaint;

    /** 最后一手标记画笔（红色） */
    private Paint lastMovePaint;
    private Paint hintPaint;
    private int[] hintMove;

    /** 星位画笔 */
    private Paint starPaint;

    /** 信息文本画笔 */
    private Paint infoPaint;

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public GoView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML布局创建时调用）。
     *
     * @param context 上下文
     * @param attrs   XML属性集
     */
    public GoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔。
     * <p>
     * 所有画笔均启用抗锯齿（ANTI_ALIAS_FLAG），确保绘制平滑。
     * 棋盘背景色为木色 rgb(220, 179, 92)。
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.rgb(220, 179, 92));

        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(Color.rgb(220, 179, 92));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(1.5f);

        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(Color.BLACK);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);

        blackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackBorderPaint.setColor(Color.rgb(50, 50, 50));
        blackBorderPaint.setStyle(Paint.Style.STROKE);
        blackBorderPaint.setStrokeWidth(1.5f);

        whiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteBorderPaint.setColor(Color.rgb(150, 150, 150));
        whiteBorderPaint.setStyle(Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(1.5f);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(255, 50, 50));
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.rgb(76, 175, 80));
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(5f);

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.BLACK);

        infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        infoPaint.setColor(Color.BLACK);
        infoPaint.setTextSize(26);
        infoPaint.setFakeBoldText(true);
    }

    /**
     * 设置游戏对象并刷新视图。
     *
     * @param game 围棋游戏逻辑对象
     */
    public void setGame(GoGame game) {
        this.game = game;
        invalidate();
    }

    /**
     * 设置交叉点点击监听器。
     *
     * @param listener 点击监听器
     */
    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void showHint(int x, int y) {
        hintMove = new int[]{x, y};
        invalidate();
    }

    public void clearHint() {
        hintMove = null;
        invalidate();
    }

    /**
     * View尺寸变化时重新计算棋盘布局参数。
     * <p>
     * 根据View宽高计算格子大小和偏移量，确保棋盘居中显示。
     * 垂直方向仅使用85%高度，预留空间给底部信息区。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int padding = 40;
        int usableW = w - padding * 2;
        int usableH = (int) (h * 0.85f) - padding * 2;
        cellSize = Math.min(usableW, usableH) / (GoGame.BOARD_SIZE - 1);
        float boardW = cellSize * (GoGame.BOARD_SIZE - 1);
        float boardH = cellSize * (GoGame.BOARD_SIZE - 1);
        offsetX = (w - boardW) / 2f;
        offsetY = padding + (usableH - boardH) / 2f;
    }

    /**
     * 绘制视图内容。
     * <p>
     * 绘制顺序：背景色 → 棋盘网格 → 棋子 → 信息文本。
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
        canvas.drawColor(Color.rgb(220, 179, 92));
        drawBoard(canvas);
        if (game != null) {
            drawStones(canvas);
            drawInfo(canvas);
        }
    }

    /**
     * 绘制棋盘网格线和星位。
     * <p>
     * 9路棋盘的星位为：(2,2) (6,2) (2,6) (6,6) (4,4)（天元）。
     *
     * @param canvas 画布
     */
    private void drawBoard(Canvas canvas) {
        for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
            float x1 = offsetX;
            float y1 = offsetY + i * cellSize;
            float x2 = offsetX + (GoGame.BOARD_SIZE - 1) * cellSize;
            float y2 = y1;
            canvas.drawLine(x1, y1, x2, y2, linePaint);

            x1 = offsetX + i * cellSize;
            y1 = offsetY;
            x2 = x1;
            y2 = offsetY + (GoGame.BOARD_SIZE - 1) * cellSize;
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        // 9路棋盘的5个星位
        int[][] starPoints = {{2,2},{6,2},{2,6},{6,6},{4,4}};
        for (int[] sp : starPoints) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            canvas.drawCircle(cx, cy, 4, starPaint);
        }
    }

    /**
     * 绘制棋盘上的所有棋子和最后一手标记。
     *
     * @param canvas 画布
     */
    private void drawStones(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int y = 0; y < GoGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GoGame.BOARD_SIZE; x++) {
                if (board[y][x] != GoGame.EMPTY) {
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float r = cellSize / 2f - 2;

                    if (board[y][x] == GoGame.BLACK) {
                        canvas.drawCircle(cx, cy, r, blackPaint);
                        canvas.drawCircle(cx, cy, r, blackBorderPaint);
                    } else {
                        canvas.drawCircle(cx, cy, r, whitePaint);
                        canvas.drawCircle(cx, cy, r, whiteBorderPaint);
                    }
                }
            }
        }

        // 绘制最后一手标记（红色小圆点）
        int[] lastMove = game.getLastMove();
        if (lastMove != null) {
            float cx = offsetX + lastMove[0] * cellSize;
            float cy = offsetY + lastMove[1] * cellSize;
            canvas.drawCircle(cx, cy, 5, lastMovePaint);
        }
        if (hintMove != null && board[hintMove[1]][hintMove[0]] == GoGame.EMPTY) {
            float cx = offsetX + hintMove[0] * cellSize;
            float cy = offsetY + hintMove[1] * cellSize;
            canvas.drawCircle(cx, cy, cellSize * 0.28f, hintPaint);
            canvas.drawCircle(cx, cy, cellSize * 0.10f, hintPaint);
        }
    }

    /**
     * 绘制棋盘下方信息区（吃子数）和对局结束遮罩。
     * <p>
     * 对局结束时绘制半透明黑色遮罩，中央显示结果文字。
     *
     * @param canvas 画布
     */
    private void drawInfo(Canvas canvas) {
        float y = offsetY + (GoGame.BOARD_SIZE - 1) * cellSize + 30;
        infoPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("黑方吃子: " + game.getBlackCaptures(), offsetX, y, infoPaint);
        infoPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("白方吃子: " + game.getWhiteCaptures(),
                offsetX + (GoGame.BOARD_SIZE - 1) * cellSize, y, infoPaint);

        if (game.isGameOver()) {
            // 绘制半透明遮罩
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), overPaint);

            overPaint.setColor(Color.WHITE);
            overPaint.setTextSize(42);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("对局结束", getWidth() / 2f, getHeight() / 2f - 20, overPaint);
            overPaint.setTextSize(28);
            canvas.drawText(game.getResultText(), getWidth() / 2f, getHeight() / 2f + 70, overPaint);
            canvas.drawText("黑吃" + game.getBlackCaptures() + "子  白吃" + game.getWhiteCaptures() + "子",
                    getWidth() / 2f, getHeight() / 2f + 30, overPaint);
        }
    }

    /**
     * 处理触摸事件，将触摸坐标映射到棋盘交叉点。
     * <p>
     * 使用Math.round四舍五入到最近的交叉点，仅在ACTION_DOWN时触发回调。
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver()) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = Math.round((event.getX() - offsetX) / cellSize);
            int y = Math.round((event.getY() - offsetY) / cellSize);
            if (x >= 0 && x < GoGame.BOARD_SIZE && y >= 0 && y < GoGame.BOARD_SIZE) {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(x, y);
                }
            }
        }
        return true;
    }

    /**
     * 交叉点点击监听器接口。
     */
    public interface OnCellClickListener {
        /**
         * 交叉点被点击时回调。
         *
         * @param x 横坐标（列索引）
         * @param y 纵坐标（行索引）
         */
        void onCellClick(int x, int y);
    }
}
