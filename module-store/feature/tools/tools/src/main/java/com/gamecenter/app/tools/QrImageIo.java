package com.gamecenter.app.tools;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.gamecenter.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 二维码图片导出工具（qr_plus 增强功能）：保存到相册 + 分享 PNG。
 * <p>
 * 保存：API 29+ 走 MediaStore RELATIVE_PATH（Pictures/二维码，无需存储权限）；
 * API 26-28 走 legacy insert（依赖宿主声明的 WRITE_EXTERNAL_STORAGE 运行时授权，未授权时失败并提示）。
 * 分享：参照宿主 ShareCardGenerator 的先例——PNG 写入 {@code cacheDir/qr_share/}，
 * 复用宿主 FileProvider（authority {@code <pkg>.browser.fileprovider}，其 file_paths.xml
 * 的 cache-path path="." 覆盖整个 cache 目录，含 qr_share 子目录），无需模块自建 provider。
 * </p>
 */
public final class QrImageIo {

    private static final String TAG = "QrImageIo";

    /** 相册子目录（RELATIVE_PATH） */
    private static final String GALLERY_RELATIVE_PATH = "Pictures/二维码";

    /**
     * 相册路径展示名（Toast 用，与 {@link #GALLERY_RELATIVE_PATH} 保持一致）。
     */
    public static String galleryPathLabel() {
        return GALLERY_RELATIVE_PATH;
    }

    /** 分享 PNG 的 cache 子目录（须在宿主 FileProvider cache-path 覆盖范围内） */
    private static final String SHARE_DIR = "qr_share";

    /** 分享 cache 保留最近文件个数，防止无限堆积 */
    private static final int SHARE_CACHE_KEEP = 5;

    private static final ToolIo.ShareCachePruner SHARE_PRUNER =
            new ToolIo.ShareCachePruner(SHARE_CACHE_KEEP);

    private QrImageIo() {
    }

    /**
     * 生成导出文件显示名（不含扩展名），如 qr_20260925_143000。
     */
    public static String displayName() {
        return "qr_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                .format(new Date());
    }

    /**
     * 将 Bitmap 保存到系统相册的 Pictures/二维码 目录。
     * 注意：属 IO 操作，应在后台线程调用。
     *
     * @param context     上下文
     * @param bitmap      要保存的图片
     * @param displayName 文件显示名（不含扩展名）
     * @return 成功返回写入的 MediaStore Uri；失败返回 null
     */
    public static Uri saveToGallery(Context context, Bitmap bitmap, String displayName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_RELATIVE_PATH);
            }
            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os == null) return null;
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
            }
            return uri;
        } catch (Exception e) {
            Log.w(TAG, "Save QR to gallery failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将 Bitmap 写入 cache 后拉起系统分享面板（PNG，ACTION_SEND）。
     * 注意：属 IO 操作，应在后台线程调用。
     *
     * @param context 上下文
     * @param bitmap  要分享的图片
     * @return 是否成功拉起分享
     */
    public static boolean sharePng(Context context, Bitmap bitmap) {
        try {
            File dir = new File(context.getCacheDir(), SHARE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create share dir: " + dir);
                return false;
            }
            // 写入前修剪旧分享文件，避免 cacheDir/qr_share 无限增长
            SHARE_PRUNER.prune(dir);
            File file = new File(dir, displayName() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }
            // 复用宿主 browser.fileprovider（cache-path 覆盖 cache 根目录）
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".browser.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent,
                    context.getString(R.string.tool_qr_plus_share));
            // 模块内 context 多为非 Activity 上下文，统一补 NEW_TASK
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Share QR failed: " + e.getMessage());
            return false;
        }
    }
}
