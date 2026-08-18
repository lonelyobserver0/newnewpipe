package org.newnewpipe.app.watchtogether

import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

/**
 * WebSocket client of the watch-together feature (plan 022, S16): connects to
 * the embedded server of the host device at `ws://<host>:<port>/watch/<roomId>`
 * (see [WatchTogetherServer], S15) and speaks the [WatchTogetherProtocol].
 *
 * The client automatically announces itself with a [JoinMessage] as soon as
 * the connection opens, so callers only have to [connectAndJoin] and then
 * react to [Listener] callbacks. Deliberately Android-free (pure JVM), like
 * the server, so the whole round-trip is unit-testable with real sockets.
 *
 * Threading: Java-WebSocket invokes the callbacks on its internal reader
 * thread; the app must marshal them to the main thread before touching the UI.
 *
 * @param host IP address or hostname of the device hosting the room.
 * @param port TCP port of the embedded server (see [WatchTogetherProtocol.DEFAULT_PORT]).
 * @param roomId room identifier, lowercase path segment on the wire.
 * @param participantId stable id of this participant (kept across reconnects).
 * @param displayName human-readable name shown to the other participants.
 */
class WatchTogetherClient @JvmOverloads constructor(
    val host: String,
    val port: Int,
    val roomId: String,
    val participantId: String,
    val displayName: String,
    private val logger: (String) -> Unit = {},
) : WebSocketClient(URI("ws://$host:$port/watch/$roomId")) {

    /** Receives protocol events. All callbacks run on the WebSocket thread. */
    interface Listener {
        /** The connection opened and the join message was sent. */
        fun onConnected()

        /** The server accepted the join and answered with the room state. */
        fun onWelcome(welcome: WelcomeMessage)

        /** Another participant joined the room. */
        fun onParticipantJoined(message: JoinedMessage)

        /** A participant left the room (possibly with host reassignment). */
        fun onParticipantLeft(message: LeftMessage)

        /** The host published a new playback state. */
        fun onRemoteState(message: StateMessage)

        /** The host jumped to a new position. */
        fun onRemoteSeek(message: SeekMessage)

        /** The server rejected a message or the connection errored. */
        fun onError(code: String, message: String)

        /** The connection closed (graceful leave or network failure). */
        fun onDisconnected(reason: String?)
    }

    var listener: Listener? = null

    private var started = false

    /**
     * Opens the connection and joins the room. No-op when called twice;
     * Java-WebSocket client instances are not reusable after closing, so a
     * rejoin must create a fresh [WatchTogetherClient] (the server sends the
     * current snapshot in the welcome, which makes a rejoin self-healing).
     */
    fun connectAndJoin() {
        if (started) {
            return
        }
        started = true
        logger("watch-together: connecting to ws://$host:$port/watch/$roomId as $participantId")
        connect()
    }

    /** Sends the authoritative playback state (host only; server rejects others). */
    fun sendState(snapshot: PlaybackSnapshot) {
        if (!isOpen) {
            return
        }
        send(WatchMessageCodec.encode(StateMessage(
            playing = snapshot.playing,
            positionMs = snapshot.positionMs,
            playbackRate = snapshot.playbackRate,
            mediaTitle = snapshot.mediaTitle,
            mediaUrl = snapshot.mediaUrl,
            mediaDurationMs = snapshot.mediaDurationMs,
        )))
    }

    /** Sends an explicit position jump (host only). */
    fun sendSeek(positionMs: Long) {
        if (!isOpen) {
            return
        }
        send(WatchMessageCodec.encode(SeekMessage(positionMs)))
    }

    /**
     * Gracefully leaves the room and closes the connection: sends a
     * [LeaveMessage] first (the server broadcasts the departure to the peers)
     * and then closes the socket. Safe to call in any state.
     */
    fun stop() {
        if (!started) {
            return
        }
        started = false
        runCatching {
            if (isOpen) {
                send(WatchMessageCodec.encode(LeaveMessage(participantId)))
            }
        }
        runCatching {
            closeConnection(CloseFrame.NORMAL, "leaving")
        }
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        if (!started) {
            closeConnection(CloseFrame.NORMAL, "stopped")
            return
        }
        send(WatchMessageCodec.encode(JoinMessage(participantId, displayName)))
        listener?.onConnected()
    }

    override fun onMessage(message: String) {
        if (!started) {
            return
        }
        val decoded = WatchMessageCodec.decode(message) ?: return
        when (decoded) {
            is WelcomeMessage -> listener?.onWelcome(decoded)
            is JoinedMessage -> listener?.onParticipantJoined(decoded)
            is LeftMessage -> listener?.onParticipantLeft(decoded)
            is StateMessage -> listener?.onRemoteState(decoded)
            is SeekMessage -> listener?.onRemoteSeek(decoded)
            is ErrorMessage -> listener?.onError(decoded.code, decoded.message)
            else -> Unit
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        if (!started) {
            return
        }
        logger("watch-together: closed (code=$code, reason=$reason, remote=$remote)")
        listener?.onDisconnected(reason.ifBlank { null })
    }

    override fun onError(ex: Exception) {
        if (!started) {
            return
        }
        logger("watch-together: error $ex")
        listener?.onError("connection_error", ex.message ?: ex.javaClass.simpleName)
    }
}
