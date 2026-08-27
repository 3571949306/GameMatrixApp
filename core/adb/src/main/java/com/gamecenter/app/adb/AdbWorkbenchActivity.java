package com.gamecenter.app.adb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.adb.ui.Section;
import com.gamecenter.app.adb.ui.*;

import java.lang.ref.WeakReference;

/**
 * Main ADB workbench Activity. Runs in private :adb process.
 * Manages tab switching, service binding, and section content hosting.
 * No Activity or View references are retained by the engine/session layer.
 */
public final class AdbWorkbenchActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE = "source";
    public static final String SOURCE_HALL = "hall_avatar";
    public static final String SOURCE_TOOLS = "tools";

    private AdbSessionService service;
    private boolean bound;
    private boolean observing;
    private boolean leaving;
    private final WeakBinding binding = new WeakBinding(this);
    // The service outlives a configuration change while work is active, so remove this
    // listener before unbinding instead of letting it retain the old Activity.
    private final AdbSessionService.Listener engineListener = this::onEngineChanged;

    private FrameLayout contentContainer;
    private TextView deviceStatus;
    private TextView btnDisconnect;
    private TextView bottomStatus;

    private Section currentSection;
    private final Section[] sections = new Section[7];
    private int currentTabIndex;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adb_workbench);

        contentContainer = findViewById(R.id.adb_content_container);
        deviceStatus = findViewById(R.id.adb_device_status);
        btnDisconnect = findViewById(R.id.adb_btn_disconnect);
        bottomStatus = findViewById(R.id.adb_bottom_status);

        View btnBack = findViewById(R.id.adb_btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> handleBack());

        btnDisconnect.setOnClickListener(v -> {
            if (service != null) {
                AdbEngine.Session session = service.engine().selected();
                if (session != null) service.engine().disconnect(session.id);
            }
        });

        setupTabs();
        initSections();
        switchTab(0);
    }

    private void setupTabs() {
        int[] tabIds = {
                R.id.adb_tab_device, R.id.adb_tab_app, R.id.adb_tab_file,
                R.id.adb_tab_shell, R.id.adb_tab_screen, R.id.adb_tab_system,
                R.id.adb_tab_fastboot
        };
        for (int i = 0; i < tabIds.length; i++) {
            int index = i;
            TextView tab = findViewById(tabIds[i]);
            if (tab != null) {
                tab.setOnClickListener(v -> switchTab(index));
            }
        }
    }

    private void initSections() {
        sections[0] = new DeviceConnectSection();
        sections[1] = new AppManagerSection();
        sections[2] = new FileManagerSection();
        sections[3] = new TerminalSection();
        sections[4] = new ScreenControlSection();
        sections[5] = new SystemSettingsSection();
        sections[6] = new FastbootSection();
    }

    private void switchTab(int index) {
        currentTabIndex = index;
        updateTabSelection();

        if (currentSection != null) currentSection.onUnbind();
        contentContainer.removeAllViews();

        Section section = sections[index];
        View view = section.createView(this);
        contentContainer.addView(view);
        if (service != null) section.onBind(service);
        currentSection = section;
    }

    private void updateTabSelection() {
        int[] tabIds = {
                R.id.adb_tab_device, R.id.adb_tab_app, R.id.adb_tab_file,
                R.id.adb_tab_shell, R.id.adb_tab_screen, R.id.adb_tab_system,
                R.id.adb_tab_fastboot
        };
        for (int i = 0; i < tabIds.length; i++) {
            TextView tab = findViewById(tabIds[i]);
            if (tab == null) continue;
            if (i == currentTabIndex) {
                tab.setTextColor(getColor(R.color.adb_tab_selected));
                tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                tab.setTypeface(null, android.graphics.Typeface.BOLD);
                tab.setBackgroundResource(R.drawable.adb_tab_bg_selected);
            } else {
                tab.setTextColor(getColor(R.color.adb_tab_unselected));
                tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                tab.setTypeface(null, android.graphics.Typeface.NORMAL);
                tab.setBackground(null);
            }
        }
    }

    private void handleBack() {
        if (leaving) return;
        leaving = true;
        if (service != null && service.engine().activeJobs() > 0) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage(R.string.adb_session_leave_confirm)
                    .setPositiveButton(R.string.adb_ok, (d, w) -> finish())
                    .setNegativeButton(R.string.adb_cancel, (d, w) -> { leaving = false; })
                    .setOnDismissListener(d -> { if (!leaving) leaving = false; })
                    .show();
        } else {
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, AdbSessionService.class), binding, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (service != null && observing) {
            service.remove(engineListener);
            observing = false;
        }
        if (bound) {
            unbindService(binding);
            bound = false;
        }
    }

    @Override
    protected void onDestroy() {
        leaving = true;
        if (service != null && observing) {
            service.remove(engineListener);
            observing = false;
        }
        for (Section section : sections) {
            if (section != null) section.onDestroy();
        }
        if (currentSection != null) currentSection.onUnbind();
        super.onDestroy();
    }

    private void onServiceConnected(AdbSessionService svc) {
        service = svc;
        bound = true;
        updateDeviceStatus();
        if (currentSection != null) currentSection.onBind(svc);
        if (!observing) {
            observing = true;
            svc.observe(engineListener);
        }
    }

    private void onServiceDisconnected() {
        service = null;
        bound = false;
        observing = false;
        updateDeviceStatus();
        if (currentSection != null) currentSection.onUnbind();
    }

    private void onEngineChanged() {
        updateDeviceStatus();
        if (currentSection != null && service != null) currentSection.onBind(service);
        String notice = service != null ? service.engine().notice : "";
        bottomStatus.setText(notice);
    }

    private void updateDeviceStatus() {
        if (deviceStatus == null) return;
        if (service == null || service.engine() == null) {
            deviceStatus.setText(R.string.adb_topbar_no_device);
            deviceStatus.setTextColor(getColor(R.color.adb_text_secondary));
            btnDisconnect.setVisibility(View.GONE);
            return;
        }
        AdbEngine.Session selected = service.engine().selected();
        if (selected == null) {
            deviceStatus.setText(R.string.adb_topbar_no_device);
            deviceStatus.setTextColor(getColor(R.color.adb_text_secondary));
            btnDisconnect.setVisibility(View.GONE);
            return;
        }
        deviceStatus.setText(selected.title);
        deviceStatus.setTextColor(getColor(R.color.adb_connected));
        btnDisconnect.setVisibility(View.VISIBLE);
    }

    public AdbEngine engine() {
        return service != null ? service.engine() : null;
    }

    public void showBottomMessage(String message) {
        if (bottomStatus != null) bottomStatus.setText(message);
    }

    public void setDeviceStatusText(CharSequence text, int colorRes) {
        if (deviceStatus != null) {
            deviceStatus.setText(text);
            deviceStatus.setTextColor(getColor(colorRes));
        }
    }

    /** Weak reference to Activity to prevent binding from leaking it. */
    private static final class WeakBinding implements ServiceConnection {
        private final WeakReference<AdbWorkbenchActivity> ref;

        WeakBinding(AdbWorkbenchActivity activity) {
            this.ref = new WeakReference<>(activity);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AdbWorkbenchActivity activity = ref.get();
            if (activity != null) {
                AdbSessionService.LocalBinder binder = (AdbSessionService.LocalBinder) service;
                activity.onServiceConnected(binder.service());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AdbWorkbenchActivity activity = ref.get();
            if (activity != null) activity.onServiceDisconnected();
        }
    }
}
