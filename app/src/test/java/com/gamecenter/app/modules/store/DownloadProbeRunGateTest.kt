package com.gamecenter.app.modules.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProbeRunGateTest {

    @Test
    fun `claiming startup schedule does not consume the probe run`() {
        val gate = DownloadProbeRunGate()

        assertTrue(gate.claimEntrySchedule())
        assertFalse(gate.claimEntrySchedule())
        assertTrue(gate.claimProbeStart())
        assertFalse(gate.claimProbeStart())

        gate.releaseEntryScheduleIfProbeNotStarted()
        assertFalse(gate.claimEntrySchedule())
    }

    @Test
    fun `schedule may retry when no probe was started`() {
        val gate = DownloadProbeRunGate()

        assertTrue(gate.claimEntrySchedule())
        gate.releaseEntryScheduleIfProbeNotStarted()
        assertTrue(gate.claimEntrySchedule())
    }
}
