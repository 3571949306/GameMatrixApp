package com.gamecenter.app.tools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码美化渲染器（qr_plus 增强功能，参考草料二维码的本地美化能力）。
 * <p>
 * 在原有黑白码基础上支持：
 * <ul>
 *   <li>前景色 / 背景色自定义</li>
 *   <li>中心 Logo 叠加：开启后自动使用最高容错级别 'H'（约 30% 冗余）补偿遮挡，
 *       Logo 尺寸控制在码图 1/5 以内，保证扫码成功率</li>
 *   <li>码图下方标题文字条：画布纵向扩展，文字与码点同色系</li>
 *   <li>容错级别 L/M/Q/H 可选（叠加 Logo 时强制 H）</li>
 * </ul>
 * <p>
 * Logo 采用 Canvas 自绘"码"字圆形徽章，零资源依赖（不引用模块/宿主任何 drawable），
 * 避免动态模块与宿主资源 ID 的耦合问题。
 */
public final class QrStyleRenderer {

    /** 默认渲染尺寸（像素），720px 在清晰度和生成速度间取得平衡 */
    public static final int DEFAULT_QR_SIZE = QrStyleMath.DEFAULT_QR_SIZE;

    private QrStyleRenderer() {
    }

    /**
     * 渲染美化二维码（默认 720px）。
     *
     * @param content  要编码的文本内容
     * @param fgColor  前景色（码点颜色）
     * @param bgColor  背景色
     * @param withLogo 是否叠加中心 Logo（叠加时强制容错 H）
     * @param caption  码图下方的标题文字，空/null 表示不加文字条
     * @param marginDp 静区边距：按 ZXing 规范此值作为静区模块数传入 MARGIN hint，
     *                 UI 侧固定传 2（不向用户暴露此设置）
     * @param ecLevel  容错级别 'L'/'M'/'Q'/'H'，withLogo 时忽略并强制 'H'
     * @return 渲染完成的 Bitmap
     * @throws Exception ZXing 编码失败时抛出
     */
    public static Bitmap renderStyledQr(String content, int fgColor, int bgColor,
            boolean withLogo, String caption, int marginDp, char ecLevel) throws Exception {
        return renderStyledQr(content, fgColor, bgColor, withLogo, caption, marginDp,
                ecLevel, DEFAULT_QR_SIZE);
    }

    /**
     * 渲染美化二维码（指定尺寸）。
     * <p>
     * 批量生码场景传入较小尺寸（如 240px）以控制内存占用。
     *
     * @param size 码图边长（像素）
     * @see #renderStyledQr(String, int, int, boolean, String, int, char)
     */
    public static Bitmap renderStyledQr(String content, int fgColor, int bgColor,
            boolean withLogo, String caption, int marginDp, char ecLevel, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // 叠加 Logo 会遮挡中心数据模块，强制最高容错 H（约 30% 冗余）补偿遮挡
        hints.put(EncodeHintType.ERROR_CORRECTION,
                String.valueOf(QrStyleMath.effectiveEcLevel(withLogo, ecLevel)));
        hints.put(EncodeHintType.MARGIN, QrStyleMath.clampedMargin(marginDp));
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix matrix = new QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size, hints);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            int offset = y * size;
            for (int x = 0; x < size; x++) {
                // BitMatrix 中 true 表示前景模块，false 表示背景
                pixels[offset + x] = matrix.get(x, y) ? fgColor : bgColor;
            }
        }
        // createBitmap(int[]...) 返回不可变 Bitmap，直接返回即可（无需再画内容时）
        Bitmap qr = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);

        boolean hasCaption = QrStyleMath.hasCaption(caption);
        if (!withLogo && !hasCaption) {
            return qr;
        }

        // 需要叠加内容时用可变画布合成：createBitmap(w, h, config) 返回可变 Bitmap
        String captionText = hasCaption ? caption.trim() : "";
        float captionTextSize = size * QrStyleMath.CAPTION_TEXT_RATIO;
        // 标题过长时按可用宽度等比缩小字号，避免截断
        Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        captionPaint.setColor(fgColor);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTypeface(Typeface.DEFAULT_BOLD);
        captionPaint.setTextSize(captionTextSize);
        float maxWidth = size - captionTextSize;
        float measured = captionPaint.measureText(captionText);
        captionTextSize = QrStyleMath.captionTextSize(size, captionTextSize, measured, maxWidth);
        captionPaint.setTextSize(captionTextSize);
        int captionBarHeight = QrStyleMath.captionBarHeight(caption, captionTextSize);

        Bitmap composed = Bitmap.createBitmap(size, size + captionBarHeight,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composed);
        canvas.drawColor(bgColor);
        canvas.drawBitmap(qr, 0, 0, null);
        if (withLogo) {
            int logoSize = QrStyleMath.logoSize(size);
            Bitmap badge = createLogoBadge(logoSize, fgColor, bgColor);
            float left = (size - logoSize) / 2f;
            float top = (size - logoSize) / 2f;
            canvas.drawBitmap(badge, left, top, null);
        }
        if (hasCaption) {
            float centerX = size / 2f;
            float centerY = size + captionBarHeight / 2f;
            Paint.FontMetrics fm = captionPaint.getFontMetrics();
            float baseline = centerY - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(captionText, centerX, baseline, captionPaint);
        }
        return composed;
    }

    /**
     * 自绘中心 Logo 徽章：背景色圆底 + 前景色描边 + 前景色"码"字。
     * <p>
     * 零资源依赖方案：不引用任何 drawable/mipmap，避免动态模块引用宿主资源的 ID 漂移问题。
     *
     * @param size    徽章直径（像素）
     * @param fgColor 前景色（描边与文字）
     * @param bgColor 背景色（圆底）
     * @return 徽章 Bitmap
     */
    private static Bitmap createLogoBadge(int size, int fgColor, int bgColor) {
        Bitmap badge = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(badge);
        float half = size / 2f;

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(bgColor);
        canvas.drawCircle(half, half, half, fillPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(fgColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(2f, size * 0.06f));
        canvas.drawCircle(half, half, half - strokePaint.getStrokeWidth() / 2f, strokePaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(fgColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size * 0.55f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = half - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("码", half, baseline, textPaint);
        return badge;
    }
}
