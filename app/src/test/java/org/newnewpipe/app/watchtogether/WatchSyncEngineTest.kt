package org.newnewpipe.app.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the pure master-slave sync policy (plan 022-S16):
 * drift tolerance, play/pause propagation, media mismatch and host throttling.
 */
class WatchSyncEngineTest {

    private fun snapshot(
        playing: Boolean = false,
        positionMs: Long = 0L,
        playbackRate: Float = 1f,
        mediaTitle: String? = null,
        mediaUrl: String? = null,
        mediaDurationMs: Long? = null,
        updatedAtMs: Long = 0L,
    ) = PlaybackSnapshot(playing, positionMs, playbackRate, mediaTitle, mediaUrl, mediaDurationMs, updatedAtMs)

    // region expectedPositionMs

    @Test
    fun `paused snapshot keeps the position fixed`() {
        val s = snapshot(playing = false, positionMs = 10_000L, updatedAtMs = 1_000L)
        assertEquals(10_000L, WatchSyncEngine.expectedPositionMs(s, 5_000L))
    }

    @Test
    fun `playing snapshot extrapolates position with elapsed time and rate`() {
        val s = snapshot(playing = true, positionMs = 10_000L, playbackRate = 2f, updatedAtMs = 1_000L)
        assertEquals(18_000L, WatchSyncEngine.expectedPositionMs(s, 5_000L))
    }

    @Test
    fun `elapsed time before the snapshot is clamped to zero`() {
        val s = snapshot(playing = true, positionMs = 10_000L, updatedAtMs = 5_000L)
        assertEquals(10_000L, WatchSyncEngine.expectedPositionMs(s, 1_000L))
    }

    // endregion

    // region evaluate

    @Test
    fun `drift within tolerance requires no action`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "u", updatedAtMs = 0L)
        assertEquals(WatchSyncAction.Nothing, WatchSyncEngine.evaluate(s, 61_000L, true, "u", 0L))
    }

    @Test
    fun `large drift returns a seek to the expected position`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "u", updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.SeekTo(63_000L),
            WatchSyncEngine.evaluate(s, 59_000L, true, "u", 3_000L)
        )
    }

    @Test
    fun `playing state mismatch without drift returns SetPlaying`() {
        val s = snapshot(playing = false, positionMs = 60_000L, mediaUrl = "u", updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.SetPlaying(false),
            WatchSyncEngine.evaluate(s, 60_000L, true, "u", 0L)
        )
    }

    @Test
    fun `drift plus playing mismatch returns SeekAndSetPlaying action`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "u", updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.SeekAndSetPlaying(63_000L, true),
            WatchSyncEngine.evaluate(s, 59_000L, false, "u", 3_000L)
        )
    }

    @Test
    fun `different media on both sides returns MediaMismatch`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "video-a", updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.MediaMismatch,
            WatchSyncEngine.evaluate(s, 0L, false, "video-b", 0L)
        )
    }

    @Test
    fun `unknown host media still syncs the position`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = null, updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.SeekTo(60_000L),
            WatchSyncEngine.evaluate(s, 55_000L, true, "local", 0L)
        )
    }

    @Test
    fun `unknown local media still syncs the position`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "remote", updatedAtMs = 0L)
        assertEquals(
            WatchSyncAction.SeekTo(60_000L),
            WatchSyncEngine.evaluate(s, 55_000L, true, null, 0L)
        )
    }

    @Test
    fun `evaluation is idempotent after applying the seek`() {
        val s = snapshot(playing = true, positionMs = 60_000L, mediaUrl = "u", updatedAtMs = 0L)
        val action = WatchSyncEngine.evaluate(s, 59_000L, true, "u", 3_000L) as WatchSyncAction.SeekTo
        // after seeking to the expected position, the same snapshot is a no-op
        assertEquals(WatchSyncAction.Nothing, WatchSyncEngine.evaluate(s, action.positionMs, true, "u", 3_000L))
    }

    // endregion

    // region shouldPublish

    @Test
    fun `first snapshot is always published`() {
        assertTrue(WatchSyncEngine.shouldPublish(null, true, 0L, 1f, "t", "u", 100L, 0L))
    }

    @Test
    fun `nothing changed and interval not elapsed means no publish`() {
        val last = snapshot(true, 10_000L, 1f, "t", "u", 100L, updatedAtMs = 0L)
        assertFalse(WatchSyncEngine.shouldPublish(last, true, 11_000L, 1f, "t", "u", 100L, 500L))
    }

    @Test
    fun `interval elapsed means publish`() {
        val last = snapshot(true, 10_000L, 1f, "t", "u", 100L, updatedAtMs = 0L)
        assertTrue(WatchSyncEngine.shouldPublish(last, true, 11_000L, 1f, "t", "u", 100L, 3_000L))
    }

    @Test
    fun `position moved beyond delta means publish`() {
        val last = snapshot(true, 10_000L, 1f, "t", "u", 100L, updatedAtMs = 0L)
        assertTrue(WatchSyncEngine.shouldPublish(last, true, 16_000L, 1f, "t", "u", 100L, 500L))
    }

    @Test
    fun `playing state changed means publish immediately`() {
        val last = snapshot(true, 10_000L, 1f, "t", "u", 100L, updatedAtMs = 0L)
        assertTrue(WatchSyncEngine.shouldPublish(last, false, 10_000L, 1f, "t", "u", 100L, 500L))
    }

    @Test
    fun `track changed means publish immediately`() {
        val last = snapshot(true, 10_000L, 1f, "old", "u-old", 100L, updatedAtMs = 0L)
        assertTrue(WatchSyncEngine.shouldPublish(last, true, 10_000L, 1f, "new", "u-new", 100L, 500L))
    }

    @Test
    fun `playback rate changed means publish immediately`() {
        val last = snapshot(true, 10_000L, 1f, "t", "u", 100L, updatedAtMs = 0L)
        assertTrue(WatchSyncEngine.shouldPublish(last, true, 10_000L, 1.5f, "t", "u", 100L, 500L))
    }

    // endregion
}
