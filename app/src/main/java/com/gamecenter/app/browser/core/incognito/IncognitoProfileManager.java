package com.gamecenter.app.browser.core.incognito;

import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.browser.core.BrowserTabManager;

/**
 * Phase C（无痕与会话隔离）的框架。
 *
 * <p>计划把无痕从"Fragment 布尔位"下沉为<b>按 Tab 维度</b>：
 * {@link BrowserTabManager.Tab#isIncognito()} 真正参与路由，{@code menu_incognito} 改为"新建无痕 Tab"。
 *
 * <p><b>关键约束（必须记录）</b>：{@code WebView.setDataDirectorySuffix} 需在 <b>App 进程启动期</b>设置，
 * 不能在运行时按 Tab 切换。因此当前实现只能提供弱无痕配置：
 * <ol>
 *   <li>不保存表单数据、关闭地理位置、并避免从磁盘缓存读取或写入；</li>
 *   <li>不清空进程级 Cookie/WebStorage，避免注销普通 Tab。因此不宣称 Cookie/站点存储强隔离。</li>
 * </ol>
 */
public final class IncognitoProfileManager {

    private IncognitoProfileManager() {}

    public static boolean isIncognito(@NonNull BrowserTabManager.Tab tab) {
        return tab.isIncognito();
    }

    /**
     * 为无痕 WebView 应用不影响普通 Tab 的最小隐私配置。
     *
     * <p>返回值只表示是否实现了<strong>强隔离</strong>，目前始终为 {@code false}。
     * 不能以清空 {@code CookieManager} 的方式伪造隔离，因为它会破坏普通标签的登录态。
     */
    public static boolean applyProfile(@Nullable WebView webView, @Nullable BrowserTabManager.Tab tab) {
        if (webView == null || tab == null || !isIncognito(tab)) {
            return false;
        }
        try {
            WebSettings settings = webView.getSettings();
            settings.setSaveFormData(false);
            settings.setGeolocationEnabled(false);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.clearFormData();
        } catch (Throwable ignored) {
            // WebView can be in teardown while a tab is being closed. The caller
            // still retains the app-level no-history/no-search policy.
        }
        return false;
    }
}
