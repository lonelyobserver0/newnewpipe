package org.newnewpipe.app.watchtogether

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end tests of [WatchTogetherSession] against a real embedded
 * [WatchTogetherServer] (plan 022-S16): create/join, host authority over
 * state/seek, participant bookkeeping, host promotion and clean teardown.
 */
class WatchTogetherSessionTest {

    private lateinit var server: WatchTogetherServer
    private var port = 0
    private lateinit var hostSession: WatchTogetherSession
    private val hostListener = TestSessionListener()
    private val peerListener = TestSessionListener()
    private var peerSession: WatchTogetherSession? = null
    private val sessions = mutableListOf<WatchTogetherSession>()

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        server = WatchTogetherServer(port)
        server.start()
        awaitReady()
        hostSession = createSession("host-device", server = server)
        hostSession.listener = hostListener
        hostSession.start()
        await { hostListener.updates.any { it.isHost && it.connected } }
    }

    @After
    fun tearDown() {
        sessions.forEach { runCatching { it.stop() } }
        runCatching { server.stop() }
    }

    // region flows

    @Test
    fun `host session becomes host with one participant`() {
        assertEquals(1, hostSession.participants.size)
        assertTrue(hostSession.isHost)
        assertTrue(hostSession.connected)
        val update = hostListener.updates.last()
        assertEquals(1, update.participantCount)
        assertTrue(update.isHost)
        assertTrue(update.connected)
    }

    @Test
    fun `peer join is reflected on both sessions`() {
        val peer = connectPeer()

        await { peerListener.updates.any { it.participantCount == 2 && it.connected } }
        await { hostListener.updates.any { it.participantCount == 2 } }
        assertEquals(listOf("host-device", "peer-device"), hostSession.participants.map { it.id })
        assertEquals(listOf("host-device", "peer-device"), peer.participants.map { it.id })
        assertFalse(peer.isHost)
        assertTrue(hostSession.isHost)
    }

    @Test
    fun `host publishSnapshot is delivered to the peer`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        assertTrue(
            hostSession.publishSnapshot(true, 12_345L, 1f, "Video", "url-a", 60_000L, nowMs())
        )

        await { peerListener.snapshots.any { it.playing && it.positionMs == 12_345L } }
        val snapshot = peerListener.snapshots.last()
        assertEquals("Video", snapshot.mediaTitle)
        assertEquals("url-a", snapshot.mediaUrl)
        assertEquals(60_000L, snapshot.mediaDurationMs)
    }

    @Test
    fun `host publishSeek is delivered to the peer`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        hostSession.publishSeek(30_000L)

        await { peerListener.seeks.contains(30_000L) }
    }

    @Test
    fun `host publish is throttled but state changes pass through`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        val now = nowMs()
        // first snapshot passes
        assertTrue(hostSession.publishSnapshot(true, 0L, 1f, "v", "u", 60_000L, now))
        // same state 500ms later is throttled
        assertFalse(hostSession.publishSnapshot(true, 400L, 1f, "v", "u", 60_000L, now + 500))
        // playing change passes immediately
        assertTrue(hostSession.publishSnapshot(false, 400L, 1f, "v", "u", 60_000L, now + 600))
    }

    @Test
    fun `peer publishSnapshot is rejected because it is not the host`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        val sent = peer.publishSnapshot(true, 99_000L, 1f, "x", "url-x", 100_000L, nowMs())
        assertFalse(sent)
        // the host must never receive a state from a peer
        Thread.sleep(150)
        assertTrue(hostListener.snapshots.isEmpty())
    }

    @Test
    fun `joining peer receives the host current snapshot in the welcome`() {
        val now = nowMs()
        hostSession.publishSnapshot(true, 42_000L, 1f, "Song", "url-s", 200_000L, now)
        // ensure the state reached the server before the peer joins
        Thread.sleep(150)

        connectPeer()

        await { peerListener.snapshots.any { it.positionMs == 42_000L } }
    }

    @Test
    fun `host leaving closes the room for the remaining peer`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        // the embedded server lives on the host device: when the host leaves,
        // the room dies and the peers are disconnected (host promotion is only
        // reachable while the server stays up — covered by the S15 server tests)
        hostSession.stop()

        await { peerListener.disconnects.isNotEmpty() }
        assertFalse(peer.connected)
        assertFalse(peer.isHost)
    }

    @Test
    fun `peer leaving shrinks the participant list on the host`() {
        val peer = connectPeer()
        await { peerListener.updates.any { it.participantCount == 2 } }

        peer.stop()

        await { hostListener.updates.any { it.participantCount == 1 } }
        assertTrue(hostSession.isHost)
        assertEquals("host-device", hostSession.participants.single().id)
    }

    @Test
    fun `stop disconnects the session silently`() {
        hostSession.stop()
        assertFalse(hostSession.connected)
        assertFalse(hostSession.isHost)
        assertTrue(hostListener.disconnects.isEmpty())
    }

    // endregion

    // region helpers

    private fun connectPeer(): WatchTogetherSession {
        val peer = createSession("peer-device", server = null)
        peer.listener = peerListener
        peerSession = peer
        peer.start()
        return peer
    }

    private fun createSession(participantId: String, server: WatchTogetherServer?): WatchTogetherSession {
        val client = WatchTogetherClient("127.0.0.1", port, ROOM_ID, participantId, participantId)
        return WatchTogetherSession(ROOM_ID, participantId, participantId, server, client)
            .also { sessions.add(it) }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun awaitReady() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use {
                    it.connect(java.net.InetSocketAddress("127.0.0.1", port), 200)
                }
                return
            } catch (e: Exception) {
                Thread.sleep(20)
            }
        }
        throw AssertionError("server did not start listening on port $port")
    }

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("timeout after ${timeoutMs}ms waiting for condition")
            }
            Thread.sleep(20)
        }
    }

    // endregion

    companion object {
        private const val ROOM_ID = "room-s16"
    }
}

/** Recording [WatchTogetherSession.Listener] for the tests. */
private class TestSessionListener : WatchTogetherSession.Listener {
    val updates = CopyOnWriteArrayList<SessionUpdate>()
    val snapshots = CopyOnWriteArrayList<PlaybackSnapshot>()
    val seeks = CopyOnWriteArrayList<Long>()
    val errors = CopyOnWriteArrayList<Pair<String, String>>()
    val disconnects = CopyOnWriteArrayList<String?>()

    override fun onSessionUpdated(participants: List<Participant>, isHost: Boolean, connected: Boolean) {
        updates.add(SessionUpdate(participants.size, isHost, connected))
    }

    override fun onRemoteSnapshot(snapshot: PlaybackSnapshot) {
        snapshots.add(snapshot)
    }

    override fun onRemoteSeek(positionMs: Long) {
        seeks.add(positionMs)
    }

    override fun onError(code: String, message: String) {
        errors.add(code to message)
    }

    override fun onDisconnected(reason: String?) {
        disconnects.add(reason)
    }
}

private data class SessionUpdate(
    val participantCount: Int,
    val isHost: Boolean,
    val connected: Boolean,
)
