package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserSettingsManager;

/**
 * 浏览器主页面 Activity - 浏览器模块的外壳容器。
 *
 * <p>职责：加载 BrowserFragment，传递初始 URL。
 * P2-2：音量键滚动页面（Feature Flag + 设置开关双控）。
 */
public class BrowserActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrowserActivity.class));
    }

    public static void start(Context context, String url) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_browser);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container), (v, insets) -> {
            v.setPadding(
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            String url = getIntent().getStringExtra("url");
            BrowserFragment fragment = BrowserFragment.newInstance(url);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment, BrowserFragment.TAG);
            transaction.commit();
        }
    }

    /**
     * P2-2 音量键滚动：拦截音量上下键，pageScroll 上下翻页。
     * Feature Flag + SettingsManager 双重开关。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (BuildConfig.BROWSER_VOLUME_SCROLL
                && BrowserSettingsManager.getInstance(this).isVolumeScrollEnabled()) {
            WebView wv = getActiveWebView();
            if (wv != null) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    wv.pageUp(false);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    wv.pageDown(false);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (BuildConfig.BROWSER_VOLUME_SCROLL
                && BrowserSettingsManager.getInstance(this).isVolumeScrollEnabled()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true; // 消费 up 事件，避免系统音量调节
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private WebView getActiveWebView() {
        Fragment f = getSupportFragmentManager().findFragmentByTag(BrowserFragment.TAG);
        if (f instanceof BrowserFragment) {
            return ((BrowserFragment) f).getControllerWebView();
        }
        return null;
    }
}
