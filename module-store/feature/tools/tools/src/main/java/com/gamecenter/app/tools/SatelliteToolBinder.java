package com.gamecenter.app.tools;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gamecenter.app.R;
import com.gamecenter.app.fragments.ToolsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Local GNSS satellite-status tool.
 *
 * <p>It deliberately registers only {@link GnssStatus.Callback}; it never requests location
 * updates, reads coordinates, writes a history, or sends data over the network. Android requires
 * foreground precise location permission for this callback, so permission is requested only after
 * the user taps "Start scan".</p>
 */
public final class SatelliteToolBinder implements ToolBinder {

    private static final String TAG = "SatelliteToolBinder";
    private static final String EMPTY_VALUE = "—";

    private enum SettingsDestination {
        NONE,
        LOCATION_SOURCE,
        APPLICATION_DETAILS
    }

    private Context appContext;
    private View rootView;
    private FrameLayout skyContainer;
    private SatelliteSkyView skyView;
    private TextView stateView;
    private TextView statusView;
    private TextView scanButton;
    private TextView settingsButton;
    private TextView visibleValueView;
    private TextView usedValueView;
    private TextView signalValueView;
    private TextView fixView;
    private TextView constellationsView;
    private LinearLayout detailsContainer;

    private LocationManager locationManager;
    private Handler callbackHandler;
    private GnssStatus.Callback gnssStatusCallback;
    private View.OnAttachStateChangeListener detachListener;
    private SettingsDestination settingsDestination = SettingsDestination.NONE;
    private boolean bound;
    private boolean listening;
    private SatelliteSnapshot.Summary latestSummary = emptySummary();

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        unbind();
        if (context == null || contentView == null) {
            return;
        }

        bound = true;
        appContext = context.getApplicationContext();
        rootView = contentView;
        skyContainer = contentView.findViewById(R.id.fl_satellite_sky);
        stateView = contentView.findViewById(R.id.tv_satellite_state);
        statusView = contentView.findViewById(R.id.tv_satellite_status);
        scanButton = contentView.findViewById(R.id.btn_satellite_scan);
        settingsButton = contentView.findViewById(R.id.btn_satellite_settings);
        visibleValueView = contentView.findViewById(R.id.tv_satellite_visible_value);
        usedValueView = contentView.findViewById(R.id.tv_satellite_used_value);
        signalValueView = contentView.findViewById(R.id.tv_satellite_signal_value);
        fixView = contentView.findViewById(R.id.tv_satellite_fix);
        constellationsView = contentView.findViewById(R.id.tv_satellite_constellations);
        detailsContainer = contentView.findViewById(R.id.ll_satellite_details);

