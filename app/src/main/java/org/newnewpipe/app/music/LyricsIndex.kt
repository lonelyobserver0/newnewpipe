package org.newnewpipe.app.music

/**
 * Indice sulle righe LRC ordinate per timestamp: dato un timestamp di
 * riproduzione (media3 position) restituisce l'indice della riga corrente.
 *
 * Puro e JVM-testabile — è il cuore dell'overlay lyrics (022-S11), separato
 * dalla UI proprio per essere coperto dagli unit test ("unit test del
 * calcolo riga corrente" nella Verify del piano). Le righe arrivano già
 * ordinate da [LrcParser.parse].
 */
class LyricsIndex(private val lines: List<LrcLine>) {

    init {
        require(lines.zipWithNext().all { (a, b) -> a.timestampMs <= b.timestampMs }) {
            "LyricsIndex: righe LRC non ordinate per timestamp"
        }
    }

    val size: Int
        get() = lines.size

    /**
     * Indice della riga corrente alla posizione [positionMs] (in millisecondi):
     * l'ultima riga il cui timestamp di inizio è <= posizione.
     *
     * @return indice in [0, size-1], oppure -1 se la posizione precede la
     * prima riga o non ci sono righe sincronizzate. O(log n) — ricerca binaria.
     */
    fun lineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) {
            return -1
        }
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
