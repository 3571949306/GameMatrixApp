// 同步声明：此实现与另一包中的 GoView 保持同步；除包名外不得分叉。
package com.gamecenter.app.go;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 9×9 围棋棋盘。
 *
 * <p>增强版提供木纹、坐标、装饰边框和棋子光影；简洁版仅保留对弈所需的
 * 网格、星位、棋子、领地与最后一手标记。棋盘数据进入 View 时会复制，避免
 * AI 后台搜索或游戏状态更新在绘制期间改变同一数组。</p>
 */
public class GoView extends View {

    public static final int BOARD_SIZE = 9;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private static final int COLOR_BOARD_SIMPLE = Color.rgb(222, 184, 135);
    private static final int COLOR_BOARD_LIGHT = Color.rgb(242, 207, 143);
    private static final int COLOR_BOARD_DARK = Color.rgb(198, 139, 69);
    private static final int COLOR_LINE = Color.rgb(83, 61, 35);
    private static final int COLOR_FRAME = Color.rgb(87, 54, 25);
    private static final int COLOR_FRAME_ACCENT = Color.rgb(232, 187, 111);
    private static final int COLOR_BLACK = Color.rgb(26, 26, 26);
    private static final int COLOR_WHITE = Color.rgb(247, 243, 234);
    private static final int COLOR_BLACK_BORDER = Color.rgb(8, 8, 8);
    private static final int COLOR_WHITE_BORDER = Color.rgb(174, 164, 148);
    private static final int COLOR_LAST_MOVE = Color.rgb(188, 54, 45);
    private static final int COLOR_TERRITORY_BLACK = 0x46000000;
    private static final int COLOR_TERRITORY_WHITE = 0x62FFFFFF;
    private static final String[] COLUMN_LABELS = {"A", "B", "C", "D", "E", "F", "G", "H", "J"};
    private static final String[] ROW_LABELS = {"9", "8", "7", "6", "5", "4", "3", "2", "1"};
    private static final int[][] STAR_POINTS = {{2, 2}, {2, 6}, {6, 2}, {6, 6}, {4, 4}};

    private final float density;
    private final int touchSlop;

    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frameAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coordinatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint territoryBlackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint territoryWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF backgroundRect = new RectF();
    private final RectF frameAccentRect = new RectF();
    private final Path boardClipPath = new Path();
    private final Path grainPath = new Path();

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;
    private float gridEndX;
    private float gridEndY;
    private float stoneRadius;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private float[][] territory;
    private boolean showTerritory;
    private boolean simpleMode;

    private float touchDownX;
    private float touchDownY;
    private boolean touchMoved;
    private int pendingClickRow = -1;
    private int pendingClickCol = -1;

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private OnCellClickListener listener;

    public GoView(@NonNull Context context) {
        this(context, null);
    }

