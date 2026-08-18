package org.newnewpipe.app.casting

/**
 * Sceglie l'URL media da inviare al renderer DLNA (D-2: URL progressivo
 * preferito per compatibilità, fallback HLS). PURO, testabile.
 */
object DlnaStreamPicker {

    /**
     * @param progressiveUrls URL progressive (MP4/WebM con audio) dello stream corrente
     * @param hlsUrl URL HLS (playlist .m3u8) o null
     * @return il primo URL progressivo non vuoto, altrimenti l'HLS, altrimenti null
     */
    fun pick(progressiveUrls: List<String>, hlsUrl: String?): String? {
        val progressive = progressiveUrls.firstOrNull { it.isNotBlank() }
        if (progressive != null) {
            return progressive
        }
        val hls = hlsUrl?.takeIf { it.isNotBlank() }
        return hls
    }
}
