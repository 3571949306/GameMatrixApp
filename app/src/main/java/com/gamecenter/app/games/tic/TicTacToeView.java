package com.gamecenter.app.games.tic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 井字棋棋盘自定义 View。
 *
 * <p>绘制 3x3 棋盘网格和 X/O 棋子，处理玩家触摸落子。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class TicTacToeView extends View {

    /** 棋盘背景色 */
    private static final int COLOR_BG = Color.parseColor("#F5F0E8");
    /** 网格线颜色 */
    private static final int COLOR_LINE = Color.parseColor("#5B8A72");
    /** X 棋子颜色 */
    private static final int COLOR_X = Color.parseColor("#2D6A4F");
    /** O 棋子颜色 */
    private static final int COLOR_O = Color.parseColor("#C44536");
    /** 胜利高亮色 */
    private static final int COLOR_HIGHLIGHT = Color.parseColor("#FFD700");

    /** 网格数量 */
    private static final int GRID_SIZE = 3;

    private Paint linePaint;
    private Paint xPaint;
    private Paint oPaint;
    private Paint highlightPaint;
    private Paint bgPaint;

    /** 单元格尺寸（像素） */
    private float cellSize;
    /** 棋盘偏移 X */
    private float boardOffsetX;
    /** 棋盘偏移 Y */
    private float boardOffsetY;

    /** 棋盘状态：0=空, 1=X, 2=O */
    private int[][] board = new int[GRID_SIZE][GRID_SIZE];

    /** 胜利连线起点和终点（用于高亮） */
    private int winStartRow = -1;
    private int winStartCol = -1;
    private int winEndRow = -1;
    private int winEndCol = -1;

    /** 是否游戏结束 */
    private boolean gameOver = false;

    /** 落子监听器 */
    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private OnCellClickListener onCellClickListener;

    public TicTacToeView(@NonNull Context context) {
        super(context);
        init();
    }

    public TicTacToeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TicTacToeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(COLOR_BG);
        bgPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);

        xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xPaint.setColor(COLOR_X);
        xPaint.setStrokeWidth(10f);
        xPaint.setStrokeCap(Paint.Cap.ROUND);
        xPaint.setStyle(Paint.Style.STROKE);

        oPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        oPaint.setColor(COLOR_O);
        oPaint.setStrokeWidth(10f);
        oPaint.setStyle(Paint.Style.STROKE);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(COLOR_HIGHLIGHT);
        highlightPaint.setStrokeWidth(14f);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setAlpha(180);
    }

    /**
     * 设置落子监听器
     */
    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    /**
     * 更新棋盘状态
     *
     * @param board 3x3 棋盘数组（0=空, 1=X, 2=O）
     */
    public void setBoard(int[][] board) {
        this.board = board;
        invalidate();
    }

    /**
     * 设置胜利连线
     *
     * @param startRow 起始行
     * @param startCol 起始列
     * @param endRow   结束行
     * @param endCol   结束列
     */
    public void setWinLine(int startRow, int startCol, int endRow, int endCol) {
        this.winStartRow = startRow;
        this.winStartCol = startCol;
        this.winEndRow = endRow;
        this.winEndCol = endCol;
        this.gameOver = true;
        invalidate();
    }

    /**
     * 清除棋盘并重绘
     */
    public void clearBoard() {
        board = new int[GRID_SIZE][GRID_SIZE];
        winStartRow = -1;
        winStartCol = -1;
        winEndRow = -1;
        winEndCol = -1;
        gameOver = false;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // 计算单元格尺寸和偏移
        float padding = 20f;
        float boardSize = Math.min(viewWidth, viewHeight) - 2 * padding;
        cellSize = boardSize / GRID_SIZE;
        boardOffsetX = (viewWidth - boardSize) / 2f;
        boardOffsetY = (viewHeight - boardSize) / 2f;

        // 绘制背景
        canvas.drawColor(COLOR_BG);

        // 绘制网格线
        drawGridLines(canvas);

        // 绘制棋子
        drawPieces(canvas);

        // 绘制胜利连线
        if (winStartRow >= 0 && winEndRow >= 0) {
            drawWinLine(canvas);
        }
    }

    /**
     * 绘制网格线
     */
    private void drawGridLines(@NonNull Canvas canvas) {
        for (int i = 1; i < GRID_SIZE; i++) {
            // 垂直线
            float x = boardOffsetX + i * cellSize;
            canvas.drawLine(x, boardOffsetY, x, boardOffsetY + GRID_SIZE * cellSize, linePaint);
            // 水平线
            float y = boardOffsetY + i * cellSize;
            canvas.drawLine(boardOffsetX, y, boardOffsetX + GRID_SIZE * cellSize, y, linePaint);
        }
    }

    /**
     * 绘制所有棋子
     */
    private void drawPieces(@NonNull Canvas canvas) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (board[row][col] == 1) {
                    drawX(canvas, row, col);
                } else if (board[row][col] == 2) {
                    drawO(canvas, row, col);
                }
            }
        }
    }

    /**
     * 绘制 X 棋子
     */
    private void drawX(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float half = cellSize * 0.3f;
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint);
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, xPaint);
    }

    /**
     * 绘制 O 棋子
     */
    private void drawO(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float radius = cellSize * 0.3f;
        canvas.drawCircle(cx, cy, radius, oPaint);
    }

    /**
     * 绘制胜利连线
     */
    private void drawWinLine(@NonNull Canvas canvas) {
        float startX = boardOffsetX + winStartCol * cellSize + cellSize / 2f;
        float startY = boardOffsetY + winStartRow * cellSize + cellSize / 2f;
        float endX = boardOffsetX + winEndCol * cellSize + cellSize / 2f;
        float endY = boardOffsetY + winEndRow * cellSize + cellSize / 2f;
        canvas.drawLine(startX, startY, endX, endY, highlightPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (gameOver || onCellClickListener == null) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            int col = (int) ((x - boardOffsetX) / cellSize);
            int row = (int) ((y - boardOffsetY) / cellSize);
            if (row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE) {
                if (board[row][col] == 0) {
                    onCellClickListener.onCellClick(row, col);
                }
            }
        }
        return true;
    }
}