    public GoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        initPaints();
        setClickable(true);
        setFocusable(true);
    }

    private void initPaints() {
        boardPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(COLOR_LINE);
        linePaint.setStyle(Paint.Style.STROKE);

        starPaint.setColor(COLOR_LINE);
        starPaint.setStyle(Paint.Style.FILL);

        framePaint.setColor(COLOR_FRAME);
        framePaint.setStyle(Paint.Style.STROKE);

        frameAccentPaint.setColor(COLOR_FRAME_ACCENT);
        frameAccentPaint.setStyle(Paint.Style.STROKE);

        grainPaint.setColor(0x26704B24);
        grainPaint.setStyle(Paint.Style.STROKE);

        coordinatePaint.setColor(COLOR_LINE);
        coordinatePaint.setTextAlign(Paint.Align.CENTER);
        coordinatePaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));

        blackPaint.setColor(COLOR_BLACK);
        blackPaint.setStyle(Paint.Style.FILL);

        whitePaint.setColor(COLOR_WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        borderPaint.setStyle(Paint.Style.STROKE);

        shadowPaint.setColor(0x52000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        highlightPaint.setStyle(Paint.Style.FILL);

        lastMovePaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setStrokeCap(Paint.Cap.ROUND);

        territoryBlackPaint.setColor(COLOR_TERRITORY_BLACK);
        territoryBlackPaint.setStyle(Paint.Style.FILL);

        territoryWhitePaint.setColor(COLOR_TERRITORY_WHITE);
        territoryWhitePaint.setStyle(Paint.Style.FILL);

        updateBoardShader(getWidth(), getHeight());
    }

    public void setOnCellClickListener(@Nullable OnCellClickListener listener) {
        this.listener = listener;
    }

    /** Copies the supplied board so later external mutations cannot race with drawing. */
    public void setBoard(@Nullable int[][] source) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            java.util.Arrays.fill(board[row], EMPTY);
            if (source != null && row < source.length && source[row] != null) {
                System.arraycopy(source[row], 0, board[row], 0,
                        Math.min(BOARD_SIZE, source[row].length));
            }
        }
        if (isCellOnBoard(lastMoveRow, lastMoveCol)
                && board[lastMoveRow][lastMoveCol] == EMPTY) {
            lastMoveRow = -1;
            lastMoveCol = -1;
        }
        invalidate();
    }

    public void setLastMove(int row, int col) {
        if (!isCellOnBoard(row, col)) {
            clearLastMove();
            return;
        }
        lastMoveRow = row;
        lastMoveCol = col;
        invalidate();
    }

    public void clearLastMove() {
        lastMoveRow = -1;
        lastMoveCol = -1;
        invalidate();
    }

    public void setSimpleMode(boolean simpleMode) {
        if (this.simpleMode == simpleMode) return;
        this.simpleMode = simpleMode;
        updateGeometry(getWidth(), getHeight());
        updateBoardShader(getWidth(), getHeight());
        invalidate();
    }

    public boolean isSimpleMode() {
        return simpleMode;
    }

    public void showTerritory(@Nullable float[][] source) {
        if (source == null) {
            hideTerritory();
            return;
        }
        territory = new float[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE && row < source.length; row++) {
            if (source[row] != null) {
                System.arraycopy(source[row], 0, territory[row], 0,
                        Math.min(BOARD_SIZE, source[row].length));
            }
        }
        showTerritory = true;
        invalidate();
    }

    public void hideTerritory() {
        showTerritory = false;
        territory = null;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            size = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? Math.round(dp(320f)) : height;
        } else {
            size = width;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                size = Math.min(size, height);
            }
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateGeometry(width, height);
        updateBoardShader(width, height);
    }

    private void updateGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        float size = Math.min(width, height);
        float padding = Math.max(dp(simpleMode ? 18f : 28f),
                size * (simpleMode ? 0.068f : 0.09f));
        cellSize = (size - 2f * padding) / (BOARD_SIZE - 1);
        stoneRadius = cellSize * 0.425f;

        float neededPadding = stoneRadius + (simpleMode ? dp(2f) : dp(5f));
        if (padding < neededPadding) {
            padding = neededPadding;
            cellSize = (size - 2f * padding) / (BOARD_SIZE - 1);
            stoneRadius = cellSize * 0.425f;
        }

        boardOffsetX = (width - (BOARD_SIZE - 1) * cellSize) / 2f;
        boardOffsetY = (height - (BOARD_SIZE - 1) * cellSize) / 2f;
        gridEndX = boardOffsetX + (BOARD_SIZE - 1) * cellSize;
        gridEndY = boardOffsetY + (BOARD_SIZE - 1) * cellSize;

        linePaint.setStrokeWidth(dp(simpleMode ? 0.85f : 1.05f));
        framePaint.setStrokeWidth(dp(2.4f));
        frameAccentPaint.setStrokeWidth(dp(0.8f));
        grainPaint.setStrokeWidth(dp(0.7f));
        borderPaint.setStrokeWidth(dp(simpleMode ? 0.8f : 1f));
        lastMovePaint.setStrokeWidth(dp(simpleMode ? 1.6f : 2.2f));
        coordinatePaint.setTextSize(Math.min(dp(13f), cellSize * 0.28f));

        float outerInset = dp(4f);
        backgroundRect.set(outerInset, outerInset, width - outerInset, height - outerInset);
        boardClipPath.reset();
        boardClipPath.addRoundRect(backgroundRect, dp(13f), dp(13f), Path.Direction.CW);
        float accentInset = dp(8f);
        frameAccentRect.set(accentInset, accentInset, width - accentInset, height - accentInset);
    }

    private void updateBoardShader(int width, int height) {
        if (simpleMode || width <= 0 || height <= 0) {
            boardPaint.setShader(null);
            boardPaint.setColor(COLOR_BOARD_SIMPLE);
            return;
        }
        boardPaint.setShader(new LinearGradient(
                0f, 0f, width, height,
                COLOR_BOARD_LIGHT, COLOR_BOARD_DARK, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize <= 0f) updateGeometry(getWidth(), getHeight());

        drawBoardSurface(canvas);
        drawGrid(canvas);
        drawTerritory(canvas);
        drawStones(canvas);
        drawLastMove(canvas);
    }

    private void drawBoardSurface(Canvas canvas) {
        if (simpleMode) {
            canvas.drawColor(COLOR_BOARD_SIMPLE);
            return;
        }

        canvas.drawColor(COLOR_FRAME);
        float corner = dp(13f);
        canvas.drawRoundRect(backgroundRect, corner, corner, boardPaint);

        int saveCount = canvas.save();
        canvas.clipPath(boardClipPath);
        drawWoodGrain(canvas);
        canvas.restoreToCount(saveCount);

        canvas.drawRoundRect(backgroundRect, corner, corner, framePaint);
        canvas.drawRoundRect(frameAccentRect, dp(9f), dp(9f), frameAccentPaint);
        drawCoordinates(canvas);
    }

    private void drawWoodGrain(Canvas canvas) {
        float step = Math.max(dp(16f), getHeight() / 15f);
        int index = 0;
        for (float y = -step; y < getHeight() + step; y += step) {
            float wave = (index++ & 1) == 0 ? dp(3.5f) : -dp(2.5f);
            grainPath.reset();
            grainPath.moveTo(0f, y);
            grainPath.cubicTo(
                    getWidth() * 0.26f, y + wave,
                    getWidth() * 0.72f, y - wave,
                    getWidth(), y + wave * 0.35f);
            canvas.drawPath(grainPath, grainPaint);
        }
    }

    private void drawCoordinates(Canvas canvas) {
        float horizontalMargin = boardOffsetX * 0.73f;
        float verticalMargin = boardOffsetY * 0.73f;
        float topBaseline = centeredTextBaseline(boardOffsetY - verticalMargin);
        float bottomBaseline = centeredTextBaseline(gridEndY + verticalMargin);
        float leftX = boardOffsetX - horizontalMargin;
        float rightX = gridEndX + horizontalMargin;

        for (int i = 0; i < BOARD_SIZE; i++) {
            float x = boardOffsetX + i * cellSize;
            canvas.drawText(COLUMN_LABELS[i], x, topBaseline, coordinatePaint);
            canvas.drawText(COLUMN_LABELS[i], x, bottomBaseline, coordinatePaint);

            float y = boardOffsetY + i * cellSize;
            float baseline = centeredTextBaseline(y);
            canvas.drawText(ROW_LABELS[i], leftX, baseline, coordinatePaint);
            canvas.drawText(ROW_LABELS[i], rightX, baseline, coordinatePaint);
        }
    }

    private float centeredTextBaseline(float centerY) {
        return centerY - (coordinatePaint.ascent() + coordinatePaint.descent()) / 2f;
    }

    private void drawGrid(Canvas canvas) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            float x = boardOffsetX + i * cellSize;
            float y = boardOffsetY + i * cellSize;
            canvas.drawLine(x, boardOffsetY, x, gridEndY, linePaint);
            canvas.drawLine(boardOffsetX, y, gridEndX, y, linePaint);
        }

        float starRadius = Math.max(dp(2.35f), cellSize * 0.052f);
        for (int[] point : STAR_POINTS) {
            canvas.drawCircle(
                    boardOffsetX + point[1] * cellSize,
                    boardOffsetY + point[0] * cellSize,
                    starRadius,
                    starPaint);
        }
    }

    private void drawTerritory(Canvas canvas) {
        if (!showTerritory || territory == null) return;
        float markerSize = cellSize * 0.14f;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (territory[row][col] == 0f || board[row][col] != EMPTY) continue;
                float cx = boardOffsetX + col * cellSize;
                float cy = boardOffsetY + row * cellSize;
                if (territory[row][col] < 0f) {
                    canvas.drawRect(cx - markerSize, cy - markerSize,
                            cx + markerSize, cy + markerSize, territoryBlackPaint);
                } else {
                    canvas.drawCircle(cx, cy, markerSize, territoryWhitePaint);
                }
            }
        }
    }

    private void drawStones(Canvas canvas) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int color = board[row][col];
                if (color != EMPTY) drawStone(canvas, row, col, color);
            }
        }
    }

    private void drawStone(Canvas canvas, int row, int col, int color) {
        float cx = boardOffsetX + col * cellSize;
        float cy = boardOffsetY + row * cellSize;

        if (!simpleMode) {
            float shadowOffset = dp(2.2f);
            canvas.drawCircle(cx + shadowOffset, cy + shadowOffset,
                    stoneRadius + dp(0.5f), shadowPaint);
        }

        Paint stonePaint = color == BLACK ? blackPaint : whitePaint;
        canvas.drawCircle(cx, cy, stoneRadius, stonePaint);

        borderPaint.setColor(color == BLACK ? COLOR_BLACK_BORDER : COLOR_WHITE_BORDER);
        canvas.drawCircle(cx, cy, stoneRadius, borderPaint);

        if (!simpleMode) {
            highlightPaint.setColor(color == BLACK ? 0x4AFFFFFF : 0xA8FFFFFF);
            canvas.drawCircle(
                    cx - stoneRadius * 0.28f,
                    cy - stoneRadius * 0.30f,
                    stoneRadius * (color == BLACK ? 0.16f : 0.20f),
                    highlightPaint);
        }
    }

    private void drawLastMove(Canvas canvas) {
        if (!isCellOnBoard(lastMoveRow, lastMoveCol)) return;
        int stone = board[lastMoveRow][lastMoveCol];
        if (stone == EMPTY) return;

        float cx = boardOffsetX + lastMoveCol * cellSize;
        float cy = boardOffsetY + lastMoveRow * cellSize;
        lastMovePaint.setColor(stone == BLACK ? Color.WHITE : COLOR_LAST_MOVE);
        canvas.drawCircle(cx, cy, stoneRadius * (simpleMode ? 0.38f : 0.48f), lastMovePaint);
        if (!simpleMode) {
            int oldAlpha = lastMovePaint.getAlpha();
            lastMovePaint.setAlpha(105);
            canvas.drawCircle(cx, cy, stoneRadius * 0.64f, lastMovePaint);
            lastMovePaint.setAlpha(oldAlpha);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchMoved = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    touchMoved = true;
                    setPressed(false);
                }
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (!touchMoved) {
                    preparePendingClick(event.getX(), event.getY());
                    return performClick();
                }
                pendingClickRow = -1;
                pendingClickCol = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                pendingClickRow = -1;
                pendingClickCol = -1;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void preparePendingClick(float x, float y) {
        pendingClickRow = -1;
        pendingClickCol = -1;
        if (cellSize <= 0f) return;
        if (x < boardOffsetX - cellSize * 0.46f || x > gridEndX + cellSize * 0.46f
                || y < boardOffsetY - cellSize * 0.46f || y > gridEndY + cellSize * 0.46f) {
            return;
        }

        int col = Math.round((x - boardOffsetX) / cellSize);
        int row = Math.round((y - boardOffsetY) / cellSize);
        if (!isCellOnBoard(row, col)) return;

        float intersectionX = boardOffsetX + col * cellSize;
        float intersectionY = boardOffsetY + row * cellSize;
        if (Math.abs(x - intersectionX) <= cellSize * 0.48f
                && Math.abs(y - intersectionY) <= cellSize * 0.48f) {
            pendingClickRow = row;
            pendingClickCol = col;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (listener != null && isCellOnBoard(pendingClickRow, pendingClickCol)) {
            int row = pendingClickRow;
            int col = pendingClickCol;
            pendingClickRow = -1;
            pendingClickCol = -1;
            listener.onCellClick(row, col);
        } else {
            pendingClickRow = -1;
            pendingClickCol = -1;
        }
        return true;
    }

    private static boolean isCellOnBoard(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private float dp(float value) {
        return value * density;
    }
}
