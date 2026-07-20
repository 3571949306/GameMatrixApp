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
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;
import com.gamecenter.app.browser.data.repository.BrowserBookmarkRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 收藏夹页面（Room 数据源）。
 */
public class BookmarkActivity extends AppCompatActivity {

    private RecyclerView rvBookmarks;
    private EditText etSearch;
    private View emptyView;
    private TextView tvCountHeader;
    private BrowserBookmarkRepository bookmarkRepository;
    private BookmarkAdapter adapter;
    private OnBackInvokedCallback backInvokedCallback;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BookmarkActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark);
        bookmarkRepository = new BrowserBookmarkRepository(getApplication());
        initViews();
        setupRecyclerView();
        setupListeners();
        loadBookmarks();
        registerPredictiveBack();
    }

    /**
     * 注册预测式返回手势回调（API 33+）。
     * <p>API < 33 的设备继续走 onBackPressed() 兼容路径，实现双轨返回逻辑。</p>
     */
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
        rvBookmarks = findViewById(R.id.rv_bookmarks);
        etSearch = findViewById(R.id.et_search);
        emptyView = findViewById(R.id.empty_view);
        tvCountHeader = findViewById(R.id.tv_bookmark_count);
        ImageButton btnBack = findViewById(R.id.btn_back);
        MaterialButton btnAdd = findViewById(R.id.btn_add_bookmark);
        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> showAddDialog());
    }

    private void setupRecyclerView() {
        adapter = new BookmarkAdapter();
        rvBookmarks.setLayoutManager(new LinearLayoutManager(this));
        rvBookmarks.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> BrowserActivity.start(this, item.getUrl()));
        adapter.setOnItemLongClickListener(this::showDeleteDialog);
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

    private void loadBookmarks() {
        bookmarkRepository.getAllBookmarks(items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void performSearch(String keyword) {
        if (keyword.isEmpty()) {
            loadBookmarks();
            return;
        }
        bookmarkRepository.searchBookmarks(keyword, items -> runOnUiThread(() -> {
            adapter.setData(items);
            updateEmptyState(items != null ? items.size() : 0);
        }));
    }

    private void updateEmptyState(int count) {
        if (count == 0) {
            rvBookmarks.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            tvCountHeader.setVisibility(View.GONE);
        } else {
            rvBookmarks.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            tvCountHeader.setVisibility(View.VISIBLE);
            tvCountHeader.setText(count + " \u6761\u6536\u85cf");
        }
    }

    private void deleteItem(BrowserBookmarkEntity item) {
        bookmarkRepository.deleteById(item.getId());
        performSearch(etSearch.getText().toString().trim());
        Toast.makeText(this, R.string.browser_bookmark_deleted, Toast.LENGTH_SHORT).show();
    }

    private void showDeleteDialog(BrowserBookmarkEntity item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_bookmark_delete_title)
                .setMessage(R.string.browser_bookmark_delete_message)
                .setPositiveButton(R.string.browser_bookmark_delete, (d, w) -> deleteItem(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddDialog() {
        AddBookmarkDialog.show(this, null, null, (title, url) -> {
            if (url == null || url.isEmpty()) return;
            bookmarkRepository.isBookmarked(url, isBookmarked -> runOnUiThread(() -> {
                if (isBookmarked) {
                    Toast.makeText(this, R.string.browser_bookmark_already_exists, Toast.LENGTH_SHORT).show();
                    return;
                }
                BrowserBookmarkEntity entity = new BrowserBookmarkEntity();
                entity.setTitle(title != null ? title : "");
                entity.setUrl(url);
                entity.setCreateTime(System.currentTimeMillis());
                entity.setUpdateTime(System.currentTimeMillis());
                bookmarkRepository.insert(entity);
                Toast.makeText(this, R.string.browser_bookmark_added, Toast.LENGTH_SHORT).show();
                loadBookmarks();
            }));
        });
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    /**
     * 统一的返回处理逻辑：搜索框有内容时先清空，否则结束当前页面。
     */
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
