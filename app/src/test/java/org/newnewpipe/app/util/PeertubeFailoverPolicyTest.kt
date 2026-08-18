package org.newnewpipe.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeertubeFailoverPolicyTest {

    // ---- rispetto della scelta manuale ----

    @Test
    fun noManualSelection_autoSwitchAllowed() {
        assertTrue(PeertubeFailoverPolicy.shouldAutoSwitch(
            manualSelectionMs = 0L, nowMs = 1_000_000L, graceMs = 600_000L,
        ))
    }

    @Test
    fun recentManualSelection_blocksAutoSwitch() {
        val now = 1_000_000L
        assertFalse(PeertubeFailoverPolicy.shouldAutoSwitch(
            manualSelectionMs = now - 60_000L, nowMs = now, graceMs = 600_000L,
        ))
    }

    @Test
    fun oldManualSelection_allowsAutoSwitch() {
        val now = 1_000_000L
        assertTrue(PeertubeFailoverPolicy.shouldAutoSwitch(
            manualSelectionMs = now - 601_000L, nowMs = now, graceMs = 600_000L,
        ))
    }

    @Test
    fun exactlyAtGraceBoundary_allowsAutoSwitch() {
        val now = 1_000_000L
        assertTrue(PeertubeFailoverPolicy.shouldAutoSwitch(
            manualSelectionMs = now - 600_000L, nowMs = now, graceMs = 600_000L,
        ))
    }

    // ---- backoff esponenziale ----

    @Test
    fun backoff_baseWhenNoFailures() {
        assertEquals(60_000L, PeertubeFailoverPolicy.backoffDelayMs(0, 60_000L, 900_000L))
    }

    @Test
    fun backoff_doublesEachFailure() {
        assertEquals(60_000L, PeertubeFailoverPolicy.backoffDelayMs(1, 60_000L, 900_000L))
        assertEquals(120_000L, PeertubeFailoverPolicy.backoffDelayMs(2, 60_000L, 900_000L))
        assertEquals(240_000L, PeertubeFailoverPolicy.backoffDelayMs(3, 60_000L, 900_000L))
        assertEquals(480_000L, PeertubeFailoverPolicy.backoffDelayMs(4, 60_000L, 900_000L))
    }

    @Test
    fun backoff_cappedAtMax() {
        assertEquals(900_000L, PeertubeFailoverPolicy.backoffDelayMs(5, 60_000L, 900_000L))
        // Molti fallimenti consecutivi: nessun overflow, resta sul cap.
        assertEquals(900_000L, PeertubeFailoverPolicy.backoffDelayMs(50, 60_000L, 900_000L))
    }
}