        if (skyContainer != null) {
            skyView = new SatelliteSkyView(context);
            skyContainer.removeAllViews();
            skyContainer.addView(skyView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        if (scanButton != null) {
            scanButton.setOnClickListener(view -> {
                if (listening) {
                    stopListening();
                    renderStoppedState();
                } else {
                    startAfterPermissionCheck();
                }
            });
        }
        if (settingsButton != null) {
            settingsButton.setOnClickListener(view -> openRequestedSettings());
        }

        detachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                // Scanning remains user initiated; never request a permission merely on attach.
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                unbind();
            }
        };
        contentView.addOnAttachStateChangeListener(detachListener);
        renderReadyState();
    }

    private void startAfterPermissionCheck() {
        if (!bound || appContext == null) {
            return;
        }
        if (hasFineLocationPermission()) {
            startListening();
            return;
        }

        if (statusView != null) {
            statusView.setText(R.string.tool_satellite_requesting_permission);
        }
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_permission_required);
        }
        if (scanButton != null) {
            scanButton.setEnabled(false);
        }

        Object tag = rootView == null ? null : rootView.getTag(R.id.tag_tools_fragment);
        if (!(tag instanceof ToolsFragment)) {
            if (scanButton != null) {
                scanButton.setEnabled(true);
            }
            renderUnavailableState(R.string.tool_satellite_permission_bridge_unavailable);
            return;
        }

        ((ToolsFragment) tag).requestSatelliteLocationPermission(granted -> {
            if (!bound) {
                return;
            }
            if (scanButton != null) {
                scanButton.setEnabled(true);
            }
            if (granted) {
                startListening();
            } else {
                renderPermissionDeniedState();
            }
        });
    }

    private void startListening() {
        if (!bound || listening || appContext == null) {
            return;
        }
        if (!hasFineLocationPermission()) {
            renderPermissionDeniedState();
            return;
        }
        if (!hasGnssHardware()) {
            renderUnavailableState(R.string.tool_satellite_hardware_unavailable);
            return;
        }

        locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            renderUnavailableState(R.string.tool_satellite_service_unavailable);
            return;
        }
        if (!isLocationServiceEnabled(locationManager)) {
            renderLocationDisabledState();
            return;
        }

        callbackHandler = new Handler(Looper.getMainLooper());
        ensureGnssStatusCallback();
        try {
            boolean registered = locationManager.registerGnssStatusCallback(
                    gnssStatusCallback, callbackHandler);
            if (!registered) {
                renderUnavailableState(R.string.tool_satellite_callback_unavailable);
                return;
            }
            listening = true;
            renderWaitingForSignalState();
        } catch (SecurityException securityException) {
            Log.w(TAG, "GNSS callback rejected after permission check", securityException);
            renderPermissionDeniedState();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to register GNSS callback", exception);
            renderUnavailableState(R.string.tool_satellite_callback_unavailable);
        }
    }

    private void ensureGnssStatusCallback() {
        if (gnssStatusCallback != null) {
            return;
        }
        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                if (bound && listening) {
                    renderWaitingForSignalState();
                }
            }

            @Override
            public void onStopped() {
                if (bound && listening) {
                    if (stateView != null) {
                        stateView.setText(R.string.tool_satellite_state_scanning);
                    }
                    if (statusView != null) {
                        statusView.setText(R.string.tool_satellite_system_stopped);
                    }
                    showSettings(SettingsDestination.LOCATION_SOURCE);
                }
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                if (bound && listening && fixView != null) {
                    fixView.setText(rootView.getContext().getString(
                            R.string.tool_satellite_first_fix_format, ttffMillis / 1000f));
                }
            }

            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                if (!bound || !listening || status == null) {
                    return;
                }
                renderStatus(status);
            }
        };
    }

    private void renderStatus(GnssStatus status) {
        List<SatelliteSnapshot.Satellite> satellites = new ArrayList<>();
        try {
            int count = status.getSatelliteCount();
            for (int index = 0; index < count; index++) {
                satellites.add(new SatelliteSnapshot.Satellite(
                        status.getSvid(index),
                        status.getConstellationType(index),
                        status.getCn0DbHz(index),
                        status.getElevationDegrees(index),
                        status.getAzimuthDegrees(index),
                        status.usedInFix(index),
                        status.hasEphemerisData(index),
                        status.hasAlmanacData(index)
                ));
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "GNSS framework returned an unreadable status", exception);
            return;
        }

        latestSummary = SatelliteSnapshot.summarize(satellites);
        renderSummary(latestSummary);
    }

    private void renderReadyState() {
        latestSummary = emptySummary();
        renderSummaryValues(latestSummary);
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_ready);
        }
        if (statusView != null) {
            statusView.setText(R.string.tool_satellite_ready_message);
        }
        if (fixView != null) {
            fixView.setText(R.string.tool_satellite_fix_waiting);
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_start_scan);
            scanButton.setEnabled(true);
        }
        hideSettings();
        if (skyView != null) {
            skyView.setScanning(false);
        }
    }

    private void renderWaitingForSignalState() {
        if (!bound) {
            return;
        }
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_scanning);
        }
        if (statusView != null) {
            statusView.setText(R.string.tool_satellite_waiting_signal);
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_stop_scan);
            scanButton.setEnabled(true);
        }
        hideSettings();
        if (skyView != null) {
            skyView.setScanning(true);
        }
    }

    private void renderSummary(SatelliteSnapshot.Summary summary) {
        if (!bound || summary == null) {
            return;
        }
        renderSummaryValues(summary);
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_scanning);
        }
        if (statusView != null) {
            if (summary.getVisibleCount() == 0) {
                statusView.setText(R.string.tool_satellite_waiting_signal);
            } else {
                statusView.setText(rootView.getContext().getString(
                        R.string.tool_satellite_scanning_format,
                        summary.getVisibleCount(), summary.getUsedInFixCount()));
            }
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_stop_scan);
            scanButton.setEnabled(true);
        }
        hideSettings();
        if (skyView != null) {
            skyView.setScanning(true);
        }
    }

    private void renderSummaryValues(SatelliteSnapshot.Summary summary) {
        if (visibleValueView != null) {
            visibleValueView.setText(summary.getVisibleCount() == 0
                    ? EMPTY_VALUE : String.valueOf(summary.getVisibleCount()));
        }
        if (usedValueView != null) {
            usedValueView.setText(summary.getVisibleCount() == 0
                    ? EMPTY_VALUE : String.valueOf(summary.getUsedInFixCount()));
        }
        if (signalValueView != null) {
            signalValueView.setText(summary.getSignalCount() == 0
                    ? EMPTY_VALUE
                    : rootView.getContext().getString(
                            R.string.tool_satellite_signal_value_format,
                            summary.getAverageCn0DbHz()));
        }
        if (skyView != null) {
            skyView.setSnapshot(summary);
        }
        renderConstellations(summary.getCountByConstellation());
        renderDetails(summary.getSatellites());
    }

    private void renderConstellations(Map<Integer, Integer> counts) {
        if (constellationsView == null) {
            return;
        }
        if (counts == null || counts.isEmpty()) {
            constellationsView.setText(R.string.tool_satellite_constellation_empty);
            return;
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (result.length() > 0) {
                result.append("   ·   ");
            }
            result.append(constellationName(entry.getKey())).append(' ').append(entry.getValue());
        }
        constellationsView.setText(result.toString());
    }

    private void renderDetails(List<SatelliteSnapshot.Satellite> satellites) {
        if (detailsContainer == null || rootView == null) {
            return;
        }
        detailsContainer.removeAllViews();
        if (satellites == null || satellites.isEmpty()) {
            TextView empty = new TextView(rootView.getContext());
            empty.setText(R.string.tool_satellite_details_empty);
            empty.setTextSize(13f);
            empty.setTextColor(resolveThemeColor(android.R.attr.textColorSecondary, 0xFF8E9AAF));
            empty.setPadding(0, dp(6), 0, dp(4));
            detailsContainer.addView(empty);
            return;
        }

        for (int index = 0; index < satellites.size(); index++) {
            if (index > 0) {
                View divider = new View(rootView.getContext());
                divider.setBackgroundColor(withAlpha(
                        resolveThemeColor(android.R.attr.textColorPrimary, 0xFF7E899F), 38));
                detailsContainer.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
            }
            detailsContainer.addView(createSatelliteDetailRow(satellites.get(index)));
        }
    }

    private View createSatelliteDetailRow(SatelliteSnapshot.Satellite satellite) {
        LinearLayout row = new LinearLayout(rootView.getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView title = new TextView(rootView.getContext());
        title.setText(rootView.getContext().getString(
                R.string.tool_satellite_detail_title_format,
                constellationName(satellite.constellationType),
                satellite.svid,
                rootView.getContext().getString(satellite.usedInFix
                        ? R.string.tool_satellite_used_in_fix
                        : R.string.tool_satellite_not_used_in_fix)));
        title.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.WHITE));
        title.setTextSize(14f);
        if (satellite.usedInFix) {
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        }
        row.addView(title);

        TextView metrics = new TextView(rootView.getContext());
        metrics.setText(rootView.getContext().getString(
                R.string.tool_satellite_detail_metrics_format,
                satellite.cn0DbHz,
                satellite.elevationDegrees,
                satellite.azimuthDegrees));
        metrics.setTextColor(resolveThemeColor(android.R.attr.textColorSecondary, 0xFF8E9AAF));
        metrics.setTextSize(12f);
        metrics.setPadding(0, dp(3), 0, 0);
        row.addView(metrics);
        return row;
    }

    private void renderStoppedState() {
        if (!bound) {
            return;
        }
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_ready);
        }
        if (statusView != null) {
            statusView.setText(R.string.tool_satellite_stopped);
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_start_scan);
            scanButton.setEnabled(true);
        }
        hideSettings();
        if (skyView != null) {
            skyView.setScanning(false);
        }
    }

    private void renderPermissionDeniedState() {
        stopListening();
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_permission_required);
        }
        if (statusView != null) {
            statusView.setText(R.string.tool_satellite_permission_denied);
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_start_scan);
            scanButton.setEnabled(true);
        }
        showSettings(SettingsDestination.APPLICATION_DETAILS);
        if (skyView != null) {
            skyView.setScanning(false);
        }
    }

    private void renderLocationDisabledState() {
        renderUnavailableState(R.string.tool_satellite_location_disabled);
        showSettings(SettingsDestination.LOCATION_SOURCE);
    }

    private void renderUnavailableState(int messageRes) {
        stopListening();
        if (stateView != null) {
            stateView.setText(R.string.tool_satellite_state_unavailable);
        }
        if (statusView != null) {
            statusView.setText(messageRes);
        }
        if (scanButton != null) {
            scanButton.setText(R.string.tool_satellite_start_scan);
            scanButton.setEnabled(true);
        }
        hideSettings();
        if (skyView != null) {
            skyView.setScanning(false);
        }
    }

    private void showSettings(SettingsDestination destination) {
        settingsDestination = destination;
        if (settingsButton == null) {
            return;
        }
        settingsButton.setText(destination == SettingsDestination.APPLICATION_DETAILS
                ? R.string.tool_satellite_open_app_settings
                : R.string.tool_satellite_open_location_settings);
        settingsButton.setVisibility(destination == SettingsDestination.NONE
                ? View.GONE : View.VISIBLE);
    }

    private void hideSettings() {
        settingsDestination = SettingsDestination.NONE;
        if (settingsButton != null) {
            settingsButton.setVisibility(View.GONE);
        }
    }

    private void openRequestedSettings() {
        if (appContext == null || settingsDestination == SettingsDestination.NONE) {
            return;
        }
        try {
            Intent intent;
            if (settingsDestination == SettingsDestination.APPLICATION_DETAILS) {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", appContext.getPackageName(), null));
            } else {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to open location settings", exception);
            renderUnavailableState(R.string.tool_satellite_settings_unavailable);
        }
    }

    private boolean hasFineLocationPermission() {
        return appContext != null
                && appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasGnssHardware() {
        return appContext != null
                && appContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    private boolean isLocationServiceEnabled(LocationManager manager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return manager.isLocationEnabled();
            }
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read system location state", exception);
            return false;
        }
    }

    private String constellationName(int constellationType) {
        if (rootView == null) {
            return "";
        }
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_gps);
            case GnssStatus.CONSTELLATION_BEIDOU:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_beidou);
            case GnssStatus.CONSTELLATION_GLONASS:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_glonass);
            case GnssStatus.CONSTELLATION_GALILEO:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_galileo);
            case GnssStatus.CONSTELLATION_QZSS:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_qzss);
            case GnssStatus.CONSTELLATION_IRNSS:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_irnss);
            case GnssStatus.CONSTELLATION_SBAS:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_sbas);
            default:
                return rootView.getContext().getString(R.string.tool_satellite_constellation_other);
        }
    }

    private int resolveThemeColor(int attribute, int fallback) {
        if (rootView == null) {
            return fallback;
        }
        TypedValue value = new TypedValue();
        if (!rootView.getContext().getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            try {
                return rootView.getContext().getColor(value.resourceId);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return value.data != 0 ? value.data : fallback;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * rootView.getResources().getDisplayMetrics().density);
    }

    private static SatelliteSnapshot.Summary emptySummary() {
        return SatelliteSnapshot.summarize(Collections.<SatelliteSnapshot.Satellite>emptyList());
    }

    private void stopListening() {
        if (locationManager != null && gnssStatusCallback != null && listening) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to unregister GNSS callback", exception);
            }
        }
        listening = false;
    }

    /** Idempotently releases the GNSS callback and every View reference. */
    public void unbind() {
        bound = false;
        stopListening();
        if (rootView != null && detachListener != null) {
            rootView.removeOnAttachStateChangeListener(detachListener);
        }
        if (skyContainer != null) {
            skyContainer.removeAllViews();
        }
        appContext = null;
        rootView = null;
        skyContainer = null;
        skyView = null;
        stateView = null;
        statusView = null;
        scanButton = null;
        settingsButton = null;
        visibleValueView = null;
        usedValueView = null;
        signalValueView = null;
        fixView = null;
        constellationsView = null;
        detailsContainer = null;
        locationManager = null;
        callbackHandler = null;
        gnssStatusCallback = null;
        detachListener = null;
        settingsDestination = SettingsDestination.NONE;
        latestSummary = emptySummary();
    }
}
