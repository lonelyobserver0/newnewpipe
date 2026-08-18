package org.newnewpipe.app.watchtogether

/**
 * Pure, single-room state machine of a watch-together session (plan 022,
 * S15). No I/O and no Android dependencies: the server feeds it decoded
 * messages and broadcasts the returned [WatchEvent]s, which keeps every
 * transition unit-testable on the JVM.
 *
 * Rules enforced here:
 *  - the first participant to join becomes the host;
 *  - only the host may publish [StateMessage]/[SeekMessage];
 *  - a room admits at most [maxParticipants] participants;
 *  - when the host leaves, the oldest remaining participant takes over;
 *  - seeks are clamped to `[0, mediaDurationMs]` when the duration is known.
 */
class WatchSessionState(
    val roomId: String,
    val maxParticipants: Int = WatchTogetherProtocol.DEFAULT_MAX_PARTICIPANTS,
) {
    private val participants = LinkedHashMap<String, Participant>()

    /** Authoritative playback state, only mutated by the host. */
    var snapshot: PlaybackSnapshot = PlaybackSnapshot()
        private set

    /** Participants in join order (host first while it is the first joiner). */
    val participantsList: List<Participant>
        get() = participants.values.toList()

    val isEmpty: Boolean get() = participants.isEmpty()
    val isFull: Boolean get() = participants.size >= maxParticipants

    /** Id of the current host, or `null` when the room is empty. */
    val hostId: String?
        get() = participants.values.firstOrNull { it.isHost }?.id

    /**
     * Adds a participant. Rejected (with [WatchEvent.Rejected]) when the id
     * is blank, already joined or the room is full. The first participant
     * becomes the host.
     */
    fun join(participantId: String, displayName: String, nowMs: Long): WatchEvent? {
        if (participantId.isBlank()) {
            return WatchEvent.Rejected("invalid_participant", "participant id is blank")
        }
        if (participants.containsKey(participantId)) {
            return WatchEvent.Rejected("already_joined", "participant $participantId is already in the room")
        }
        if (isFull) {
            return WatchEvent.Rejected("room_full", "room $roomId is full (max $maxParticipants)")
        }
        val isHost = participants.isEmpty()
        val participant = Participant(
            id = participantId,
            displayName = displayName.ifBlank { participantId },
            isHost = isHost,
            joinedAtMs = nowMs,
        )
        participants[participantId] = participant
        return WatchEvent.ParticipantJoined(participant, snapshot)
    }

    /**
     * Removes a participant. `null` when the id was not in the room. If the
     * host leaves and the room is not empty, the oldest remaining
     * participant is promoted to host.
     */
    fun leave(participantId: String, nowMs: Long): WatchEvent? {
        val removed = participants.remove(participantId) ?: return null
        val newHostId: String? = if (removed.isHost && participants.isNotEmpty()) {
            val newHost = participants.values.first()
            participants[newHost.id] = newHost.copy(isHost = true)
            newHost.id
        } else {
            null
        }
        return WatchEvent.ParticipantLeft(participantId, newHostId, snapshot)
    }

    /**
     * Applies a host state update. Rejected when the sender is not the host
     * or the payload is invalid (negative position, non-positive rate).
     */
    fun applyState(hostId: String, message: StateMessage, nowMs: Long): WatchEvent? {
        if (hostId != this.hostId) {
            return WatchEvent.Rejected("not_host", "only the host can publish state")
        }
        if (message.positionMs < 0) {
            return WatchEvent.Rejected("invalid_position", "position cannot be negative")
        }
        if (message.playbackRate <= 0f) {
            return WatchEvent.Rejected("invalid_rate", "playback rate must be positive")
        }
        snapshot = PlaybackSnapshot(
            playing = message.playing,
            positionMs = message.positionMs,
            playbackRate = message.playbackRate,
            mediaTitle = message.mediaTitle,
            mediaUrl = message.mediaUrl,
            mediaDurationMs = message.mediaDurationMs,
            updatedAtMs = nowMs,
        )
        return WatchEvent.StateChanged(snapshot)
    }

    /**
     * Applies a host position jump, clamped to `[0, mediaDurationMs]` when
     * the duration is known. Rejected when the sender is not the host.
     */
    fun applySeek(hostId: String, message: SeekMessage, nowMs: Long): WatchEvent? {
        if (hostId != this.hostId) {
            return WatchEvent.Rejected("not_host", "only the host can seek")
        }
        val mediaDurationMs = snapshot.mediaDurationMs
        val clamped = when {
            message.positionMs < 0 -> 0L
            mediaDurationMs != null && message.positionMs > mediaDurationMs -> mediaDurationMs
            else -> message.positionMs
        }
        snapshot = snapshot.copy(positionMs = clamped, updatedAtMs = nowMs)
        return WatchEvent.Seeked(clamped, snapshot)
    }
}

/** Outcome of a state machine transition, to be broadcast by the server. */
sealed class WatchEvent {
    /** A participant joined (already present in [snapshot]-carrying event). */
    data class ParticipantJoined(
        val participant: Participant,
        val snapshot: PlaybackSnapshot,
    ) : WatchEvent()

    /** A participant left; [newHostId] is set when the host was reassigned. */
    data class ParticipantLeft(
        val participantId: String,
        val newHostId: String?,
        val snapshot: PlaybackSnapshot,
    ) : WatchEvent()

    /** The host published a new playback state. */
    data class StateChanged(val snapshot: PlaybackSnapshot) : WatchEvent()

    /** The host jumped to a new position. */
    data class Seeked(
        val positionMs: Long,
        val snapshot: PlaybackSnapshot,
    ) : WatchEvent()

    /** The message was rejected; [code] is a stable machine-readable code. */
    data class Rejected(
        val code: String,
        val reason: String,
    ) : WatchEvent()
}
