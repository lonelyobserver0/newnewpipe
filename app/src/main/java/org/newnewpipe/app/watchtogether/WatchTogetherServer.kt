package org.newnewpipe.app.watchtogether

import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal self-hosted watch-together server (plan 022, S15, decision D-6):
 * an embedded WebSocket server that hosts rooms at
 * `ws://<host>:<port>/watch/<roomId>`, with no central service — the host's
 * app instance runs it and peers join over LAN.
 *
 * The server is a thin adapter over [WatchSessionState]: every decoded
 * message is fed to the room's state machine and the resulting [WatchEvent]
 * is broadcast to the peers of the room. It deliberately uses no
 * Android-specific logging (a plain [logger] callback), so it runs and is
 * testable on a plain JVM.
 *
 * @param port TCP port to bind (see [WatchTogetherProtocol.DEFAULT_PORT]).
 * @param maxParticipants per-room capacity (host included).
 */
class WatchTogetherServer(
    private val port: Int = WatchTogetherProtocol.DEFAULT_PORT,
    private val maxParticipants: Int = WatchTogetherProtocol.DEFAULT_MAX_PARTICIPANTS,
) : WebSocketServer(InetSocketAddress(port)) {

    /** Injectable logger (default: silent). Wire it to Logcat from the app. */
    var logger: (String) -> Unit = {}

    private val rooms = ConcurrentHashMap<String, WatchSessionState>()
    private val connectionRoom = ConcurrentHashMap<WebSocket, String>()
    private val connectionParticipant = ConcurrentHashMap<WebSocket, String>()

    override fun onStart() {
        logger("WatchTogetherServer listening on ${address.address.hostAddress ?: "*"}:${address.port}")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val roomId = parseRoomId(conn.resourceDescriptor)
        if (roomId == null) {
            logger("rejecting connection with invalid path: ${conn.resourceDescriptor}")
            conn.close(CloseFrame.PROTOCOL_ERROR, "expected path /watch/<roomId>")
            return
        }
        connectionRoom[conn] = roomId
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val roomId = connectionRoom[conn] ?: return
        val decoded = WatchMessageCodec.decode(message)
        if (decoded == null) {
            sendTo(conn, ErrorMessage("parse_error", "malformed or unknown message"))
            return
        }
        val room = rooms.computeIfAbsent(roomId) { WatchSessionState(roomId, maxParticipants) }
        when (decoded) {
            is JoinMessage -> handleJoin(conn, room, decoded)
            is LeaveMessage -> handleLeave(conn, room, decoded)
            is StateMessage -> handleState(conn, room, decoded)
            is SeekMessage -> handleSeek(conn, room, decoded)
            else -> sendTo(
                conn, ErrorMessage("unexpected_type", "server only accepts join/leave/state/seek")
            )
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        removeParticipant(conn)
    }

    override fun onError(conn: WebSocket, ex: Exception) {
        logger("connection error: $ex")
        removeParticipant(conn)
    }

    // region Message handling

    private fun handleJoin(conn: WebSocket, room: WatchSessionState, msg: JoinMessage) {
        val event = synchronized(room) { room.join(msg.participantId, msg.displayName, nowMs()) }
        when (event) {
            is WatchEvent.ParticipantJoined -> {
                connectionParticipant[conn] = msg.participantId
                val hostId = synchronized(room) { room.hostId }
                sendTo(
                    conn,
                    WelcomeMessage(
                        participantId = msg.participantId,
                        hostParticipantId = hostId ?: msg.participantId,
                        participants = synchronized(room) { room.participantsList },
                        snapshot = event.snapshot,
                    )
                )
                broadcast(room, conn, JoinedMessage(event.participant, room.participantsList))
            }

            is WatchEvent.Rejected -> sendTo(conn, ErrorMessage(event.code, event.reason))
            else -> Unit
        }
    }

    private fun handleLeave(conn: WebSocket, room: WatchSessionState, msg: LeaveMessage) {
        val registered = connectionParticipant[conn]
        if (registered == null || registered != msg.participantId) {
            sendTo(conn, ErrorMessage("not_joined", "connection is not joined as ${msg.participantId}"))
            return
        }
        leaveAndBroadcast(conn, room, msg.participantId)
    }

    private fun handleState(conn: WebSocket, room: WatchSessionState, msg: StateMessage) {
        val participantId = connectionParticipant[conn]
        if (participantId == null) {
            sendTo(conn, ErrorMessage("not_joined", "join the room before publishing state"))
            return
        }
        val event = synchronized(room) { room.applyState(participantId, msg, nowMs()) }
        when (event) {
            is WatchEvent.StateChanged -> broadcast(room, conn, StateMessage(
                playing = event.snapshot.playing,
                positionMs = event.snapshot.positionMs,
                playbackRate = event.snapshot.playbackRate,
                mediaTitle = event.snapshot.mediaTitle,
                mediaUrl = event.snapshot.mediaUrl,
                mediaDurationMs = event.snapshot.mediaDurationMs,
            ))

            is WatchEvent.Rejected -> sendTo(conn, ErrorMessage(event.code, event.reason))
            else -> Unit
        }
    }

    private fun handleSeek(conn: WebSocket, room: WatchSessionState, msg: SeekMessage) {
        val participantId = connectionParticipant[conn]
        if (participantId == null) {
            sendTo(conn, ErrorMessage("not_joined", "join the room before seeking"))
            return
        }
        val event = synchronized(room) { room.applySeek(participantId, msg, nowMs()) }
        when (event) {
            is WatchEvent.Seeked -> broadcast(room, conn, SeekMessage(event.positionMs))
            is WatchEvent.Rejected -> sendTo(conn, ErrorMessage(event.code, event.reason))
            else -> Unit
        }
    }

    private fun leaveAndBroadcast(conn: WebSocket, room: WatchSessionState, participantId: String) {
        val event = synchronized(room) { room.leave(participantId, nowMs()) }
        connectionParticipant.remove(conn)
        when (event) {
            is WatchEvent.ParticipantLeft -> broadcast(
                room, conn,
                LeftMessage(
                    participantId = participantId,
                    newHostParticipantId = event.newHostId,
                    participants = synchronized(room) { room.participantsList },
                )
            )

            else -> Unit
        }
        if (synchronized(room) { room.isEmpty }) {
            rooms.remove(room.roomId, room)
        }
    }

    private fun removeParticipant(conn: WebSocket) {
        val roomId = connectionRoom.remove(conn) ?: return
        val participantId = connectionParticipant.remove(conn) ?: return
        val room = rooms[roomId] ?: return
        leaveAndBroadcast(conn, room, participantId)
    }

    // endregion

    // region Helpers

    private fun broadcast(room: WatchSessionState, sender: WebSocket, message: WatchMessage) {
        val payload = WatchMessageCodec.encode(message)
        for (conn in connections) {
            if (conn != sender && connectionRoom[conn] == room.roomId) {
                sendTo(conn, payload)
            }
        }
    }

    private fun sendTo(conn: WebSocket, message: WatchMessage) {
        sendTo(conn, WatchMessageCodec.encode(message))
    }

    private fun sendTo(conn: WebSocket, payload: String) {
        if (conn.isOpen) {
            conn.send(payload)
        }
    }

    private fun parseRoomId(descriptor: String): String? {
        if (descriptor.isBlank()) return null
        val segments = descriptor.substringBefore('?')
            .split('/')
            .filter { it.isNotEmpty() }
        return if (segments.size == 2 &&
            segments[0] == WatchTogetherProtocol.ROOM_PATH &&
            segments[1].isNotBlank()
        ) {
            segments[1]
        } else {
            null
        }
    }

    /**
     * IPv4 addresses of this host, for advertising the server over LAN.
     * Returns an empty list when no non-loopback IPv4 address is available.
     */
    fun localIpAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
    } catch (e: Exception) {
        emptyList()
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    // endregion
}
