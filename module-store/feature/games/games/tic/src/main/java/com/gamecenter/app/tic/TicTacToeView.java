package com.gamecenter.app.tic;

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
 * 井字棋棋盘自定义 View（模块独立版本）。
 *
 * <p>绘制 3x3 棋盘网格、X/O 棋子、最新落子高亮、智能提示指引与胜利划线。</p>
 */
public class TicTacToeView extends View {

    private static final int GRID_SIZE = 3;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint oPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    private int[][] board = new int[GRID_SIZE][GRID_SIZE];

    private int winStartRow = -1;
    private int winStartCol = -1;
    private int winEndRow = -1;
    private int winEndCol = -1;
    private boolean gameOver = false;

    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private int hintRow = -1;
    private int hintCol = -1;

    private boolean darkMode = false;

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
        updateColors();

        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);

        xPaint.setStrokeWidth(12f);
        xPaint.setStrokeCap(Paint.Cap.ROUND);
        xPaint.setStyle(Paint.Style.STROKE);

        oPaint.setStrokeWidth(12f);
        oPaint.setStyle(Paint.Style.STROKE);

        highlightPaint.setColor(Color.parseColor("#FFB300"));
        highlightPaint.setStrokeWidth(14f);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setAlpha(220);

        lastMovePaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setStrokeWidth(3f);
        lastMovePaint.setColor(Color.parseColor("#38BDF8"));

        hintPaint.setStyle(Paint.Style.FILL);
        hintPaint.setColor(Color.parseColor("#FFD54F"));
        hintPaint.setAlpha(80);
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        updateColors();
        invalidate();
    }

    private void updateColors() {
        if (darkMode) {
            bgPaint.setColor(Color.parseColor("#1E293B"));
            linePaint.setColor(Color.parseColor("#475569"));
            xPaint.setColor(Color.parseColor("#34D399")); // 浅绿翡翠
            oPaint.setColor(Color.parseColor("#F87171")); // 珊瑚红
        } else {
            bgPaint.setColor(Color.parseColor("#FFFFFF"));
            linePaint.setColor(Color.parseColor("#CBD5E1"));
            xPaint.setColor(Color.parseColor("#059669")); // 深绿
            oPaint.setColor(Color.parseColor("#DC2626")); // 赤红
        }
    }

    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void setBoard(int[][] board) {
        this.board = board;
        invalidate();
    }

    public void setLastMove(int row, int col) {
        this.lastMoveRow = row;
        this.lastMoveCol = col;
        invalidate();
    }

    public void setHintCell(int row, int col) {
        this.hintRow = row;
        this.hintCol = col;
        invalidate();
    }

    public void clearHighlights() {
        this.hintRow = -1;
        this.hintCol = -1;
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
        lastMoveRow = -1;
        lastMoveCol = -1;
        hintRow = -1;
        hintCol = -1;
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

        // 绘制圆角棋盘背景底色
        RectF boardRect = new RectF(boardOffsetX, boardOffsetY, boardOffsetX + boardSize, boardOffsetY + boardSize);
        canvas.drawRoundRect(boardRect, 24f, 24f, bgPaint);

        // 绘制提示落子格高亮
        if (hintRow >= 0 && hintCol >= 0 && hintRow < GRID_SIZE && hintCol < GRID_SIZE) {
            float left = boardOffsetX + hintCol * cellSize + 6f;
            float top = boardOffsetY + hintRow * cellSize + 6f;
            RectF hintRect = new RectF(left, top, left + cellSize - 12f, top + cellSize - 12f);
            canvas.drawRoundRect(hintRect, 16f, 16f, hintPaint);
        }

        // 绘制分割线
        for (int i = 1; i < GRID_SIZE; i++) {
            float x = boardOffsetX + i * cellSize;
            canvas.drawLine(x, boardOffsetY + 10f, x, boardOffsetY + boardSize - 10f, linePaint);
            float y = boardOffsetY + i * cellSize;
            canvas.drawLine(boardOffsetX + 10f, y, boardOffsetX + boardSize - 10f, y, linePaint);
        }

        // 绘制棋子
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (board[row][col] == 1) {
                    drawX(canvas, row, col);
                } else if (board[row][col] == 2) {
                    drawO(canvas, row, col);
                }
            }
        }

        // 绘制最后一步落子标记框
        if (lastMoveRow >= 0 && lastMoveCol >= 0 && lastMoveRow < GRID_SIZE && lastMoveCol < GRID_SIZE) {
            float left = boardOffsetX + lastMoveCol * cellSize + 10f;
            float top = boardOffsetY + lastMoveRow * cellSize + 10f;
            RectF lmRect = new RectF(left, top, left + cellSize - 20f, top + cellSize - 20f);
            canvas.drawRoundRect(lmRect, 14f, 14f, lastMovePaint);
        }

        // 绘制胜利连线
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
        float half = cellSize * 0.28f;
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint);
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, xPaint);
    }

    private void drawO(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float radius = cellSize * 0.28f;
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
