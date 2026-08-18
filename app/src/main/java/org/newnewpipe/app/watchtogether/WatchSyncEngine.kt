package org.newnewpipe.app.watchtogether

import kotlin.math.abs

/**
 * Pure master-slave synchronization logic of the watch-together feature
 * (plan 022, S16). The host (master) publishes [PlaybackSnapshot]s and seek
 * events; every other participant (slave) evaluates them against its own
 * playback state and decides what to apply to the local player.
 *
 * No I/O and no Android dependencies: fully unit-testable on the JVM.
 *
 * Policy:
 *  - the expected remote position extrapolates the published position with
 *    the elapsed time and playback rate while the host is playing;
 *  - a seek is applied only when the drift exceeds the tolerance (default 2 s),
 *    which absorbs network jitter and buffering without fighting the user;
 *  - play/pause is applied whenever the states differ;
 *  - when the host plays a different track, nothing is applied: the slave
 *    shows the mismatch instead of hijacking its own playback.
 */
object WatchSyncEngine {

    /** Drift above this threshold triggers a seek on the slave (millis). */
    const val DEFAULT_TOLERANCE_MS = 2000L

    /** Minimum interval between two host snapshot broadcasts (millis). */
    const val DEFAULT_PUBLISH_INTERVAL_MS = 2000L

    /** Position movement that forces a snapshot even inside the interval. */
    const val DEFAULT_PUBLISH_POSITION_DELTA_MS = 5000L

    /**
     * The position the host is (or would be) at `nowMs`, extrapolating with
     * the playback rate while playing. A paused host keeps the position fixed.
     */
    fun expectedPositionMs(snapshot: PlaybackSnapshot, nowMs: Long): Long {
        if (!snapshot.playing) {
            return snapshot.positionMs
        }
        val elapsedMs = (nowMs - snapshot.updatedAtMs).coerceAtLeast(0L)
        return snapshot.positionMs + (elapsedMs * snapshot.playbackRate).toLong()
    }

    /**
     * Decides what a slave should do with the host's [snapshot]. The result is
     * idempotent: after applying it, the local state is back within tolerance
     * and the next evaluation returns [WatchSyncAction.Nothing].
     *
     * @param localMediaUrl url of the locally playing item; when it differs
     *   from the host's, the result is [WatchSyncAction.MediaMismatch] unless
     *   either side is unknown (`null` — e.g. a fresh session without media).
     */
    @JvmOverloads
    fun evaluate(
        snapshot: PlaybackSnapshot,
        localPositionMs: Long,
        localPlaying: Boolean,
        localMediaUrl: String?,
        nowMs: Long,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): WatchSyncAction {
        if (snapshot.mediaUrl != null && localMediaUrl != null && snapshot.mediaUrl != localMediaUrl) {
            return WatchSyncAction.MediaMismatch
        }
        val target = expectedPositionMs(snapshot, nowMs)
        val needsSeek = abs(target - localPositionMs) > toleranceMs
        val needsPlayPause = snapshot.playing != localPlaying
        return when {
            needsSeek && needsPlayPause -> WatchSyncAction.SeekAndSetPlaying(target, snapshot.playing)
            needsSeek -> WatchSyncAction.SeekTo(target)
            needsPlayPause -> WatchSyncAction.SetPlaying(snapshot.playing)
            else -> WatchSyncAction.Nothing
        }
    }

    /**
     * Host-side throttling of snapshot broadcasts: publish when there is no
     * previous snapshot, the state or media changed, the interval elapsed, or
     * the position moved beyond [positionDeltaMs]. Mirrors the slave tolerance:
     * a 2 s cadence plus 5 s position deltas keeps the wire quiet while still
     * catching every user-relevant change.
     */
    fun shouldPublish(
        last: PlaybackSnapshot?,
        playing: Boolean,
        positionMs: Long,
        playbackRate: Float,
        mediaTitle: String?,
        mediaUrl: String?,
        mediaDurationMs: Long?,
        nowMs: Long,
        publishIntervalMs: Long = DEFAULT_PUBLISH_INTERVAL_MS,
        positionDeltaMs: Long = DEFAULT_PUBLISH_POSITION_DELTA_MS,
    ): Boolean {
        if (last == null) {
            return true
        }
        if (last.playing != playing || last.playbackRate != playbackRate ||
            last.mediaTitle != mediaTitle || last.mediaUrl != mediaUrl ||
            last.mediaDurationMs != mediaDurationMs
        ) {
            return true
        }
        if (nowMs - last.updatedAtMs >= publishIntervalMs) {
            return true
        }
        return abs(positionMs - last.positionMs) >= positionDeltaMs
    }
}

/** What a slave should do with the host's playback state (see [WatchSyncEngine.evaluate]). */
sealed class WatchSyncAction {
    /** Within tolerance and same playing state: nothing to change. */
    object Nothing : WatchSyncAction()

    /** Seek the local player to this position. */
    data class SeekTo(val positionMs: Long) : WatchSyncAction()

    /** Only the playing state differs. */
    data class SetPlaying(val playing: Boolean) : WatchSyncAction()

    /** Drift and playing state both differ: seek first, then play/pause. */
    data class SeekAndSetPlaying(val positionMs: Long, val playing: Boolean) : WatchSyncAction()

    /** The host is playing a different track: never touch local playback. */
    object MediaMismatch : WatchSyncAction()
}
