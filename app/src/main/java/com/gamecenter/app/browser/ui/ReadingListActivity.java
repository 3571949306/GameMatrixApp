package com.gamecenter.app.browser.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;
import com.gamecenter.app.browser.data.repository.BrowserReadingListRepository;

import java.util.List;

/**
 * 阅读列表页面（P1-3）。
 * <p>显示用户加入"稍后阅读"的所有网页。点击打开；长按标记已读/未读；右侧按钮删除。</p>
 */
public class ReadingListActivity extends AppCompatActivity {

    private RecyclerView rvReadingList;
    private EditText etSearch;
    private View emptyView;
    private TextView tvCountHeader;
    private BrowserReadingListRepository repository;
    private ReadingListAdapter adapter;
    private OnBackInvokedCallback backInvokedCallback;

    public static void start(Context context) {
        context.startActivity(new Intent(context, ReadingListActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_list);
        repository = new BrowserReadingListRepository(getApplication());
        initViews();
        setupRecyclerView();
        setupListeners();
        loadList();
        registerPredictiveBack();
    }

    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = () -> handleBack();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backInvokedCallback
            );
        }
    }

    private void initViews() {
        rvReadingList = findViewById(R.id.rv_reading_list);
        etSearch = findViewById(R.id.et_search);
        emptyView = findViewById(R.id.empty_view);
        tvCountHeader = findViewById(R.id.tv_reading_count);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnClearAll = findViewById(R.id.btn_clear_all);
        btnBack.setOnClickListener(v -> finish());
        btnClearAll.setOnClickListener(v -> confirmClearAll());
    }

    private void setupRecyclerView() {
        adapter = new ReadingListAdapter();
        rvReadingList.setLayoutManager(new LinearLayoutManager(this));
        rvReadingList.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            // 标记已读后跳转
            repository.markRead(item.getId(), true);
            BrowserActivity.start(this, item.getUrl());
            finish();
        });
        adapter.setOnItemLongClickListener(this::showToggleReadDialog);
        adapter.setOnDeleteClickListener(this::deleteItem);
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadList() {
        repository.getAll(items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void performSearch(String keyword) {
        if (keyword.isEmpty()) {
            loadList();
            return;
        }
        repository.search(keyword, items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void updateEmptyState(int count) {
        if (count == 0) {
            rvReadingList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            tvCountHeader.setVisibility(View.GONE);
        } else {
            rvReadingList.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            tvCountHeader.setVisibility(View.VISIBLE);
            tvCountHeader.setText(count + " " + getString(R.string.browser_reading_list_count_suffix));
        }
    }

    private void deleteItem(BrowserReadingListEntity item) {
        repository.deleteById(item.getId());
        performSearch(etSearch.getText().toString().trim());
        Toast.makeText(this, R.string.browser_reading_list_deleted, Toast.LENGTH_SHORT).show();
    }

    private void showToggleReadDialog(BrowserReadingListEntity item) {
        boolean unread = item.getRead() == 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_reading_list_options_title)
                .setItems(new CharSequence[]{
                        unread ? getString(R.string.browser_reading_list_mark_read)
                                : getString(R.string.browser_reading_list_mark_unread),
                        getString(R.string.browser_reading_list_delete)
                }, (d, which) -> {
                    if (which == 0) {
                        repository.markRead(item.getId(), unread);
                        performSearch(etSearch.getText().toString().trim());
                        Toast.makeText(this,
                                unread ? R.string.browser_reading_list_marked_read
                                        : R.string.browser_reading_list_marked_unread,
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        deleteItem(item);
                    }
                })
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_reading_list_clear_title)
                .setMessage(R.string.browser_reading_list_clear_message)
                .setPositiveButton(R.string.browser_reading_list_clear_all, (d, w) -> {
                    repository.deleteAll();
                    loadList();
                    Toast.makeText(this, R.string.browser_reading_list_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (etSearch.getText().toString().trim().isEmpty()) {
            finish();
        } else {
            etSearch.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        if (backInvokedCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        super.onDestroy();
    }
}
