package com.gamecenter.app.games.snake;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.gamecenter.app.R;
import java.util.List;

/**
 * 贪吃蛇游戏自定义绘制 View
 *
 * <p>负责将 SnakeGame 的状态渲染到屏幕上，包括棋盘网格、蛇身、食物和游戏结束画面。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>根据 View 尺寸动态计算格子大小和棋盘偏移量，确保棋盘居中显示</li>
 *   <li>从颜色资源中读取主题色，支持主题切换</li>
 *   <li>蛇头和蛇身使用不同颜色区分，食物以圆形绘制</li>
 *   <li>游戏结束时绘制半透明遮罩和提示文字</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>所有 Paint 对象在 init() 中一次性创建，避免 onDraw 中频繁分配导致性能问题</li>
 *   <li>棋盘四周留有1格边距（COLS+2, ROWS+2），使棋盘不贴边显示</li>
 *   <li>蛇身从尾部向头部绘制，确保蛇头始终在最上层</li>
 * </ul>
 */
public class SnakeView extends View {

    /** 游戏逻辑实例 */
    private SnakeGame game;

    /** 每个格子的像素大小 */
    private int cellSize;

    /** 棋盘在 View 中的水平偏移量，用于居中 */
    private float offsetX;

    /** 棋盘在 View 中的垂直偏移量，用于居中 */
    private float offsetY;

    /** 棋盘背景画笔 */
    private Paint bgPaint, gridPaint, snakeHeadPaint, snakeBodyPaint;

    /** 蛇身边框、食物、食物边框、文字画笔 */
    private Paint snakeBorderPaint, foodPaint, foodBorderPaint, textPaint;

