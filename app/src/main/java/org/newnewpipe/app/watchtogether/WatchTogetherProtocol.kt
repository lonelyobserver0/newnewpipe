package org.newnewpipe.app.watchtogether

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter

/**
 * Wire protocol of the watch-together feature (plan 022, S15, decision D-6):
 * a JSON envelope per WebSocket text frame, no central service — the host
 * app embeds [WatchTogetherServer] and peers connect over LAN.
 *
 * Every message carries a `type` discriminator and an epoch-millis `ts`.
 * Client → server: `join`, `leave`, `state`, `seek`.
 * Server → client: `welcome`, `joined`, `left`, `state`, `seek`, `error`.
 */
object WatchTogetherProtocol {
    /** Protocol version reported by clients; bumped on breaking changes. */
    const val VERSION = 1

    /** Default TCP port of the embedded server. */
    const val DEFAULT_PORT = 8420

    /** Maximum participants per room (host included). */
    const val DEFAULT_MAX_PARTICIPANTS = 8

    /** Path prefix on the WebSocket server: `ws://<host>:<port>/watch/<roomId>`. */
    const val ROOM_PATH = "watch"

    // Message types
    const val TYPE_JOIN = "join"
    const val TYPE_LEAVE = "leave"
    const val TYPE_STATE = "state"
    const val TYPE_SEEK = "seek"
    const val TYPE_WELCOME = "welcome"
    const val TYPE_JOINED = "joined"
    const val TYPE_LEFT = "left"
    const val TYPE_ERROR = "error"

    // JSON keys
    internal const val KEY_TYPE = "type"
    internal const val KEY_VERSION = "v"
    internal const val KEY_TS = "ts"
    internal const val KEY_PARTICIPANT_ID = "participantId"
    internal const val KEY_DISPLAY_NAME = "displayName"
    internal const val KEY_IS_HOST = "isHost"
    internal const val KEY_HOST_PARTICIPANT_ID = "hostParticipantId"
    internal const val KEY_NEW_HOST_PARTICIPANT_ID = "newHostParticipantId"
    internal const val KEY_PARTICIPANT = "participant"
    internal const val KEY_PARTICIPANTS = "participants"
    internal const val KEY_PLAYING = "playing"
    internal const val KEY_POSITION_MS = "positionMs"
    internal const val KEY_PLAYBACK_RATE = "playbackRate"
    internal const val KEY_MEDIA_TITLE = "mediaTitle"
    internal const val KEY_MEDIA_URL = "mediaUrl"
    internal const val KEY_MEDIA_DURATION_MS = "mediaDurationMs"
    internal const val KEY_STATE = "state"
    internal const val KEY_CODE = "code"
    internal const val KEY_MESSAGE = "message"
}

/** One participant of a room. */
data class Participant(
    val id: String,
    val displayName: String,
    val isHost: Boolean,
    val joinedAtMs: Long,
)

/** Authoritative playback state, broadcast by the host. */
data class PlaybackSnapshot(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val playbackRate: Float = 1f,
    val mediaTitle: String? = null,
    val mediaUrl: String? = null,
    val mediaDurationMs: Long? = null,
    val updatedAtMs: Long = 0L,
)

/** Base type of every frame on the wire. */
sealed class WatchMessage {
    abstract val sentAtMs: Long
}

// region Client → server

