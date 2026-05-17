package com.gamecenter.app.games.game2048;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 2048 游戏自定义绘制视图
 *
 * <p>负责将 {@link Game2048Game} 的棋盘状态渲染到屏幕上。
 * 包含棋盘背景、方块颜色映射、数字绘制以及游戏结束遮罩的绘制逻辑。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>方块颜色通过以 2 为底的对数计算索引，映射到预定义的颜色数组</li>
 *   <li>棋盘始终居中显示，取宽高的较小值作为棋盘尺寸</li>
 *   <li>数字字号根据数值大小动态调整（≥1000 时缩小）</li>
 * </ul>
 * </p>
 */
public class Game2048View extends View {

    /** 游戏逻辑实例 */
    private Game2048Game game;
    /** 画笔，复用以减少对象创建 */
    private Paint paint;
    /** 方块背景颜色数组，索引 = log2(方块值) */
    private int[] tileColors;
    /** 方块文字颜色数组，与 tileColors 一一对应 */
    private int[] textColors;

    /**
     * 单参数构造方法（代码创建时使用）
     *
     * @param context 上下文
     */
    public Game2048View(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法（XML 布局膨胀时使用）
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public Game2048View(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔和颜色映射表
     *
     * <p>tileColors 数组的索引对应 log2(方块值)：
     * 索引 0 对应空格（浅米色），索引 1 对应 2，索引 2 对应 4，以此类推。
     * textColors 在低数值时使用深色文字，高数值时使用白色文字以保证可读性。</p>
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tileColors = new int[]{
            0xFFE0D5C5, 0xFFEEE4DA, 0xFFFFDFB8, 0xFFFFCC66,
            0xFFFFAA33, 0xFFFF8833, 0xFFFF6633, 0xFFFF4400,
            0xFF88CCFF, 0xFF55AADD, 0xFF2288BB, 0xFF115599,
            0xFFDDAA77, 0xFFCC8844, 0xFFBB6622, 0xFF4A3728
        };
        textColors = new int[]{
            0xFF5D4E37, 0xFF5D4E37, 0xFF5D4E37, 0xFF5D4E37,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF
        };
    }

    /**
     * 设置游戏逻辑实例
     *
     * @param game 2048 游戏逻辑对象
     */
    public void setGame(Game2048Game game) {
        this.game = game;
    }

    /**
     * 绘制棋盘、方块和游戏结束遮罩
     *
     * <p>绘制流程：
     * <ol>
     *   <li>计算棋盘尺寸和偏移量，确保居中显示</li>
     *   <li>绘制圆角矩形棋盘背景</li>
     *   <li>遍历 4×4 网格，绘制每个方块的背景色和数字</li>
     *   <li>若游戏结束，绘制半透明遮罩和结束文字</li>
     * </ol>
     * </p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (game == null) return;

        int width = getWidth();
        int height = getHeight();
        // 取宽高较小值减去边距，确保棋盘不超出屏幕
        int boardSize = Math.min(width, height) - 32;
        int tileSize = boardSize / 4;
        // 居中偏移
        int offsetX = (width - boardSize) / 2;
        int offsetY = (height - boardSize) / 2;

        // 绘制棋盘背景（圆角矩形）
        paint.setColor(0xFFBBADA0);
        canvas.drawRoundRect(new RectF(offsetX, offsetY, offsetX + boardSize, offsetY + boardSize), 10, 10, paint);

        // 遍历绘制每个方块
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                // 每个方块之间留 4px 间距
                int left = offsetX + x * tileSize + 4;
                int top = offsetY + y * tileSize + 4;
                int right = left + tileSize - 8;
                int bottom = top + tileSize - 8;

                int value = game.getTile(x, y);
                // 通过 log2(值) 计算颜色索引
                int colorIndex = 0;
                if (value > 0) {
                    colorIndex = (int)(Math.log(value) / Math.log(2));
                    // 防止索引越界：超大数值使用最后一个颜色
                    if (colorIndex >= tileColors.length) colorIndex = tileColors.length - 1;
                }

                // 绘制方块背景
                paint.setColor(tileColors[colorIndex]);
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 5, 5, paint);

                // 绘制方块数字
                if (value > 0) {
                    paint.setColor(textColors[colorIndex]);
                    paint.setTextSize(tileSize / 3);
                    paint.setTextAlign(Paint.Align.CENTER);
                    String text = String.valueOf(value);
                    // 大数字（≥1000）缩小字号以适应方块
                    if (value >= 1000) paint.setTextSize(tileSize / 4);
                    // 垂直居中：通过 ascent + descent 偏移修正
                    canvas.drawText(text, (left + right) / 2f, (top + bottom) / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
                }
            }
        }

        // 游戏结束遮罩
        if (game.isGameOver()) {
            paint.setColor(0xCC000000);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(48);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("游戏结束!", width / 2f, height / 2f - 30, paint);
            paint.setTextSize(24);
            canvas.drawText("最终分数: " + game.getScore(), width / 2f, height / 2f + 30, paint);
        }
    }
}
