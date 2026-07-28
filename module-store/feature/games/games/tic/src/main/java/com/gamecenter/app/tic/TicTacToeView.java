package com.gamecenter.app.tic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 井字棋棋盘自定义 View（模块独立版本）。
 *
 * <p>绘制 3x3 棋盘网格和 X/O 棋子，处理玩家触摸落子。</p>
 */
public class TicTacToeView extends View {

    private static final int COLOR_BG = Color.parseColor("#F5F0E8");
    private static final int COLOR_LINE = Color.parseColor("#5B8A72");
    private static final int COLOR_X = Color.parseColor("#2D6A4F");
    private static final int COLOR_O = Color.parseColor("#C44536");
    private static final int COLOR_HIGHLIGHT = Color.parseColor("#FFD700");

    private static final int GRID_SIZE = 3;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint oPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    private int[][] board = new int[GRID_SIZE][GRID_SIZE];

    private int winStartRow = -1;
    private int winStartCol = -1;
    private int winEndRow = -1;
    private int winEndCol = -1;
    private boolean gameOver = false;

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

    private void init() {
        bgPaint.setColor(COLOR_BG);
        bgPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);

        xPaint.setColor(COLOR_X);
        xPaint.setStrokeWidth(10f);
        xPaint.setStrokeCap(Paint.Cap.ROUND);
        xPaint.setStyle(Paint.Style.STROKE);

        oPaint.setColor(COLOR_O);
        oPaint.setStrokeWidth(10f);
        oPaint.setStyle(Paint.Style.STROKE);

        highlightPaint.setColor(COLOR_HIGHLIGHT);
        highlightPaint.setStrokeWidth(14f);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setAlpha(180);
    }

    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void setBoard(int[][] board) {
        this.board = board;
        invalidate();
    }

    public void setWinLine(int startRow, int startCol, int endRow, int endCol) {
        this.winStartRow = startRow;
        this.winStartCol = startCol;
        this.winEndRow = endRow;
        this.winEndCol = endCol;
        this.gameOver = true;
        invalidate();
    }

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

        float padding = 20f;
        float boardSize = Math.min(viewWidth, viewHeight) - 2 * padding;
        cellSize = boardSize / GRID_SIZE;
        boardOffsetX = (viewWidth - boardSize) / 2f;
        boardOffsetY = (viewHeight - boardSize) / 2f;

        canvas.drawColor(COLOR_BG);

        for (int i = 1; i < GRID_SIZE; i++) {
            float x = boardOffsetX + i * cellSize;
            canvas.drawLine(x, boardOffsetY, x, boardOffsetY + GRID_SIZE * cellSize, linePaint);
            float y = boardOffsetY + i * cellSize;
            canvas.drawLine(boardOffsetX, y, boardOffsetX + GRID_SIZE * cellSize, y, linePaint);
        }

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (board[row][col] == 1) {
                    drawX(canvas, row, col);
                } else if (board[row][col] == 2) {
                    drawO(canvas, row, col);
                }
            }
        }

        if (winStartRow >= 0 && winEndRow >= 0) {
            float startX = boardOffsetX + winStartCol * cellSize + cellSize / 2f;
            float startY = boardOffsetY + winStartRow * cellSize + cellSize / 2f;
            float endX = boardOffsetX + winEndCol * cellSize + cellSize / 2f;
            float endY = boardOffsetY + winEndRow * cellSize + cellSize / 2f;
            canvas.drawLine(startX, startY, endX, endY, highlightPaint);
        }
    }

    private void drawX(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float half = cellSize * 0.3f;
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint);
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, xPaint);
    }

    private void drawO(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float radius = cellSize * 0.3f;
        canvas.drawCircle(cx, cy, radius, oPaint);
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
