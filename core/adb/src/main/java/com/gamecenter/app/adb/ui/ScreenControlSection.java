package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;
import com.gamecenter.app.adb.ScrcpySession;

import java.lang.ref.WeakReference;

/**
 * Screen control section: scrcpy-based screen mirroring and control.
 * Manages SurfaceView lifecycle and touch/key control dispatch.
 */
public final class ScreenControlSection extends BaseSection {

    private SurfaceView surfaceView;
    private View placeholder;
    private TextView toggleBtn, screenshotBtn, fullscreenBtn;
    private TextView backBtn, homeBtn, recentBtn, rotateBtn;
    private TextView resolutionValue, bitrateValue;
    private FrameLayout surfaceContainer;

    private ScrcpySession scrcpy;
    private boolean running;
    private String currentResolution = "1080";
    private String currentBitrate = "8 Mbps";

    @Override
    public View createView(Activity activity) {
        activityRef = new WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_screen_control, null);

        surfaceContainer = view.findViewById(R.id.adb_screen_surface_container);
        surfaceView = view.findViewById(R.id.adb_screen_surface);
        placeholder = view.findViewById(R.id.adb_screen_placeholder);

        toggleBtn = view.findViewById(R.id.adb_screen_toggle);
        screenshotBtn = view.findViewById(R.id.adb_screen_screenshot);
        fullscreenBtn = view.findViewById(R.id.adb_screen_fullscreen);
        backBtn = view.findViewById(R.id.adb_screen_back);
        homeBtn = view.findViewById(R.id.adb_screen_home);
        recentBtn = view.findViewById(R.id.adb_screen_recent);
        rotateBtn = view.findViewById(R.id.adb_screen_rotate);
        resolutionValue = view.findViewById(R.id.adb_screen_resolution_value);
        bitrateValue = view.findViewById(R.id.adb_screen_bitrate_value);

        toggleBtn.setOnClickListener(v -> toggleScrcpy());
        screenshotBtn.setOnClickListener(v -> takeScreenshot());
        fullscreenBtn.setOnClickListener(v -> toggleFullscreen());
        backBtn.setOnClickListener(v -> sendKey(ScrcpySession.KEY_BACK));
        homeBtn.setOnClickListener(v -> sendKey(ScrcpySession.KEY_HOME));
        recentBtn.setOnClickListener(v -> sendKey(ScrcpySession.KEY_RECENT));
        rotateBtn.setOnClickListener(v -> toggleRotation());

        resolutionValue.setOnClickListener(v -> showResolutionDialog());
        bitrateValue.setOnClickListener(v -> showBitrateDialog());

        setupSurfaceView();
        return view;
    }

    private void setupSurfaceView() {
        if (surfaceView == null) return;
        surfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(android.view.SurfaceHolder holder) {
                if (running && scrcpy != null) {
                    scrcpy.setSurface(holder.getSurface());
                }
            }
            @Override
            public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) {
            }
            @Override
            public void surfaceDestroyed(android.view.SurfaceHolder holder) {
                if (scrcpy != null) scrcpy.setSurface(null);
            }
        });
    }

    private void toggleScrcpy() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        if (running) {
            stopScrcpy();
        } else {
            startScrcpy();
        }
    }

    private void startScrcpy() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_screen_no_device));
            return;
        }
        showBottomMessage(act.getString(R.string.adb_screen_starting));

        int width = Integer.parseInt(currentResolution);
        int bitrateMbps = Integer.parseInt(currentBitrate.split(" ")[0]);
        int bitrate = bitrateMbps * 1024 * 1024;

        scrcpy = engine().startScrcpy(selected.id, width, bitrate);
        if (scrcpy != null) {
            running = true;
            toggleBtn.setText(R.string.adb_screen_stop);
            surfaceView.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
            if (surfaceView.getHolder().getSurface() != null && surfaceView.getHolder().getSurface().isValid()) {
                scrcpy.setSurface(surfaceView.getHolder().getSurface());
            }
            scrcpy.start();
        } else {
            showBottomMessage("启动投屏失败");
        }
    }

    private void stopScrcpy() {
        running = false;
        if (scrcpy != null) {
            scrcpy.stop();
            scrcpy = null;
        }
        // This section may never have been selected. Activity teardown still visits every
        // section, so its view references are legitimately absent in that case.
        if (toggleBtn != null) {
            toggleBtn.setText(R.string.adb_screen_start);
        }
        if (surfaceView != null) {
            surfaceView.setVisibility(View.GONE);
        }
        if (placeholder != null) {
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    private void takeScreenshot() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_screen_no_device));
            return;
        }
        // Create a temporary file URI for screenshot save
        try {
            java.io.File tempFile = java.io.File.createTempFile("adb_screenshot_", ".png", act.getExternalCacheDir());
            android.net.Uri outputUri = android.net.Uri.fromFile(tempFile);
            engine().screenshot(selected.id, outputUri);
            showBottomMessage("截图保存中…");
        } catch (Exception e) {
            showBottomMessage("截图失败：" + e.getMessage());
        }
    }

    private void toggleFullscreen() {
        Activity act = activity();
        if (act == null) return;
        int flags = act.getWindow().getAttributes().flags;
        boolean isFullscreen = (flags & android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
        if (isFullscreen) {
            act.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            act.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void sendKey(int key) {
        Activity act = activity();
        if (act == null || scrcpy == null) return;
        scrcpy.sendKey(key);
    }

    private void toggleRotation() {
        boolean isLandscape = "landscape".equals(rotateBtn.getTag());
        if (isLandscape) {
            rotateBtn.setText(R.string.adb_screen_landscape);
            rotateBtn.setTag("portrait");
        } else {
            rotateBtn.setText(R.string.adb_screen_portrait);
            rotateBtn.setTag("landscape");
        }
        if (scrcpy != null) scrcpy.setRotation(!isLandscape);
    }

    private void showResolutionDialog() {
        Activity act = activity();
        if (act == null) return;
        String[] options = {"720", "1080", "1440", "2160"};
        int checked = 1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(currentResolution)) { checked = i; break; }
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_screen_resolution)
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    currentResolution = options[which];
                    resolutionValue.setText(options[which]);
                })
                .setPositiveButton(R.string.adb_ok, null)
                .show();
    }

    private void showBitrateDialog() {
        Activity act = activity();
        if (act == null) return;
        String[] options = {"2 Mbps", "4 Mbps", "8 Mbps", "15 Mbps", "25 Mbps"};
        int checked = 2;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(currentBitrate)) { checked = i; break; }
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_screen_bitrate)
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    currentBitrate = options[which];
                    bitrateValue.setText(options[which]);
                })
                .setPositiveButton(R.string.adb_ok, null)
                .show();
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
    }

    @Override
    protected void onEngineUnbound() {
        stopScrcpy();
    }

    @Override
    public void onDestroy() {
        stopScrcpy();
        activityRef = null;
    }
}
