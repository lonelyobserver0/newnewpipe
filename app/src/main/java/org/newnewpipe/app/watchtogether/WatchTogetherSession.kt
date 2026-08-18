package org.newnewpipe.app.watchtogether

import kotlin.random.Random

/**
 * App-side controller of a watch-together session (plan 022, S16): wires the
 * embedded [WatchTogetherServer] (only when this device created the room),
 * the [WatchTogetherClient] and the protocol state into one object the player
 * can drive. Android-free — the display name is passed in — so the whole
 * session flow is unit-testable on the JVM against a real server.
 *
 * Threading: [Listener] callbacks arrive on the WebSocket reader thread; the
 * app must marshal them to the main thread before touching the UI.
 *
 * @param server the embedded server, or `null` when this device is joining a
 *   room hosted elsewhere.
 */
class WatchTogetherSession @JvmOverloads constructor(
    val roomId: String,
    val participantId: String,
    val displayName: String,
    private val server: WatchTogetherServer?,
    private val client: WatchTogetherClient,
) {

    /** Receives session-level events (participants, remote playback, errors). */
    interface Listener {
        /** Participants/host/connection state changed. */
        fun onSessionUpdated(participants: List<Participant>, isHost: Boolean, connected: Boolean)

        /** The host published a playback snapshot to mirror. */
        fun onRemoteSnapshot(snapshot: PlaybackSnapshot)

        /** The host jumped to a new position. */
        fun onRemoteSeek(positionMs: Long)

        /** The server rejected a message or the connection failed. */
        fun onError(code: String, message: String)

        /** The connection closed (network failure; graceful leave is silent). */
        fun onDisconnected(reason: String?)
    }

    var listener: Listener? = null

    private var _participants: List<Participant> = emptyList()
    private var _isHost: Boolean = false
    private var _connected: Boolean = false
    private var latestSnapshot: PlaybackSnapshot = PlaybackSnapshot()
    private var lastPublished: PlaybackSnapshot? = null

    /** Participants currently in the room, in join order. */
    val participants: List<Participant> get() = _participants

    /** True once this device is the room host (authority for state/seek). */
    val isHost: Boolean get() = _isHost

    /** True while the WebSocket to the server is open. */
    val connected: Boolean get() = _connected

    /** Opens the connection and joins the room (the client announces itself). */
    fun start() {
        client.listener = createClientListener()
        client.connectAndJoin()
    }

    /**
     * Publishes the current playback state to the peers (host only), throttled
     * by [WatchSyncEngine.shouldPublish]. Returns true when a snapshot was
     * actually sent. No-op for slaves: the server rejects peer states anyway.
     */
    fun publishSnapshot(
        playing: Boolean,
        positionMs: Long,
        playbackRate: Float,
        mediaTitle: String?,
        mediaUrl: String?,
        mediaDurationMs: Long?,
        nowMs: Long,
    ): Boolean {
        if (!_isHost || !_connected) {
            return false
        }
        if (!WatchSyncEngine.shouldPublish(
                lastPublished, playing, positionMs, playbackRate,
                mediaTitle, mediaUrl, mediaDurationMs, nowMs
            )
        ) {
            return false
        }
        val snapshot = PlaybackSnapshot(
            playing = playing,
            positionMs = positionMs,
            playbackRate = playbackRate,
            mediaTitle = mediaTitle,
            mediaUrl = mediaUrl,
            mediaDurationMs = mediaDurationMs,
            updatedAtMs = nowMs,
        )
        lastPublished = snapshot
        client.sendState(snapshot)
        return true
    }

    /** Publishes an explicit position jump to the peers (host only). */
    fun publishSeek(positionMs: Long) {
        if (!_isHost || !_connected) {
            return
        }
        client.sendSeek(positionMs)
        lastPublished = lastPublished?.copy(
            positionMs = positionMs,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    /**
     * Leaves the room and releases the resources: leaves gracefully, closes
     * the client connection and stops the embedded server when this device
     * created the room. Safe to call in any state; afterwards [connected] is
     * false and no further [Listener] callbacks are delivered.
     */
    fun stop() {
        listener = null
        client.stop()
        runCatching { server?.stop() }
        _connected = false
        _isHost = false
    }

    private fun createClientListener(): WatchTogetherClient.Listener =
        object : WatchTogetherClient.Listener {
            override fun onConnected() {
                // The client sends the join on open; the welcome carries the
                // host assignment and the current room state.
            }

            override fun onWelcome(welcome: WelcomeMessage) {
                _isHost = welcome.hostParticipantId == participantId
                _participants = welcome.participants
                _connected = true
                latestSnapshot = welcome.snapshot
                listener?.onSessionUpdated(_participants, _isHost, true)
                // A joiner adopts the host's current state immediately; the
                // host must not mirror its own (empty) welcome snapshot.
                if (!_isHost && welcome.snapshot.mediaUrl != null) {
                    listener?.onRemoteSnapshot(welcome.snapshot)
                }
            }

            override fun onParticipantJoined(message: JoinedMessage) {
                _participants = message.participants
                listener?.onSessionUpdated(_participants, _isHost, _connected)
            }

            override fun onParticipantLeft(message: LeftMessage) {
                _participants = message.participants
                if (message.newHostParticipantId == participantId) {
                    _isHost = true
                }
                listener?.onSessionUpdated(_participants, _isHost, _connected)
            }

            override fun onRemoteState(message: StateMessage) {
                latestSnapshot = PlaybackSnapshot(
                    playing = message.playing,
                    positionMs = message.positionMs,
                    playbackRate = message.playbackRate,
                    mediaTitle = message.mediaTitle,
                    mediaUrl = message.mediaUrl,
                    mediaDurationMs = message.mediaDurationMs,
                    updatedAtMs = message.sentAtMs,
                )
                listener?.onRemoteSnapshot(latestSnapshot)
            }

            override fun onRemoteSeek(message: SeekMessage) {
                listener?.onRemoteSeek(message.positionMs)
            }

            override fun onError(code: String, message: String) {
                listener?.onError(code, message)
            }

            override fun onDisconnected(reason: String?) {
                _connected = false
                listener?.onSessionUpdated(_participants, _isHost, false)
                listener?.onDisconnected(reason)
            }
        }
}

/**
 * Generates short, human-readable room codes for watch together (S16):
 * 4 characters from an unambiguous alphabet (no 0/O, 1/I/L) so they are easy
 * to dictate over the phone. Pure and deterministic with an injected [Random]
 * for tests.
 */
object WatchTogetherRoomCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    @JvmOverloads
    fun generate(length: Int = 4, random: Random = Random.Default): String =
        buildString {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
}
