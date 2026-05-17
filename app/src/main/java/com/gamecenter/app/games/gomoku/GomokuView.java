package com.gamecenter.app.games.gomoku;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.gamecenter.app.R;

/**
 * 五子棋棋盘自定义视图。
 * <p>
 * 负责15×15五子棋棋盘的绘制和触摸交互，包括：
 * <ul>
 *   <li>棋盘网格线和星位绘制</li>
 *   <li>3D渐变效果棋子绘制（黑白棋子带光泽效果）</li>
 *   <li>最后一手标记（红色圆点）</li>
 *   <li>悬停预览（手指移动时显示半透明棋子轮廓）</li>
 *   <li>回合信息和胜负结果显示</li>
 *   <li>对局结束遮罩层</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘尺寸自适应View大小，保持正方形</li>
 *   <li>棋子使用 {@link RadialGradient} 实现立体光泽效果</li>
 *   <li>触摸坐标通过四舍五入映射到最近的交叉点</li>
 *   <li>ACTION_MOVE事件用于悬停预览，ACTION_DOWN用于实际落子</li>
 * </ul>
 */
public class GomokuView extends View {

    /** 游戏逻辑对象 */
    private GomokuGame game;

    /** 每格像素大小 */
    private int cellSize;

    /** 棋盘内边距 */
    private int boardPadding;

    /** 棋盘绘制偏移量（水平方向），用于居中 */
    private float offsetX;

    /** 棋盘绘制偏移量（垂直方向），用于居中 */
    private float offsetY;

    /** 当前悬停位置 [x, y] */
    private int[] hoverPos;

    /** 交叉点点击监听器 */
    private OnCellClickListener onCellClickListener;

    /** 控制操作监听器（悔棋/重开） */
    private OnControlActionListener onControlActionListener;

    /** 游戏结束监听器 */
    private OnGameOverListener gameOverListener;

    /** 背景画笔 */
    private Paint bgPaint, linePaint, blackPiecePaint, whitePiecePaint;

    /** 黑子/白子边框画笔 */
    private Paint blackPieceBorderPaint, whitePieceBorderPaint;

    /** 最后一手标记画笔（红色） */
    private Paint lastMovePaint;

    /** 悬停预览画笔（半透明） */
    private Paint hoverPaint;

    /** 星位画笔 */
    private Paint starPointPaint;

    /** 信息文本画笔 */
    private Paint textPaint;

    /** 高亮边缘颜色 */
    private int highlightEdgeColor;

    /** 15路棋盘的5个星位坐标 */
    private static final int[][] STAR_POINTS = {{3, 3}, {3, 11}, {7, 7}, {11, 3}, {11, 11}};

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public GomokuView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML布局创建时调用）。
     *
     * @param context 上下文
     * @param attrs   XML属性集
     */
    public GomokuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔和颜色资源。
     * <p>
     * 颜色值从资源文件中读取，支持主题切换。
     * 所有画笔均启用抗锯齿。
     */
    private void init() {
        cellSize = 0;
        boardPadding = 20;

        Resources res = getResources();
        int bg = res.getColor(R.color.gomoku_bg, null);
        int line = res.getColor(R.color.gomoku_line, null);
        int blackP = res.getColor(R.color.gomoku_black_piece, null);
        int whiteP = res.getColor(R.color.gomoku_white_piece, null);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bg);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(line);
        linePaint.setStrokeWidth(1.5f);

        blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPiecePaint.setColor(blackP);

        blackPieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceBorderPaint.setStyle(Paint.Style.STROKE);
        blackPieceBorderPaint.setColor(Color.rgb(60, 60, 60));
        blackPieceBorderPaint.setStrokeWidth(1);

        whitePiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePiecePaint.setColor(whiteP);

        whitePieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceBorderPaint.setStyle(Paint.Style.STROKE);
        whitePieceBorderPaint.setColor(Color.rgb(180, 180, 180));
        whitePieceBorderPaint.setStrokeWidth(1);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(255, 50, 50));

        hoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hoverPaint.setColor(Color.argb(100, 200, 200, 200));
        hoverPaint.setStyle(Paint.Style.STROKE);
        hoverPaint.setStrokeWidth(1);

        starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPointPaint.setColor(line);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(line);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);

        highlightEdgeColor = Color.rgb(255, 50, 50);

        hoverPos = null;
    }

    /**
     * 设置游戏对象并刷新视图。
     *
     * @param game 五子棋游戏逻辑对象
     */
    public void setGame(GomokuGame game) {
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

    /**
     * 设置控制操作监听器。
     *
     * @param listener 控制操作监听器
     */
    public void setOnControlActionListener(OnControlActionListener listener) {
        this.onControlActionListener = listener;
    }

    /**
     * 清除悬停预览位置。
     */
    public void clearHover() {
        hoverPos = null;
        invalidate();
    }

    /**
     * View尺寸变化时重新计算棋盘布局参数。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    /**
     * 根据View尺寸重新计算格子大小和偏移量，确保棋盘居中。
     *
     * @param w View宽度
     * @param h View高度
     */
    private void recalcDimensions(int w, int h) {
        int usableWidth = w - boardPadding * 2;
        int usableHeight = h - boardPadding * 2;
        cellSize = Math.min(usableWidth, usableHeight) / (GomokuGame.BOARD_SIZE - 1);
        int totalWidth = cellSize * (GomokuGame.BOARD_SIZE - 1);
        int totalHeight = cellSize * (GomokuGame.BOARD_SIZE - 1);
        offsetX = (w - totalWidth) / 2f;
        offsetY = (h - totalHeight) / 2f;
    }

    /**
     * 绘制视图内容。
     * <p>
     * 绘制顺序：背景 → 棋盘网格 → 棋子 → 游戏信息。
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) {
            recalcDimensions(getWidth(), getHeight());
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        drawBoard(canvas);
        if (game != null) {
            drawPieces(canvas);
            drawGameInfo(canvas);
        }
    }

    /**
     * 绘制游戏信息（回合数、当前执子方）和对局结束遮罩。
     * <p>
     * 对局结束时绘制半透明黑色遮罩，中央显示胜负结果。
     * 同时触发游戏结束回调通知Activity层。
     *
     * @param canvas 画布
     */
    private void drawGameInfo(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        textPaint.setTextSize(24);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // 回合数 = (落子数 + 1) / 2
        int currentTurn = (game.getMoveCount() + 1) / 2;
        String turnText = "第 " + currentTurn + " 回合";
        canvas.drawText(turnText, 20, 40, textPaint);

        int currentPlayer = game.getCurrentPlayer();
        String playerText = currentPlayer == GomokuGame.BLACK ? "黑方回合" : "白方回合";
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(playerText, w - 20, 40, textPaint);

        if (game.isGameOver()) {
            // 绘制半透明遮罩
            Paint overlayPaint = new Paint();
            overlayPaint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, overlayPaint);

            textPaint.setTextSize(48);
            textPaint.setTextAlign(Paint.Align.CENTER);

            Integer winner = game.getWinner();
            String resultText;
            if (winner == null) {
                resultText = "平局!";
            } else if (winner == GomokuGame.BLACK) {
                resultText = "黑方胜利!";
            } else {
                resultText = "白方胜利!";
            }

            textPaint.setColor(Color.WHITE);
            canvas.drawText(resultText, w / 2f, h / 2f - 30, textPaint);

            textPaint.setTextSize(24);
            canvas.drawText("最终回合数: " + game.getMoveCount(), w / 2f, h / 2f + 30, textPaint);

            // 通知Activity层游戏结束
            if (gameOverListener != null) {
                gameOverListener.onGameOver(winner);
            }
        }
    }

    /**
     * 绘制棋盘网格线和星位。
     *
     * @param canvas 画布
     */
    private void drawBoard(Canvas canvas) {
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float x1 = offsetX;
            float y1 = offsetY + i * cellSize;
            float x2 = offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            float y2 = y1;
            canvas.drawLine(x1, y1, x2, y2, linePaint);

            x1 = offsetX + i * cellSize;
            y1 = offsetY;
            x2 = x1;
            y2 = offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        // 绘制5个星位
        for (int[] sp : STAR_POINTS) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            canvas.drawCircle(cx, cy, 4, starPointPaint);
        }
    }

    /**
     * 绘制棋盘上的所有棋子、最后一手标记和悬停预览。
     *
     * @param canvas 画布
     */
    private void drawPieces(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] != GomokuGame.EMPTY) {
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float radius = cellSize / 2f - 2;

                    if (board[y][x] == GomokuGame.BLACK) {
                        drawPiece3D(canvas, cx, cy, radius, blackPiecePaint, blackPieceBorderPaint, true);
                    } else {
                        drawPiece3D(canvas, cx, cy, radius, whitePiecePaint, whitePieceBorderPaint, false);
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

        // 绘制悬停预览（半透明棋子轮廓）
        if (hoverPos != null && !game.isGameOver()) {
            int hx = hoverPos[0], hy = hoverPos[1];
            if (game.isValidMove(hx, hy)) {
                float cx = offsetX + hx * cellSize;
                float cy = offsetY + hy * cellSize;
                float r = cellSize / 2f - 2;
                canvas.drawCircle(cx, cy, r, hoverPaint);
            }
        }
    }

    /**
     * 绘制带3D渐变效果的棋子。
     * <p>
     * 使用 {@link RadialGradient} 在棋子左上方创建高光效果，
     * 模拟光源从左上方照射的立体感。
     *
     * @param canvas  画布
     * @param cx      棋子中心X坐标
     * @param cy      棋子中心Y坐标
     * @param radius  棋子半径
     * @param fill    填充画笔（未使用，渐变覆盖）
     * @param border  边框画笔
     * @param isBlack 是否为黑子
     */
    private void drawPiece3D(Canvas canvas, float cx, float cy, float radius, Paint fill, Paint border, boolean isBlack) {
        int baseColor = isBlack ? Color.rgb(20, 20, 20) : Color.rgb(240, 240, 240);
        int highlightColor = isBlack ? Color.rgb(80, 80, 80) : Color.rgb(255, 255, 255);
        // 高光偏移到左上方，模拟光源效果
        RadialGradient gradient = new RadialGradient(cx - radius * 0.3f, cy - radius * 0.3f, radius,
                new int[]{highlightColor, baseColor}, null, Shader.TileMode.CLAMP);
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, radius, gradPaint);
        canvas.drawCircle(cx, cy, radius, border);
    }

    /**
     * 处理触摸事件。
     * <p>
     * ACTION_DOWN：将触摸坐标映射到最近的交叉点，触发落子回调。
     * ACTION_MOVE：更新悬停预览位置，实时显示半透明棋子轮廓。
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
            if (x >= 0 && x < GomokuGame.BOARD_SIZE && y >= 0 && y < GomokuGame.BOARD_SIZE) {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(x, y);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            // 更新悬停位置
            int hx = Math.round((event.getX() - offsetX) / cellSize);
            int hy = Math.round((event.getY() - offsetY) / cellSize);
            if (hx >= 0 && hx < GomokuGame.BOARD_SIZE && hy >= 0 && hy < GomokuGame.BOARD_SIZE) {
                hoverPos = new int[]{hx, hy};
            } else {
                hoverPos = null;
            }
            invalidate();
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

    /**
     * 控制操作监听器接口（悔棋/重开）。
     */
    public interface OnControlActionListener {
        /** 悔棋回调 */
        void onUndo();
        /** 重新开始回调 */
        void onRestart();
    }

    /**
     * 游戏结束监听器接口。
     */
    public interface OnGameOverListener {
        /**
         * 游戏结束时回调。
         *
         * @param winner 获胜方颜色，null表示平局
         */
        void onGameOver(Integer winner);
    }

    /**
     * 设置游戏结束监听器。
     *
     * @param listener 游戏结束监听器
     */
    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }
}
