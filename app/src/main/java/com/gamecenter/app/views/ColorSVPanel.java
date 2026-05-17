package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 颜色饱和度-明度（Saturation-Value）二维选择面板控件。
 *
 * <p>以二维面板的形式展示指定色相下的饱和度和明度组合，
 * 水平方向表示饱和度（左=0/白色，右=1/纯色），
 * 垂直方向表示明度（上=1/亮，下=0/暗），
 * 用户可以通过触摸拖动来同时选择饱和度和明度。</p>
 *
 * <p><b>视觉设计：</b></p>
 * <ul>
 *   <li>底层：从白色（左）到纯色相（右）的水平饱和度渐变</li>
 *   <li>上层：从透明（上）到黑色（下）的垂直明度渐变</li>
 *   <li>两层叠加形成完整的SV色彩空间</li>
 *   <li>圆形光标标识当前选中位置，颜色根据亮度自动切换黑/白</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>使用两次独立渐变叠加（而非一次性生成位图），利用GPU加速渲染</li>
 *   <li>光标颜色基于 ITU-R BT.601 亮度公式自动计算，保证在任何颜色上都清晰可见</li>
 *   <li>光标半径自适应面板尺寸（取宽高较小值的6%），最小不低于8px</li>
 * </ul>
 */
public class ColorSVPanel extends View {

    /** 渐变绘制画笔 */
    private Paint paint;
    /** 光标填充画笔（自适应黑/白色描边） */
    private Paint cursorPaint;
    /** 光标外框画笔（黑色细线） */
    private Paint cursorStroke;
    /** 当前色相值，范围 0.0°~360.0° */
    private float hue = 0f;
    /** 当前饱和度值，范围 0.0（灰色/白色）~ 1.0（纯色） */
    private float sat = 1f;
    /** 当前明度值，范围 0.0（黑色）~ 1.0（最亮） */
    private float val = 1f;
    /** 颜色变化监听器 */
    private OnColorChangedListener listener;

    /**
     * 颜色变化监听接口。当用户拖动选择新的饱和度/明度值时回调。
     */
    public interface OnColorChangedListener {
        /**
         * 颜色值发生变化时调用。
         * @param hue 色相值，范围 0.0~360.0
         * @param sat 饱和度值，范围 0.0~1.0
         * @param val 明度值，范围 0.0~1.0
         */
        void onColorChanged(float hue, float sat, float val);
    }

    /**
     * 单参数构造方法，供代码动态创建时使用。
     * @param context 上下文
     */
    public ColorSVPanel(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法，供XML布局文件inflate时使用。
     * @param context 上下文
     * @param attrs XML属性集
     */
    public ColorSVPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 三参数构造方法，支持默认样式属性。
     * @param context 上下文
     * @param attrs XML属性集
     * @param defStyleAttr 默认样式属性
     */
    public ColorSVPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔。配置光标画笔为描边模式，用于绘制选择指示器。
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(3f);
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeWidth(1.5f);
        cursorStroke.setColor(Color.BLACK);
    }

    /**
     * 设置当前色相值并刷新视图。色相变化会改变面板的纯色端颜色。
     * @param h 色相值，范围 0.0~360.0
     */
    public void setHue(float h) {
        hue = h;
        invalidate();
    }

    /**
     * 同时设置饱和度和明度值并刷新视图。
     * @param s 饱和度值，范围 0.0~1.0
     * @param v 明度值，范围 0.0~1.0
     */
    public void setSV(float s, float v) {
        sat = s;
        val = v;
        invalidate();
    }

    /**
     * 设置颜色变化监听器。
     * @param l 监听器实例
     */
    public void setOnColorChangedListener(OnColorChangedListener l) {
        listener = l;
    }

    /** 获取当前色相值 */
    public float getHue() { return hue; }
    /** 获取当前饱和度值 */
    public float getSat() { return sat; }
    /** 获取当前明度值 */
    public float getVal() { return val; }

