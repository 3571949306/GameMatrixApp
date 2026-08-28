package com.gamecenter.app.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GnssStatus;
import android.util.TypedValue;
import android.view.View;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small, local-only polar sky plot for the GNSS status tool.
 *
 * <p>Azimuth maps around the circle (north at top) and elevation maps from the rim to the
 * centre. It renders only values supplied by {@link GnssStatus}; no position, map, or network
 * data is requested or retained.</p>
 */
public final class SatelliteSkyView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint satellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint usedRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private List<SatelliteSnapshot.Satellite> satellites = Collections.emptyList();
    private boolean scanning;

    public SatelliteSkyView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        int primaryText = resolveThemeColor(android.R.attr.textColorPrimary, Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(withAlpha(primaryText, 68));

        labelPaint.setColor(withAlpha(primaryText, 205));
        labelPaint.setTextSize(dp(11f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        satellitePaint.setStyle(Paint.Style.FILL);
        usedRingPaint.setStyle(Paint.Style.STROKE);
        usedRingPaint.setStrokeWidth(dp(1.8f));
        usedRingPaint.setColor(withAlpha(primaryText, 225));
    }

    public void setSnapshot(SatelliteSnapshot.Summary summary) {
        satellites = summary == null
                ? Collections.<SatelliteSnapshot.Satellite>emptyList()
                : new ArrayList<>(summary.getSatellites());
        updateContentDescription();
        invalidate();
    }

    public void setScanning(boolean scanning) {
        this.scanning = scanning;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float labelSpace = dp(20f);
        float radius = Math.max(dp(26f), Math.min(width, height - labelSpace) / 2f - dp(14f));
        float centreX = width / 2f;
        float centreY = (height - labelSpace) / 2f + dp(8f);

        canvas.drawCircle(centreX, centreY, radius, gridPaint);
        canvas.drawCircle(centreX, centreY, radius * (2f / 3f), gridPaint);
        canvas.drawCircle(centreX, centreY, radius / 3f, gridPaint);
        canvas.drawLine(centreX - radius, centreY, centreX + radius, centreY, gridPaint);
        canvas.drawLine(centreX, centreY - radius, centreX, centreY + radius, gridPaint);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("N", centreX, centreY - radius - dp(6f), labelPaint);
        canvas.drawText("S", centreX, centreY + radius + dp(15f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("W", centreX - radius - dp(7f), centreY + dp(4f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("E", centreX + radius + dp(7f), centreY + dp(4f), labelPaint);

        if (satellites.isEmpty()) {
            drawWaitingState(canvas, centreX, centreY);
            return;
        }

        for (SatelliteSnapshot.Satellite satellite : satellites) {
            drawSatellite(canvas, centreX, centreY, radius, satellite);
        }
    }

    private void drawWaitingState(Canvas canvas, float centreX, float centreY) {
        satellitePaint.setColor(scanning ? 0xFF5D9CFF : 0xFF8E9AAF);
        float dotRadius = dp(4f);
        canvas.drawCircle(centreX - dp(14f), centreY, dotRadius, satellitePaint);
        canvas.drawCircle(centreX, centreY, dotRadius, satellitePaint);
        canvas.drawCircle(centreX + dp(14f), centreY, dotRadius, satellitePaint);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(13f));
        canvas.drawText(getContext().getString(R.string.tool_satellite_sky_waiting),
                centreX, centreY + dp(31f), labelPaint);
        labelPaint.setTextSize(dp(11f));
    }

    private void drawSatellite(
            Canvas canvas,
            float centreX,
            float centreY,
            float radius,
            SatelliteSnapshot.Satellite satellite
    ) {
        float elevation = clamp(satellite.elevationDegrees, 0f, 90f);
        float azimuthRadians = (float) Math.toRadians(satellite.azimuthDegrees - 90f);
        float distance = radius * ((90f - elevation) / 90f);
        float x = centreX + (float) Math.cos(azimuthRadians) * distance;
        float y = centreY + (float) Math.sin(azimuthRadians) * distance;

        float signalScale = clamp(satellite.cn0DbHz / 45f, 0f, 1f);
        float dotRadius = dp(4f + signalScale * 2.6f);
        satellitePaint.setColor(colorForConstellation(satellite.constellationType));
        if (satellite.usedInFix) {
            canvas.drawCircle(x, y, dotRadius + dp(3f), usedRingPaint);
        }
        canvas.drawCircle(x, y, dotRadius, satellitePaint);

        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(x < centreX ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float labelX = x + (x < centreX ? -dp(7f) : dp(7f));
        canvas.drawText(String.valueOf(satellite.svid), labelX, y + dp(3f), labelPaint);
        labelPaint.setTextSize(dp(11f));
    }

    private void updateContentDescription() {
        if (satellites.isEmpty()) {
            setContentDescription(getContext().getString(R.string.tool_satellite_sky_waiting));
        } else {
            setContentDescription(getContext().getString(
                    R.string.tool_satellite_sky_content_description, satellites.size()));
        }
    }

    private int colorForConstellation(int constellationType) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                return 0xFF4F8CFF;
            case GnssStatus.CONSTELLATION_BEIDOU:
                return 0xFFFF9F43;
            case GnssStatus.CONSTELLATION_GLONASS:
                return 0xFF44B78B;
            case GnssStatus.CONSTELLATION_GALILEO:
                return 0xFFB386FF;
            case GnssStatus.CONSTELLATION_QZSS:
                return 0xFFFF6F91;
            case GnssStatus.CONSTELLATION_IRNSS:
                return 0xFF2EB8C7;
            case GnssStatus.CONSTELLATION_SBAS:
                return 0xFF8F9BB3;
            default:
                return 0xFF89A7D8;
        }
    }

    private int resolveThemeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            try {
                return getContext().getColor(value.resourceId);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return value.data != 0 ? value.data : fallback;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * density;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