    /** 从资源中读取的主题颜色值 */
    private int snakeColorHead;
    private int snakeColorBody;
    private int foodColor;
    private int gridColor;
    private int boardBgColor;

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public SnakeView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML 布局 inflate 时调用）。
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public SnakeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔和颜色资源。
     *
     * <p>从 colors.xml 资源中读取主题色，创建抗锯齿画笔。
     * 所有画笔在此一次性创建，避免 onDraw 中的对象分配。</p>
     */
    private void init() {
        Resources res = getResources();
        boardBgColor = res.getColor(R.color.snake_bg, null);
        gridColor = res.getColor(R.color.snake_grid, null);
        snakeColorHead = res.getColor(R.color.snake_head, null);
        snakeColorBody = res.getColor(R.color.snake_body, null);
        foodColor = res.getColor(R.color.snake_food, null);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(boardBgColor);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        snakeHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeHeadPaint.setColor(snakeColorHead);

        snakeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeBodyPaint.setColor(snakeColorBody);

        snakeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeBorderPaint.setStyle(Paint.Style.STROKE);
        snakeBorderPaint.setColor(Color.rgb(50, 50, 50));
        snakeBorderPaint.setStrokeWidth(2f);

        foodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foodPaint.setColor(foodColor);

        foodBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foodBorderPaint.setStyle(Paint.Style.STROKE);
        foodBorderPaint.setColor(Color.rgb(180, 50, 50));
        foodBorderPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置游戏逻辑实例并触发重绘。
     *
     * @param game SnakeGame 实例
     */
    public void setGame(SnakeGame game) {
        this.game = game;
        invalidate();
    }

    /**
     * View 尺寸变化时重新计算格子大小和偏移量。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    /**
     * 根据当前 View 尺寸重新计算格子大小和棋盘偏移量。
     *
     * <p>棋盘四周预留1格边距（COLS+2, ROWS+2），
     * 取宽高方向上较小的格子尺寸以确保棋盘完整显示，
     * 然后计算偏移量使棋盘在 View 中居中。</p>
     *
     * @param w View 宽度
     * @param h View 高度
     */
    private void recalcDimensions(int w, int h) {
        int cellW = w / (SnakeGame.COLS + 2);
        int cellH = h / (SnakeGame.ROWS + 2);
        cellSize = Math.min(cellW, cellH);
        int boardW = SnakeGame.COLS * cellSize;
        int boardH = SnakeGame.ROWS * cellSize;
        offsetX = (w - boardW) / 2f;
        offsetY = (h - boardH) / 2f;
    }

    /**
     * 绘制游戏画面。
     *
     * <p>绘制顺序：背景 → 网格 → 食物 → 蛇身 → 游戏结束遮罩。</p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) recalcDimensions(getWidth(), getHeight());

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        drawGrid(canvas);
        drawFood(canvas);
        drawSnake(canvas);

        if (game != null && game.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    /**
     * 绘制棋盘网格线。
     *
     * @param canvas 画布
     */
    private void drawGrid(Canvas canvas) {
        for (int y = 0; y <= SnakeGame.ROWS; y++) {
            float y1 = offsetY + y * cellSize;
            canvas.drawLine(offsetX, y1, offsetX + SnakeGame.COLS * cellSize, y1, gridPaint);
        }
        for (int x = 0; x <= SnakeGame.COLS; x++) {
            float x1 = offsetX + x * cellSize;
            canvas.drawLine(x1, offsetY, x1, offsetY + SnakeGame.ROWS * cellSize, gridPaint);
        }
    }

    /**
     * 绘制食物（圆形）。
     *
     * <p>食物以圆形绘制在所在格子的中心，半径为格子尺寸的40%，
     * 外加深红色边框增强视觉效果。</p>
     *
     * @param canvas 画布
     */
    private void drawFood(Canvas canvas) {
        if (game == null) return;
        int[] food = game.getFood();
        float cx = offsetX + food[0] * cellSize + cellSize / 2f;
        float cy = offsetY + food[1] * cellSize + cellSize / 2f;
        float radius = cellSize * 0.4f;
        canvas.drawCircle(cx, cy, radius, foodPaint);
        canvas.drawCircle(cx, cy, radius, foodBorderPaint);
    }

    /**
     * 绘制蛇身。
     *
     * <p>从尾部向头部遍历绘制，确保蛇头在最上层。
     * 每节蛇身以圆角矩形绘制，四周留2像素间距。
     * 蛇头（索引0）使用不同颜色区分。</p>
     *
     * @param canvas 画布
     */
    private void drawSnake(Canvas canvas) {
        if (game == null) return;
        List<int[]> snake = game.getSnake();
        if (snake == null || snake.isEmpty()) return;

        // 从尾部向头部绘制，保证蛇头在最上层
        for (int i = snake.size() - 1; i >= 0; i--) {
            int[] segment = snake.get(i);
            float left = offsetX + segment[0] * cellSize + 2;
            float top = offsetY + segment[1] * cellSize + 2;
            float right = offsetX + (segment[0] + 1) * cellSize - 2;
            float bottom = offsetY + (segment[1] + 1) * cellSize - 2;

            // 蛇头使用不同颜色
            Paint paint = (i == 0) ? snakeHeadPaint : snakeBodyPaint;
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, 8, 8, paint);
            canvas.drawRoundRect(rect, 8, 8, snakeBorderPaint);
        }
    }

    /**
     * 绘制游戏结束画面。
     *
     * <p>绘制半透明黑色遮罩，居中显示"游戏结束"、得分和重新开始提示。
     * 注意：每次绘制后需将文字大小恢复为默认值（48），避免影响后续绘制。</p>
     *
     * @param canvas 画布
     */
    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint();
        overlay.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        int score = game.getScore();
        canvas.drawText("游戏结束", getWidth() / 2f, getHeight() / 2f - 40, textPaint);
        textPaint.setTextSize(36);
        canvas.drawText("得分: " + score, getWidth() / 2f, getHeight() / 2f + 20, textPaint);
        textPaint.setTextSize(28);
        canvas.drawText("点击重新开始", getWidth() / 2f, getHeight() / 2f + 70, textPaint);
        // 恢复默认文字大小
        textPaint.setTextSize(48);
    }

    /**
     * 辅助无障碍点击方法。
     *
     * @return 始终返回 true
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
