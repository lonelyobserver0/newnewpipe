package org.newnewpipe.app.casting

/**
 * Backend del casting DLNA: discovery SSDP + avvio stream sul renderer.
 * L'implementazione di produzione è [JupnpDlnaBackend]; l'interfaccia esiste
 * così la logica pura (registro, input AVTransport) è testabile su JVM senza
 * stack UPnP (unit test del discovery "mockato").
 */
interface DlnaBackend {

    /** Avvia il discovery e notifica [listener] su ogni evento dispositivo. */
    fun start(listener: Listener)

    /** Ferma il discovery e rilascia le risorse di rete. */
    fun stop()

    /** Invia una ricerca SSDP (M-SEARCH) per aggiornare la lista dispositivi. */
    fun search()

    /**
     * Avvia la riproduzione di [streamUrl] sul renderer (SetAVTransportURI + Play).
     * [onResult] è chiamato su un thread di rete (chi chiama deve postare sul
     * main thread se serve UI).
     */
    fun cast(device: DlnaDevice, streamUrl: String, onResult: CastCallback)

    /** Callback SAM: i lambda Java funzionano (a differenza di FunctionN<*, Unit>). */
    fun interface CastCallback {
        fun onResult(ok: Boolean, error: String?)
    }

    interface Listener {
        /** Dispositivo scoperto o aggiornato (es. nome/AVTransport noti). */
        fun onDeviceFound(device: DlnaDevice)

        /** Dispositivo uscito dalla rete (BYEBYE/timeout del registry). */
        fun onDeviceLost(device: DlnaDevice)

        /** Discovery avviato e stack UPnP pronto (bind completato). */
        fun onStarted()
    }
}