/** Announces a participant and asks to enter the room. */
data class JoinMessage(
    val participantId: String,
    val displayName: String,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Explicitly leaves the room (also implied by closing the WebSocket). */
data class LeaveMessage(
    val participantId: String,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Host → server: authoritative playback state to mirror to peers. */
data class StateMessage(
    val playing: Boolean,
    val positionMs: Long,
    val playbackRate: Float = 1f,
    val mediaTitle: String? = null,
    val mediaUrl: String? = null,
    val mediaDurationMs: Long? = null,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Host → server: an explicit position jump to mirror to peers. */
data class SeekMessage(
    val positionMs: Long,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

// endregion

// region Server → client

/** Sent to a participant right after a successful [JoinMessage]. */
data class WelcomeMessage(
    val participantId: String,
    val hostParticipantId: String,
    val participants: List<Participant>,
    val snapshot: PlaybackSnapshot,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Broadcast to the other peers when someone joins. */
data class JoinedMessage(
    val participant: Participant,
    val participants: List<Participant>,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Broadcast to the remaining peers when someone leaves. */
data class LeftMessage(
    val participantId: String,
    val newHostParticipantId: String?,
    val participants: List<Participant>,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

/** Server rejection of a client message. */
data class ErrorMessage(
    val code: String,
    val message: String,
    override val sentAtMs: Long = System.currentTimeMillis(),
) : WatchMessage()

// endregion

/**
 * Nanojson-based codec for [WatchMessage]. Pure JVM: fully unit-testable
 * without Android. Unknown types and malformed JSON decode to `null`
 * (the server answers with an [ErrorMessage] instead of crashing).
 */
object WatchMessageCodec {

    fun encode(message: WatchMessage): String {
        val writer = JsonWriter.string()
        val root = writer.`object`()
            .value(WatchTogetherProtocol.KEY_TYPE, message.type())
            .value(WatchTogetherProtocol.KEY_VERSION, WatchTogetherProtocol.VERSION)
            .value(WatchTogetherProtocol.KEY_TS, message.sentAtMs)

        when (message) {
            is JoinMessage -> root
                .value(WatchTogetherProtocol.KEY_PARTICIPANT_ID, message.participantId)
                .value(WatchTogetherProtocol.KEY_DISPLAY_NAME, message.displayName)

            is LeaveMessage -> root
                .value(WatchTogetherProtocol.KEY_PARTICIPANT_ID, message.participantId)

            is StateMessage -> root
                .value(WatchTogetherProtocol.KEY_PLAYING, message.playing)
                .value(WatchTogetherProtocol.KEY_POSITION_MS, message.positionMs)
                .value(WatchTogetherProtocol.KEY_PLAYBACK_RATE, message.playbackRate)
                .value(WatchTogetherProtocol.KEY_MEDIA_TITLE, message.mediaTitle)
                .value(WatchTogetherProtocol.KEY_MEDIA_URL, message.mediaUrl)
                .value(WatchTogetherProtocol.KEY_MEDIA_DURATION_MS, message.mediaDurationMs)

            is SeekMessage -> root
                .value(WatchTogetherProtocol.KEY_POSITION_MS, message.positionMs)

            is WelcomeMessage -> root
                .value(WatchTogetherProtocol.KEY_PARTICIPANT_ID, message.participantId)
                .value(WatchTogetherProtocol.KEY_HOST_PARTICIPANT_ID, message.hostParticipantId)
                .`object`(WatchTogetherProtocol.KEY_STATE)
                .also { writeSnapshot(it, message.snapshot) }
                .end()
                .`array`(WatchTogetherProtocol.KEY_PARTICIPANTS)
                .also { writeParticipants(it, message.participants) }
                .end()

            is JoinedMessage -> root
                .`object`(WatchTogetherProtocol.KEY_PARTICIPANT)
                .also { writeParticipant(it, message.participant) }
                .end()
                .`array`(WatchTogetherProtocol.KEY_PARTICIPANTS)
                .also { writeParticipants(it, message.participants) }
                .end()

            is LeftMessage -> root
                .value(WatchTogetherProtocol.KEY_PARTICIPANT_ID, message.participantId)
                .value(WatchTogetherProtocol.KEY_NEW_HOST_PARTICIPANT_ID, message.newHostParticipantId)
                .`array`(WatchTogetherProtocol.KEY_PARTICIPANTS)
                .also { writeParticipants(it, message.participants) }
                .end()

            is ErrorMessage -> root
                .value(WatchTogetherProtocol.KEY_CODE, message.code)
                .value(WatchTogetherProtocol.KEY_MESSAGE, message.message)
        }

        return root.end().done()
    }

    /**
     * Decodes a frame, or returns `null` when the payload is not valid JSON
     * or uses an unknown/unexpected message type.
     */
    fun decode(json: String): WatchMessage? {
        val root = try {
            JsonParser.`object`().from(json)
        } catch (e: JsonParserException) {
            return null
        }
        val type = root.getString(WatchTogetherProtocol.KEY_TYPE, "")
        val ts = root.getLong(WatchTogetherProtocol.KEY_TS, System.currentTimeMillis())
        return when (type) {
            WatchTogetherProtocol.TYPE_JOIN -> JoinMessage(
                participantId = root.getString(WatchTogetherProtocol.KEY_PARTICIPANT_ID, ""),
                displayName = root.getString(WatchTogetherProtocol.KEY_DISPLAY_NAME, ""),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_LEAVE -> LeaveMessage(
                participantId = root.getString(WatchTogetherProtocol.KEY_PARTICIPANT_ID, ""),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_STATE -> StateMessage(
                playing = root.getBoolean(WatchTogetherProtocol.KEY_PLAYING, false),
                positionMs = root.getLong(WatchTogetherProtocol.KEY_POSITION_MS, 0L),
                playbackRate = root.getDouble(WatchTogetherProtocol.KEY_PLAYBACK_RATE, 1.0).toFloat(),
                mediaTitle = root.getString(WatchTogetherProtocol.KEY_MEDIA_TITLE, null),
                mediaUrl = root.getString(WatchTogetherProtocol.KEY_MEDIA_URL, null),
                mediaDurationMs = root.getLong(WatchTogetherProtocol.KEY_MEDIA_DURATION_MS, -1L)
                    .takeIf { it >= 0 },
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_SEEK -> SeekMessage(
                positionMs = root.getLong(WatchTogetherProtocol.KEY_POSITION_MS, 0L),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_WELCOME -> WelcomeMessage(
                participantId = root.getString(WatchTogetherProtocol.KEY_PARTICIPANT_ID, ""),
                hostParticipantId = root.getString(
                    WatchTogetherProtocol.KEY_HOST_PARTICIPANT_ID, ""
                ),
                participants = decodeParticipants(root),
                snapshot = decodeSnapshot(
                    root.getObject(WatchTogetherProtocol.KEY_STATE, JsonObject())
                ),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_JOINED -> JoinedMessage(
                participant = decodeParticipant(
                    root.getObject(WatchTogetherProtocol.KEY_PARTICIPANT, JsonObject())
                ),
                participants = decodeParticipants(root),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_LEFT -> LeftMessage(
                participantId = root.getString(WatchTogetherProtocol.KEY_PARTICIPANT_ID, ""),
                newHostParticipantId = root.getString(
                    WatchTogetherProtocol.KEY_NEW_HOST_PARTICIPANT_ID, null
                ),
                participants = decodeParticipants(root),
                sentAtMs = ts,
            )

            WatchTogetherProtocol.TYPE_ERROR -> ErrorMessage(
                code = root.getString(WatchTogetherProtocol.KEY_CODE, "unknown"),
                message = root.getString(WatchTogetherProtocol.KEY_MESSAGE, ""),
                sentAtMs = ts,
            )

            else -> null
        }
    }

    private fun WatchMessage.type(): String = when (this) {
        is JoinMessage -> WatchTogetherProtocol.TYPE_JOIN
        is LeaveMessage -> WatchTogetherProtocol.TYPE_LEAVE
        is StateMessage -> WatchTogetherProtocol.TYPE_STATE
        is SeekMessage -> WatchTogetherProtocol.TYPE_SEEK
        is WelcomeMessage -> WatchTogetherProtocol.TYPE_WELCOME
        is JoinedMessage -> WatchTogetherProtocol.TYPE_JOINED
        is LeftMessage -> WatchTogetherProtocol.TYPE_LEFT
        is ErrorMessage -> WatchTogetherProtocol.TYPE_ERROR
    }

    private fun writeParticipant(writer: JsonStringWriter, p: Participant) {
        writer.value(WatchTogetherProtocol.KEY_PARTICIPANT_ID, p.id)
            .value(WatchTogetherProtocol.KEY_DISPLAY_NAME, p.displayName)
            .value(WatchTogetherProtocol.KEY_IS_HOST, p.isHost)
            .value(WatchTogetherProtocol.KEY_TS, p.joinedAtMs)
    }

    private fun writeParticipants(
        writer: JsonStringWriter,
        participants: List<Participant>,
    ) {
        for (p in participants) {
            writer.`object`()
            writeParticipant(writer, p)
            writer.end()
        }
    }

    private fun writeSnapshot(
        writer: JsonStringWriter,
        snapshot: PlaybackSnapshot,
    ) {
        writer.value(WatchTogetherProtocol.KEY_PLAYING, snapshot.playing)
            .value(WatchTogetherProtocol.KEY_POSITION_MS, snapshot.positionMs)
            .value(WatchTogetherProtocol.KEY_PLAYBACK_RATE, snapshot.playbackRate)
            .value(WatchTogetherProtocol.KEY_MEDIA_TITLE, snapshot.mediaTitle)
            .value(WatchTogetherProtocol.KEY_MEDIA_URL, snapshot.mediaUrl)
            .value(WatchTogetherProtocol.KEY_MEDIA_DURATION_MS, snapshot.mediaDurationMs)
            .value(WatchTogetherProtocol.KEY_TS, snapshot.updatedAtMs)
    }

    private fun decodeParticipant(obj: JsonObject): Participant {
        return Participant(
            id = obj.getString(WatchTogetherProtocol.KEY_PARTICIPANT_ID, ""),
            displayName = obj.getString(WatchTogetherProtocol.KEY_DISPLAY_NAME, ""),
            isHost = obj.getBoolean(WatchTogetherProtocol.KEY_IS_HOST, false),
            joinedAtMs = obj.getLong(WatchTogetherProtocol.KEY_TS, 0L),
        )
    }

    private fun decodeParticipants(root: JsonObject): List<Participant> {
        val array: JsonArray = root.getArray(
            WatchTogetherProtocol.KEY_PARTICIPANTS, JsonArray()
        )
        val result = ArrayList<Participant>(array.size)
        for (element in array) {
            (element as? JsonObject)?.let { result.add(decodeParticipant(it)) }
        }
        return result
    }

    private fun decodeSnapshot(obj: JsonObject): PlaybackSnapshot {
        return PlaybackSnapshot(
            playing = obj.getBoolean(WatchTogetherProtocol.KEY_PLAYING, false),
            positionMs = obj.getLong(WatchTogetherProtocol.KEY_POSITION_MS, 0L),
            playbackRate = obj.getDouble(WatchTogetherProtocol.KEY_PLAYBACK_RATE, 1.0).toFloat(),
            mediaTitle = obj.getString(WatchTogetherProtocol.KEY_MEDIA_TITLE, null),
            mediaUrl = obj.getString(WatchTogetherProtocol.KEY_MEDIA_URL, null),
            mediaDurationMs = obj.getLong(WatchTogetherProtocol.KEY_MEDIA_DURATION_MS, -1L)
                .takeIf { it >= 0 },
            updatedAtMs = obj.getLong(WatchTogetherProtocol.KEY_TS, 0L),
        )
    }
}