    /**
     * 绘制饱和度-明度选择面板。
     *
     * <p>绘制步骤：</p>
     * <ol>
     *   <li>绘制水平饱和度渐变（白色→纯色相）</li>
     *   <li>叠加垂直明度渐变（透明→黑色）</li>
     *   <li>绘制圆形光标指示当前选中位置</li>
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

        // 根据当前色相计算纯色（S=1, V=1）
        float[] hsv = new float[]{hue, 1f, 1f};
        int pureColor = Color.HSVToColor(hsv);

        // 第一层：水平饱和度渐变 — 从白色（左，S=0）到纯色（右，S=1）
        LinearGradient satGradient = new LinearGradient(0, 0, w, 0,
                Color.WHITE, pureColor, Shader.TileMode.CLAMP);
        paint.setShader(satGradient);
        canvas.drawRect(0, 0, w, h, paint);

        // 第二层：垂直明度渐变 — 从透明（上，V=1）到黑色（下，V=0）
        // 使用透明而非白色，确保与底层饱和度渐变正确叠加
        LinearGradient valGradient = new LinearGradient(0, 0, 0, h,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        paint.setShader(valGradient);
        canvas.drawRect(0, 0, w, h, paint);

        // 根据当前sat和val计算光标位置
        float cx = sat * w;
        // val=1时在顶部(y=0)，val=0时在底部(y=h)，所以需要反转
        float cy = (1f - val) * h;
        // 光标半径自适应面板尺寸，最小8px保证可见性
        int radius = (int) (Math.min(w, h) * 0.06f);
        radius = Math.max(radius, 8);

        // 根据当前选中颜色计算亮度，自动选择光标颜色（黑或白）
        int currentColor = Color.HSVToColor(new float[]{hue, sat, val});
        // ITU-R BT.601 亮度公式：Y = 0.299R + 0.587G + 0.114B
        int brightness = (Color.red(currentColor) * 299 + Color.green(currentColor) * 587 + Color.blue(currentColor) * 114) / 1000;
        // 亮度 > 128 时用黑色光标（浅色背景），否则用白色光标（深色背景）
        cursorPaint.setColor(brightness > 128 ? Color.BLACK : Color.WHITE);

        // 绘制圆形光标：自适应颜色粗描边 + 黑色细描边
        canvas.drawCircle(cx, cy, radius, cursorPaint);
        canvas.drawCircle(cx, cy, radius, cursorStroke);
    }

    /**
     * 处理触摸事件，实现饱和度和明度的二维拖动选择。
     *
     * <p>支持 ACTION_DOWN、ACTION_MOVE、ACTION_UP 三种事件，
     * 允许用户按下后持续拖动来调整饱和度和明度值。</p>
     *
     * <p>坐标映射规则：</p>
     * <ul>
     *   <li>X坐标 → 饱和度：左=0.0（白色），右=1.0（纯色）</li>
     *   <li>Y坐标 → 明度：上=1.0（最亮），下=0.0（黑色），Y轴需反转</li>
     * </ul>
     *
     * <p>无论 ACTION_UP 还是其他事件，都会通知监听器，
     * 确保拖动过程中和松手时都能获取最新颜色值。</p>
     *
     * @param event 触摸事件
     * @return 处理了触摸事件时返回true，否则委托给父类
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            // 将触摸坐标限制在视图范围内，防止越界
            float x = Math.max(0, Math.min(event.getX(), getWidth()));
            float y = Math.max(0, Math.min(event.getY(), getHeight()));
            // X映射为饱和度，Y映射为明度（Y轴反转：上=亮，下=暗）
            sat = x / getWidth();
            val = 1f - y / getHeight();
            invalidate();
            // 拖动过程中通知监听器
            if (listener != null && event.getAction() != MotionEvent.ACTION_UP) {
                listener.onColorChanged(hue, sat, val);
            }
            // 松手时也通知监听器，确保最终值被传递
            if (listener != null && event.getAction() == MotionEvent.ACTION_UP) {
                listener.onColorChanged(hue, sat, val);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
