package com.gamecenter.app.browser.core.framework;

import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Phase F（God Class 拆分）的契约骨架。
 *
 * <p>{@code BrowserFragment} 当前约 1,946 行，计划按职责外移为若干 delegate / controller。
 * 本文件只定义<b>稳定签名</b>（接口 + 宿主注册点），真实实现将在 Phase F 逐类抽取，
 * 每抽一类跑一次 {@code verify_browser.py} + 编译，禁止一次性大改。
 *
 * <p>拆分顺序（计划 §Phase F）：先抽无状态 delegate，后抽有状态 controller。
 */
public final class BrowserFragmentDelegates {

    private BrowserFragmentDelegates() {}

    /** 宿主注册点：BrowserFragment 在未来把各 delegate 注入此处。当前为空实现占位。 */
    public static final class Registry {
        @Nullable public UrlBarDelegate urlBar;
        @Nullable public DownloadDelegate download;
        @Nullable public PermissionsDelegate permissions;
        @Nullable public ChromeDelegate chrome;
        @Nullable public MenuDelegate menu;

        public boolean isEmpty() {
            return urlBar == null && download == null && permissions == null
                    && chrome == null && menu == null;
        }
    }

    /** URL 栏：建议 popup、搜索引擎选择、粘贴并前往、搜索历史（~350 行）。Phase F 抽取目标。 */
    public interface UrlBarDelegate {
        void onUrlCommit(@NonNull String rawInput);
        void showSuggestions();
        void clearSearchHistory();
    }

    /** 下载：handleDownload、危险文件确认、下载列表入口（~60 行）。Phase F 抽取目标。 */
    public interface DownloadDelegate {
        void handleDownload(@NonNull String url, @Nullable String contentDisposition, @Nullable String mimeType);
        void confirmDangerousFile(@NonNull String fileName);
    }

    /** 权限：文件选择、全屏视频、地理/媒体权限（~180 行）。Phase F 抽取目标。 */
    public interface PermissionsDelegate {
        void onPermissionRequest(@NonNull PermissionRequest request);
        void onPermissionRevoked(@NonNull PermissionRequest request);
    }

    /** Chrome 绑定：configureChromeClient + 全屏视图管理（~120 行）。Phase F 抽取目标。 */
    public interface ChromeDelegate {
        void configure(@NonNull WebView webView);
        void onToggleFullscreen(boolean enter, @Nullable View fullscreenView);
    }

    /** 菜单：showMoreMenu 及其 ~20 个分支（~140 行）。Phase F 抽取目标。 */
    public interface MenuDelegate {
        void showMoreMenu();
        void onMenuItemSelected(int itemId);
    }
}
