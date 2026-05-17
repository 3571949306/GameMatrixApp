package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 颜色透明度（Alpha）选择条控件。
 *
 * <p>以水平条的形式展示指定颜色从完全透明到完全不透明的渐变效果，
 * 用户可以通过触摸拖动来选择透明度值。</p>
 *
 * <p><b>视觉设计：</b></p>
 * <ul>
 *   <li>底层绘制棋盘格图案（灰白交替），用于直观表示透明区域</li>
 *   <li>上层叠加从透明到不透明的水平线性渐变</li>
 *   <li>白色竖线光标标识当前选中位置，外加黑色描边增强对比度</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>棋盘格使用 Bitmap 缓存，避免每帧重复绘制，提升性能</li>
 *   <li>光标超出视图边界4px（上/下），增强视觉辨识度</li>
 * </ul>
 */
public class ColorAlphaBar extends View {

    /** 渐变绘制画笔 */
    private Paint paint;
    /** 棋盘格绘制画笔 */
    private Paint checkerPaint;
    /** 光标填充画笔（白色描边） */
    private Paint cursorPaint;
    /** 光标外框画笔（黑色细线） */
    private Paint cursorStroke;
    /** 当前选中的基础颜色（不含透明度），默认红色 */
    private int color = Color.RED;
    /** 当前透明度值，范围 0.0（完全透明）~ 1.0（完全不透明） */
    private float alpha = 1f;
    /** 透明度变化监听器 */
    private OnAlphaChangedListener listener;
    /** 缓存的棋盘格位图，尺寸与视图一致时复用 */
    private Bitmap checkerBitmap;

    /**
     * 透明度变化监听接口。当用户拖动选择新的透明度值时回调。
     */
    public interface OnAlphaChangedListener {
        /**
         * 透明度值发生变化时调用。
         * @param alpha 新的透明度值，范围 0.0~1.0
         */
        void onAlphaChanged(float alpha);
    }

    /**
     * 单参数构造方法，供代码动态创建时使用。
     * @param context 上下文
     */
    public ColorAlphaBar(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法，供XML布局文件inflate时使用。
     * @param context 上下文
     * @param attrs XML属性集
     */
    public ColorAlphaBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 三参数构造方法，支持默认样式属性。
     * @param context 上下文
     * @param attrs XML属性集
     * @param defStyleAttr 默认样式属性
     */
    public ColorAlphaBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔。配置光标画笔为描边模式，用于绘制选择指示器。
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(3f);
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeWidth(1.5f);
        cursorStroke.setColor(Color.BLACK);
    }

    /**
     * 设置基础颜色（不含透明度）。渐变条将从此颜色的透明版本渐变到不透明版本。
     * @param c ARGB颜色值（alpha分量会被忽略，仅使用RGB通道）
     */
    public void setColor(int c) {
        color = c;
        invalidate();
    }

    /**
     * 设置当前透明度值并刷新视图。
     * @param a 透明度值，范围 0.0（完全透明）~ 1.0（完全不透明）
     */
    public void setAlpha(float a) {
        alpha = a;
        invalidate();
    }

    /**
     * 获取当前透明度值。
     * @return 透明度值，范围 0.0~1.0
     */
    public float getAlpha() { return alpha; }

    /**
     * 设置透明度变化监听器。
     * @param l 监听器实例
     */
    public void setOnAlphaChangedListener(OnAlphaChangedListener l) {
        listener = l;
    }

    /**
     * 创建棋盘格位图。灰白交替的方格图案用于直观表示透明区域，
     * 这是图形编辑器中表示透明度的通用做法。
     *
     * @param w 位图宽度
     * @param h 位图高度
     */
    private void createCheckerBitmap(int w, int h) {
        checkerBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(checkerBitmap);
        Paint light = new Paint();
        light.setColor(Color.LTGRAY);
        Paint dark = new Paint();
        dark.setColor(Color.GRAY);
        // 每个棋盘格方块的边长为8像素
        int size = 8;
        for (int y = 0; y < h; y += size) {
            for (int x = 0; x < w; x += size) {
                // 通过行列索引之和的奇偶性决定浅色/深色
                boolean isLight = ((x / size) + (y / size)) % 2 == 0;
                c.drawRect(x, y, x + size, y + size, isLight ? light : dark);
            }
        }
    }

    /**
     * 绘制透明度选择条。
     *
     * <p>绘制步骤：</p>
     * <ol>
     *   <li>绘制棋盘格背景（表示透明区域）</li>
     *   <li>叠加从透明到不透明的水平渐变</li>
     *   <li>绘制白色光标指示当前选中位置</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 棋盘格位图仅在尺寸变化时重新创建，避免每帧分配内存
        if (checkerBitmap == null || checkerBitmap.getWidth() != w || checkerBitmap.getHeight() != h) {
            createCheckerBitmap(w, h);
        }
        canvas.drawBitmap(checkerBitmap, 0, 0, checkerPaint);

        // 提取当前颜色的RGB通道，分别构造完全透明和完全不透明的版本
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int transparent = Color.argb(0, r, g, b);
        int opaque = Color.argb(255, r, g, b);

        // 从左（透明）到右（不透明）的水平线性渐变
        LinearGradient gradient = new LinearGradient(0, 0, w, 0,
                transparent, opaque, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);

        // 根据当前alpha值计算光标水平位置
        float cx = alpha * w;
        int cursorW = 6;

        // 绘制光标：白色粗描边 + 黑色细描边，上下各超出4px
        cursorPaint.setColor(Color.WHITE);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, h + 4, cursorPaint);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, h + 4, cursorStroke);
    }

    /**
     * 处理触摸事件，实现透明度的拖动选择。
     *
     * <p>支持 ACTION_DOWN、ACTION_MOVE、ACTION_UP 三种事件，
     * 允许用户按下后持续拖动来调整透明度值。</p>
     *
     * <p>触摸X坐标被限制在 [0, getWidth()] 范围内，
     * 然后映射为 alpha 值 [0.0, 1.0]。</p>
     *
     * @param event 触摸事件
     * @return 处理了触摸事件时返回true，否则委托给父类
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            // 将触摸X坐标限制在视图宽度范围内，防止越界
            float x = Math.max(0, Math.min(event.getX(), getWidth()));
            // X坐标映射为alpha值：左=0（透明），右=1（不透明）
            alpha = x / getWidth();
            invalidate();
            if (listener != null) {
                listener.onAlphaChanged(alpha);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
