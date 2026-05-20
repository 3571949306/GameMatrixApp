package com.gamecenter.app.games.sokoban;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 推箱子游戏自定义绘制视图
 *
 * <p>负责将 {@link SokobanGame} 的地图状态渲染到屏幕上，
 * 并处理滑动手势以控制玩家移动。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>每种地图元素使用独立的 Paint 对象，带阴影效果增强立体感</li>
 *   <li>墙壁使用线性渐变模拟光照效果</li>
 *   <li>滑动手势阈值 50px，避免误触</li>
 *   <li>棋盘自动居中，根据视图尺寸计算单元格大小</li>
 * </ul>
 * </p>
 */
public class SokobanView extends View {

    /** 游戏逻辑实例 */
    private SokobanGame game;
    /** 墙壁画笔（深棕色，带阴影） */
    private Paint wallPaint;
    /** 地板画笔（浅灰色） */
    private Paint floorPaint;
    /** 箱子画笔（橙色，带阴影） */
    private Paint boxPaint;
    /** 目标点画笔（绿色，半透明） */
    private Paint targetPaint;
    /** 箱子在目标点上的画笔（浅绿色，带阴影） */
    private Paint boxOnTargetPaint;
    /** 玩家画笔（蓝色，带阴影） */
    private Paint playerPaint;
    /** 玩家在目标点上的画笔（浅蓝色，带阴影） */
    private Paint playerOnTargetPaint;
    /** 单元格尺寸（像素） */
    private float cellSize;
    /** 棋盘绘制偏移量，用于居中显示 */
    private float offsetX, offsetY;
    /** 关卡完成监听器 */
    private OnLevelCompleteListener listener;
    /** 触摸起始坐标，用于计算滑动方向 */
    private float touchStartX, touchStartY;

    /**
     * 关卡完成回调接口
     */
    public interface OnLevelCompleteListener {
        /** 关卡完成时调用 */
        void onComplete();
    }

    /**
     * 单参数构造方法
     *
     * @param context 上下文
     */
    public SokobanView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法（XML 布局使用）
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public SokobanView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     *
     * <p>为每种地图元素创建独立的 Paint 对象，设置颜色、样式和阴影效果。
     * 阴影使用 setShadowLayer 实现简单的立体感。</p>
     */
    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(0xFF5D4037);
        wallPaint.setStyle(Paint.Style.FILL);
        wallPaint.setShadowLayer(4, 2, 2, 0x40000000);

        floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorPaint.setColor(0xFFF5F5F5);
        floorPaint.setStyle(Paint.Style.FILL);

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(0xFFFF9800);
        boxPaint.setStyle(Paint.Style.FILL);
        boxPaint.setShadowLayer(3, 1, 1, 0x40000000);

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setColor(0xFF4CAF50);
        targetPaint.setStyle(Paint.Style.FILL);
        targetPaint.setAlpha(180);

        boxOnTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxOnTargetPaint.setColor(0xFF8BC34A);
        boxOnTargetPaint.setStyle(Paint.Style.FILL);
        boxOnTargetPaint.setShadowLayer(3, 1, 1, 0x40000000);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(0xFF2196F3);
        playerPaint.setStyle(Paint.Style.FILL);
        playerPaint.setShadowLayer(3, 1, 1, 0x40000000);

        playerOnTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerOnTargetPaint.setColor(0xFF64B5F6);
        playerOnTargetPaint.setStyle(Paint.Style.FILL);
        playerOnTargetPaint.setShadowLayer(3, 1, 1, 0x40000000);
    }

    /**
     * 设置游戏逻辑实例
     *
     * @param game 推箱子游戏逻辑对象
     */
    public void setGame(SokobanGame game) {
        this.game = game;
    }

    /**
     * 设置关卡完成监听器
     *
     * @param listener 关卡完成回调
     */
    public void setOnLevelCompleteListener(OnLevelCompleteListener listener) {
        this.listener = listener;
    }

    /**
     * 获取关卡完成监听器
     *
     * @return 当前的关卡完成监听器
     */
    public OnLevelCompleteListener getOnLevelCompleteListener() {
        return listener;
    }

    /**
     * 视图尺寸变化时重新计算单元格大小和偏移量
     *
     * <p>根据视图宽高和地图行列数，计算使棋盘居中显示的单元格尺寸和偏移量。</p>
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        int rows = game.getRows();
        int cols = game.getCols();
        float cellW = w / (float) cols;
        float cellH = h / (float) rows;
        // 取较小值确保单元格为正方形
        cellSize = Math.min(cellW, cellH);
        // 居中偏移
        offsetX = (w - cellSize * cols) / 2;
        offsetY = (h - cellSize * rows) / 2;
    }

    /**
     * 绘制推箱子地图
     *
     * <p>绘制流程：
     * <ol>
     *   <li>遍历地图每个格子</li>
     *   <li>墙壁：使用线性渐变模拟光照</li>
     *   <li>地板：浅灰色填充</li>
     *   <li>目标点：绘制同心圆标记</li>
     *   <li>箱子：圆角矩形 + 对角线装饰</li>
     *   <li>箱子在目标点上：圆角矩形 + 中心圆点</li>
     *   <li>玩家：圆形 + 中心白点</li>
     * </ol>
     * </p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        int rows = game.getRows();
        int cols = game.getCols();

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                int tile = game.getTile(x, y);

                if (tile == SokobanGame.WALL) {
                    // 墙壁使用线性渐变模拟光照效果
                    wallPaint.setShader(new android.graphics.LinearGradient(left, top, right, bottom, 
                        0xFF8D6E63, 0xFF5D4037, android.graphics.Shader.TileMode.CLAMP));
                    canvas.drawRect(left, top, right, bottom, wallPaint);
                    wallPaint.setShader(null);
                } else {
                    // 非墙壁区域先绘制地板
                    canvas.drawRect(left, top, right, bottom, floorPaint);

                    // 目标点标记：绘制两个同心圆
                    if (tile == SokobanGame.TARGET || tile == SokobanGame.BOX_ON_TARGET || tile == SokobanGame.PLAYER_ON_TARGET) {
                        float margin = cellSize * 0.2f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, targetPaint);
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin * 0.6f, targetPaint);
                    }

                    if (tile == SokobanGame.BOX) {
                        // 箱子：圆角矩形 + 对角线装饰
                        float margin = cellSize * 0.12f;
                        android.graphics.RectF boxRect = new android.graphics.RectF(left + margin, top + margin, right - margin, bottom - margin);
                        canvas.drawRoundRect(boxRect, 12, 12, boxPaint);
                        // 对角线装饰
                        canvas.drawLine(left + margin * 2, top + margin * 2, right - margin * 2, bottom - margin * 2, new Paint() {{
                            setColor(0xB3FFFFFF);
                            setStrokeWidth(3);
                            setAntiAlias(true);
                        }});
                    } else if (tile == SokobanGame.BOX_ON_TARGET) {
                        // 箱子在目标点上：圆角矩形 + 中心白色圆点
                        float margin = cellSize * 0.12f;
                        android.graphics.RectF boxRect = new android.graphics.RectF(left + margin, top + margin, right - margin, bottom - margin);
                        canvas.drawRoundRect(boxRect, 12, 12, boxOnTargetPaint);
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.15f, new Paint() {{
                            setColor(0xFFFFFFFF);
                            setAntiAlias(true);
                        }});
                    } else if (tile == SokobanGame.PLAYER) {
                        // 玩家：蓝色圆形 + 中心白点
                        float margin = cellSize * 0.18f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, playerPaint);
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.12f, new Paint() {{
                            setColor(0xFFFFFFFF);
                            setAntiAlias(true);
                        }});
                    } else if (tile == SokobanGame.PLAYER_ON_TARGET) {
                        // 玩家在目标点上：浅蓝色圆形 + 中心白点
                        float margin = cellSize * 0.18f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, playerOnTargetPaint);
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.12f, new Paint() {{
                            setColor(0xFFFFFFFF);
                            setAntiAlias(true);
                        }});
                    }
                }
            }
        }
    }

    /**
     * 处理触摸事件，实现滑动手势控制
     *
     * <p>记录触摸起始点，在手指抬起时计算滑动方向和距离。
     * 滑动距离超过 50px 时才触发移动，避免误触。
     * 移动后检查关卡是否完成，若完成则触发监听器回调。</p>
     *
     * @param event 触摸事件
     * @return 是否消费了该事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                // 滑动距离阈值 50px
                if (Math.max(absDx, absDy) > 50) {
                    // 判断主滑动方向
                    if (absDx > absDy) {
                        game.move(dx > 0 ? 1 : -1, 0);
                    } else {
                        game.move(0, dy > 0 ? 1 : -1);
                    }

                    // 检查关卡完成
                    if (game.isLevelComplete() && listener != null) {
                        listener.onComplete();
                    }
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
