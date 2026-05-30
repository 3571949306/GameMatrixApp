package com.gamecenter.app.games.klotski;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 华容道棋盘自定义 View。
 *
 * <p>绘制 4×5 棋盘，支持滑块拖动和手势识别。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class KlotskiView extends View {

    /** 棋盘行数 */
    public static final int ROWS = 5;
    /** 棋盘列数 */
    public static final int COLS = 4;

    private static final int COLOR_BG = Color.parseColor("#F5F0E8");
    private static final int COLOR_CELL = Color.parseColor("#E8E0D0");
    private static final int COLOR_CAO_CAO = Color.parseColor("#C44536");
    private static final int COLOR_GENERAL = Color.parseColor("#5B8A72");
    private static final int COLOR_SOLDIER = Color.parseColor("#8B7355");
    private static final int COLOR_TEXT = Color.WHITE;
    private static final int COLOR_BORDER = Color.parseColor("#2D2D2D");

    private Paint cellPaint;
    private Paint caocaoPaint;
    private Paint generalPaint;
    private Paint soldierPaint;
    private Paint textPaint;
    private Paint borderPaint;

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    /** 滑块列表 */
    private List<KlotskiBlock> blocks = new ArrayList<>();

    /** 当前选中的滑块索引 */
    private int selectedBlockIndex = -1;

    /** 滑动起始位置 */
    private float touchStartX;
    private float touchStartY;

    /** 滑动监听器 */
    public interface OnMoveListener {
        void onMove(int blockIndex, int direction);
    }

    private OnMoveListener onMoveListener;

    public KlotskiView(@NonNull Context context) {
        super(context);
        init();
    }

    public KlotskiView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KlotskiView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellPaint.setColor(COLOR_CELL);
        cellPaint.setStyle(Paint.Style.FILL);

        caocaoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        caocaoPaint.setColor(COLOR_CAO_CAO);
        caocaoPaint.setStyle(Paint.Style.FILL);

        generalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        generalPaint.setColor(COLOR_GENERAL);
        generalPaint.setStyle(Paint.Style.FILL);

        soldierPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        soldierPaint.setColor(COLOR_SOLDIER);
        soldierPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(COLOR_BORDER);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
    }

    public void setOnMoveListener(@Nullable OnMoveListener listener) {
        this.onMoveListener = listener;
    }

    /**
     * 设置滑块列表
     */
    public void setBlocks(@NonNull List<KlotskiBlock> blocks) {
        this.blocks = blocks;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = (int) (width * (ROWS / (float) COLS));
        setMeasuredDimension(width, desiredHeight);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        float boardWidth = viewWidth - 20f;
        cellSize = boardWidth / COLS;
        boardOffsetX = 10f;
        boardOffsetY = 10f;
        float boardHeight = cellSize * ROWS;

        canvas.drawColor(COLOR_BG);

        // 绘制棋盘格子
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = boardOffsetX + c * cellSize;
                float top = boardOffsetY + r * cellSize;
                canvas.drawRect(left, top, left + cellSize, top + cellSize, cellPaint);
            }
        }

        // 绘制滑块
        for (int i = 0; i < blocks.size(); i++) {
            drawBlock(canvas, blocks.get(i), i == selectedBlockIndex);
        }

        // 绘制边框
        canvas.drawRect(boardOffsetX, boardOffsetY,
                boardOffsetX + boardWidth, boardOffsetY + boardHeight, borderPaint);
    }

    /**
     * 绘制单个滑块
     */
    private void drawBlock(@NonNull Canvas canvas, @NonNull KlotskiBlock block, boolean selected) {
        float left = boardOffsetX + block.col * cellSize;
        float top = boardOffsetY + block.row * cellSize;
        float right = left + block.width * cellSize;
        float bottom = top + block.height * cellSize;

        Paint paint;
        String label;
        switch (block.type) {
            case CAO_CAO:
                paint = caocaoPaint;
                label = "曹操";
                break;
            case GENERAL_V:
            case GENERAL_H:
                paint = generalPaint;
                label = "将";
                break;
            default:
                paint = soldierPaint;
                label = "兵";
                break;
        }

        if (selected) {
            paint.setAlpha(180);
        } else {
            paint.setAlpha(255);
        }

        RectF rect = new RectF(left + 2, top + 2, right - 2, bottom - 2);
        canvas.drawRoundRect(rect, 8f, 8f, paint);
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint);

        // 绘制标签
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f + textPaint.getTextSize() / 3f;
        textPaint.setTextSize(cellSize * 0.35f);
        canvas.drawText(label, cx, cy, textPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (onMoveListener == null) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                selectedBlockIndex = findBlockAt(touchStartX, touchStartY);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (selectedBlockIndex >= 0) {
                    float dx = event.getX() - touchStartX;
                    float dy = event.getY() - touchStartY;

                    if (Math.abs(dx) > cellSize * 0.3f || Math.abs(dy) > cellSize * 0.3f) {
                        int direction;
                        if (Math.abs(dx) > Math.abs(dy)) {
                            direction = dx > 0 ? 3 : 2; // 右/左
                        } else {
                            direction = dy > 0 ? 1 : 0; // 下/上
                        }
                        onMoveListener.onMove(selectedBlockIndex, direction);
                    }
                }
                selectedBlockIndex = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 查找点击位置所在的滑块
     */
    private int findBlockAt(float x, float y) {
        int col = (int) ((x - boardOffsetX) / cellSize);
        int row = (int) ((y - boardOffsetY) / cellSize);

        for (int i = 0; i < blocks.size(); i++) {
            KlotskiBlock b = blocks.get(i);
            if (row >= b.row && row < b.row + b.height
                    && col >= b.col && col < b.col + b.width) {
                return i;
            }
        }
        return -1;
    }
}
