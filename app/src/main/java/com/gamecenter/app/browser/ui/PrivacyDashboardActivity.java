package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserSettingsManager;
import com.gamecenter.app.browser.security.BrowserTrackerBlocker;
import com.gamecenter.app.browser.security.BrowserTrackerStats;

import java.util.List;
import java.util.Map;

/**
 * 隐私仪表盘（P1-2）。
 *
 * <p>展示追踪拦截统计：</p>
 * <ul>
 *   <li>累计拦截数 / 本次会话拦截数</li>
 *   <li>内置规则总数</li>
 *   <li>Top 域名分布（按拦截次数倒序）</li>
 *   <li>追踪保护开关（同步到 BrowserTrackerBlocker + BrowserSettingsManager）</li>
 *   <li>重置统计按钮</li>
 * </ul>
 */
public class PrivacyDashboardActivity extends AppCompatActivity {

    private static final int TOP_DOMAINS_LIMIT = 20;

    private RecyclerView rvDomains;
    private View emptyView;
    private TextView tvTotalBlocked;
    private TextView tvSessionBlocked;
    private TextView tvRuleCount;
    private com.google.android.material.materialswitch.MaterialSwitch switchProtection;
    private TrackerDomainAdapter adapter;
    private OnBackInvokedCallback backInvokedCallback;

    public static void start(Context context) {
        context.startActivity(new Intent(context, PrivacyDashboardActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_dashboard);
        initViews();
        setupRecyclerView();
        setupListeners();
        refreshStats();
        registerPredictiveBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = () -> finish();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backInvokedCallback
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
        }
        super.onDestroy();
    }

    private void initViews() {
        rvDomains = findViewById(R.id.rv_domains);
        emptyView = findViewById(R.id.empty_view);
        tvTotalBlocked = findViewById(R.id.tv_total_blocked);
        tvSessionBlocked = findViewById(R.id.tv_session_blocked);
        tvRuleCount = findViewById(R.id.tv_rule_count);
        switchProtection = findViewById(R.id.switch_tracker_protection);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnReset = findViewById(R.id.btn_reset);
        btnBack.setOnClickListener(v -> finish());
        btnReset.setOnClickListener(v -> confirmReset());
    }

    private void setupRecyclerView() {
        adapter = new TrackerDomainAdapter();
        rvDomains.setLayoutManager(new LinearLayoutManager(this));
        rvDomains.setHasFixedSize(false);
        rvDomains.setAdapter(adapter);
    }

    private void setupListeners() {
        switchProtection.setChecked(
                BrowserSettingsManager.getInstance(this).isTrackerProtectionEnabled());
        switchProtection.setOnCheckedChangeListener((button, checked) -> {
            BrowserSettingsManager.getInstance(this).setTrackerProtectionEnabled(checked);
            Toast.makeText(this,
                    checked ? R.string.browser_privacy_protection_on
                            : R.string.browser_privacy_protection_off,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshStats() {
        BrowserTrackerStats stats = BrowserTrackerStats.getInstance(this);
        BrowserTrackerBlocker blocker = BrowserTrackerBlocker.getInstance();

        int total = stats.getTotalBlocked();
        int session = stats.getSessionBlocked();
        tvTotalBlocked.setText(String.valueOf(total));
        tvSessionBlocked.setText(String.valueOf(session));
        tvRuleCount.setText(getString(R.string.browser_privacy_rule_count, blocker.getBuiltinRuleCount()));

        List<Map.Entry<String, Integer>> topDomains = stats.getTopDomains(TOP_DOMAINS_LIMIT);
        adapter.submit(topDomains);

        if (topDomains.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvDomains.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvDomains.setVisibility(View.VISIBLE);
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_privacy_reset_confirm_title)
                .setMessage(R.string.browser_privacy_reset_confirm_message)
                .setPositiveButton(R.string.browser_privacy_reset_confirm,
                        (d, w) -> doReset())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doReset() {
        BrowserTrackerStats.getInstance(this).reset();
        refreshStats();
        Toast.makeText(this, R.string.browser_privacy_reset_done, Toast.LENGTH_SHORT).show();
    }

    /** 域名列表适配器 */
    private static class TrackerDomainAdapter extends RecyclerView.Adapter<TrackerDomainAdapter.VH> {
        private List<Map.Entry<String, Integer>> data = java.util.Collections.emptyList();

        void submit(@NonNull List<Map.Entry<String, Integer>> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tracker_domain, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map.Entry<String, Integer> entry = data.get(position);
            holder.tvDomain.setText(entry.getKey());
            holder.tvCount.setText(holder.itemView.getContext()
                    .getString(R.string.browser_privacy_domain_count, entry.getValue()));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvDomain;
            final TextView tvCount;
            VH(@NonNull View v) {
                super(v);
                tvDomain = v.findViewById(R.id.tv_domain);
                tvCount = v.findViewById(R.id.tv_count);
            }
        }
    }
}
