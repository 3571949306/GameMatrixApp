package com.gamecenter.app.games.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 数独自定义视图
 *
 * <p>职责：负责数独棋盘的视觉渲染和格子选择交互处理。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘自动居中，尺寸取 View 宽高的较小值减去 32px 边距</li>
 *   <li>3x3 宫格边界使用粗线（3.5px），普通格线使用细线（1.5px）</li>
 *   <li>选中格子高亮显示，相同数字的格子也高亮（蓝色背景）</li>
 *   <li>错误格子以红色背景标记，固定数字为黑色粗体，玩家填入为橙色</li>
 *   <li>完成时覆盖半透明绿色遮罩，并触发 OnSolvedListener 回调</li>
 * </ul>
 */
public class SudokuView extends View {

    /** 棋盘背景色（暖白色） */
    private static final int COLOR_BOARD_BG = 0xFFFFF8F0;

    /** 细网格线颜色（浅灰） */
    private static final int COLOR_THIN_GRID = 0xFFB0B0B0;

    /** 粗网格线颜色（深灰，用于3x3宫格边界） */
    private static final int COLOR_THICK_GRID = 0xFF333333;

    /** 选中格子背景色（米黄色） */
    private static final int COLOR_SELECTED_BG = 0xFFE8D5B7;

    /** 相同数字格子背景色（浅蓝色） */
    private static final int COLOR_SAME_NUM_BG = 0xFFBBDEFB;

    /** 错误格子背景色（浅红色） */
    private static final int COLOR_ERROR_BG = 0xFFFFCDD2;

    /** 固定数字文字颜色（深黑色） */
    private static final int COLOR_FIXED_TEXT = 0xFF1A1A1A;

    /** 玩家填入数字文字颜色（深橙色） */
    private static final int COLOR_PLAYER_TEXT = 0xFFD84315;

    /** 相同数字高亮文字颜色（深蓝色） */
    private static final int COLOR_SAME_NUM_TEXT = 0xFF1565C0;

    /** 完成时覆盖层颜色（半透明绿色） */
    private static final int COLOR_SOLVED_OVERLAY = 0x5500C853;

    /** 游戏逻辑对象 */
    private SudokuGame game;

    /** 细网格线画笔 */
    private Paint gridThinPaint;

    /** 粗网格线画笔（3x3宫格边界） */
    private Paint gridThickPaint;

    /** 棋盘背景画笔 */
    private Paint boardBgPaint;

    /** 选中格子背景画笔 */
    private Paint selectedBgPaint;

    /** 相同数字背景画笔 */
    private Paint sameNumBgPaint;

    /** 错误格子背景画笔 */
    private Paint errorBgPaint;

    /** 固定数字文字画笔（黑色粗体） */
    private Paint fixedTextPaint;

    /** 玩家填入数字文字画笔（橙色） */
    private Paint playerTextPaint;

    /** 相同数字高亮文字画笔（蓝色粗体） */
    private Paint sameNumTextPaint;

    /** 完成覆盖层画笔 */
    private Paint solvedOverlayPaint;

    /** 当前选中格子的列索引，-1 表示未选中 */
    private int selectedX = -1, selectedY = -1;

    /** 每个格子的像素大小 */
    private float cellSize;

    /** 棋盘水平偏移量，用于居中 */
    private float offsetX, offsetY;

    /** 格子选中监听器 */
    private OnCellSelectedListener listener;

    /** 数独完成监听器 */
    private OnSolvedListener solvedListener;

    /**
     * 格子选中回调接口
     */
    public interface OnCellSelectedListener {
        /**
         * 格子被选中时调用
         * @param x 列索引
         * @param y 行索引
         */
        void onCellSelected(int x, int y);
    }

    /**
     * 数独完成回调接口
     */
    public interface OnSolvedListener {
        /**
         * 数独解完时调用
         * @param elapsedMs 完成用时（毫秒）
         */
        void onSolved(long elapsedMs);
    }

    /**
     * 设置数独完成监听器
     * @param listener 完成回调
     */
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

    /**
     * 初始化所有画笔
     *
     * <p>所有画笔均启用抗锯齿（ANTI_ALIAS_FLAG）。
     * 文字画笔设置居中对齐（Align.CENTER），固定数字和相同数字使用粗体。</p>
     */
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

    /**
     * 设置游戏逻辑对象
     * @param game SudokuGame 实例
     */
    public void setGame(SudokuGame game) {
        this.game = game;
    }

    /**
     * 设置格子选中监听器
     * @param listener 选中回调
     */
    public void setOnCellSelectedListener(OnCellSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 视图尺寸变化时重新计算棋盘布局
     *
     * <p>棋盘大小取宽高较小值减去 32px 边距，居中偏移。
     * 同时根据格子大小调整文字尺寸（cellSize * 0.5f）。</p>
     *
     * @param w 新宽度
     * @param h 新高度
     */
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

    /**
     * 绘制数独棋盘、数字和状态标记
     *
     * <p>绘制顺序：</p>
     * <ol>
     *   <li>圆角矩形棋盘背景</li>
     *   <li>选中格子高亮背景</li>
     *   <li>相同数字格子高亮背景</li>
     *   <li>错误格子红色背景</li>
     *   <li>数字文字（固定/玩家/高亮）</li>
     *   <li>网格线（细线 + 粗线宫格边界）</li>
     *   <li>完成时绿色覆盖层</li>
     * </ol>
     *
     * @param canvas 画布
     */
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

    /**
     * 处理触摸事件
     *
     * <p>根据触摸坐标计算格子位置，更新选中状态并触发回调。
     * 仅处理落在棋盘范围内的触摸。</p>
     *
     * @param event 触摸事件
     * @return 始终返回 true，表示消费了触摸事件
     */
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

    /**
     * 刷新选中状态（触发重绘）
     *
     * <p>在数字输入后调用，更新格子高亮和错误标记的显示。</p>
     */
    public void refreshSelection() {
        invalidate();
    }

    /**
     * @return 当前选中格子的列索引，-1 表示未选中
     */
    public int getSelectedX() { return selectedX; }

    /**
     * @return 当前选中格子的行索引，-1 表示未选中
     */
    public int getSelectedY() { return selectedY; }
}
