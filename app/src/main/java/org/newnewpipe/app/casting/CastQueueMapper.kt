package org.newnewpipe.app.casting

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter

/**
 * Voce PURO della play queue pronta per il Cast SDK (022-S3): URL media scelto
 * (stessa policy D-2 del DLNA: progressivo preferito, fallback HLS) + customData
 * JSON con l'indice della queue, così il ricevitore può mappare l'item remoto a
 * quello locale.
 *
 * @param contentUrl URL media da riprodurre (già scelto dal picker)
 * @param queueIndex indice della voce nella play queue locale
 * @param customDataJson JSON serializzato passato al ricevitore (nanojson)
 */
data class CastQueueEntry(
    val title: String,
    val contentUrl: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val queueIndex: Int,
    val originalUrl: String,
    val customDataJson: String,
)

/**
 * Mappa la play queue locale in voci per il Cast SDK — PURO, testabile su JVM
 * (nessuna dipendenza GMS/Android). Riutilizza [DlnaStreamPicker] per la scelta
 * dell'URL (D-2) e serializza il customData con nanojson.
 */
object CastQueueMapper {

    /** Chiavi del customData JSON passato al ricevitore. */
    const val CUSTOM_DATA_INDEX_KEY = "queueIndex"
    const val CUSTOM_DATA_URL_KEY = "originalUrl"

    /**
     * @param inputs voci della queue locale (nell'ordine della queue)
     * @return voci pronte per il Cast SDK: quelle senza URL riproducibile
     *         (nessun progressivo né HLS) vengono ESCLUSE e l'ordine relativo
     *         delle restanti è preservato.
     */
    fun map(inputs: List<CastQueueInput>): List<CastQueueEntry> {
        val entries = ArrayList<CastQueueEntry>(inputs.size)
        for (input in inputs) {
            val url = DlnaStreamPicker.pick(input.progressiveUrls, input.hlsUrl)
                ?: continue // senza URL riproducibile l'item non può andare al ricevitore
            entries += CastQueueEntry(
                title = input.title,
                contentUrl = url,
                thumbnailUrl = input.thumbnailUrl,
                durationMs = input.durationMs,
                queueIndex = input.queueIndex,
                originalUrl = input.originalUrl,
                customDataJson = customData(input.queueIndex, input.originalUrl),
            )
        }
        return entries
    }

    /** CustomData JSON: {queueIndex: N, originalUrl: "..."} — identità per il sync. */
    fun customData(queueIndex: Int, originalUrl: String): String =
        JsonWriter.string()
            .`object`()
            .value(CUSTOM_DATA_INDEX_KEY, queueIndex)
            .value(CUSTOM_DATA_URL_KEY, originalUrl)
            .end()
            .done()

    /**
     * Legge l'indice della queue dal customData del ricevitore (per mappare lo
     * stato remoto a quello locale). Ritorna null se il JSON non è valido o la
     * chiave manca (es. item arrivati da un'altra sorgente).
     */
    fun queueIndexFromCustomData(customDataJson: String?): Int? {
        if (customDataJson.isNullOrBlank()) {
            return null
        }
        return try {
            val obj = JsonParser.`object`().from(customDataJson)
            if (obj.has(CUSTOM_DATA_INDEX_KEY)) obj.getInt(CUSTOM_DATA_INDEX_KEY) else null
        } catch (e: JsonParserException) {
            null
        }
    }
}
