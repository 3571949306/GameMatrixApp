package com.gamecenter.app.games.checkers;

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
 * 跳棋棋盘自定义 View。
 *
 * <p>绘制 8×8 跳棋棋盘和棋子，处理触摸选子和落子。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class CheckersView extends View {

    public static final int BOARD_SIZE = 8;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int BLACK_KING = 2;
    public static final int WHITE = 3;
    public static final int WHITE_KING = 4;

    private static final int COLOR_DARK = Color.parseColor("#5B8A72");
    private static final int COLOR_LIGHT = Color.parseColor("#F5F0E8");
    private static final int COLOR_BLACK = Color.parseColor("#2D2D2D");
    private static final int COLOR_WHITE = Color.parseColor("#FBF9F6");
    private static final int COLOR_SELECTED = Color.parseColor("#FFD700");
    private static final int COLOR_VALID = Color.parseColor("#D4EDE1");
    private static final int COLOR_KING_CROWN = Color.parseColor("#FFD700");

    private Paint darkPaint;
    private Paint lightPaint;
    private Paint blackPiecePaint;
    private Paint whitePiecePaint;
    private Paint selectedPaint;
    private Paint validPaint;
    private Paint kingPaint;
    private Paint borderPaint;
    private Paint crownPaint;

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean[][] validMoves = new boolean[BOARD_SIZE][BOARD_SIZE];

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private OnCellClickListener listener;

    public CheckersView(@NonNull Context context) {
        super(context);
        init();
    }

    public CheckersView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckersView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(COLOR_DARK);
        darkPaint.setStyle(Paint.Style.FILL);

        lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPaint.setColor(COLOR_LIGHT);
        lightPaint.setStyle(Paint.Style.FILL);

        blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPiecePaint.setColor(COLOR_BLACK);
        blackPiecePaint.setStyle(Paint.Style.FILL);

        whitePiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePiecePaint.setColor(COLOR_WHITE);
        whitePiecePaint.setStyle(Paint.Style.FILL);

        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(COLOR_SELECTED);
        selectedPaint.setAlpha(120);
        selectedPaint.setStyle(Paint.Style.FILL);

        validPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        validPaint.setColor(COLOR_VALID);
        validPaint.setAlpha(160);
        validPaint.setStyle(Paint.Style.FILL);

        kingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        kingPaint.setColor(COLOR_KING_CROWN);
        kingPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#2D2D2D"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);

        crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownPaint.setColor(COLOR_KING_CROWN);
        crownPaint.setTextSize(24f);
        crownPaint.setTextAlign(Paint.Align.CENTER);
        crownPaint.setFakeBoldText(true);
    }

    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.listener = listener;
    }

    public void setBoard(int[][] board) {
        this.board = board;
        invalidate();
    }

    public void setSelected(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        invalidate();
    }

    public void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        validMoves = new boolean[BOARD_SIZE][BOARD_SIZE];
        invalidate();
    }

    public void setValidMoves(boolean[][] validMoves) {
        this.validMoves = validMoves;
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
        float padding = 10f;
        float boardSize = viewWidth - 2 * padding;
        cellSize = boardSize / BOARD_SIZE;
        boardOffsetX = padding;
        boardOffsetY = padding;

        // 绘制棋盘
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                float left = boardOffsetX + c * cellSize;
                float top = boardOffsetY + r * cellSize;

                // 选中高亮
                if (r == selectedRow && c == selectedCol) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, selectedPaint);
                }
                // 合法落子高亮
                else if (validMoves[r][c]) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, validPaint);
                }
                // 格子颜色
                else if ((r + c) % 2 == 0) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, lightPaint);
                } else {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, darkPaint);
                }

                // 绘制棋子
                if (board[r][c] != EMPTY) {
                    drawPiece(canvas, r, c);
                }
            }
        }
    }

    /**
     * 绘制棋子
     */
    private void drawPiece(@NonNull Canvas canvas, int row, int col) {
        float cx = boardOffsetX + col * cellSize + cellSize / 2f;
        float cy = boardOffsetY + row * cellSize + cellSize / 2f;
        float radius = cellSize * 0.38f;

        int piece = board[row][col];
        Paint paint;
        boolean isKing = false;

        switch (piece) {
            case BLACK:
                paint = blackPiecePaint;
                break;
            case BLACK_KING:
                paint = blackPiecePaint;
                isKing = true;
                break;
            case WHITE:
                paint = whitePiecePaint;
                break;
            case WHITE_KING:
                paint = whitePiecePaint;
                isKing = true;
                break;
            default:
                return;
        }

        // 绘制阴影
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#40000000"));
        canvas.drawCircle(cx + 2, cy + 2, radius, shadowPaint);

        // 绘制棋子
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // 绘制王冠标记
        if (isKing) {
            crownPaint.setTextSize(cellSize * 0.35f);
            canvas.drawText("♛", cx, cy + cellSize * 0.12f, crownPaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null) {
            int col = (int) ((event.getX() - boardOffsetX) / cellSize);
            int row = (int) ((event.getY() - boardOffsetY) / cellSize);
            if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE) {
                listener.onCellClick(row, col);
            }
        }
        return true;
    }
}
