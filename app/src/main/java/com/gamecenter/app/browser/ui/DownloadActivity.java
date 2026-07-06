package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.BrowserDownloadManager;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载记录页面。
 */
public class DownloadActivity extends AppCompatActivity {

    private RecyclerView rvDownloads;
    private View emptyView;
    private DownloadAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public static void start(Context context) {
        context.startActivity(new Intent(context, DownloadActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        ImageButton btnBack = findViewById(R.id.btn_back);
        rvDownloads = findViewById(R.id.rv_downloads);
        emptyView = findViewById(R.id.empty_view);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        adapter = new DownloadAdapter();
        rvDownloads.setLayoutManager(new LinearLayoutManager(this));
        rvDownloads.setAdapter(adapter);

        adapter.setOnItemClickListener(this::openFile);
        adapter.setOnDeleteClickListener(this::confirmAndDelete);

        loadDownloads();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
    }

    private void loadDownloads() {
        io.execute(() -> {
            List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(this).downloadDao().getAllDownloads();
            runOnUiThread(() -> {
                adapter.setData(list);
                if (list == null || list.isEmpty()) {
                    rvDownloads.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    rvDownloads.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                }
            });
        });
    }

    private void openFile(BrowserDownloadEntity item) {
        if (item.getStatus() != BrowserDownloadEntity.STATUS_COMPLETED) {
            Toast.makeText(this, R.string.browser_download_file_not_complete, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = null;
        android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null && item.getSystemDownloadId() != -1) {
            uri = downloadManager.getUriForDownloadedFile(item.getSystemDownloadId());
        }

        if (uri == null && item.getFilePath() != null && !item.getFilePath().isEmpty()) {
            String path = item.getFilePath();
            if (path.startsWith("content://")) {
                uri = Uri.parse(path);
            } else {
                String realPath = path;
                if (path.startsWith("file://")) {
                    realPath = Uri.parse(path).getPath();
                }
                if (realPath != null) {
                    File file = new File(realPath);
                    if (file.exists()) {
                        try {
                            uri = androidx.core.content.FileProvider.getUriForFile(
                                    this,
                                    getPackageName() + ".browser.fileprovider",
                                    file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        if (uri == null) {
            Toast.makeText(this, R.string.browser_download_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, item.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.browser_download_file_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndDelete(BrowserDownloadEntity item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_download_delete_title)
                .setMessage(R.string.browser_download_delete_message)
                .setPositiveButton(R.string.browser_download_delete_record_and_file, (d, w) -> {
                    BrowserDownloadManager.getInstance(this).deleteDownloadWithFile(item.getId());
                    loadDownloads();
                })
                .setNeutralButton(R.string.browser_download_delete_record_only, (d, w) -> {
                    BrowserDownloadManager.getInstance(this).deleteDownload(item.getId());
                    loadDownloads();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
