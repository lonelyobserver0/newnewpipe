package org.newnewpipe.app.casting

/**
 * Input delle azioni UPnP-AV AVTransport (SetAVTransportURI / Play) — PURO,
 * testabile senza stack jupnp. `InstanceID` è fisso 0 (sorgente singola,
 * come da profilo DLNA per i renderer domestici).
 */
object DlnaCastInputs {

    /**
     * Input di `SetAVTransportURI`: imposta l'URI media da riprodurre.
     * `CurrentURIMetaData` (DIDL-Lite) è lasciato vuoto: la maggior parte dei
     * renderer DLNA riproduce l'URI diretto senza metadati.
     */
    fun setAvTransportUri(streamUrl: String): Map<String, Any> = linkedMapOf(
        "InstanceID" to 0,
        "CurrentURI" to streamUrl,
        "CurrentURIMetaData" to "",
    )

    /** Input di `Play`: avvia la riproduzione a velocità normale. */
    fun play(): Map<String, Any> = linkedMapOf(
        "InstanceID" to 0,
        "Speed" to 1,
    )
}
