package com.gamecenter.app.browser.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserTabManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器标签管理页面。
 *
 * <p>P0-1：所有会返回 BrowserFragment 的操作（切换/新建/关闭）通过 setResult 回传
 * {@link #EXTRA_SELECTED_TAB_ID}，由 Fragment 接收并执行 WebView 切换。
 */
public class TabManagerActivity extends AppCompatActivity {

    /** Result extra：选中/新建/自动切换后的目标 Tab id */
    public static final String EXTRA_SELECTED_TAB_ID = "extra_selected_tab_id";
    /** Result extra：被关闭的 Tab id（用于触发 BrowserController.closeTabWebView） */
    public static final String EXTRA_CLOSED_TAB_ID = "extra_closed_tab_id";
    /** Result code：新建 Tab */
    public static final int RESULT_NEW_TAB = 0x1001;

    private BrowserTabManager tabManager;
    private TabAdapter adapter;
    private TextView tvTabCount;

    /** 启动方式 1：仅展示（无结果回传）。 */
    public static void start(Context context) {
        context.startActivity(new Intent(context, TabManagerActivity.class));
    }

    /** 启动方式 2：带结果回传（用于 BrowserFragment 切换 Tab）。 */
    public static void startForResult(@NonNull Activity activity, int requestCode) {
        Intent intent = new Intent(activity, TabManagerActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_manager);

        tabManager = BrowserTabManager.getInstance(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        tvTabCount = findViewById(R.id.tv_tab_count);
        RecyclerView rvTabs = findViewById(R.id.rv_tabs);
        FloatingActionButton fabNewTab = findViewById(R.id.fab_new_tab);

        btnBack.setOnClickListener(v -> {
            finish();
            // P3-2 Tab 切换动画：Feature Flag 开启时使用 slide 动画
            if (com.gamecenter.app.BuildConfig.BROWSER_TAB_SWITCHER_ANIM) {
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        adapter = new TabAdapter();
        rvTabs.setLayoutManager(new LinearLayoutManager(this));
        rvTabs.setAdapter(adapter);

        adapter.setOnItemClickListener(tab -> {
            tabManager.switchTab(tab.getId());
            returnSelectedTab(tab.getId());
        });

        adapter.setOnCloseClickListener(tab -> {
            String closedId = tab.getId();
            tabManager.closeTab(closedId);
            BrowserTabManager.Tab next = tabManager.getCurrentTab();
            // 回传关闭的 Tab id（让 Controller 释放对应 WebView）+ 自动切换的目标 Tab id
            Intent data = new Intent();
            data.putExtra(EXTRA_CLOSED_TAB_ID, closedId);
            if (next != null) data.putExtra(EXTRA_SELECTED_TAB_ID, next.getId());
            setResult(RESULT_OK, data);
            // 若关闭后没有 Tab（理论上不会，因为 closeTab 会自动 createTab），直接 finish
            refreshTabs();
            if (tabManager.getTabCount() == 0 || next == null) {
                finish();
                return;
            }
            // 关闭后停留在此页让用户继续管理，不自动 finish
            Toast.makeText(this, R.string.browser_tab_closed_one, Toast.LENGTH_SHORT).show();
        });

        fabNewTab.setOnClickListener(v -> {
            BrowserTabManager.Tab newTab = tabManager.createTab(null);
            if (newTab != null) {
                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_TAB_ID, newTab.getId());
                setResult(RESULT_NEW_TAB, data);
                finish();
            } else {
                Toast.makeText(this, R.string.browser_tab_max_reached, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_close_all).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_tab_close_all_title)
                .setMessage(R.string.browser_tab_close_all_message)
                .setPositiveButton(R.string.browser_tab_close_all, (d, w) -> {
                    // 通知 Fragment 销毁所有 WebView
                    Intent data = new Intent();
                    data.putExtra(EXTRA_CLOSED_TAB_ID, "__all__");
                    tabManager.closeAllTabs();
                    BrowserTabManager.Tab fresh = tabManager.getCurrentTab();
                    if (fresh != null) data.putExtra(EXTRA_SELECTED_TAB_ID, fresh.getId());
                    setResult(RESULT_OK, data);
                    Toast.makeText(this, R.string.browser_tab_closed_all, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        refreshTabs();
    }

    /** 回传选中的 Tab id 并 finish。 */
    private void returnSelectedTab(@NonNull String tabId) {
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_TAB_ID, tabId);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTabs();
    }

    private void refreshTabs() {
        List<BrowserTabManager.Tab> tabs = tabManager.getTabList();
        adapter.setData(new ArrayList<>(tabs));
        tvTabCount.setText(getString(R.string.browser_tab_count, tabs.size()));
    }
}
