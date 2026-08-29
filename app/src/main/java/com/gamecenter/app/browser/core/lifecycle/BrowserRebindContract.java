package com.gamecenter.app.browser.core.lifecycle;

import android.webkit.WebView;
import androidx.annotation.NonNull;

/**
 * Phase D（生命周期与重绑统一）的契约骨架。
 *
 * <p>计划抽出 {@code rebindHelpers(WebView)}（手势 + Find + Reader + 下载监听 + ChromeClient 配置器），
 * 在<b>每一处 active WebView 变化点</b>调用：{@code switchToTabById}、关全部 Tab 重建、新建 Tab。
 *
 * <p>本接口定义"变化点"通知；真实实现将在 Phase D 把现有散落的绑定逻辑收敛到单一入口。
 */
public interface BrowserRebindContract {

    /** active WebView 变化时调用，重新绑定所有依附于 WebView 的辅助器。 */
    void rebindHelpers(@NonNull WebView newWebView);

    /** 生命周期宿主（BrowserFragment）实现此回调，把变化点转发给各 rebind 目标。 */
    interface RebindTarget {
        void onActiveWebViewChanged(@NonNull WebView webView);
    }
}
