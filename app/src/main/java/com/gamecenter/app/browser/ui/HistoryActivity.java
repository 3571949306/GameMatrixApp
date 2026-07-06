package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.data.repository.BrowserHistoryRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 浏览历史记录页面（Room 数据源）。
 * 支持搜索、删除单条、清空全部、按日期分组、点击打开URL。
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private EditText etSearch;
    private ImageButton btnBack;
    private MaterialButton btnClearAll;
    private View emptyView;
    private TextView tvCountHeader;

    private BrowserHistoryRepository historyRepository;
    private HistoryAdapter adapter;

    public static void start(Context context) {
        Intent intent = new Intent(context, HistoryActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        historyRepository = new BrowserHistoryRepository(getApplication());
        initViews();
        setupRecyclerView();
        setupListeners();
        loadHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rv_history);
        etSearch = findViewById(R.id.et_search);
        btnBack = findViewById(R.id.btn_back);
        btnClearAll = findViewById(R.id.btn_clear_all);
        emptyView = findViewById(R.id.empty_view);
        tvCountHeader = findViewById(R.id.tv_history_count_header);
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> BrowserActivity.start(this, item.getUrl()));
        adapter.setOnItemLongClickListener(this::showDeleteConfirmDialog);
        adapter.setOnDeleteClickListener(this::deleteItem);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnClearAll.setOnClickListener(v -> showClearAllDialog());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadHistory() {
        historyRepository.getAllHistory(items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void performSearch(String keyword) {
        if (keyword.isEmpty()) {
            loadHistory();
            return;
        }
        historyRepository.searchHistory(keyword, items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void updateEmptyState(int count) {
        if (count == 0) {
            rvHistory.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            tvCountHeader.setVisibility(View.GONE);
        } else {
            rvHistory.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            tvCountHeader.setVisibility(View.VISIBLE);
            tvCountHeader.setText(count + " \u6761\u8bb0\u5f55");
        }
    }

    private void deleteItem(BrowserHistoryEntity item) {
        historyRepository.deleteById(item.getId());
        performSearch(etSearch.getText().toString().trim());
    }

    private void showDeleteConfirmDialog(BrowserHistoryEntity item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history_delete_title)
                .setMessage(R.string.browser_history_delete_message)
                .setPositiveButton(R.string.browser_history_delete, (d, w) -> deleteItem(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history_clear_title)
                .setMessage(R.string.browser_history_clear_message)
                .setPositiveButton(R.string.browser_history_clear_all, (d, w) -> {
                    historyRepository.deleteAll();
                    loadHistory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (etSearch.getText().toString().trim().isEmpty()) {
            super.onBackPressed();
        } else {
            etSearch.setText("");
        }
    }
}
