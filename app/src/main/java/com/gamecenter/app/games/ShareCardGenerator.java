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
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.gamecenter.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
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

        // 5. 战绩四格
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

        // 6. Footer
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(0xCCFFFFFF);
        footerPaint.setTextSize(sp(20, density));
        footerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        String footer = context.getString(R.string.share_card_footer);
        canvas.drawText(footer, (CARD_WIDTH - footerPaint.measureText(footer)) / 2f,
                1700, footerPaint);

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
        if (!data.hasData()) return null;
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
        return sb.toString();
    }

    @NonNull
    private static String sanitize(@NonNull String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
