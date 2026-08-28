package com.gamecenter.app.tools;

import android.location.GnssStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, UI-independent representation of one GNSS status update.
 *
 * <p>The binder converts Android's {@link GnssStatus} into this model before rendering it.
 * Keeping the aggregation here makes the displayed totals deterministic and unit-testable;
 * this class deliberately contains no location or persistence APIs.</p>
 */
public final class SatelliteSnapshot {

    private static final List<Integer> CONSTELLATION_DISPLAY_ORDER = Arrays.asList(
            GnssStatus.CONSTELLATION_GPS,
            GnssStatus.CONSTELLATION_BEIDOU,
            GnssStatus.CONSTELLATION_GLONASS,
            GnssStatus.CONSTELLATION_GALILEO,
            GnssStatus.CONSTELLATION_QZSS,
            GnssStatus.CONSTELLATION_IRNSS,
            GnssStatus.CONSTELLATION_SBAS,
            GnssStatus.CONSTELLATION_UNKNOWN
    );

    private SatelliteSnapshot() {
    }

    /** One physical satellite as reported by the Android GNSS framework. */
    public static final class Satellite {
        public final int svid;
        public final int constellationType;
        public final float cn0DbHz;
        public final float elevationDegrees;
        public final float azimuthDegrees;
        public final boolean usedInFix;
        public final boolean hasEphemerisData;
        public final boolean hasAlmanacData;

        public Satellite(
                int svid,
                int constellationType,
                float cn0DbHz,
                float elevationDegrees,
                float azimuthDegrees,
                boolean usedInFix,
                boolean hasEphemerisData,
                boolean hasAlmanacData
        ) {
            this.svid = svid;
            this.constellationType = constellationType;
            this.cn0DbHz = cn0DbHz;
            this.elevationDegrees = elevationDegrees;
            this.azimuthDegrees = azimuthDegrees;
            this.usedInFix = usedInFix;
            this.hasEphemerisData = hasEphemerisData;
            this.hasAlmanacData = hasAlmanacData;
        }
    }

    /** Aggregate values used by the statistics card, constellation list, and detail list. */
    public static final class Summary {
        private final List<Satellite> satellites;
        private final Map<Integer, Integer> countByConstellation;
        private final int usedInFixCount;
        private final int signalCount;
        private final float averageCn0DbHz;

        private Summary(
                List<Satellite> satellites,
                Map<Integer, Integer> countByConstellation,
                int usedInFixCount,
                int signalCount,
                float averageCn0DbHz
        ) {
            this.satellites = Collections.unmodifiableList(new ArrayList<>(satellites));
            this.countByConstellation = Collections.unmodifiableMap(
                    new LinkedHashMap<>(countByConstellation));
            this.usedInFixCount = usedInFixCount;
            this.signalCount = signalCount;
            this.averageCn0DbHz = averageCn0DbHz;
        }

        public List<Satellite> getSatellites() {
            return satellites;
        }

        public int getVisibleCount() {
            return satellites.size();
        }

        public int getUsedInFixCount() {
            return usedInFixCount;
        }

        public int getSignalCount() {
            return signalCount;
        }

        public float getAverageCn0DbHz() {
            return averageCn0DbHz;
        }

        public Map<Integer, Integer> getCountByConstellation() {
            return countByConstellation;
        }
    }

    /**
     * Produces stable totals and a signal-priority order for a framework update.
     *
     * <p>A zero C/N0 value is treated as "no measurable signal" and is excluded from the
     * average. It still counts as a visible satellite, because the Android framework reported
     * it. Satellites used in the current fix are shown first, then by stronger signal.</p>
     */
    public static Summary summarize(List<Satellite> input) {
        List<Satellite> satellites = new ArrayList<>();
        if (input != null) {
            for (Satellite satellite : input) {
                if (satellite != null) {
                    satellites.add(satellite);
                }
            }
        }

        Collections.sort(satellites, new Comparator<Satellite>() {
            @Override
            public int compare(Satellite left, Satellite right) {
                if (left.usedInFix != right.usedInFix) {
                    return left.usedInFix ? -1 : 1;
                }
                int signal = Float.compare(right.cn0DbHz, left.cn0DbHz);
                if (signal != 0) {
                    return signal;
                }
                int constellation = Integer.compare(left.constellationType, right.constellationType);
                return constellation != 0 ? constellation : Integer.compare(left.svid, right.svid);
            }
        });

        Map<Integer, Integer> rawCounts = new HashMap<>();
        int usedCount = 0;
        int signalCount = 0;
        float totalCn0 = 0f;
        for (Satellite satellite : satellites) {
            Integer oldCount = rawCounts.get(satellite.constellationType);
            rawCounts.put(satellite.constellationType, oldCount == null ? 1 : oldCount + 1);
            if (satellite.usedInFix) {
                usedCount++;
            }
            if (satellite.cn0DbHz > 0f) {
                signalCount++;
                totalCn0 += satellite.cn0DbHz;
            }
        }

        Map<Integer, Integer> orderedCounts = new LinkedHashMap<>();
        for (Integer constellationType : CONSTELLATION_DISPLAY_ORDER) {
            Integer count = rawCounts.remove(constellationType);
            if (count != null) {
                orderedCounts.put(constellationType, count);
            }
        }
        List<Integer> remainingTypes = new ArrayList<>(rawCounts.keySet());
        Collections.sort(remainingTypes);
        for (Integer constellationType : remainingTypes) {
            orderedCounts.put(constellationType, rawCounts.get(constellationType));
        }

        float averageCn0 = signalCount == 0 ? 0f : totalCn0 / signalCount;
        return new Summary(satellites, orderedCounts, usedCount, signalCount, averageCn0);
    }
}
