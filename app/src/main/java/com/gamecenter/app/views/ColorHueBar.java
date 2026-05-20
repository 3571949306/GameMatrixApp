package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 颜色色相（Hue）选择条控件。
 *
 * <p>以水平条的形式展示完整的色相光谱（0°~360°），
 * 用户可以通过触摸拖动来选择色相值。</p>
 *
 * <p><b>视觉设计：</b></p>
 * <ul>
 *   <li>水平线性渐变展示色相光谱：红→黄→绿→青→蓝→洋红→红</li>
 *   <li>白色竖线光标标识当前选中位置，外加黑色描边增强对比度</li>
 *   <li>光标颜色根据当前位置的亮度自动切换黑/白，保证在任何色相上都清晰可见</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>使用7个采样点（每60°一个）生成渐变，覆盖0°~360°色相范围</li>
 *   <li>光标颜色基于 ITU-R BT.601 亮度公式自动计算：
 *       {@code brightness = R*0.299 + G*0.587 + B*0.114}，
 *       亮度 > 128 时用黑色光标，否则用白色光标</li>
 * </ul>
 */
public class ColorHueBar extends View {

    /** 渐变绘制画笔 */
    private Paint paint;
    /** 光标填充画笔（自适应黑/白色描边） */
    private Paint cursorPaint;
    /** 光标外框画笔（黑色细线） */
    private Paint cursorStroke;
    /** 当前色相值，范围 0.0°~360.0° */
    private float hue = 0f;
    /** 色相变化监听器 */
    private OnHueChangedListener listener;

    /**
     * 色相变化监听接口。当用户拖动选择新的色相值时回调。
     */
    public interface OnHueChangedListener {
        /**
         * 色相值发生变化时调用。
         * @param hue 新的色相值，范围 0.0~360.0
         */
        void onHueChanged(float hue);
    }

    /**
     * 单参数构造方法，供代码动态创建时使用。
     * @param context 上下文
     */
    public ColorHueBar(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法，供XML布局文件inflate时使用。
     * @param context 上下文
     * @param attrs XML属性集
     */
    public ColorHueBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 三参数构造方法，支持默认样式属性。
     * @param context 上下文
     * @param attrs XML属性集
     * @param defStyleAttr 默认样式属性
     */
    public ColorHueBar(Context context, AttributeSet attrs, int defStyleAttr) {
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
     * 设置当前色相值并刷新视图。
     * @param h 色相值，范围 0.0~360.0
     */
    public void setHue(float h) {
        hue = h;
        invalidate();
    }

    /**
     * 获取当前色相值。
     * @return 色相值，范围 0.0~360.0
     */
    public float getHue() { return hue; }

    /**
     * 设置色相变化监听器。
     * @param l 监听器实例
     */
    public void setOnHueChangedListener(OnHueChangedListener l) {
        listener = l;
    }

    /**
     * 绘制色相选择条。
     *
     * <p>绘制步骤：</p>
     * <ol>
     *   <li>绘制色相光谱渐变条（7个采样点，每60°一个）</li>
     *   <li>绘制光标指示当前选中位置</li>
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

        // 生成7个色相采样点：0°, 60°, 120°, 180°, 240°, 300°, 360°
        // HSV中S=1, V=1，确保颜色为纯色相
        int[] colors = new int[7];
        float[] hsv = new float[]{0f, 1f, 1f};
        for (int i = 0; i < 7; i++) {
            hsv[0] = i * 60f;
            colors[i] = Color.HSVToColor(hsv);
        }
        // 水平线性渐变，7个颜色均匀分布
        LinearGradient gradient = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);

        // 根据当前hue值计算光标水平位置
        float cx = (hue / 360f) * w;
        int cursorH = h + 8;
        int cursorW = 6;

        // 根据当前位置的色相计算亮度，自动选择光标颜色（黑或白）
        int currentColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        // ITU-R BT.601 亮度公式：Y = 0.299R + 0.587G + 0.114B
        int brightness = (Color.red(currentColor) * 299 + Color.green(currentColor) * 587 + Color.blue(currentColor) * 114) / 1000;
        // 亮度 > 128 时用黑色光标（浅色背景），否则用白色光标（深色背景）
        cursorPaint.setColor(brightness > 128 ? Color.BLACK : Color.WHITE);

        // 绘制光标：自适应颜色粗描边 + 黑色细描边，上下各超出4px
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, cursorH, cursorPaint);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, cursorH, cursorStroke);
    }

    /**
     * 处理触摸事件，实现色相的拖动选择。
     *
     * <p>支持 ACTION_DOWN、ACTION_MOVE、ACTION_UP 三种事件，
     * 允许用户按下后持续拖动来调整色相值。</p>
     *
     * <p>触摸X坐标被限制在 [0, getWidth()] 范围内，
     * 然后映射为色相值 [0°, 360°]。</p>
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
            // X坐标映射为色相值：左=0°，右=360°
            hue = (x / getWidth()) * 360f;
            invalidate();
            if (listener != null) {
                listener.onHueChanged(hue);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
