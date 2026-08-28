package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.location.GnssStatus;

import org.junit.Test;

import java.util.Arrays;

/** Regression coverage for the GNSS numbers users see in the satellite workspace. */
public class SatelliteSnapshotTest {

    @Test
    public void countsVisibleUsedAndMeasurableSignalsWithoutInventingData() {
        SatelliteSnapshot.Summary summary = SatelliteSnapshot.summarize(Arrays.asList(
                new SatelliteSnapshot.Satellite(
                        12, GnssStatus.CONSTELLATION_GPS, 42f, 55f, 120f, true, true, true),
                new SatelliteSnapshot.Satellite(
                        8, GnssStatus.CONSTELLATION_BEIDOU, 28f, 31f, 260f, false, true, false),
                new SatelliteSnapshot.Satellite(
                        4, GnssStatus.CONSTELLATION_GPS, 0f, 10f, 20f, false, false, false)
        ));

        assertEquals(3, summary.getVisibleCount());
        assertEquals(1, summary.getUsedInFixCount());
        assertEquals(2, summary.getSignalCount());
        assertEquals(35f, summary.getAverageCn0DbHz(), 0.001f);
        assertEquals(Integer.valueOf(2),
                summary.getCountByConstellation().get(GnssStatus.CONSTELLATION_GPS));
        assertEquals(Integer.valueOf(1),
                summary.getCountByConstellation().get(GnssStatus.CONSTELLATION_BEIDOU));
        assertTrue("Satellites used for the active fix should be prominent in the UI",
                summary.getSatellites().get(0).usedInFix);
    }
}
