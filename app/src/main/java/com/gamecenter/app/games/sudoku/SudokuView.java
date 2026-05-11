package com.gamecenter.app.games.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class SudokuView extends View {

    private static final int COLOR_BOARD_BG = 0xFFFFF8F0;
    private static final int COLOR_THIN_GRID = 0xFFB0B0B0;
    private static final int COLOR_THICK_GRID = 0xFF333333;
    private static final int COLOR_SELECTED_BG = 0xFFE8D5B7;
    private static final int COLOR_SAME_NUM_BG = 0xFFBBDEFB;
    private static final int COLOR_ERROR_BG = 0xFFFFCDD2;
    private static final int COLOR_FIXED_TEXT = 0xFF1A1A1A;
    private static final int COLOR_PLAYER_TEXT = 0xFFD84315;
    private static final int COLOR_SAME_NUM_TEXT = 0xFF1565C0;
    private static final int COLOR_SOLVED_OVERLAY = 0x5500C853;

    private SudokuGame game;
    private Paint gridThinPaint;
    private Paint gridThickPaint;
    private Paint boardBgPaint;
    private Paint selectedBgPaint;
    private Paint sameNumBgPaint;
    private Paint errorBgPaint;
    private Paint fixedTextPaint;
    private Paint playerTextPaint;
    private Paint sameNumTextPaint;
    private Paint solvedOverlayPaint;

    private int selectedX = -1, selectedY = -1;
    private float cellSize;
    private float offsetX, offsetY;

    private OnCellSelectedListener listener;
    private OnSolvedListener solvedListener;

    public interface OnCellSelectedListener {
        void onCellSelected(int x, int y);
    }

    public interface OnSolvedListener {
        void onSolved(long elapsedMs);
    }

    public void setOnSolvedListener(OnSolvedListener listener) {
        this.solvedListener = listener;
    }

    public SudokuView(Context context) {
        super(context);
        init();
    }

    public SudokuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardBgPaint.setColor(COLOR_BOARD_BG);
        boardBgPaint.setStyle(Paint.Style.FILL);

        gridThinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridThinPaint.setColor(COLOR_THIN_GRID);
        gridThinPaint.setStrokeWidth(1.5f);
        gridThinPaint.setStyle(Paint.Style.STROKE);

        gridThickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridThickPaint.setColor(COLOR_THICK_GRID);
        gridThickPaint.setStrokeWidth(3.5f);
        gridThickPaint.setStyle(Paint.Style.STROKE);

        selectedBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedBgPaint.setColor(COLOR_SELECTED_BG);
        selectedBgPaint.setStyle(Paint.Style.FILL);

        sameNumBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sameNumBgPaint.setColor(COLOR_SAME_NUM_BG);
        sameNumBgPaint.setStyle(Paint.Style.FILL);

        errorBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorBgPaint.setColor(COLOR_ERROR_BG);
        errorBgPaint.setStyle(Paint.Style.FILL);

        fixedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fixedTextPaint.setColor(COLOR_FIXED_TEXT);
        fixedTextPaint.setTextAlign(Paint.Align.CENTER);
        fixedTextPaint.setFakeBoldText(true);

        playerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerTextPaint.setColor(COLOR_PLAYER_TEXT);
        playerTextPaint.setTextAlign(Paint.Align.CENTER);

        sameNumTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sameNumTextPaint.setColor(COLOR_SAME_NUM_TEXT);
        sameNumTextPaint.setTextAlign(Paint.Align.CENTER);
        sameNumTextPaint.setFakeBoldText(true);

        solvedOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        solvedOverlayPaint.setColor(COLOR_SOLVED_OVERLAY);
        solvedOverlayPaint.setStyle(Paint.Style.FILL);
    }

    public void setGame(SudokuGame game) {
        this.game = game;
    }

    public void setOnCellSelectedListener(OnCellSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h) - 32;
        cellSize = size / 9;
        offsetX = (w - size) / 2;
        offsetY = (h - size) / 2;
        float textSize = cellSize * 0.5f;
        fixedTextPaint.setTextSize(textSize);
        playerTextPaint.setTextSize(textSize);
        sameNumTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        float boardSize = cellSize * 9;
        int[][] board = game.getBoard();

        canvas.drawRoundRect(new RectF(offsetX - 4, offsetY - 4, offsetX + boardSize + 4, offsetY + boardSize + 4), 8, 8, boardBgPaint);

        int selectedValue = (selectedX >= 0 && selectedY >= 0) ? board[selectedY][selectedX] : 0;

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;

                if (x == selectedX && y == selectedY) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, selectedBgPaint);
                } else if (selectedValue > 0 && board[y][x] == selectedValue) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, sameNumBgPaint);
                }

                int value = board[y][x];
                if (value != 0) {
                    float textY = top + cellSize / 2 - (playerTextPaint.ascent() + playerTextPaint.descent()) / 2;

                    boolean isError = (game.getErrorMatrix() != null && game.isError(x, y));
                    if (isError && x != selectedX) {
                        canvas.drawRect(left, top, left + cellSize, top + cellSize, errorBgPaint);
                    }

                    Paint p;
                    if (game.isFixed(x, y)) {
                        p = fixedTextPaint;
                    } else if (selectedValue > 0 && value == selectedValue) {
                        p = sameNumTextPaint;
                    } else {
                        p = playerTextPaint;
                    }
                    canvas.drawText(String.valueOf(value), left + cellSize / 2, textY, p);
                }
            }
        }

        for (int i = 0; i <= 9; i++) {
            Paint p = (i % 3 == 0) ? gridThickPaint : gridThinPaint;
            canvas.drawLine(offsetX + i * cellSize, offsetY, offsetX + i * cellSize, offsetY + boardSize, p);
            canvas.drawLine(offsetX, offsetY + i * cellSize, offsetX + boardSize, offsetY + i * cellSize, p);
        }

        if (game.isSolved()) {
            canvas.drawRoundRect(new RectF(offsetX - 4, offsetY - 4, offsetX + boardSize + 4, offsetY + boardSize + 4), 8, 8, solvedOverlayPaint);
            if (solvedListener != null) {
                solvedListener.onSolved(((SudokuActivity) getContext()).getElapsedMs());
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
            if (x >= 0 && x < cellSize * 9 && y >= 0 && y < cellSize * 9) {
                selectedX = (int) (x / cellSize);
                selectedY = (int) (y / cellSize);
                invalidate();
                if (listener != null) {
                    listener.onCellSelected(selectedX, selectedY);
                }
            }
        }
        return true;
    }

    public void refreshSelection() {
        invalidate();
    }

    public int getSelectedX() { return selectedX; }
    public int getSelectedY() { return selectedY; }
}
