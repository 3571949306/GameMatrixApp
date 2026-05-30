package com.gamecenter.app.games.plane;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 飞机大战游戏的自定义视图
 * <p>
 * 负责将 PlaneGame 的游戏状态渲染到屏幕上，包括背景星空、玩家飞机、子弹、敌机和得分文字。
 * 同时处理触摸事件，将玩家手指位置传递给游戏逻辑。
 * <p>
 * 关键设计决策：
 * - 飞机和敌机使用 Path 绘制三角形/箭头形状，而非位图，减少资源依赖
 * - 星空背景使用确定性伪随机数（固定种子 42）生成，确保每帧星空位置一致不闪烁
 * - 触摸事件中调用 performClick() 以满足无障碍访问要求
 */
public class PlaneView extends View {

    private PlaneGame game;
    /** 背景画笔（深蓝色太空） */
    private Paint bgPaint;
    /** 玩家飞机画笔（青色） */
    private Paint planePaint;
    /** 子弹画笔（黄色） */
    private Paint bulletPaint;
    /** 敌机画笔（红色） */
    private Paint enemyPaint;
    /** 得分文字画笔 */
    private Paint scorePaint;
    /** 提示文字画笔 */
    private Paint textPaint;

    private float viewWidth;
    private float viewHeight;

    /**
     * 单参数构造函数，用于代码动态创建视图
     *
     * @param context 上下文
     */
    public PlaneView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造函数，用于 XML 布局中声明视图
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public PlaneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔，设置颜色和文字属性
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#0D0D2B"));

        planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint.setColor(Color.parseColor("#00BCD4"));
        planePaint.setStyle(Paint.Style.FILL);

        bulletPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bulletPaint.setColor(Color.parseColor("#FFEB3B"));
        bulletPaint.setStyle(Paint.Style.FILL);

        enemyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enemyPaint.setColor(Color.parseColor("#E53935"));
        enemyPaint.setStyle(Paint.Style.FILL);

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(50);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 绑定游戏逻辑对象
     *
     * @param game PlaneGame 实例
     */
    public void setGame(PlaneGame game) {
        this.game = game;
    }

    /**
     * 视图尺寸变化时回调，将实际像素尺寸传递给游戏逻辑以计算飞机大小
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (game != null) {
            game.setGameArea(w, h);
        }
    }

    /**
     * 绘制游戏画面，每帧由 invalidate() 触发
     * <p>
     * 绘制顺序：背景 → 星空 → 子弹 → 敌机 → 玩家飞机 → 尾焰 → 得分 → 提示/结束文字
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#0D0D2B"));

        drawStars(canvas);

        // 绘制所有子弹（黄色小圆点）
        for (PlaneGame.Bullet b : game.getBullets()) {
            canvas.drawCircle(b.x, b.y, 5, bulletPaint);
        }

        // 绘制所有敌机（红色倒三角形状）
        for (PlaneGame.Enemy e : game.getEnemies()) {
            Path path = new Path();
            path.moveTo(e.x, e.y - e.h / 2);
            path.lineTo(e.x + e.w / 2, e.y + e.h / 2);
            path.lineTo(e.x, e.y + e.h / 3);
            path.lineTo(e.x - e.w / 2, e.y + e.h / 2);
            path.close();
            canvas.drawPath(path, enemyPaint);
        }

        float px = game.getPlaneX();
        float py = game.getPlaneY();
        float pw = game.getPlaneW();
        float ph = game.getPlaneH();

        // 绘制玩家飞机（青色正三角/箭头形状）
        Path planePath = new Path();
        planePath.moveTo(px, py - ph / 2);
        planePath.lineTo(px + pw / 2, py + ph / 2);
        planePath.lineTo(px, py + ph / 3);
        planePath.lineTo(px - pw / 2, py + ph / 2);
        planePath.close();
        canvas.drawPath(planePath, planePaint);

        // 绘制飞机尾焰（橙色小矩形）
        Paint flamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flamePaint.setColor(Color.parseColor("#FF9800"));
        canvas.drawRect(px - pw * 0.15f, py + ph * 0.25f, px + pw * 0.15f, py + ph * 0.55f, flamePaint);

        // 绘制得分文字
        canvas.drawText("" + game.getScore(), viewWidth / 2, viewHeight * 0.08f, scorePaint);

        // 游戏未开始时显示操作提示
        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("滑动屏幕移动飞机", viewWidth / 2, viewHeight * 0.35f, textPaint);
        }

        // 游戏结束时显示结果
        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(50);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("游戏结束!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 46, textPaint);
        }
    }

    /**
     * 绘制星空背景
     * <p>
     * 使用确定性伪随机数生成器（LCG 算法，固定种子 42）生成 40 颗星星的位置和大小。
     * 固定种子确保每帧绘制结果一致，避免闪烁。
     *
     * @param canvas 画布
     */
    private void drawStars(Canvas canvas) {
        Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.WHITE);
        int starSeed = 42;
        for (int i = 0; i < 40; i++) {
            // LCG 伪随机数生成：seed = seed * 1103515245 + 12345
            starSeed = starSeed * 1103515245 + 12345;
            float sx = ((starSeed >> 16) & 0x7FFF) / 32767f * viewWidth;
            starSeed = starSeed * 1103515245 + 12345;
            float sy = ((starSeed >> 16) & 0x7FFF) / 32767f * viewHeight;
            // 星星半径 1~3 像素，模拟远近不同的星星
            float r = (Math.abs(starSeed % 3) + 1) * 1f;
            canvas.drawCircle(sx, sy, r, starPaint);
        }
    }

    /**
     * 处理触摸事件
     * <p>
     * 游戏结束时点击重置游戏；正常游戏中滑动/点击控制飞机水平位置。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null) return true;
        if (game.isGameOver()) {
            // 游戏结束后点击重置
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                game.reset();
                game.setGameArea(viewWidth, viewHeight);
                invalidate();
            }
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 将触摸 X 坐标传递给游戏逻辑以移动飞机
                game.setPlaneX(event.getX());
                invalidate();
                break;
        }
        performClick();
        return true;
    }

    /**
     * 无障碍访问支持方法，必须由 onTouchEvent 调用
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
