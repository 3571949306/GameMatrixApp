package com.gamecenter.app.games.flappy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Flappy Bird 风格游戏自定义绘制 View
 *
 * <p>负责将 FlappyGame 的状态渲染到屏幕上，包括天空背景、管道、
 * 地面、小鸟、得分和游戏状态提示。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>绘制天空蓝色背景和棕色地面</li>
 *   <li>绘制绿色管道（含深色管帽）</li>
 *   <li>绘制金色小鸟（含翅膀和眼睛细节）</li>
 *   <li>绘制得分、开始提示和游戏结束画面</li>
 *   <li>处理触摸事件（跳跃/重新开始）</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>View 同时负责绘制和触摸事件处理，因为 Flappy Bird 的交互逻辑简单（点击即跳跃）</li>
 *   <li>管道绘制分为管身和管帽两部分，管帽比管身宽30%，增强视觉效果</li>
 *   <li>小鸟由圆形身体 + 三角形翅膀 + 圆形眼睛组成</li>
 *   <li>游戏结束后点击屏幕自动重置并重新开始</li>
 * </ul>
 */
public class FlappyView extends View {

    /** 游戏逻辑实例 */
    private FlappyGame game;

    /** 天空背景画笔 */
    private Paint skyPaint;

    /** 小鸟身体画笔（金色） */
    private Paint birdPaint;

    /** 小鸟眼睛画笔（黑色） */
    private Paint birdEyePaint;

    /** 管道管身画笔（绿色） */
    private Paint pipePaint;

    /** 管道管帽画笔（深绿色） */
    private Paint pipeCapPaint;

    /** 地面画笔（棕色） */
    private Paint groundPaint;

    /** 得分文字画笔 */
    private Paint scorePaint;

    /** 标题文字画笔（"点击屏幕起飞!"） */
    private Paint titlePaint;

    /** 副标题文字画笔（"避开绿色管道"） */
    private Paint subPaint;

    /** View 宽度（像素） */
    private float viewWidth;

    /** View 高度（像素） */
    private float viewHeight;

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public FlappyView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML 布局 inflate 时调用）。
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public FlappyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔。
     *
     * <p>创建天空蓝、金色小鸟、黑色眼睛、绿色管道、深绿管帽、
     * 棕色地面和各种文字画笔。所有画笔在此一次性创建，
     * 避免在 onDraw 中频繁分配对象。</p>
     */
    private void init() {
        skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        skyPaint.setColor(Color.parseColor("#87CEEB"));

        birdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdPaint.setColor(Color.parseColor("#FFD700"));
        birdPaint.setStyle(Paint.Style.FILL);

        birdEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdEyePaint.setColor(Color.BLACK);
        birdEyePaint.setStyle(Paint.Style.FILL);

        pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipePaint.setColor(Color.parseColor("#4CAF50"));
        pipePaint.setStyle(Paint.Style.FILL);

        pipeCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeCapPaint.setColor(Color.parseColor("#388E3C"));
        pipeCapPaint.setStyle(Paint.Style.FILL);

        groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        groundPaint.setColor(Color.parseColor("#8B4513"));

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(80);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(50);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);

        subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.WHITE);
        subPaint.setTextSize(36);
        subPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置游戏逻辑实例。
     *
     * @param game FlappyGame 实例
     */
    public void setGame(FlappyGame game) {
        this.game = game;
    }

    /**
     * View 尺寸变化时更新尺寸数据并通知游戏逻辑。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
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
     * 绘制游戏画面。
     *
     * <p>绘制顺序：天空背景 → 管道 → 地面 → 小鸟 → 开始提示 → 得分 → 游戏结束画面。</p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#87CEEB"));

        float groundY = game.getGroundY();

        // 绘制所有管道
        for (FlappyGame.Pipe pipe : game.getPipes()) {
            float pipeX = pipe.x;
            float pipeW = game.getPipeWidth();
            float capH = pipeW * 0.3f;

            // 上方管道：管身 + 管帽
            canvas.drawRect(pipeX, 0, pipeX + pipeW, pipe.gapY - capH, pipePaint);
            canvas.drawRect(pipeX - capH * 0.3f, pipe.gapY - capH, pipeX + pipeW + capH * 0.3f, pipe.gapY, pipeCapPaint);

            // 下方管道：管帽 + 管身
            float lowerTop = pipe.gapY + pipe.gapHeight;
            canvas.drawRect(pipeX - capH * 0.3f, lowerTop, pipeX + pipeW + capH * 0.3f, lowerTop + capH, pipeCapPaint);
            canvas.drawRect(pipeX, lowerTop + capH, pipeX + pipeW, groundY, pipePaint);
        }

        // 绘制地面（棕色）和草地（绿色细条）
        canvas.drawRect(0, groundY, viewWidth, viewHeight, groundPaint);
        Paint grassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grassPaint.setColor(Color.parseColor("#228B22"));
        canvas.drawRect(0, groundY, viewWidth, groundY + 6, grassPaint);

        // 绘制小鸟（圆形身体 + 三角形翅膀 + 圆形眼睛）
        float birdX = game.getBirdX();
        float birdY = game.getBirdY();
        float birdR = game.getBirdRadius();

        // 条件始终为true（逻辑冗余但保留绘制），确保小鸟在任何状态下都可见
        if (!game.isStarted() || !game.isGameOver() || game.isGameOver()) {
            canvas.drawCircle(birdX, birdY, birdR, birdPaint);
            // 翅膀：三角形，位于小鸟左侧
            Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wingPaint.setColor(Color.parseColor("#FFA000"));
            Path wing = new Path();
            wing.moveTo(birdX - birdR * 0.3f, birdY);
            wing.lineTo(birdX - birdR * 1.2f, birdY - birdR * 0.5f);
            wing.lineTo(birdX - birdR * 0.3f, birdY + birdR * 0.3f);
            wing.close();
            canvas.drawPath(wing, wingPaint);
            // 眼睛：小圆点，位于小鸟右上方
            canvas.drawCircle(birdX + birdR * 0.45f, birdY - birdR * 0.25f, birdR * 0.2f, birdEyePaint);
        }

        // 游戏未开始时显示操作提示
        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("点击屏幕起飞!", viewWidth / 2, viewHeight * 0.3f, titlePaint);
            canvas.drawText("避开绿色管道", viewWidth / 2, viewHeight * 0.3f + 50, subPaint);
        }

        // 绘制得分（带阴影增强可读性）
        String scoreText = String.valueOf(game.getScore());
        scorePaint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText(scoreText, viewWidth / 2, viewHeight * 0.12f, scorePaint);

        // 游戏结束时显示结束画面
        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(56);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            overPaint.setShadowLayer(4, 2, 2, Color.BLACK);
            canvas.drawText("游戏结束!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            subPaint.setTextSize(36);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 50, subPaint);
        }
    }

    /**
     * 处理触摸事件。
     *
     * <p>触摸逻辑：</p>
     * <ul>
     *   <li>游戏结束时：重置游戏并重新设置游戏区域</li>
     *   <li>游戏进行中：执行跳跃</li>
     * </ul>
     *
     * @param event 触摸事件
     * @return 始终返回 true，表示事件已消费
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            if (game.isGameOver()) {
                // 游戏结束后点击：重置游戏
                game.reset();
                game.setGameArea(viewWidth, viewHeight);
                invalidate();
                return true;
            }
            // 游戏进行中：跳跃
            game.jump();
            invalidate();
            performClick();
        }
        return true;
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
