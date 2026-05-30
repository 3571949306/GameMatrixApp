package com.gamecenter.app.games.go;

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
 * 围棋棋盘自定义 View。
 *
 * <p>绘制简化版 9×9 围棋棋盘和棋子，处理触摸落子。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class GoView extends View {

    public static final int BOARD_SIZE = 9;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private static final int COLOR_BOARD = Color.parseColor("#DEB887");
    private static final int COLOR_LINE = Color.parseColor("#5B4A2F");
    private static final int COLOR_BLACK = Color.parseColor("#1A1A1A");
    private static final int COLOR_WHITE = Color.parseColor("#F5F0E8");
    private static final int COLOR_LAST_MOVE = Color.parseColor("#C44536");
    private static final int COLOR_TERRITORY_BLACK = Color.parseColor("#30000000");
    private static final int COLOR_TERRITORY_WHITE = Color.parseColor("#30FFFFFF");

    private Paint boardPaint;
    private Paint linePaint;
    private Paint blackPaint;
    private Paint whitePaint;
    private Paint lastMovePaint;
    private Paint territoryBlackPaint;
    private Paint territoryWhitePaint;
    private Paint shadowPaint;

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private float[][] territory = null; // -1=黑领地, 0=无, 1=白领地
    private boolean showTerritory = false;

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private OnCellClickListener listener;

    public GoView(@NonNull Context context) {
        super(context);
        init();
    }

    public GoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(COLOR_BOARD);
        boardPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(1.5f);
        linePaint.setStyle(Paint.Style.STROKE);

        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(COLOR_BLACK);
        blackPaint.setStyle(Paint.Style.FILL);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(COLOR_WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(COLOR_LAST_MOVE);
        lastMovePaint.setStyle(Paint.Style.FILL);

        territoryBlackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        territoryBlackPaint.setColor(COLOR_TERRITORY_BLACK);
        territoryBlackPaint.setStyle(Paint.Style.FILL);

        territoryWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        territoryWhitePaint.setColor(COLOR_TERRITORY_WHITE);
        territoryWhitePaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#30000000"));
        shadowPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.listener = listener;
    }

    public void setBoard(int[][] board) {
        this.board = board;
        invalidate();
    }

    public void setLastMove(int row, int col) {
        lastMoveRow = row;
        lastMoveCol = col;
        invalidate();
    }

    public void showTerritory(float[][] territory) {
        this.territory = territory;
        this.showTerritory = true;
        invalidate();
    }

    public void hideTerritory() {
        this.showTerritory = false;
        this.territory = null;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        float padding = 20f;
        float boardSize = viewWidth - 2 * padding;
        cellSize = boardSize / (BOARD_SIZE - 1);
        boardOffsetX = padding;
        boardOffsetY = padding;

        // 绘制棋盘背景
        canvas.drawRect(0, 0, viewWidth, viewWidth, boardPaint);

        // 绘制网格线
        for (int i = 0; i < BOARD_SIZE; i++) {
            float pos = boardOffsetX + i * cellSize;
            canvas.drawLine(pos, boardOffsetY, pos,
                    boardOffsetY + (BOARD_SIZE - 1) * cellSize, linePaint);
            pos = boardOffsetY + i * cellSize;
            canvas.drawLine(boardOffsetX, pos,
                    boardOffsetX + (BOARD_SIZE - 1) * cellSize, pos, linePaint);
        }

        // 绘制星位（天元和四星）
        float starRadius = 4f;
        int[][] starPoints = {{2, 2}, {2, 6}, {6, 2}, {6, 6}, {4, 4}};
        for (int[] point : starPoints) {
            float cx = boardOffsetX + point[1] * cellSize;
            float cy = boardOffsetY + point[0] * cellSize;
            canvas.drawCircle(cx, cy, starRadius, linePaint);
        }

        // 绘制领地标记
        if (showTerritory && territory != null) {
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (territory[r][c] != 0 && board[r][c] == EMPTY) {
                        float cx = boardOffsetX + c * cellSize;
                        float cy = boardOffsetY + r * cellSize;
                        float size = cellSize * 0.15f;
                        if (territory[r][c] < 0) {
                            canvas.drawRect(cx - size, cy - size, cx + size, cy + size,
                                    territoryBlackPaint);
                        } else {
                            canvas.drawCircle(cx, cy, size, territoryWhitePaint);
                        }
                    }
                }
            }
        }

        // 绘制棋子
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != EMPTY) {
                    drawStone(canvas, r, c, board[r][c]);
                }
            }
        }

        // 绘制最后一手标记
        if (lastMoveRow >= 0 && lastMoveCol >= 0) {
            float cx = boardOffsetX + lastMoveCol * cellSize;
            float cy = boardOffsetY + lastMoveRow * cellSize;
            float radius = cellSize * 0.12f;
            int lastColor = board[lastMoveRow][lastMoveCol];
            if (lastColor == BLACK) {
                lastMovePaint.setColor(Color.WHITE);
            } else {
                lastMovePaint.setColor(COLOR_LAST_MOVE);
            }
            canvas.drawCircle(cx, cy, radius, lastMovePaint);
        }
    }

    /**
     * 绘制棋子
     */
    private void drawStone(@NonNull Canvas canvas, int row, int col, int color) {
        float cx = boardOffsetX + col * cellSize;
        float cy = boardOffsetY + row * cellSize;
        float radius = cellSize * 0.43f;

        // 阴影
        canvas.drawCircle(cx + 2, cy + 2, radius, shadowPaint);

        // 棋子
        Paint paint = color == BLACK ? blackPaint : whitePaint;
        canvas.drawCircle(cx, cy, radius, paint);

        // 边框
        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setStyle(Paint.Style.STROKE);
        borderP.setStrokeWidth(1f);
        borderP.setColor(color == BLACK ? Color.parseColor("#0A0A0A") : Color.parseColor("#C0C0C0"));
        canvas.drawCircle(cx, cy, radius, borderP);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null) {
            int col = Math.round((event.getX() - boardOffsetX) / cellSize);
            int row = Math.round((event.getY() - boardOffsetY) / cellSize);
            if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE) {
                listener.onCellClick(row, col);
            }
        }
        return true;
    }
}
