package org.newnewpipe.app.music

/** Una riga LRC sincronizzata: testo + timestamp di inizio in millisecondi. */
data class LrcLine(val timestampMs: Long, val text: String)

/**
 * Parser del formato LRC (https://en.wikipedia.org/wiki/LRC_(file_format)).
 * Puro (nessuna dipendenza Android/network): è la parte coperta dagli unit test.
 *
 * Supporta i tag `[mm:ss]` e `[mm:ss.xx]` (frazioni in centesimi, come da
 * convenzione LRC), anche più tag sulla stessa riga. Le righe senza timestamp
 * (es. metadata `[ti:...]`, `[ar:...]`, `[offset:...]`) non vengono sincronizzate
 * e sono scartate: l'overlay del player consuma solo righe con posizione nota.
 */
object LrcParser {

    /** Tag temporale LRC: `[mm:ss]` o `[mm:ss.xx]` (frazione 1-3 cifre). */
    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * Parsa un testo LRC in righe ordinate per timestamp.
     * Una riga con N tag produce N [LrcLine] (stesso testo, timestamp diversi).
     */
    fun parse(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (raw in lrc.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                continue
            }
            val tags = TIME_TAG_REGEX.findAll(line).toList()
            if (tags.isEmpty()) {
                continue // metadata o riga non sincronizzata
            }
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) {
                continue
            }
            for (tag in tags) {
                val ms = timestampToMs(tag.value) ?: continue
                lines += LrcLine(ms, text)
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    /**
     * Converte un singolo tag temporale LRC (es. `[00:27.93]`) in millisecondi.
     * @return millisecondi dal minuto zero, oppure null se il tag non è un
     * timestamp valido (`[mm:ss]` con secondi ≥ 60 o formato diverso).
     */
    fun timestampToMs(tag: String): Long? {
        val match = TIME_TAG_REGEX.matchEntire(tag.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60L) {
            return null
        }
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L // 1 cifra = decimi
            2 -> fraction.toLong() * 10L // 2 cifre = centesimi (convenzione LRC)
            else -> fraction.take(3).toLong() // 3+ cifre = millesimi
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}
