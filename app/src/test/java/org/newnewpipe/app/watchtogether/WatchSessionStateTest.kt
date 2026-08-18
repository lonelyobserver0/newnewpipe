package org.newnewpipe.app.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSessionStateTest {

    private val now = 1_787_000_000_000L

    private fun room(max: Int = WatchTogetherProtocol.DEFAULT_MAX_PARTICIPANTS) =
        WatchSessionState("room1", max)

    // region join

    @Test
    fun `first joiner becomes host`() {
        val state = room()
        val event = state.join("alice", "Alice", now) as WatchEvent.ParticipantJoined
        assertTrue(event.participant.isHost)
        assertEquals("alice", state.hostId)
        assertEquals(1, state.participantsList.size)
    }

    @Test
    fun `second joiner is a peer`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.join("bob", "Bob", now) as WatchEvent.ParticipantJoined
        assertEquals(false, event.participant.isHost)
        assertEquals("alice", state.hostId)
        assertEquals(2, state.participantsList.size)
    }

    @Test
    fun `blank participant id is rejected`() {
        val state = room()
        val event = state.join("  ", "Nobody", now) as WatchEvent.Rejected
        assertEquals("invalid_participant", event.code)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `duplicate join is rejected`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.join("alice", "Alice again", now) as WatchEvent.Rejected
        assertEquals("already_joined", event.code)
        assertEquals(1, state.participantsList.size)
    }

    @Test
    fun `full room rejects new joiners`() {
        val state = room(max = 2)
        state.join("alice", "Alice", now)
        state.join("bob", "Bob", now)
        val event = state.join("carol", "Carol", now) as WatchEvent.Rejected
        assertEquals("room_full", event.code)
        assertEquals(2, state.participantsList.size)
    }

    @Test
    fun `blank display name falls back to participant id`() {
        val state = room()
        val event = state.join("alice", "", now) as WatchEvent.ParticipantJoined
        assertEquals("alice", event.participant.displayName)
    }

    // region state

    @Test
    fun `host can publish state`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.applyState(
            "alice",
            StateMessage(playing = true, positionMs = 50_000, mediaTitle = "T"),
            now + 1_000,
        ) as WatchEvent.StateChanged
        assertTrue(event.snapshot.playing)
        assertEquals(50_000L, state.snapshot.positionMs)
        assertEquals("T", state.snapshot.mediaTitle)
        assertEquals(now + 1_000, state.snapshot.updatedAtMs)
    }

    @Test
    fun `peer cannot publish state`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.join("bob", "Bob", now)
        val event = state.applyState("bob", StateMessage(playing = true, positionMs = 1), now)
            as WatchEvent.Rejected
        assertEquals("not_host", event.code)
        assertEquals(false, state.snapshot.playing)
    }

    @Test
    fun `unknown participant cannot publish state`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.applyState("mallory", StateMessage(playing = true, positionMs = 1), now)
            as WatchEvent.Rejected
        assertEquals("not_host", event.code)
    }

    @Test
    fun `negative position is rejected`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.applyState("alice", StateMessage(playing = true, positionMs = -5), now)
            as WatchEvent.Rejected
        assertEquals("invalid_position", event.code)
    }

    @Test
    fun `non-positive playback rate is rejected`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.applyState("alice", StateMessage(playing = true, positionMs = 1, playbackRate = 0f), now)
            as WatchEvent.Rejected
        assertEquals("invalid_rate", event.code)
    }

    // region seek

    @Test
    fun `host can seek`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.applyState("alice", StateMessage(playing = true, positionMs = 10_000), now)
        val event = state.applySeek("alice", SeekMessage(90_000), now + 500) as WatchEvent.Seeked
        assertEquals(90_000L, event.positionMs)
        assertEquals(90_000L, state.snapshot.positionMs)
        assertTrue(state.snapshot.playing)
    }

    @Test
    fun `seek is clamped to media duration`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.applyState(
            "alice",
            StateMessage(playing = true, positionMs = 10_000, mediaDurationMs = 60_000),
            now,
        )
        val event = state.applySeek("alice", SeekMessage(120_000), now) as WatchEvent.Seeked
        assertEquals(60_000L, event.positionMs)
    }

    @Test
    fun `seek below zero clamps to zero`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.applyState("alice", StateMessage(playing = true, positionMs = 10_000), now)
        val event = state.applySeek("alice", SeekMessage(-5), now) as WatchEvent.Seeked
        assertEquals(0L, event.positionMs)
    }

    @Test
    fun `seek beyond unknown duration is not clamped`() {
        val state = room()
        state.join("alice", "Alice", now)
        val event = state.applySeek("alice", SeekMessage(1_000_000), now) as WatchEvent.Seeked
        assertEquals(1_000_000L, event.positionMs)
    }

    @Test
    fun `peer cannot seek`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.join("bob", "Bob", now)
        val event = state.applySeek("bob", SeekMessage(5), now) as WatchEvent.Rejected
        assertEquals("not_host", event.code)
    }

    // region leave

    @Test
    fun `leaving peer keeps host`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.join("bob", "Bob", now)
        val event = state.leave("bob", now) as WatchEvent.ParticipantLeft
        assertEquals("alice", state.hostId)
        assertNull(event.newHostId)
        assertEquals(1, state.participantsList.size)
    }

    @Test
    fun `leaving host promotes oldest remaining participant`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.join("bob", "Bob", now + 1)
        state.join("carol", "Carol", now + 2)
        val event = state.leave("alice", now + 3) as WatchEvent.ParticipantLeft
        assertEquals("bob", event.newHostId)
        assertEquals("bob", state.hostId)
        assertTrue(state.participantsList.first { it.id == "bob" }.isHost)
    }

    @Test
    fun `leaving unknown participant returns null`() {
        val state = room()
        state.join("alice", "Alice", now)
        assertNull(state.leave("ghost", now))
        assertEquals(1, state.participantsList.size)
    }

    @Test
    fun `last participant leaving empties the room`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.leave("alice", now)
        assertTrue(state.isEmpty)
        assertNull(state.hostId)
    }

    @Test
    fun `snapshot survives joins and leaves`() {
        val state = room()
        state.join("alice", "Alice", now)
        state.applyState("alice", StateMessage(playing = true, positionMs = 42_000, mediaTitle = "Song"), now)
        state.join("bob", "Bob", now + 1)
        state.leave("alice", now + 2)
        // bob is now host and keeps the previous snapshot
        assertEquals("bob", state.hostId)
        assertEquals(42_000L, state.snapshot.positionMs)
        assertEquals("Song", state.snapshot.mediaTitle)
    }
}
