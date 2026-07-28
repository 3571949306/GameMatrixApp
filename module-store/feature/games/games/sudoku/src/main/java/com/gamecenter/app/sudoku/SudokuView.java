package com.gamecenter.app.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 数独棋盘自定义 View（模块化版本）。
 *
 * <p>从 app 模块复制并改包名为 com.gamecenter.app.sudoku。
 * 绘制 9×9 数独网格，支持选中格子高亮和数字输入。
 * 颜色全部硬编码，不依赖 R 资源。</p>
 */
public class SudokuView extends View {

    private static final int GRID_SIZE = 9;
    private static final int BOX_SIZE = 3;

    private static final int COLOR_BG = Color.parseColor("#F5F0E8");
    private static final int COLOR_CELL = Color.parseColor("#FBF9F6");
    private static final int COLOR_SELECTED = Color.parseColor("#D4EDE1");
    private static final int COLOR_HIGHLIGHT = Color.parseColor("#E8F5E9");
    private static final int COLOR_ERROR = Color.parseColor("#FEE2E2");
    private static final int COLOR_LINE = Color.parseColor("#2D2D2D");
    private static final int COLOR_BOX_LINE = Color.parseColor("#5B8A72");
    private static final int COLOR_GIVEN = Color.parseColor("#2D2D2D");
    private static final int COLOR_USER = Color.parseColor("#5B8A72");
    private static final int COLOR_ERROR_TEXT = Color.parseColor("#DC2626");
    private static final int COLOR_NOTE = Color.parseColor("#7A7A7A");

