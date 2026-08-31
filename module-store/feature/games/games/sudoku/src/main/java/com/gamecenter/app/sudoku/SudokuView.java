package com.gamecenter.app.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;

/**
 * 数独棋盘。
 *
 * <p>棋盘网格和单元格内容仍使用轻量 Canvas 绘制，但每个格子都是独立的可聚焦子 View。
 * 这样既能保持 9×9 棋盘的渲染稳定性，又能让 TalkBack、键盘和测试工具准确定位每个格子。</p>
 */
public class SudokuView extends ViewGroup {

    private static final int GRID_SIZE = 9;
    private static final int BOX_SIZE = 3;

    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SudokuCellView[][] cells = new SudokuCellView[GRID_SIZE][GRID_SIZE];

    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] isGiven = new boolean[GRID_SIZE][GRID_SIZE];
    private boolean[][] isHinted = new boolean[GRID_SIZE][GRID_SIZE];
    private boolean[][] isError = new boolean[GRID_SIZE][GRID_SIZE];
    private int[][] notes = new int[GRID_SIZE][GRID_SIZE];

    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean interactionEnabled = true;

    private int colorBoard = Color.parseColor("#FFF5F0E8");
    private int colorCell = Color.parseColor("#FFFBF9F6");
    private int colorSelected = Color.parseColor("#FFD4EDE1");
    private int colorRelated = Color.parseColor("#FFE8F5E9");
    private int colorError = Color.parseColor("#FFFEE2E2");
    private int colorGrid = Color.parseColor("#FFB8B8B8");
    private int colorBoxGrid = Color.parseColor("#FF5B8A72");
    private int colorGiven = Color.parseColor("#FF2D2D2D");
    private int colorUser = Color.parseColor("#FF3E8061");
    private int colorErrorText = Color.parseColor("#FFB42318");
    private int colorNote = Color.parseColor("#FF7A7A7A");
    private final float density;
    private float boardPadding;

    public interface OnCellSelectListener {
        void onCellSelected(int row, int col);
    }

    private OnCellSelectListener onCellSelectListener;

    public SudokuView(@NonNull Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    public SudokuView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    public SudokuView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClipToPadding(false);
        setFocusable(true);
        setContentDescription(getContext().getString(R.string.game_sudoku_board_description));
        boardPadding = dp(8);

        gridPaint.setStyle(Paint.Style.STROKE);
        boxGridPaint.setStyle(Paint.Style.STROKE);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                final int row = r;
                final int col = c;
                SudokuCellView cell = new SudokuCellView(getContext());
                cell.setOnClickListener(v -> selectCell(row, col));
                cells[r][c] = cell;
                addView(cell);
            }
        }
        refreshCells();
    }

    /** 设置宿主主题传入的颜色，避免动态模块依赖自身资源上下文。 */
    public void setColors(int boardColor, int cellColor, int selectedColor, int relatedColor,
                          int errorColor, int gridColor, int boxGridColor, int givenColor,
                          int userColor, int errorTextColor, int noteColor) {
        colorBoard = boardColor;
        colorCell = cellColor;
        colorSelected = selectedColor;
        colorRelated = relatedColor;
        colorError = errorColor;
        colorGrid = gridColor;
        colorBoxGrid = boxGridColor;
        colorGiven = givenColor;
        colorUser = userColor;
        colorErrorText = errorTextColor;
        colorNote = noteColor;
        refreshCells();
    }

    public void setOnCellSelectListener(@Nullable OnCellSelectListener listener) {
        onCellSelectListener = listener;
    }

    /** 用规则层快照整体渲染，避免 UI 和游戏层共享可变数组。 */
    public void render(@NonNull SudokuGame.State state, @NonNull boolean[][] errors) {
        board = copy(state.getBoard());
        isGiven = copy(state.getGiven());
        isHinted = copy(state.getHinted());
        notes = copy(state.getNotes());
        isError = copy(errors);
        refreshCells();
    }

    /** 兼容旧调用方；新代码应优先使用 render。 */
    public void setBoard(int[][] newBoard, boolean[][] given) {
        board = copy(newBoard);
        isGiven = copy(given);
        isHinted = new boolean[GRID_SIZE][GRID_SIZE];
        isError = new boolean[GRID_SIZE][GRID_SIZE];
        notes = new int[GRID_SIZE][GRID_SIZE];
        refreshCells();
    }

    public void setInteractionEnabled(boolean enabled) {
        interactionEnabled = enabled;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) cells[r][c].setEnabled(enabled);
        }
        refreshCells();
    }

    public void updateCell(int row, int col, int value) {
        if (!isInside(row, col) || value < 0 || value > GRID_SIZE) return;
        board[row][col] = value;
        if (value != 0) notes[row][col] = 0;
        refreshCells();
    }

    public void setError(int row, int col, boolean error) {
        if (!isInside(row, col)) return;
        isError[row][col] = error;
        refreshCells();
    }

    public void toggleNote(int row, int col, int number) {
        if (!isInside(row, col) || number < 1 || number > GRID_SIZE) return;
        notes[row][col] ^= (1 << number);
        refreshCells();
    }

    public void clearNotes(int row, int col) {
        if (!isInside(row, col)) return;
        notes[row][col] = 0;
        refreshCells();
    }

    public void removeNoteFromPeers(int row, int col, int number) {
        if (!isInside(row, col) || number < 1 || number > GRID_SIZE) return;
        int bit = ~(1 << number);
        for (int c = 0; c < GRID_SIZE; c++) if (c != col) notes[row][c] &= bit;
        for (int r = 0; r < GRID_SIZE; r++) if (r != row) notes[r][col] &= bit;
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (r != row || c != col) notes[r][c] &= bit;
            }
        }
        refreshCells();
    }

    public void setSelected(int row, int col) {
        if (!isInside(row, col)) return;
        selectCell(row, col);
    }

    public void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        refreshCells();
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int desired = (int) dp(336);
        int size = widthMode == MeasureSpec.UNSPECIFIED ? desired : widthSize;
        if (heightMode != MeasureSpec.UNSPECIFIED) size = Math.min(size, heightSize);
        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode != MeasureSpec.UNSPECIFIED) {
            size = heightSize;
        }
        size = Math.max(0, size);
        setMeasuredDimension(size, size);

        int inner = Math.max(0, size - (int) (boardPadding * 2));
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int childWidth = Math.max(0, Math.round((c + 1) * inner / 9f)
                        - Math.round(c * inner / 9f));
                int childHeight = Math.max(0, Math.round((r + 1) * inner / 9f)
                        - Math.round(r * inner / 9f));
                cells[r][c].measure(
                        MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int inner = Math.max(0, getWidth() - (int) (boardPadding * 2));
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int cellLeft = (int) boardPadding + Math.round(c * inner / 9f);
                int cellTop = (int) boardPadding + Math.round(r * inner / 9f);
                int cellRight = (int) boardPadding + Math.round((c + 1) * inner / 9f);
                int cellBottom = (int) boardPadding + Math.round((r + 1) * inner / 9f);
                cells[r][c].layout(cellLeft, cellTop, cellRight, cellBottom);
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        boardPaint.setColor(colorBoard);
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), dp(18), dp(18), boardPaint);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        int inner = Math.max(0, getWidth() - (int) (boardPadding * 2));
        gridPaint.setColor(colorGrid);
        gridPaint.setStrokeWidth(Math.max(1f, dp(1)));
        boxGridPaint.setColor(colorBoxGrid);
        boxGridPaint.setStrokeWidth(Math.max(2f, dp(2)));
        for (int i = 0; i <= GRID_SIZE; i++) {
            float offset = boardPadding + i * inner / 9f;
            Paint paint = i % BOX_SIZE == 0 ? boxGridPaint : gridPaint;
            canvas.drawLine(offset, boardPadding, offset, boardPadding + inner, paint);
            canvas.drawLine(boardPadding, offset, boardPadding + inner, offset, paint);
        }
    }

    private void selectCell(int row, int col) {
        if (!isInside(row, col)) return;
        selectedRow = row;
        selectedCol = col;
        refreshCells();
        if (onCellSelectListener != null) onCellSelectListener.onCellSelected(row, col);
    }

    private void refreshCells() {
        int selectedValue = isInside(selectedRow, selectedCol)
                ? board[selectedRow][selectedCol] : 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                boolean selected = r == selectedRow && c == selectedCol;
                boolean related = isInside(selectedRow, selectedCol)
                        && (r == selectedRow || c == selectedCol
                        || (r / BOX_SIZE == selectedRow / BOX_SIZE
                        && c / BOX_SIZE == selectedCol / BOX_SIZE)
                        || (selectedValue != 0 && board[r][c] == selectedValue));
                boolean locked = isGiven[r][c] || isHinted[r][c];
                cells[r][c].bind(board[r][c], locked, isHinted[r][c], selected, related,
                        isError[r][c], notes[r][c], interactionEnabled);
                cells[r][c].setContentDescription(makeCellDescription(r, c, locked));
            }
        }
        invalidate();
    }

    private String makeCellDescription(int row, int col, boolean locked) {
        int value = board[row][col];
        String valueText = value == 0
                ? getContext().getString(R.string.game_sudoku_a11y_empty)
                : String.valueOf(value);
        String stateText;
        if (isGiven[row][col]) {
            stateText = getContext().getString(R.string.game_sudoku_a11y_given);
        } else if (isHinted[row][col]) {
            stateText = getContext().getString(R.string.game_sudoku_a11y_hint);
        } else {
            stateText = getContext().getString(R.string.game_sudoku_a11y_editable);
        }
        return getContext().getString(R.string.game_sudoku_a11y_cell,
                row + 1, col + 1, valueText, stateText,
                locked ? getContext().getString(R.string.game_sudoku_a11y_locked) : "");
    }

    private float dp(float value) {
        return value * density;
    }

    private static boolean isInside(int row, int col) {
        return row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE;
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[GRID_SIZE][GRID_SIZE];
        if (source == null) return result;
        for (int r = 0; r < GRID_SIZE && r < source.length; r++) {
            if (source[r] != null) {
                System.arraycopy(source[r], 0, result[r], 0,
                        Math.min(GRID_SIZE, source[r].length));
            }
        }
        return result;
    }

    private static boolean[][] copy(boolean[][] source) {
        boolean[][] result = new boolean[GRID_SIZE][GRID_SIZE];
        if (source == null) return result;
        for (int r = 0; r < GRID_SIZE && r < source.length; r++) {
            if (source[r] != null) {
                for (int c = 0; c < GRID_SIZE && c < source[r].length; c++) {
                    result[r][c] = source[r][c];
                }
            }
        }
        return result;
    }

    private final class SudokuCellView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int value;
        private boolean locked;
        private boolean hinted;
        private boolean selected;
        private boolean related;
        private boolean error;
        private int noteMask;
        private boolean enabledForGame;

        SudokuCellView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void bind(int value, boolean locked, boolean hinted, boolean selected, boolean related,
                  boolean error, int noteMask, boolean enabledForGame) {
            this.value = value;
            this.locked = locked;
            this.hinted = hinted;
            this.selected = selected;
            this.related = related;
            this.error = error;
            this.noteMask = noteMask;
            this.enabledForGame = enabledForGame;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int background = error ? colorError
                    : selected ? colorSelected
                    : related ? colorRelated : colorCell;
            backgroundPaint.setColor(background);
            canvas.drawRect(1, 1, getWidth() - 1, getHeight() - 1, backgroundPaint);

            if (value != 0) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTypeface(android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        locked ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
                textPaint.setTextSize(Math.min(getWidth(), getHeight()) * 0.55f);
                textPaint.setColor(error ? colorErrorText : (hinted ? colorBoxGrid
                        : locked ? colorGiven : colorUser));
                Paint.FontMetrics metrics = textPaint.getFontMetrics();
                float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(String.valueOf(value), getWidth() / 2f, baseline, textPaint);
                return;
            }

            notePaint.setTextAlign(Paint.Align.CENTER);
            notePaint.setTextSize(Math.min(getWidth(), getHeight()) * 0.18f);
            notePaint.setColor(colorNote);
            float noteWidth = getWidth() / 3f;
            float noteHeight = getHeight() / 3f;
            for (int number = 1; number <= GRID_SIZE; number++) {
                if ((noteMask & (1 << number)) == 0) continue;
                int noteRow = (number - 1) / 3;
                int noteCol = (number - 1) % 3;
                float x = noteCol * noteWidth + noteWidth / 2f;
                Paint.FontMetrics metrics = notePaint.getFontMetrics();
                float y = noteRow * noteHeight + noteHeight / 2f
                        - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(String.valueOf(number), x, y, notePaint);
            }
            if (!enabledForGame) setAlpha(0.65f);
            else setAlpha(1f);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(@NonNull android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
            info.setClickable(isClickable() && enabledForGame);
            info.setEnabled(enabledForGame);
        }
    }
}
