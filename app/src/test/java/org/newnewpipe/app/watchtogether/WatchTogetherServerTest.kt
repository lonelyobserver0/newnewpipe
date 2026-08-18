package org.newnewpipe.app.watchtogether

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end tests of [WatchTogetherServer] with real WebSockets
 * (Java-WebSocket client on a plain JVM): join/welcome flow, host
 * authority over state/seek, leave and host reassignment, room isolation.
 */
class WatchTogetherServerTest {

    private lateinit var server: WatchTogetherServer
    private val clients = mutableListOf<TestClient>()

    @Before
    fun setUp() {
        val port = ServerSocket(0).use { it.localPort }
        server = WatchTogetherServer(port)
        server.start()
        awaitReady()
    }

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.closeBlocking() } }
        runCatching { server.stop() }
    }

    // region flows

    @Test
    fun `first joiner receives welcome as host`() {
        val host = connect("room-a")
        host.send(JoinMessage("alice", "Alice"))

        val welcome = await { host.messages.ofType<WelcomeMessage>() }
        assertEquals("alice", welcome.participantId)
        assertEquals("alice", welcome.hostParticipantId)
        assertEquals(1, welcome.participants.size)
        assertTrue(welcome.participants.first().isHost)
    }

    @Test
    fun `peer joining is listed in both welcomes`() {
        val host = connect("room-b")
        host.send(JoinMessage("alice", "Alice"))
        await { host.messages.ofType<WelcomeMessage>() }

        val peer = connect("room-b")
        peer.send(JoinMessage("bob", "Bob"))

        val peerWelcome = await { peer.messages.ofType<WelcomeMessage>() }
        assertEquals("alice", peerWelcome.hostParticipantId)
        assertEquals(listOf("alice", "bob"), peerWelcome.participants.map { it.id })

        // the host is notified of the join
        val joined = await { host.messages.ofType<JoinedMessage>() }
        assertEquals("bob", joined.participant.id)
    }

    @Test
    fun `host state is broadcast to peer`() {
        val host = connect("room-c")
        val peer = connect("room-c")
        joinHostThenPeer(host, "alice", peer, "bob")

        host.send(
            StateMessage(
                playing = true,
                positionMs = 60_000,
                playbackRate = 1f,
                mediaTitle = "Song",
                mediaDurationMs = 300_000,
            )
        )

        val state = await { peer.messages.ofType<StateMessage>() }
        assertTrue(state.playing)
        assertEquals(60_000L, state.positionMs)
        assertEquals("Song", state.mediaTitle)
        assertEquals(300_000L, state.mediaDurationMs)
    }

    @Test
    fun `peer state is rejected with not_host`() {
        val host = connect("room-d")
        val peer = connect("room-d")
        joinHostThenPeer(host, "alice", peer, "bob")

        peer.send(StateMessage(playing = true, positionMs = 5))

        val error = await { peer.messages.ofType<ErrorMessage>() }
        assertEquals("not_host", error.code)

        // the host must NOT have received the peer state
        Thread.sleep(100)
        assertNull(host.messages.ofType<StateMessage>())
    }

    @Test
    fun `host seek is broadcast and clamped to duration`() {
        val host = connect("room-e")
        val peer = connect("room-e")
        joinHostThenPeer(host, "alice", peer, "bob")

        host.send(StateMessage(playing = true, positionMs = 0, mediaDurationMs = 60_000))
        await { peer.messages.ofType<StateMessage>() }

        host.send(SeekMessage(120_000))

        val seek = await { peer.messages.ofType<SeekMessage>() }
        assertEquals(60_000L, seek.positionMs)
    }

    @Test
    fun `peer leaving is broadcast and host stays`() {
        val host = connect("room-f")
        val peer = connect("room-f")
        joinHostThenPeer(host, "alice", peer, "bob")

        peer.send(LeaveMessage("bob"))

        val left = await { host.messages.ofType<LeftMessage>() }
        assertEquals("bob", left.participantId)
        assertNull(left.newHostParticipantId)
        assertEquals(listOf("alice"), left.participants.map { it.id })
    }

    @Test
    fun `host leaving promotes the remaining peer`() {
        val host = connect("room-g")
        val peer = connect("room-g")
        joinHostThenPeer(host, "alice", peer, "bob")

        host.send(LeaveMessage("alice"))

        val left = await { peer.messages.ofType<LeftMessage>() }
        assertEquals("alice", left.participantId)
        assertEquals("bob", left.newHostParticipantId)
        assertEquals(listOf("bob"), left.participants.map { it.id })
    }

    @Test
    fun `disconnect removes the participant automatically`() {
        val host = connect("room-h")
        val peer = connect("room-h")
        joinHostThenPeer(host, "alice", peer, "bob")

        peer.closeBlocking()

        val left = await { host.messages.ofType<LeftMessage>() }
        assertEquals("bob", left.participantId)
    }

    @Test
    fun `rooms are isolated`() {
        val hostA = connect("room-iso-a")
        val hostB = connect("room-iso-b")
        hostA.send(JoinMessage("alice", "Alice"))
        hostB.send(JoinMessage("carol", "Carol"))
        await { hostA.messages.ofType<WelcomeMessage>() }
        await { hostB.messages.ofType<WelcomeMessage>() }

        hostA.send(StateMessage(playing = true, positionMs = 99))
        // the state is never broadcast back to the sender, and never crosses rooms
        Thread.sleep(150)
        assertNull(hostA.messages.ofType<StateMessage>())
        assertNull(hostB.messages.ofType<StateMessage>())
    }

    @Test
    fun `invalid path closes the connection`() {
        val bad = TestClient(URI("ws://127.0.0.1:${server.port}/nope/room"))
        bad.connectBlocking()
        clients.add(bad)
        val closed = await(timeoutMs = 5_000) {
            if (bad.isClosed) true else null
        }
        assertTrue(closed)
    }

    @Test
    fun `state before joining is rejected`() {
        val client = connect("room-i")
        client.send(StateMessage(playing = true, positionMs = 1))
        val error = await { client.messages.ofType<ErrorMessage>() }
        assertEquals("not_joined", error.code)
    }

    @Test
    fun `duplicate join is rejected`() {
        val client = connect("room-j")
        client.send(JoinMessage("alice", "Alice"))
        await { client.messages.ofType<WelcomeMessage>() }
        client.send(JoinMessage("alice", "Alice"))
        val error = await { client.messages.ofType<ErrorMessage>() }
        assertEquals("already_joined", error.code)
    }

    @Test
    fun `malformed message is rejected with parse_error`() {
        val client = connect("room-k")
        client.send("this is not json")
        val error = await { client.messages.ofType<ErrorMessage>() }
        assertEquals("parse_error", error.code)
    }

    // endregion

    // region helpers

    /**
     * Serializes the join order: the host must open the room before the peer
     * joins, mirroring real usage and avoiding a race on which concurrent
     * join reaches the server first.
     */
    private fun joinHostThenPeer(
        host: TestClient,
        hostId: String,
        peer: TestClient,
        peerId: String,
    ) {
        host.send(JoinMessage(hostId, hostId))
        await { host.messages.ofType<WelcomeMessage>() }
        peer.send(JoinMessage(peerId, peerId))
        await { peer.messages.ofType<WelcomeMessage>() }
    }

    private fun connect(room: String): TestClient {
        val client = TestClient(URI("ws://127.0.0.1:${server.port}/watch/$room"))
        assertTrue("connection to $room timed out", client.connectBlocking())
        clients.add(client)
        return client
    }

    private fun awaitReady() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", server.port), 200) }
                return
            } catch (e: Exception) {
                Thread.sleep(20)
            }
        }
        throw AssertionError("server did not start listening on port ${server.port}")
    }

    private fun <T> await(
        timeoutMs: Long = 5_000,
        poll: () -> T?,
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            poll()?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("timeout after ${timeoutMs}ms waiting for condition")
    }

    private inline fun <reified T : WatchMessage> List<WatchMessage>.ofType(): T? =
        filterIsInstance<T>().lastOrNull()

    // endregion
}

/** Minimal recording WebSocket client for the tests. */
private class TestClient(uri: URI) : WebSocketClient(uri) {
    val messages: MutableList<WatchMessage> = CopyOnWriteArrayList()

    fun send(message: WatchMessage) {
        send(WatchMessageCodec.encode(message))
    }

    override fun onOpen(handshakedata: ServerHandshake) = Unit

    override fun onMessage(message: String) {
        WatchMessageCodec.decode(message)?.let { messages.add(it) }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) = Unit

    override fun onError(ex: Exception) = Unit
}
