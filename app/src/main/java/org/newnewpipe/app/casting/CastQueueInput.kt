package org.newnewpipe.app.casting

/**
 * Input PURO per il trasporto della play queue verso Chromecast (022-S3).
 * Non dipende da PlayQueueItem (che trascina Android/ExtractorHelper) così la
 * mappatura è testabile su JVM: chi chiama (Player.java) adatta i propri item.
 *
 * @param queueIndex indice della voce nella play queue LOCALE (preservato dal mapper)
 * @param title titolo del brano/video
 * @param originalUrl URL della pagina (usato come identità nel customData)
 * @param thumbnailUrl miniatura o null
 * @param durationMs durata in millisecondi (0 se ignota)
 * @param progressiveUrls URL progressive (MP4/WebM con audio) disponibili
 * @param hlsUrl URL HLS (playlist .m3u8) o null
 */
data class CastQueueInput(
    val queueIndex: Int,
    val title: String,
    val originalUrl: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val progressiveUrls: List<String>,
    val hlsUrl: String?,
)
