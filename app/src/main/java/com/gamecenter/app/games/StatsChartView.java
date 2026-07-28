package com.gamecenter.app.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * P2-8 (STATS_VISUALIZATION): 通用统计图表 View，支持三种类型：
 * <ul>
 *   <li>{@link #TYPE_LINE}: 折线趋势图（带数据点 + 网格 + 数值标签）</li>
 *   <li>{@link #TYPE_PIE}: 饼图（多段 + 百分比标签 + 图例）</li>
 *   <li>{@link #TYPE_BAR}: 柱状图（横向多柱 + 数值标签）</li>
 * </ul>
 *
 * <p>使用方式：通过 {@link #setChartType(int)} 切换类型，然后调用
 * {@link #setLineData(List, List)} / {@link #setPieData(List)} / {@link #setBarData(List, List)}
 * 传入数据并 {@link #invalidate()}。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>主题感知：通过 ?attr/colorPrimary / ?attr/colorOnSurface 取主题色</li>
 *   <li>无外部依赖：纯 Canvas 绘制，避免引入 MPAndroidChart 等库</li>
 *   <li>自适应：onMeasure 根据类型返回合理默认高度</li>
 * </ul>
 * </p>
 */
public class StatsChartView extends View {

    public static final int TYPE_LINE = 0;
    public static final int TYPE_PIE = 1;
    public static final int TYPE_BAR = 2;

    private static final int[] PIE_COLORS = {
            0xFF6750A4, 0xFF7D5260, 0xFFEFB8C8, 0xFF617585,
            0xFF7986CB, 0xFF33B679, 0xFFB39DDB, 0xFFFF8A65
    };

    private int chartType = TYPE_LINE;

    // Line data
    private List<String> lineLabels = new ArrayList<>();
    private List<Float> lineValues = new ArrayList<>();

    // Pie data
    private List<PieEntry> pieEntries = new ArrayList<>();

    // Bar data
    private List<String> barLabels = new ArrayList<>();
    private List<Float> barValues = new ArrayList<>();

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pieRect = new RectF();

    private int colorPrimary;
    private int colorOnSurface;
    private int colorOnSurfaceVariant;
    private int colorSurfaceVariant;

    public StatsChartView(Context context) {
        super(context);
        init();
    }

    public StatsChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StatsChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        colorPrimary = resolveThemeColor(android.R.attr.colorPrimary, 0xFF6750A4);
        colorOnSurface = resolveThemeColor(android.R.attr.textColorPrimary, 0xFF1B1B1F);
        colorOnSurfaceVariant = resolveThemeColor(android.R.attr.textColorSecondary, 0xFF49454F);
        colorSurfaceVariant = resolveThemeColor(android.R.attr.colorBackground, 0xFFE7E0EC);

        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(1.5f);
        axisPaint.setColor(colorOnSurfaceVariant);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.8f);
        gridPaint.setColor(colorSurfaceVariant);
        gridPaint.setAlpha(120);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(colorPrimary);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(colorPrimary);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(colorPrimary);
        fillPaint.setAlpha(40);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(colorOnSurfaceVariant);
        textPaint.setTextSize(sp(11));

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(colorOnSurface);
        valuePaint.setTextSize(sp(11));
        valuePaint.setFakeBoldText(true);
    }

    private int resolveThemeColor(int attr, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return fallback;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public void setChartType(int type) {
        this.chartType = type;
        invalidate();
    }

    public void setLineData(@NonNull List<String> labels, @NonNull List<Float> values) {
        this.lineLabels = new ArrayList<>(labels);
        this.lineValues = new ArrayList<>(values);
        invalidate();
    }

    public void setPieData(@NonNull List<PieEntry> entries) {
        this.pieEntries = new ArrayList<>(entries);
        invalidate();
    }

    public void setBarData(@NonNull List<String> labels, @NonNull List<Float> values) {
        this.barLabels = new ArrayList<>(labels);
        this.barValues = new ArrayList<>(values);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultHeightDp;
        switch (chartType) {
            case TYPE_PIE:
                defaultHeightDp = 220;
                break;
            case TYPE_BAR:
                defaultHeightDp = 180;
                break;
            case TYPE_LINE:
            default:
                defaultHeightDp = 200;
                break;
        }
        int desiredHeight = (int) (defaultHeightDp * getResources().getDisplayMetrics().density);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getPaddingLeft() + getPaddingRight() >= getWidth()
                || getPaddingTop() + getPaddingBottom() >= getHeight()) {
            return;
        }
        switch (chartType) {
            case TYPE_LINE:
                drawLine(canvas);
                break;
            case TYPE_PIE:
                drawPie(canvas);
                break;
            case TYPE_BAR:
                drawBar(canvas);
                break;
        }
    }

    // ==================== Line Chart ====================

    private void drawLine(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        int left = getPaddingLeft();
        int top = getPaddingTop();
        float labelHeight = sp(12) + dp(4);
        float valueHeight = sp(12) + dp(4);
        float plotTop = top + valueHeight;
        float plotBottom = top + h - labelHeight;
        float plotH = plotBottom - plotTop;
        float plotLeft = left + dp(8);
        float plotRight = left + w - dp(8);
        float plotW = plotRight - plotLeft;

        float maxVal = 0f;
        for (Float v : lineValues) {
            if (v != null && v > maxVal) maxVal = v;
        }
        if (maxVal <= 0f) maxVal = 1f;

        // 3 条网格线
        for (int i = 0; i <= 3; i++) {
            float y = plotBottom - plotH * (i / 3f);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
        }

        if (lineValues.isEmpty()) return;

        int n = lineValues.size();
        float stepX = n > 1 ? plotW / (n - 1) : 0f;

        // 填充区域
        android.graphics.Path fillPath = new android.graphics.Path();
        fillPath.moveTo(plotLeft, plotBottom);
        for (int i = 0; i < n; i++) {
            float x = plotLeft + stepX * i;
            float y = plotBottom - plotH * (lineValues.get(i) / maxVal);
            fillPath.lineTo(x, y);
        }
        fillPath.lineTo(plotLeft + stepX * (n - 1), plotBottom);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // 折线
        android.graphics.Path linePath = new android.graphics.Path();
        for (int i = 0; i < n; i++) {
            float x = plotLeft + stepX * i;
            float y = plotBottom - plotH * (lineValues.get(i) / maxVal);
            if (i == 0) linePath.moveTo(x, y);
            else linePath.lineTo(x, y);
        }
        canvas.drawPath(linePath, linePaint);

        // 数据点 + 标签
        for (int i = 0; i < n; i++) {
            float x = plotLeft + stepX * i;
            float y = plotBottom - plotH * (lineValues.get(i) / maxVal);
            canvas.drawCircle(x, y, dp(3), pointPaint);

            String label = i < lineLabels.size() ? lineLabels.get(i) : "";
            canvas.drawText(label, x, plotBottom + labelHeight - dp(2), textPaint);

            String value = formatValue(lineValues.get(i));
            canvas.drawText(value, x, y - dp(6), valuePaint);
        }
    }

    // ==================== Pie Chart ====================

    private void drawPie(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        int left = getPaddingLeft();
        int top = getPaddingTop();

        float total = 0f;
        for (PieEntry e : pieEntries) {
            if (e.value > 0) total += e.value;
        }
        if (total <= 0f) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", left + w / 2f, top + h / 2f, textPaint);
            return;
        }

        // 饼图区域（左侧正方形），图例区域（右侧）
        float pieSize = Math.min(w * 0.55f, h - dp(8));
        float pieLeft = left + dp(8);
        float pieTop = top + (h - pieSize) / 2f;
        pieRect.set(pieLeft, pieTop, pieLeft + pieSize, pieTop + pieSize);
        float cx = pieRect.centerX();
        float cy = pieRect.centerY();
        float radius = pieSize / 2f;

        float startAngle = -90f;
        for (int i = 0; i < pieEntries.size(); i++) {
            PieEntry e = pieEntries.get(i);
            if (e.value <= 0) continue;
            float sweep = 360f * (e.value / total);
            fillPaint.setColor(PIE_COLORS[i % PIE_COLORS.length]);
            fillPaint.setAlpha(255);
            canvas.drawArc(pieRect, startAngle, sweep, true, fillPaint);
            startAngle += sweep;
        }

        // 中心镂空（甜甜圈样式）
        fillPaint.setColor(resolveThemeColor(android.R.attr.colorBackground, Color.WHITE));
        fillPaint.setAlpha(255);
        canvas.drawCircle(cx, cy, radius * 0.55f, fillPaint);

        // 中心总数字
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(16));
        canvas.drawText(formatValue(total), cx, cy + sp(6), valuePaint);
        valuePaint.setTextSize(sp(11));

        // 图例
        float legendLeft = pieLeft + pieSize + dp(12);
        float legendTop = pieTop + dp(8);
        float lineH = sp(14) + dp(4);
        textPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < pieEntries.size(); i++) {
            PieEntry e = pieEntries.get(i);
            if (e.value <= 0) continue;
            float y = legendTop + lineH * i;
            fillPaint.setColor(PIE_COLORS[i % PIE_COLORS.length]);
            fillPaint.setAlpha(255);
            canvas.drawCircle(legendLeft + dp(4), y - sp(4), dp(4), fillPaint);
            String label = e.label + "  " + formatValue(e.value);
            canvas.drawText(label, legendLeft + dp(12), y, textPaint);
        }
    }

    // ==================== Bar Chart ====================

    private void drawBar(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        int left = getPaddingLeft();
        int top = getPaddingTop();
        float labelHeight = sp(12) + dp(4);
        float valueHeight = sp(12) + dp(4);
        float plotTop = top + valueHeight;
        float plotBottom = top + h - labelHeight;
        float plotH = plotBottom - plotTop;
        float plotLeft = left + dp(8);
        float plotRight = left + w - dp(8);
        float plotW = plotRight - plotLeft;

        float maxVal = 0f;
        for (Float v : barValues) {
            if (v != null && v > maxVal) maxVal = v;
        }
        if (maxVal <= 0f) maxVal = 1f;

        // 底线
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, axisPaint);

        if (barValues.isEmpty()) return;

        int n = barValues.size();
        float barW = plotW / n * 0.6f;
        float gap = plotW / n * 0.4f;

        for (int i = 0; i < n; i++) {
            float xCenter = plotLeft + (plotW / n) * (i + 0.5f);
            float x = xCenter - barW / 2f;
            float v = barValues.get(i);
            float barH = plotH * (v / maxVal);
            float y = plotBottom - barH;

            // 渐变填充
            fillPaint.setColor(PIE_COLORS[i % PIE_COLORS.length]);
            fillPaint.setAlpha(180);
            canvas.drawRoundRect(new RectF(x, y, x + barW, y + barH),
                    dp(3), dp(3), fillPaint);

            // 数值
            String value = formatValue(v);
            canvas.drawText(value, xCenter, y - dp(4), valuePaint);

            // 标签
            String label = i < barLabels.size() ? barLabels.get(i) : "";
            canvas.drawText(label, xCenter, plotBottom + labelHeight - dp(2), textPaint);
        }
    }

    private String formatValue(float v) {
        if (v >= 100) return String.valueOf((int) v);
        if (v >= 10) return String.format("%.1f", v);
        return String.format("%.2f", v);
    }

    /** 饼图数据条目。 */
    public static class PieEntry {
        public final String label;
        public final float value;

        public PieEntry(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }
}
