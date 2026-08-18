package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleTrackerTest {

    @Test
    fun scrobbleAtHalfDuration_whenShorterThanFourMinutes() {
        // 3 minuti: la soglia che scatta prima è il 50% (90s).
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song", "Artist", durationMs = 180_000L)

        assertFalse(tracker.onPosition(89_000L))
        assertTrue(tracker.onPosition(90_000L))
    }

    @Test
    fun scrobbleAtFourMinutes_whenLongerThanEightMinutes() {
        // 10 minuti: 50% = 5 min, ma la soglia che scatta prima è 4 minuti.
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song", "Artist", durationMs = 600_000L)

        assertFalse(tracker.onPosition(239_000L))
        assertTrue(tracker.onPosition(240_000L))
    }

    @Test
    fun scrobbleFiresOnlyOncePerTrack() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song", "Artist", durationMs = 100_000L)

        assertTrue(tracker.onPosition(50_000L))
        assertFalse(tracker.onPosition(99_000L))
        assertFalse(tracker.onPosition(100_000L))
    }

    @Test
    fun unknownDuration_fallsBackToFourMinutes() {
        // Live/stream senza durata: soglia fissa di 4 minuti.
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Live", "Artist", durationMs = 0L)

        assertFalse(tracker.onPosition(239_000L))
        assertTrue(tracker.onPosition(240_000L))
    }

    @Test
    fun trackChange_reenablesScrobble() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song A", "Artist", durationMs = 100_000L)
        assertTrue(tracker.onPosition(50_000L))

        tracker.onTrackChanged("Song B", "Artist", durationMs = 100_000L)
        assertFalse(tracker.onPosition(49_000L))
        assertTrue(tracker.onPosition(50_000L))
    }

    @Test
    fun sameTrackTick_doesNotResetStartTimestamp() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song", "Artist", durationMs = 100_000L)
        val start = tracker.startTimestampSec
        // Tick ripetuti dello stesso brano: timestamp invariato.
        tracker.onTrackChanged("Song", "Artist", durationMs = 100_000L)
        assertEquals(start, tracker.startTimestampSec)
    }

    @Test
    fun noTrack_noScrobble() {
        val tracker = ScrobbleTracker()
        assertFalse(tracker.onPosition(999_000L))
        assertNull(tracker.lastTitle)
    }

    @Test
    fun reset_clearsState() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged("Song", "Artist", durationMs = 100_000L)
        tracker.reset()
        assertNull(tracker.lastTitle)
        assertFalse(tracker.onPosition(99_000L))
    }
}
