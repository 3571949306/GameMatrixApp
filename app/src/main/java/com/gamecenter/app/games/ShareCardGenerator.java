package com.gamecenter.app.games;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.gamecenter.app.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用战绩分享卡片生成器（P0-3）。
 * <p>
 * 使用 Canvas 绘制 1080×1920 的 Bitmap，包含：
 * <ul>
 *   <li>顶部渐变背景 + App 名</li>
 *   <li>游戏图标 + 名称</li>
 *   <li>核心战绩四格（最高分/总对局/胜负/总时长）</li>
 *   <li>底部 footer</li>
 * </ul>
 * </p>
 * <p>生成后写入 cacheDir/share_card/，通过 FileProvider URI 发起 ACTION_SEND 分享。</p>
 */
public final class ShareCardGenerator {

    private static final int CARD_WIDTH = 1080;
    private static final int CARD_HEIGHT = 1920;

    private final Context context;

    public ShareCardGenerator(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** 战绩数据。 */
    public static final class Data {
        @Nullable public String gameName;
        @Nullable public String gameId; // 用于查图标
        public int gameIconRes;
        public int highScore;
        public int playCount;
        public int winCount;
        public int lossCount;
        public long playTimeMs;
        /**
         * 二维码载荷（可选）。非 null 且含非空白字符时，在卡片底部绘制该内容的二维码
         * （内容原样编码，如推箱子关卡码，扫码即得文本）；为 null 时卡片行为与历史版本完全一致。
         */
        @Nullable public String qrPayload;

        /** 是否有可分享的数据（至少一项 > 0）。 */
        public boolean hasData() {
            return highScore > 0 || playCount > 0 || winCount > 0 || lossCount > 0 || playTimeMs > 0;
        }
    }

    /** 生成 Bitmap（同步调用，建议在子线程执行）。 */
    @NonNull
    public Bitmap generate(@NonNull Data data) {
        Bitmap bmp = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        float density = context.getResources().getDisplayMetrics().density;
        // 是否绘制二维码：qrPayload 非 null 且含非空白字符；为空时下方布局与历史版本完全一致
        boolean hasQr = hasQrPayload(data);

        // 1. 背景渐变
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(
                0, 0, 0, CARD_HEIGHT,
                0xFF6750A4, 0xFF21005D,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, CARD_WIDTH, CARD_HEIGHT, bgPaint);

        // 2. 顶部 App 名
        Paint appNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        appNamePaint.setColor(Color.WHITE);
        appNamePaint.setTextSize(sp(22, density));
        appNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String appName = context.getString(R.string.share_card_app_name);
        float appNameY = 140;
        canvas.drawText(appName, (CARD_WIDTH - appNamePaint.measureText(appName)) / 2f,
                appNameY, appNamePaint);

        // 3. 游戏图标（圆形背景）
        float iconSize = 280;
        float iconCx = CARD_WIDTH / 2f;
        float iconCy = 380;
        Paint iconBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconBgPaint.setColor(0x33FFFFFF);
        canvas.drawCircle(iconCx, iconCy, iconSize / 2f + 20, iconBgPaint);

        Drawable icon = null;
        if (data.gameIconRes != 0) {
            try {
                icon = context.getResources().getDrawable(data.gameIconRes, null);
            } catch (Exception ignored) {}
        }
        if (icon != null) {
            int l = (int) (iconCx - iconSize / 2f);
            int t = (int) (iconCy - iconSize / 2f);
            icon.setBounds(l, t, l + (int) iconSize, t + (int) iconSize);
            icon.draw(canvas);
        } else {
            // 占位圆
            Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            placeholderPaint.setColor(0x55FFFFFF);
            canvas.drawCircle(iconCx, iconCy, iconSize / 2f, placeholderPaint);
        }

        // 4. 游戏名
        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(sp(40, density));
        namePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String gameName = TextUtils.isEmpty(data.gameName)
                ? context.getString(R.string.share_card_app_name) : data.gameName;
        canvas.drawText(gameName, (CARD_WIDTH - namePaint.measureText(gameName)) / 2f,
                580, namePaint);

        // 5. 战绩四格（仅无二维码的通用战报卡绘制；带二维码的关卡分享卡跳过——
        //    关卡码场景战绩无信息量，二维码居中放大更清晰，也避免四格与 QR 布局拥挤重叠）
        if (!hasQr) {
            drawStatBlock(canvas, density, 700,
                    context.getString(R.string.share_card_high_score_label),
                    String.valueOf(data.highScore));
            drawStatBlock(canvas, density, 920,
                    context.getString(R.string.share_card_play_count_label),
                    String.valueOf(data.playCount));
            drawStatBlock(canvas, density, 1140,
                    context.getString(R.string.share_card_win_loss_label),
                    context.getString(R.string.share_card_format_win_loss,
                            data.winCount, data.lossCount));
            long minutes = TimeUnit.MILLISECONDS.toMinutes(data.playTimeMs);
            drawStatBlock(canvas, density, 1360,
                    context.getString(R.string.share_card_play_time_label),
                    context.getString(R.string.share_card_format_minutes, (int) minutes));
        }

        // 6. Footer（布局统一：无二维码时在战绩四格之下，有二维码时在二维码提示之下）
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(0xCCFFFFFF);
        footerPaint.setTextSize(sp(20, density));
        footerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        String footer = context.getString(R.string.share_card_footer);
        canvas.drawText(footer, (CARD_WIDTH - footerPaint.measureText(footer)) / 2f,
                1700, footerPaint);

        // 6.5 二维码（qrPayload 非空时）：扫码即得关卡码文本；生成失败仅记日志跳过，不影响卡片其余部分
        if (hasQr) {
            drawQrCode(canvas, data.qrPayload, density);
        }

        // 7. 顶部装饰条
        Paint decoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        decoPaint.setColor(0xFFE8DEF8);
        canvas.drawRoundRect(380, 80, 700, 88, 4, 4, decoPaint);

        return bmp;
    }

    private void drawStatBlock(@NonNull Canvas canvas, float density, float top,
                               @NonNull String label, @NonNull String value) {
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xCCFFFFFF);
        labelPaint.setTextSize(sp(22, density));
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextSize(sp(48, density));
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // 背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0x33FFFFFF);
        float pad = 32;
        float blockW = CARD_WIDTH - 200;
        float blockH = 160;
        float left = 100;
        Rect rect = new Rect((int) left, (int) top,
                (int) (left + blockW), (int) (top + blockH));
        canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, 24, 24, bgPaint);

