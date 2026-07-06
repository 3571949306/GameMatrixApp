package com.gamecenter.app.browser.ui;

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
 */
public class TabManagerActivity extends AppCompatActivity {

    private BrowserTabManager tabManager;
    private TabAdapter adapter;
    private TextView tvTabCount;

    public static void start(Context context) {
        context.startActivity(new Intent(context, TabManagerActivity.class));
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

        btnBack.setOnClickListener(v -> finish());

        adapter = new TabAdapter();
        rvTabs.setLayoutManager(new LinearLayoutManager(this));
        rvTabs.setAdapter(adapter);

        adapter.setOnItemClickListener(tab -> {
            tabManager.switchTab(tab.getId());
            finish();
        });

        adapter.setOnCloseClickListener(tab -> {
            tabManager.closeTab(tab.getId());
            refreshTabs();
        });

        fabNewTab.setOnClickListener(v -> {
            tabManager.createTab(null);
            refreshTabs();
        });

        findViewById(R.id.btn_close_all).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_tab_close_all_title)
                .setMessage(R.string.browser_tab_close_all_message)
                .setPositiveButton(R.string.browser_tab_close_all, (d, w) -> {
                    tabManager.closeAllTabs();
                    refreshTabs();
                    Toast.makeText(this, R.string.browser_tab_closed_all, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        refreshTabs();
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
