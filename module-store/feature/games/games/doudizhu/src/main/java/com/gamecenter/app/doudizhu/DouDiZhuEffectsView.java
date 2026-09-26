package com.gamecenter.app.doudizhu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 斗地主特效视图 (DouDiZhu Effects View)
 *
 * <p>这是一个覆盖在游戏桌面上的特效视图，用于显示炸弹、飞机、春天等特效动画。
 * 所有效果均使用 Canvas + Paint 纯代码绘制，不依赖任何图片资源。</p>
 *
 * <p><b>支持的特效类型：</b></p>
 * <ul>
 *   <li><b>BOMB（炸弹）</b>：红色到橙色径向渐变爆炸圆 + 火花粒子 + "炸弹"文字</li>
 *   <li><b>ROCKET（火箭）</b>：与炸弹类似但更强烈的爆炸效果</li>
 *   <li><b>PLANE（飞机）</b>：简笔画飞机从左飞入右飞出 + 烟雾轨迹</li>
 *   <li><b>SPRING（春天）</b>：粉色花瓣飘落 + "春天"文字 + 淡粉色背景</li>
 *   <li><b>SHUNZI（顺子）</b>：金色链条效果（5个圆环连接）+ "顺子"文字</li>
 *   <li><b>DOUBLE_LINE（连对）</b>：两排金色圆点平行排列 + "连对"文字</li>
 * </ul>
 *
 * <p><b>设计思路：</b></p>
 * <ul>
 *   <li>使用 ValueAnimator 驱动所有动画，动画结束后自动清除</li>
 *   <li>震动不在此视图内触发：统一由 DoudizhuFeedback.playFeedbackFor
 *       按特效档位单点负责（避免动画与震动双路径）</li>
 *   <li>所有绘制在 onDraw 中完成，保持高性能</li>
 * </ul>
 *
 * <p><b>时序防护（P5 审查修复）：</b>旧动画被 {@link #clearEffect()} cancel 时
 * 仍会触发 {@code onAnimationEnd}，若在结束回调里排延迟清除，陈旧回调会在
 * 新特效播放中执行造成级联抹除。为此引入 {@link EffectGeneration} 代际计数：
 * 每次入场/清除产生新代际，结束回调与延迟清除回调携带所属代际，执行前校验
 * 归属，过时回调直接失效。延迟清除统一走具名 {@link #effectClearTask}
 * （showEffect 前先 removeCallbacks），并在 {@link #onDetachedFromWindow}
 * 中移除回调 + cancel 动画，防窗口泄漏。</p>
 */
public class DouDiZhuEffectsView extends View {

    /**
     * 特效类型枚举
     */
    public enum EffectType {
        BOMB,      // 炸弹
        ROCKET,    // 火箭（王炸）
        PLANE,     // 飞机
        SPRING,    // 春天
        SHUNZI,    // 顺子
        DOUBLE_LINE // 连对
    }

    private EffectType currentEffect;
    private float centerX;
    private float centerY;
    private ValueAnimator animator;
    private float animationProgress = 0f;
    private Random random = new Random();

    /** 特效代际计数：动画结束回调/延迟清除回调执行前校验归属，防陈旧回调级联。 */
    private final EffectGeneration effectGeneration = new EffectGeneration();

    // 炸弹特效数据
    private List<Spark> sparks = new ArrayList<>();
    private float bombRadius = 0f;
    private float bombTextScale = 0f;

    // 飞机特效数据
    private float planeX = -200f;
    private float planeY = 0f;
    private List<SmokeTrail> smokeTrails = new ArrayList<>();
    private float planeTextAlpha = 0f;

    // 春天特效数据
    private List<Petal> petals = new ArrayList<>();
    private float springBgAlpha = 0f;
    private float springTextScale = 0f;

    // 顺子特效数据
    private float chainProgress = 0f;
    private float shunziTextAlpha = 0f;

    // 连对特效数据
    private float doubleLineProgress = 0f;
    private float doubleLineTextAlpha = 0f;

    private Paint paint;
    private Paint textPaint;

    public DouDiZhuEffectsView(Context context) {
        super(context);
        init();
    }

    public DouDiZhuEffectsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DouDiZhuEffectsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setVisibility(GONE);
    }

    /**
     * 显示指定类型的特效
     *
     * @param type    特效类型
     * @param centerX 特效中心 X 坐标
     * @param centerY 特效中心 Y 坐标
     */
    public void showEffect(EffectType type, float centerX, float centerY) {
        clearEffect();

        this.currentEffect = type;
        this.centerX = centerX;
        this.centerY = centerY;
        this.animationProgress = 0f;
        setVisibility(VISIBLE);

        switch (type) {
            case BOMB:
            case ROCKET:
                initBombEffect(type);
                break;
            case PLANE:
                initPlaneEffect();
                break;
            case SPRING:
                initSpringEffect();
                break;
            case SHUNZI:
                initShunziEffect();
                break;
            case DOUBLE_LINE:
                initDoubleLineEffect();
                break;
        }

        invalidate();
    }

    /**
     * 清除当前特效。
     *
     * <p>先 {@code begin()} 使旧动画的结束回调失效，再 cancel——cancel 会同步
     * 触发 {@code onAnimationEnd}，旧回调此时校验代际已不匹配，不会再排延迟
     * 清除，"陈旧回调抹掉新特效"的级联就此截断。</p>
     */
    public void clearEffect() {
        // begin() 使旧动画的结束回调与在途延迟清除任务一并失效，
        // 再移除任务本体——cancel 触发的 onAnimationEnd 校验代际已不匹配，
        // 不会再排新任务，"陈旧回调抹掉新特效"的级联就此截断
        effectGeneration.begin();
        removeCallbacks(effectClearTask);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        currentEffect = null;
        sparks.clear();
        smokeTrails.clear();
        petals.clear();
        setVisibility(GONE);
        invalidate();
    }

    /** 当前动画所属代际（init 时登记）；结束回调据此判断自己是否已过时。 */
    private int animatorGeneration;

    /** 延迟清除任务被排出时所属的代际；执行时校验，防止抹掉新特效。 */
    private int pendingClearGeneration;

    /**
     * 延迟清除任务（具名，便于 showEffect 前 removeCallbacks、detach 时移除）。
     */
    private final Runnable effectClearTask = this::clearEffectIfCurrent;

    /** 延迟清除任务执行体：代际仍归属当前特效才真正清除，否则静默丢弃。 */
    private void clearEffectIfCurrent() {
        if (effectGeneration.isCurrent(pendingClearGeneration)) {
            clearEffect();
        }
    }

    /** 当前动画是否仍是最新归属（自然结束 vs 被 cancel/取代 的分界）。 */
    private boolean animatorIsCurrent() {
        return effectGeneration.isCurrent(animatorGeneration);
    }

    /**
     * 动画自然结束后的统一收尾：排一次携带代际的延迟清除。炸弹 500ms 与
     * 顺子/连对/春天的 800ms 统一走本方法。
     *
     * @param delayMs 结束后再停留的毫秒数
     */
    private void scheduleClearAfterEnd(long delayMs) {
        pendingClearGeneration = animatorGeneration;
        postDelayed(effectClearTask, delayMs);
    }

    /** 登记新动画的代际归属并启动（各 initXxxEffect 统一收口）。 */
    private void startEffectAnimator(ValueAnimator anim) {
        animator = anim;
        animatorGeneration = effectGeneration.begin();
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        // 防泄漏：移除待执行的延迟清除；先失效再 cancel，防止结束回调又排新任务
        removeCallbacks(effectClearTask);
        if (animator != null) {
            effectGeneration.begin();
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    // ==================== 炸弹/火箭特效 ====================

    private void initBombEffect(EffectType type) {
        // 初始化火花粒子
        sparks.clear();
        int sparkCount = type == EffectType.ROCKET ? 16 : 8;
        for (int i = 0; i < sparkCount; i++) {
            float angle = (float) (Math.PI * 2 * i / sparkCount);
            float speed = 150 + random.nextFloat() * 200;
            sparks.add(new Spark(
                centerX, centerY,
                (float) Math.cos(angle) * speed,
                (float) Math.sin(angle) * speed,
                6 + random.nextFloat() * 8
            ));
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(type == EffectType.ROCKET ? 500 : 300);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            bombRadius = animationProgress * 200f;

            // 更新火花位置
            for (Spark spark : sparks) {
                spark.x = centerX + spark.vx * animationProgress;
                spark.y = centerY + spark.vy * animationProgress;
                spark.alpha = (int) (255 * (1f - animationProgress));
                spark.size = spark.initialSize * (1f - animationProgress * 0.5f);
            }

            // 文字弹跳动画
            if (animationProgress < 0.3f) {
                bombTextScale = animationProgress / 0.3f;
            } else if (animationProgress < 0.6f) {
                bombTextScale = 1f + (0.6f - animationProgress) / 0.3f * 0.3f;
            } else {
                bombTextScale = 1f;
            }

            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 只有自然结束（未被 cancel/取代）才排延迟清除；
                // clearEffect/detach 路径 cancel 时代际已失效，直接跳过
                if (animatorIsCurrent()) {
                    scheduleClearAfterEnd(500);
                }
            }
        });
        startEffectAnimator(animator);
    }

    private void drawBomb(Canvas canvas) {
        if (animationProgress <= 0f) return;

        // 绘制爆炸圆（径向渐变）
        RadialGradient gradient = new RadialGradient(
            centerX, centerY, bombRadius,
            new int[]{Color.YELLOW, Color.parseColor("#FF6B35"), Color.RED, Color.TRANSPARENT},
            new float[]{0f, 0.3f, 0.7f, 1f},
            Shader.TileMode.CLAMP
        );
        paint.setShader(gradient);
        canvas.drawCircle(centerX, centerY, bombRadius, paint);
        paint.setShader(null);

        // 绘制火花
        paint.setStyle(Paint.Style.FILL);
        for (Spark spark : sparks) {
            paint.setColor(Color.argb(spark.alpha, 255, 140, 0));
            canvas.drawCircle(spark.x, spark.y, spark.size, paint);
        }

        // 绘制文字
        String text = getContext().getString(currentEffect == EffectType.ROCKET
                ? R.string.game_doudizhu_effect_rocket : R.string.game_doudizhu_effect_bomb);
        textPaint.setColor(Color.parseColor("#FFD700"));
        textPaint.setTextSize(48 * getResources().getDisplayMetrics().scaledDensity * bombTextScale);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(4, 2, 2, Color.parseColor("#B8860B"));

        float textY = centerY + textPaint.getTextSize() / 3;
        canvas.drawText(text, centerX, textY, textPaint);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }

    // ==================== 飞机特效 ====================

    private void initPlaneEffect() {
        planeX = -200f;
        planeY = centerY;
        smokeTrails.clear();
        planeTextAlpha = 0f;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();

            float screenWidth = getWidth();
            planeX = -200 + (screenWidth + 400) * animationProgress;
            planeY = centerY + (float) Math.sin(animationProgress * Math.PI * 4) * 50;

            // 添加烟雾轨迹
            if (random.nextFloat() < 0.3f) {
                smokeTrails.add(new SmokeTrail(planeX - 60, planeY, 10 + random.nextFloat() * 10));
            }

            // 更新烟雾
            for (int i = smokeTrails.size() - 1; i >= 0; i--) {
                SmokeTrail smoke = smokeTrails.get(i);
                smoke.alpha -= 3;
                smoke.size += 0.5f;
                smoke.x -= 2;
                if (smoke.alpha <= 0) {
                    smokeTrails.remove(i);
                }
            }

            // 文字淡入淡出
            if (animationProgress < 0.2f) {
                planeTextAlpha = animationProgress / 0.2f;
            } else if (animationProgress > 0.8f) {
                planeTextAlpha = (1f - animationProgress) / 0.2f;
            } else {
                planeTextAlpha = 1f;
            }

            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animatorIsCurrent()) {
                    clearEffect();
                }
            }
        });
        startEffectAnimator(animator);
    }

    private void drawPlane(Canvas canvas) {
        // 绘制烟雾轨迹
        paint.setStyle(Paint.Style.FILL);
        for (SmokeTrail smoke : smokeTrails) {
            paint.setColor(Color.argb(smoke.alpha, 220, 220, 220));
            canvas.drawCircle(smoke.x, smoke.y, smoke.size, paint);
        }

        // 绘制简笔画飞机
        drawSimplePlane(canvas, planeX, planeY);

        // 绘制文字
        textPaint.setColor(Color.parseColor("#FFD700"));
        textPaint.setTextSize(48 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setAlpha((int) (255 * planeTextAlpha));
        textPaint.setShadowLayer(4, 2, 2, Color.parseColor("#B8860B"));

        float textX = planeX;
        float textY = planeY - 60;
        canvas.drawText(getContext().getString(R.string.game_doudizhu_effect_plane), textX, textY, textPaint);
        textPaint.setAlpha(255);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }

    private void drawSimplePlane(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.FILL);

        // 机身（白色椭圆）
        paint.setColor(Color.WHITE);
        RectF body = new RectF(x - 60, y - 15, x + 60, y + 15);
        canvas.drawOval(body, paint);

        // 机头
        Path nose = new Path();
        nose.moveTo(x + 60, y);
        nose.lineTo(x + 90, y - 5);
        nose.lineTo(x + 90, y + 5);
        nose.close();
        canvas.drawPath(nose, paint);

        // 机翼（蓝色）
        paint.setColor(Color.parseColor("#2196F3"));
        Path wing = new Path();
        wing.moveTo(x - 10, y - 15);
        wing.lineTo(x + 20, y - 50);
        wing.lineTo(x + 40, y - 50);
        wing.lineTo(x + 30, y - 15);
        wing.close();
        canvas.drawPath(wing, paint);

        // 尾翼
        Path tail = new Path();
        tail.moveTo(x - 50, y - 10);
        tail.lineTo(x - 70, y - 30);
        tail.lineTo(x - 60, y - 30);
        tail.lineTo(x - 45, y - 10);
        tail.close();
        canvas.drawPath(tail, paint);

        // 窗户
        paint.setColor(Color.parseColor("#90CAF9"));
        canvas.drawCircle(x + 30, y - 5, 5, paint);
        canvas.drawCircle(x + 45, y - 5, 5, paint);
    }

    // ==================== 春天特效 ====================

    private void initSpringEffect() {
        petals.clear();
        // 创建10-15个花瓣
        int petalCount = 10 + random.nextInt(6);
        for (int i = 0; i < petalCount; i++) {
            petals.add(new Petal(
                random.nextFloat() * getWidth(),
                -50 - random.nextFloat() * 200,
                15 + random.nextFloat() * 15,
                1 + random.nextFloat() * 2,
                random.nextFloat() * 360,
                1 + random.nextFloat() * 2,
                random.nextFloat() * 2 - 1
            ));
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            float screenHeight = getHeight();

            // 更新花瓣位置
            for (Petal petal : petals) {
                petal.y += petal.speed;
                petal.x += (float) Math.sin(animationProgress * Math.PI * 4 + petal.offset) * 2;
                petal.rotation += petal.rotationSpeed;

                if (petal.y > screenHeight + 50) {
                    petal.y = -50;
                    petal.x = random.nextFloat() * getWidth();
                }
            }

            // 背景淡入淡出
            if (animationProgress < 0.2f) {
                springBgAlpha = animationProgress / 0.2f * 30;
            } else if (animationProgress > 0.8f) {
                springBgAlpha = (1f - animationProgress) / 0.2f * 30;
            } else {
                springBgAlpha = 30;
            }

            // 文字缩放
            if (animationProgress < 0.3f) {
                springTextScale = animationProgress / 0.3f;
            } else if (animationProgress < 0.5f) {
                springTextScale = 1f + (0.5f - animationProgress) / 0.2f * 0.2f;
            } else {
                springTextScale = 1f;
            }

            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animatorIsCurrent()) {
                    clearEffect();
                }
            }
        });
        startEffectAnimator(animator);
    }

    private void drawSpring(Canvas canvas) {
        // 绘制淡粉色背景
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) springBgAlpha, 255, 182, 193));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // 绘制花瓣
        for (Petal petal : petals) {
            drawPetal(canvas, petal);
        }

        // 绘制文字
        textPaint.setColor(Color.parseColor("#FF69B4"));
        textPaint.setTextSize(48 * getResources().getDisplayMetrics().scaledDensity * springTextScale);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(4, 2, 2, Color.parseColor("#C71585"));

        float textY = centerY + textPaint.getTextSize() / 3;
        canvas.drawText(getContext().getString(R.string.game_doudizhu_effect_spring), centerX, textY, textPaint);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }

    private void drawPetal(Canvas canvas, Petal petal) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200, 255, 182, 193));

        canvas.save();
        canvas.translate(petal.x, petal.y);
        canvas.rotate(petal.rotation);

        Path path = new Path();
        path.moveTo(0, -petal.size);
        path.quadTo(petal.size, 0, 0, petal.size);
        path.quadTo(-petal.size, 0, 0, -petal.size);
        canvas.drawPath(path, paint);

        canvas.restore();
    }

    // ==================== 顺子特效 ====================

    private void initShunziEffect() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            chainProgress = animationProgress;

            if (animationProgress < 0.3f) {
                shunziTextAlpha = animationProgress / 0.3f;
            } else {
                shunziTextAlpha = 1f;
            }

            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 只有自然结束（未被 cancel/取代）才排延迟清除；
                // clearEffect/detach 路径 cancel 时代际已失效，直接跳过
                if (animatorIsCurrent()) {
                    scheduleClearAfterEnd(800);
                }
            }
        });
        startEffectAnimator(animator);
    }

    private void drawShunzi(Canvas canvas) {
        // 绘制5个金色圆环链条
        float ringRadius = 25f;
        float spacing = 70f;
        float totalWidth = 4 * spacing;
        float startX = centerX - totalWidth / 2;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);

        for (int i = 0; i < 5; i++) {
            float ringX = startX + i * spacing;
            float appearProgress = Math.min(1f, chainProgress * 5 - i);
            if (appearProgress <= 0) continue;

            // 金色渐变圆环
            float alpha = appearProgress;
            paint.setColor(Color.argb((int) (255 * alpha), 255, 215, 0));
            paint.setShadowLayer(8, 0, 0, Color.argb((int) (150 * alpha), 255, 215, 0));
            canvas.drawCircle(ringX, centerY, ringRadius * appearProgress, paint);

            // 连接链条
            if (i < 4 && appearProgress >= 1f) {
                float nextProgress = Math.min(1f, chainProgress * 5 - (i + 1));
                if (nextProgress > 0) {
                    paint.setStrokeWidth(4);
                    paint.setColor(Color.parseColor("#FFD700"));
                    canvas.drawLine(ringX + ringRadius, centerY, ringX + spacing - ringRadius, centerY, paint);
                }
            }
        }
        paint.setShadowLayer(0, 0, 0, 0);

        // 绘制文字
        textPaint.setColor(Color.parseColor("#FFD700"));
        textPaint.setTextSize(48 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setAlpha((int) (255 * shunziTextAlpha));
        textPaint.setShadowLayer(4, 2, 2, Color.parseColor("#B8860B"));

        float textY = centerY - ringRadius - 30;
        canvas.drawText(getContext().getString(R.string.game_doudizhu_effect_straight), centerX, textY, textPaint);
        textPaint.setAlpha(255);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }

    // ==================== 连对特效 ====================

    private void initDoubleLineEffect() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            doubleLineProgress = animationProgress;

            if (animationProgress < 0.3f) {
                doubleLineTextAlpha = animationProgress / 0.3f;
            } else {
                doubleLineTextAlpha = 1f;
            }

            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 只有自然结束（未被 cancel/取代）才排延迟清除；
                // clearEffect/detach 路径 cancel 时代际已失效，直接跳过
                if (animatorIsCurrent()) {
                    scheduleClearAfterEnd(800);
                }
            }
        });
        startEffectAnimator(animator);
    }

    private void drawDoubleLine(Canvas canvas) {
        // 两排金色圆点
        int dotCount = 6;
        float dotRadius = 12f;
        float spacing = 50f;
        float totalWidth = (dotCount - 1) * spacing;
        float startX = centerX - totalWidth / 2;
        float lineSpacing = 40f;

        paint.setStyle(Paint.Style.FILL);

        for (int row = 0; row < 2; row++) {
            float rowY = centerY - lineSpacing / 2 + row * lineSpacing;
            for (int i = 0; i < dotCount; i++) {
                float dotX = startX + i * spacing;
                float appearProgress = Math.min(1f, doubleLineProgress * dotCount - i);
                if (appearProgress <= 0) continue;

                float alpha = appearProgress;
                paint.setColor(Color.argb((int) (255 * alpha), 255, 215, 0));
                paint.setShadowLayer(6, 0, 0, Color.argb((int) (120 * alpha), 255, 215, 0));
                canvas.drawCircle(dotX, rowY, dotRadius * appearProgress, paint);
            }
        }
        paint.setShadowLayer(0, 0, 0, 0);

        // 绘制文字
        textPaint.setColor(Color.parseColor("#FFD700"));
        textPaint.setTextSize(48 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setAlpha((int) (255 * doubleLineTextAlpha));
        textPaint.setShadowLayer(4, 2, 2, Color.parseColor("#B8860B"));

        float textY = centerY - lineSpacing - 30;
        canvas.drawText(getContext().getString(R.string.game_doudizhu_effect_consecutive_pairs), centerX, textY, textPaint);
        textPaint.setAlpha(255);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }

    // ==================== 绘制入口 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentEffect == null) return;

        switch (currentEffect) {
            case BOMB:
            case ROCKET:
                drawBomb(canvas);
                break;
            case PLANE:
                drawPlane(canvas);
                break;
            case SPRING:
                drawSpring(canvas);
                break;
            case SHUNZI:
                drawShunzi(canvas);
                break;
            case DOUBLE_LINE:
                drawDoubleLine(canvas);
                break;
        }
    }

    // ==================== 数据类 ====================

    /**
     * 火花粒子
     */
    private static class Spark {
        float x, y;
        float vx, vy;
        float size;
        float initialSize;
        int alpha = 255;

        Spark(float x, float y, float vx, float vy, float size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.initialSize = size;
        }
    }

    /**
     * 烟雾轨迹
     */
    private static class SmokeTrail {
        float x, y;
        float size;
        int alpha = 200;

        SmokeTrail(float x, float y, float size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    /**
     * 花瓣
     */
    private static class Petal {
        float x, y;
        float size;
        float speed;
        float rotation;
        float rotationSpeed;
        float offset;

        Petal(float x, float y, float size, float speed, float rotation, float rotationSpeed, float offset) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
            this.rotation = rotation;
            this.rotationSpeed = rotationSpeed;
            this.offset = offset;
        }
    }
}
