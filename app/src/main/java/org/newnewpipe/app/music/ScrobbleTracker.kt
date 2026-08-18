package org.newnewpipe.app.music

/**
 * Regola di scrobbling Last.fm: un brano va scrobbato quando è stato ascoltato
 * per almeno il 50% della durata OPPURE per almeno 4 minuti — qualunque soglia
 * arrivi prima (https://www.last.fm/api/scrobbling). Il tracker è puro (nessuna
 * dipendenza Android/network) ed è la parte coperta dagli unit test.
 *
 * Un brano viene scrobbato UNA sola volta: [onPosition] ritorna true solo al
 * primo superamento della soglia per il brano corrente.
 */
class ScrobbleTracker(
    private val minScrobbleDurationMs: Long = DEFAULT_MIN_SCROBBLE_MS,
) {

    companion object {
        /** 4 minuti: soglia minima di ascolto prevista da Last.fm. */
        const val DEFAULT_MIN_SCROBBLE_MS = 240_000L
    }

    /** Titolo del brano corrente (null se nessun brano tracciato). */
    var lastTitle: String? = null
        private set

    /** Artista del brano corrente. */
    var lastArtist: String? = null
        private set

    /** Durata del brano corrente in millisecondi (0/negativa se sconosciuta, es. live). */
    var lastDurationMs: Long = 0L
        private set

    /** Timestamp (epoch secondi) di quando il brano corrente ha iniziato ad essere ascoltato. */
    var startTimestampSec: Long = 0L
        private set

    private var fired = false

    /**
     * Cambia il brano tracciato. Chiamabile a ogni tick: se il brano non è
     * cambiato e non è ancora stato scrobbllato, è un no-op (il timestamp di
     * inizio ascolto resta valido).
     */
    fun onTrackChanged(title: String, artist: String, durationMs: Long) {
        if (title == lastTitle && artist == lastArtist && !fired) {
            return
        }
        lastTitle = title
        lastArtist = artist
        lastDurationMs = durationMs
        startTimestampSec = System.currentTimeMillis() / 1_000L
        fired = false
    }

    /**
     * @return true solo la prima volta che la posizione di riproduzione supera
     * la soglia (50% della durata, o [minScrobbleDurationMs] per durate
     * sconosciute/live).
     */
    fun onPosition(positionMs: Long): Boolean {
        if (fired || lastTitle == null || lastArtist == null) {
            return false
        }
        val halfDuration = if (lastDurationMs > 0L) lastDurationMs / 2L else Long.MAX_VALUE
        val threshold = minOf(halfDuration, minScrobbleDurationMs)
        if (positionMs >= threshold) {
            fired = true
            return true
        }
        return false
    }

    /** Azzera lo stato (es. cambio di lista o fine riproduzione). */
    fun reset() {
        lastTitle = null
        lastArtist = null
        lastDurationMs = 0L
        startTimestampSec = 0L
        fired = false
    }
}
