package org.newnewpipe.app.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTogetherProtocolTest {

    private val ts = 1_787_000_000_000L

    @Test
    fun `join roundtrip`() {
        val original = JoinMessage("alice", "Alice", ts)
        val decoded = WatchMessageCodec.decode(WatchMessageCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `leave roundtrip`() {
        val original = LeaveMessage("alice", ts)
        assertEquals(original, WatchMessageCodec.decode(WatchMessageCodec.encode(original)))
    }

    @Test
    fun `state roundtrip with all fields`() {
        val original = StateMessage(
            playing = true,
            positionMs = 61_234,
            playbackRate = 1.5f,
            mediaTitle = "My Song",
            mediaUrl = "https://example.com/watch?v=abc",
            mediaDurationMs = 300_000,
            sentAtMs = ts,
        )
        assertEquals(original, WatchMessageCodec.decode(WatchMessageCodec.encode(original)))
    }

    @Test
    fun `state roundtrip with null media and duration`() {
        val original = StateMessage(
            playing = false,
            positionMs = 0,
            sentAtMs = ts,
        )
        val decoded = WatchMessageCodec.decode(WatchMessageCodec.encode(original)) as StateMessage
        assertEquals(original, decoded)
        assertNull(decoded.mediaTitle)
        assertNull(decoded.mediaUrl)
        assertNull(decoded.mediaDurationMs)
    }

    @Test
    fun `seek roundtrip`() {
        val original = SeekMessage(42_000, ts)
        assertEquals(original, WatchMessageCodec.decode(WatchMessageCodec.encode(original)))
    }

    @Test
    fun `welcome roundtrip with participants and snapshot`() {
        val original = WelcomeMessage(
            participantId = "bob",
            hostParticipantId = "alice",
            participants = listOf(
                Participant("alice", "Alice", isHost = true, joinedAtMs = ts - 5_000),
                Participant("bob", "Bob", isHost = false, joinedAtMs = ts),
            ),
            snapshot = PlaybackSnapshot(
                playing = true,
                positionMs = 12_345,
                playbackRate = 1.25f,
                mediaTitle = "Track",
                mediaUrl = "https://example.com/t",
                mediaDurationMs = 200_000,
                updatedAtMs = ts - 1_000,
            ),
            sentAtMs = ts,
        )
        val decoded = WatchMessageCodec.decode(WatchMessageCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `joined and left roundtrip`() {
        val joined = JoinedMessage(
            participant = Participant("carol", "Carol", isHost = false, joinedAtMs = ts),
            participants = listOf(Participant("alice", "Alice", isHost = true, joinedAtMs = ts - 10)),
            sentAtMs = ts,
        )
        assertEquals(joined, WatchMessageCodec.decode(WatchMessageCodec.encode(joined)))

        val left = LeftMessage(
            participantId = "carol",
            newHostParticipantId = null,
            participants = listOf(Participant("alice", "Alice", isHost = true, joinedAtMs = ts - 10)),
            sentAtMs = ts,
        )
        assertEquals(left, WatchMessageCodec.decode(WatchMessageCodec.encode(left)))

        val leftWithNewHost = left.copy(newHostParticipantId = "dave")
        assertEquals(
            leftWithNewHost,
            WatchMessageCodec.decode(WatchMessageCodec.encode(leftWithNewHost))
        )
    }

    @Test
    fun `error roundtrip`() {
        val original = ErrorMessage("room_full", "the room is full", ts)
        assertEquals(original, WatchMessageCodec.decode(WatchMessageCodec.encode(original)))
    }

    @Test
    fun `malformed json decodes to null`() {
        assertNull(WatchMessageCodec.decode("not json at all"))
        assertNull(WatchMessageCodec.decode("{\"type\": \"join\""))
        assertNull(WatchMessageCodec.decode(""))
    }

    @Test
    fun `unknown type decodes to null`() {
        assertNull(WatchMessageCodec.decode("""{"type":"explode","ts":1}"""))
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val decoded = WatchMessageCodec.decode(
            """{"type":"state","ts":123}"""
        )
        assertNotNull(decoded)
        assertTrue(decoded is StateMessage)
        decoded as StateMessage
        assertEquals(false, decoded.playing)
        assertEquals(0L, decoded.positionMs)
        assertEquals(1f, decoded.playbackRate, 0.0001f)
        assertNull(decoded.mediaDurationMs)
    }

    @Test
    fun `negative duration encodes as null duration`() {
        val decoded = WatchMessageCodec.decode(
            """{"type":"state","ts":123,"positionMs":5,"mediaDurationMs":-1}"""
        )
        assertNotNull(decoded)
        decoded as StateMessage
        assertNull(decoded.mediaDurationMs)
    }
}
