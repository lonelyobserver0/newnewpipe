package org.newnewpipe.app.casting

/**
 * Un dispositivo DLNA scoperto in rete (TV, renderer, media server).
 * [udn] è l'identità UPnP stabile del dispositivo (univoca sulla LAN),
 * [friendlyName] il nome mostrato all'utente.
 */
data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    /** true se il dispositivo espone il servizio AVTransport (renderer DLNA). */
    val hasAvTransport: Boolean,
) {
    /** Un dispositivo è "castabile" se può ricevere lo stream (AVTransport). */
    val isCastable: Boolean
        get() = hasAvTransport
}