        // 标签
        canvas.drawText(label, left + pad, top + 60, labelPaint);
        // 值
        canvas.drawText(value, left + pad, top + 130, valuePaint);
    }

    /**
     * 在卡片中部绘制二维码与提示小字（关卡分享卡布局：跳过战绩四格，二维码居中放大；
     * 与标题区、footer 均留有充足间距，互不重叠）。
     * <p>生成失败（WriterException）时仅记日志并跳过二维码绘制，卡片其余部分照常输出。</p>
     */
    private void drawQrCode(@NonNull Canvas canvas, @NonNull String payload, float density) {
        final int qrSize = 430;
        try {
            // 纠错级别 L + 最小留白：关卡码为短文本且分享文本附带明文，优先保证码点稀疏易扫
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(payload, BarcodeFormat.QR_CODE, qrSize, qrSize, hints);
            // 逐像素转为黑码白底位图（白底与深色卡片背景形成对比，保证扫码成功率）
            int[] pixels = new int[qrSize * qrSize];
            for (int y = 0; y < qrSize; y++) {
                int offset = y * qrSize;
                for (int x = 0; x < qrSize; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap qrBmp = Bitmap.createBitmap(pixels, qrSize, qrSize, Bitmap.Config.ARGB_8888);
            // 居中放大：跳过战绩四格后，二维码占据卡片中部核心区域（游戏名基线 580 之下留白即开始）
            canvas.drawBitmap(qrBmp, (CARD_WIDTH - qrSize) / 2f, 920, null);
            qrBmp.recycle();
            // 二维码下方提示小字（字号与 footer 协调）
            Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            hintPaint.setColor(0xCCFFFFFF);
            hintPaint.setTextSize(sp(20, density));
            String hint = "扫码获取关卡码，可在推箱子编辑器导入";
            canvas.drawText(hint, (CARD_WIDTH - hintPaint.measureText(hint)) / 2f,
                    1430, hintPaint);
        } catch (WriterException e) {
            // 不静默吞掉：留日志便于排查，同时保证失败时卡片其余部分仍然可用
            Log.w("ShareCardGenerator", "二维码生成失败，跳过二维码绘制", e);
        }
    }

    /** qrPayload 是否有效（非 null 且含非空白字符）。 */
    private static boolean hasQrPayload(@NonNull Data data) {
        return data.qrPayload != null && !data.qrPayload.trim().isEmpty();
    }

    private float sp(int value, float density) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics());
    }

    /** 将 Bitmap 写入 cacheDir/share_card/share_<gameId>.png，返回 FileProvider URI。 */
    @Nullable
    public Uri saveToCache(@NonNull Bitmap bmp, @NonNull String gameId) {
        File dir = new File(context.getCacheDir(), "share_card");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File file = new File(dir, "share_" + sanitize(gameId) + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (IOException e) {
            return null;
        }
        try {
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".browser.fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    /** 创建分享 Intent。 */
    @Nullable
    public Intent buildShareIntent(@NonNull Data data) {
        // 有关卡码时即使战绩全 0 也生成卡片（推箱子自定义关卡分享场景）
        if (!data.hasData() && !hasQrPayload(data)) return null;
        Bitmap bmp = generate(data);
        String id = TextUtils.isEmpty(data.gameId) ? "default" : data.gameId;
        Uri uri = saveToCache(bmp, id);
        if (uri == null) return null;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, buildShareText(data));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, context.getString(R.string.share_card_chooser_title));
    }

    @NonNull
    private String buildShareText(@NonNull Data data) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.share_card_app_name))
                .append(" · ")
                .append(TextUtils.isEmpty(data.gameName)
                        ? context.getString(R.string.share_card_title) : data.gameName)
                .append('\n');
        sb.append(context.getString(R.string.share_card_high_score_label))
                .append(": ").append(data.highScore).append("  ");
        sb.append(context.getString(R.string.share_card_play_count_label))
                .append(": ").append(data.playCount).append('\n');
        sb.append(context.getString(R.string.share_card_win_loss_label))
                .append(": ")
                .append(context.getString(R.string.share_card_format_win_loss,
                        data.winCount, data.lossCount)).append("  ");
        long minutes = TimeUnit.MILLISECONDS.toMinutes(data.playTimeMs);
        sb.append(context.getString(R.string.share_card_play_time_label))
                .append(": ")
                .append(context.getString(R.string.share_card_format_minutes, (int) minutes))
                .append('\n');
        sb.append(context.getString(R.string.share_card_footer));
        // 二维码场景：附带关卡码明文，接收方在不便扫码识别时也可直接复制文本导入
        if (hasQrPayload(data)) {
            sb.append('\n').append("关卡码: ").append(data.qrPayload);
        }
        return sb.toString();
    }

    @NonNull
    private static String sanitize(@NonNull String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