    private Paint cellPaint;
    private Paint selectedPaint;
    private Paint highlightPaint;
    private Paint errorPaint;
    private Paint linePaint;
    private Paint boxLinePaint;
    private Paint textPaint;
    private Paint errorTextPaint;
    private Paint notePaint;

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] isGiven = new boolean[GRID_SIZE][GRID_SIZE];
    private boolean[][] isError = new boolean[GRID_SIZE][GRID_SIZE];
    @SuppressWarnings("unchecked")
    private Set<Integer>[][] notes = (Set<Integer>[][]) new Set<?>[GRID_SIZE][GRID_SIZE];

    private int selectedRow = -1;
    private int selectedCol = -1;

    public interface OnCellSelectListener {
        void onCellSelected(int row, int col);
    }

    private OnCellSelectListener onCellSelectListener;

    public SudokuView(@NonNull Context context) {
        super(context);
        init();
    }

    public SudokuView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SudokuView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellPaint.setColor(COLOR_CELL);
        cellPaint.setStyle(Paint.Style.FILL);

        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(COLOR_SELECTED);
        selectedPaint.setStyle(Paint.Style.FILL);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(COLOR_HIGHLIGHT);
        highlightPaint.setStyle(Paint.Style.FILL);

        errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setColor(COLOR_ERROR);
        errorPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(1f);
        linePaint.setStyle(Paint.Style.STROKE);

        boxLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxLinePaint.setColor(COLOR_BOX_LINE);
        boxLinePaint.setStrokeWidth(3f);
        boxLinePaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_GIVEN);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        errorTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorTextPaint.setColor(COLOR_ERROR_TEXT);
        errorTextPaint.setTextAlign(Paint.Align.CENTER);
        errorTextPaint.setFakeBoldText(true);

        notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaint.setColor(COLOR_NOTE);
        notePaint.setTextAlign(Paint.Align.CENTER);
        notePaint.setFakeBoldText(false);
    }

    public void setOnCellSelectListener(@Nullable OnCellSelectListener listener) {
        this.onCellSelectListener = listener;
    }

    public void setBoard(int[][] board, boolean[][] isGiven) {
        this.board = board;
        this.isGiven = isGiven;
        this.isError = new boolean[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                notes[r][c] = new HashSet<>();
            }
        }
        invalidate();
    }

    public void toggleNote(int row, int col, int num) {
        if (row < 0 || row >= GRID_SIZE || col < 0 || col >= GRID_SIZE) return;
        if (num < 1 || num > 9) return;
        if (notes[row][col] == null) notes[row][col] = new HashSet<>();
        if (notes[row][col].contains(num)) {
            notes[row][col].remove(num);
        } else {
            notes[row][col].add(num);
        }
        invalidate();
    }

    public void clearNotes(int row, int col) {
        if (row < 0 || row >= GRID_SIZE || col < 0 || col >= GRID_SIZE) return;
        if (notes[row][col] != null) notes[row][col].clear();
        invalidate();
    }

    public void removeNoteFromPeers(int row, int col, int num) {
        if (num < 1 || num > 9) return;
        for (int i = 0; i < GRID_SIZE; i++) {
            if (notes[row][i] != null) notes[row][i].remove(num);
            if (notes[i][col] != null) notes[i][col].remove(num);
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (notes[r][c] != null) notes[r][c].remove(num);
            }
        }
        invalidate();
    }

    public void updateCell(int row, int col, int value) {
        board[row][col] = value;
        isError[row][col] = false;
        invalidate();
    }

    public void setError(int row, int col, boolean error) {
        isError[row][col] = error;
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
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        float padding = 10f;
        float boardSize = viewWidth - 2 * padding;
        cellSize = boardSize / GRID_SIZE;
        boardOffsetX = padding;
        boardOffsetY = padding;

        canvas.drawColor(COLOR_BG);

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                float left = boardOffsetX + c * cellSize;
                float top = boardOffsetY + r * cellSize;

                if (r == selectedRow && c == selectedCol) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, selectedPaint);
                } else if (selectedRow >= 0 && selectedCol >= 0) {
                    if (r == selectedRow || c == selectedCol
                            || (r / BOX_SIZE == selectedRow / BOX_SIZE
                            && c / BOX_SIZE == selectedCol / BOX_SIZE)) {
                        canvas.drawRect(left, top, left + cellSize, top + cellSize, highlightPaint);
                    }
                }

                if (isError[r][c]) {
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, errorPaint);
                }
            }
        }

        textPaint.setTextSize(cellSize * 0.55f);
        errorTextPaint.setTextSize(cellSize * 0.55f);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] != 0) {
                    float cx = boardOffsetX + c * cellSize + cellSize / 2f;
                    float cy = boardOffsetY + r * cellSize + cellSize / 2f + cellSize * 0.18f;
                    if (!isGiven[r][c]) {
                        textPaint.setColor(COLOR_USER);
                    } else {
                        textPaint.setColor(COLOR_GIVEN);
                    }
                    canvas.drawText(String.valueOf(board[r][c]), cx, cy,
                            isError[r][c] ? errorTextPaint : textPaint);
                }
            }
        }

        float noteCell = cellSize / 3f;
        notePaint.setTextSize(noteCell * 0.7f);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] != 0) continue;
                Set<Integer> set = notes[r][c];
                if (set == null || set.isEmpty()) continue;
                float left = boardOffsetX + c * cellSize;
                float top = boardOffsetY + r * cellSize;
                for (int n = 1; n <= 9; n++) {
                    if (!set.contains(n)) continue;
                    int nr = (n - 1) / 3;
                    int nc = (n - 1) % 3;
                    float cx = left + nc * noteCell + noteCell / 2f;
                    float cy = top + nr * noteCell + noteCell * 0.7f;
                    canvas.drawText(String.valueOf(n), cx, cy, notePaint);
                }
            }
        }

        for (int i = 0; i <= GRID_SIZE; i++) {
            float pos = boardOffsetX + i * cellSize;
            canvas.drawLine(pos, boardOffsetY, pos, boardOffsetY + GRID_SIZE * cellSize,
                    i % BOX_SIZE == 0 ? boxLinePaint : linePaint);
            pos = boardOffsetY + i * cellSize;
            canvas.drawLine(boardOffsetX, pos, boardOffsetX + GRID_SIZE * cellSize, pos,
                    i % BOX_SIZE == 0 ? boxLinePaint : linePaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int col = (int) ((event.getX() - boardOffsetX) / cellSize);
            int row = (int) ((event.getY() - boardOffsetY) / cellSize);
            if (row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE) {
                selectedRow = row;
                selectedCol = col;
                invalidate();
                if (onCellSelectListener != null) {
                    onCellSelectListener.onCellSelected(row, col);
                }
            }
        }
        return true;
    }
}
