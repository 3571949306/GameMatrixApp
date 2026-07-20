package com.gamecenter.app.browser.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 浏览器截图辅助类。
 *
 * <p>策略：
 * <ul>
 *   <li>Android 9 (API 28) 及以下：使用 WebView.capturePicture + Canvas → Bitmap</li>
 *   <li>Android 10+：使用 PixelCopy（API 24+ 可用），捕获 WebView 当前可见区域</li>
 *   <li>保存到 Pictures/BrowserScreenshots 目录</li>
 *   <li>Android 10+ 使用 MediaStore 写入，旧版本使用 File 直接写入</li>
 * </ul>
 */
public class BrowserScreenshotHelper {

    public interface ScreenshotCallback {
        void onSuccess(@NonNull Uri savedUri);
        void onFailure(@Nullable String errorMessage);
    }

    /** 截取 WebView 当前可见区域的 Bitmap（不包含滚动外内容） */
    @Nullable
    public static Bitmap captureVisible(@NonNull WebView webView) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    webView.getWidth(),
                    webView.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 截图并保存到 Pictures/BrowserScreenshots。
     * 在主线程调用即可（Bitmap 创建 + 文件 IO 一次性完成，文件通常 <1MB）。
     */
    public static void captureAndSave(@NonNull final Context context,
                                      @NonNull final WebView webView,
                                      @Nullable final ScreenshotCallback callback) {
        final Bitmap bitmap = captureVisible(webView);
        if (bitmap == null) {
            Toast.makeText(context, R.string.browser_screenshot_failed, Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onFailure("capture failed");
            return;
        }
        final String fileName = "Browser_" + new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";
        final String dirName = "BrowserScreenshots";

        new Thread(() -> {
            Uri savedUri = null;
            String errorMsg = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+：使用 MediaStore（无需 WRITE_EXTERNAL_STORAGE 权限）
                    ContentResolver resolver = context.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/" + dirName);
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri collection = MediaStore.Images.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    savedUri = resolver.insert(collection, values);
                    if (savedUri != null) {
                        try (OutputStream os = resolver.openOutputStream(savedUri)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        }
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        resolver.update(savedUri, values, null, null);
                    }
                } else {
                    // Android 9 及以下：直接写文件
                    File picturesDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES);
                    File screenshotDir = new File(picturesDir, dirName);
                    if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
                        errorMsg = "create dir failed";
                    } else {
                        File file = new File(screenshotDir, fileName);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                        }
                        // 通知相册扫描
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(Uri.fromFile(file));
                        context.sendBroadcast(mediaScanIntent);
                        savedUri = Uri.fromFile(file);
                    }
                }
            } catch (Throwable t) {
                errorMsg = t.getMessage();
            } finally {
                // 不立刻 recycle，因为 Toast 可能需要展示
            }

            final Uri finalUri = savedUri;
            final String finalError = errorMsg;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (finalUri != null) {
                    Toast.makeText(context,
                            context.getString(R.string.browser_screenshot_saved, dirName),
                            Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onSuccess(finalUri);
                } else {
                    Toast.makeText(context, R.string.browser_screenshot_failed, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onFailure(finalError);
                }
                bitmap.recycle();
            });
        }, "browser-screenshot").start();
    }
}
