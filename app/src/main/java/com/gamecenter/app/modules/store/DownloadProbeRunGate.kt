package com.gamecenter.app.modules.store

import java.util.concurrent.atomic.AtomicBoolean

/** Keeps entry scheduling and the actual one-shot probe as separate lifecycle events. */
internal class DownloadProbeRunGate {
    private val entryScheduled = AtomicBoolean(false)
    private val probeStarted = AtomicBoolean(false)

    fun claimEntrySchedule(): Boolean = entryScheduled.compareAndSet(false, true)

    fun claimProbeStart(): Boolean = probeStarted.compareAndSet(false, true)

    fun releaseEntryScheduleIfProbeNotStarted() {
        if (!probeStarted.get()) {
            entryScheduled.set(false)
        }
    }
}
