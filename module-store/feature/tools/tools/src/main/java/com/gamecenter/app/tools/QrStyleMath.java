package com.gamecenter.app.tools;

/**
 * 二维码美化几何与策略常量（纯 Java，无 Android 依赖）。
 * <p>
 * {@link QrStyleRenderer} 的视觉契约真源：Logo 1/5、标题条 2.4 倍字高、
 * 长标题等比缩字号下限 3%、静区钳制 ≥0、Logo 强制容错 H。
 * 拆出以便 {@code scripts/verify_qr.py} 纯 javac 回归。
 * </p>
 */
public final class QrStyleMath {

    /** 默认渲染尺寸（像素） */
    public static final int DEFAULT_QR_SIZE = 720;

    /** 批量小码渲染尺寸（像素） */
    public static final int BATCH_QR_SIZE = 240;

    /** 批量生码行数上限 */
    public static final int BATCH_MAX_LINES = 50;

    /** ZXing 静区模块数（UI 固定值） */
    public static final int QUIET_ZONE_MODULES = 2;

    /** Logo 徽章直径占码图边长的比例（≤1/5） */
    public static final float LOGO_SIZE_RATIO = 1f / 5f;

    /** 标题文字大小占码图边长的比例 */
    public static final float CAPTION_TEXT_RATIO = 0.06f;

    /** 标题文字条高度与文字大小的倍数关系 */
    public static final float CAPTION_BAR_RATIO = 2.4f;

    /** 长标题缩字号下限占码图边长的比例 */
    public static final float CAPTION_MIN_TEXT_RATIO = 0.03f;

    private QrStyleMath() {
    }

    /**
     * 有效容错级别：叠加 Logo 时强制 'H'（约 30% 冗余补偿遮挡）。
     */
    public static char effectiveEcLevel(boolean withLogo, char ecLevel) {
        return withLogo ? 'H' : ecLevel;
    }

    /**
     * 静区模块数钳制为 ≥0。
     */
    public static int clampedMargin(int marginModules) {
        return Math.max(0, marginModules);
    }

    /**
     * 是否需要标题文字条（null / 空 / 纯空白均不加）。
     */
    public static boolean hasCaption(String caption) {
        return caption != null && !caption.trim().isEmpty();
    }

    /**
     * Logo 徽章边长（像素）。
     */
    public static int logoSize(int size) {
        return Math.round(size * LOGO_SIZE_RATIO);
    }

    /**
     * 标题字号：过长时按可用宽度等比缩小，下限 {@link #CAPTION_MIN_TEXT_RATIO}。
     *
     * @param size        码图边长
     * @param baseTextSize 初始字号（通常 size * CAPTION_TEXT_RATIO）
     * @param measured    当前字号下测得的文字宽度
     * @param maxWidth    可用最大宽度
     */
    public static float captionTextSize(int size, float baseTextSize, float measured, float maxWidth) {
        if (measured > maxWidth && measured > 0) {
            return Math.max(size * CAPTION_MIN_TEXT_RATIO, baseTextSize * maxWidth / measured);
        }
        return baseTextSize;
    }

    /**
     * 标题条高度（像素）。无标题时为 0。
     */
    public static int captionBarHeight(String caption, float textSize) {
        if (!hasCaption(caption)) return 0;
        return Math.round(textSize * CAPTION_BAR_RATIO);
    }
}
