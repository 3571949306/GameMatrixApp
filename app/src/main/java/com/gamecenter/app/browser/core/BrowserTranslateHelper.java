package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.gamecenter.app.R;

import java.net.URLEncoder;

/**
 * 页面翻译（P1-5）。
 *
 * <p>接入 Google / 百度 / 必应翻译 URL 模式，新开 WebView 加载翻译后的页面。</p>
 */
public class BrowserTranslateHelper {

    public static final int ENGINE_GOOGLE = 0;
    public static final int ENGINE_BAIDU = 1;
    public static final int ENGINE_BING = 2;

    private BrowserTranslateHelper() {}

    /** 弹出选择翻译引擎对话框，由调用方提供页面 URL */
    public static void showEngineDialog(@NonNull Context context, @Nullable String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            Toast.makeText(context, R.string.browser_translate_no_url, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[]{
                context.getString(R.string.browser_translate_google),
                context.getString(R.string.browser_translate_baidu),
                context.getString(R.string.browser_translate_bing)
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.browser_translate_engine_title)
                .setItems(items, (d, which) -> openTranslate(context, pageUrl, which))
                .setNegativeButton(R.string.browser_translate_cancel, null)
                .show();
    }

    /** 根据引擎构造翻译 URL 并通过 ACTION_VIEW 打开 */
    public static void openTranslate(@NonNull Context context, @NonNull String pageUrl, int engine) {
        String translateUrl = buildTranslateUrl(pageUrl, engine);
        if (translateUrl == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(translateUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // B19：跳转前先确认有目标（与 BrowserSecurityPolicy 的既有做法一致），
            // 避免 ActivityNotFoundException 只靠 Throwable 兜底、提示语不可读。
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                Toast.makeText(context, R.string.browser_translate_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, R.string.browser_translate_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private static String buildTranslateUrl(@NonNull String pageUrl, int engine) {
        try {
            String encoded = URLEncoder.encode(pageUrl, "UTF-8");
            switch (engine) {
                case ENGINE_GOOGLE:
                    return "https://translate.google.com/translate?sl=auto&tl=zh-CN&u=" + encoded;
                case ENGINE_BAIDU:
                    return "https://fanyi.baidu.com/transpage?query=" + encoded + "&from=auto&to=zh&source=url";
                case ENGINE_BING:
                    return "https://www.translatetheweb.com/?from=&to=zh-Hans&a=" + encoded;
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
