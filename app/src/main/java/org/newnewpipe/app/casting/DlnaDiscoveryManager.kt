package org.newnewpipe.app.casting

import android.content.Context

/**
 * Facade app-level del casting DLNA: possiede il [DlnaBackend] (jupnp) e il
 * [DlnaDeviceRegistry] puro, ed espone alla UI lo snapshot dei dispositivi.
 *
 * Singleton con application context (nessun leak). [start] è idempotente:
 * la UI del player lo chiama all'apertura del dialogo cast e [stop] rilascia
 * le risorse di rete quando il dialogo si chiude.
 */
class DlnaDiscoveryManager private constructor(
    private val backend: DlnaBackend,
    private val registry: DlnaDeviceRegistry = DlnaDeviceRegistry(),
) {

    /** Callback SAM (fun interface): i lambda Java funzionano. */
    fun interface DevicesListener {
        fun onDevicesChanged(devices: List<DlnaDevice>)
    }

    /** Dispositivi correnti (renderer per primi, ordinati per nome). */
    val devices: List<DlnaDevice>
        get() = registry.devices

    /**
     * Notificato a ogni cambiamento dello snapshot (i callback jupnp NON sono
     * sul main thread: chi consuma deve postare sul main se tocca la UI).
     */
    var devicesListener: DevicesListener? = null

    private var started = false

    init {
        registry.onDevicesChanged = { devices ->
            devicesListener?.onDevicesChanged(devices)
        }
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        backend.start(object : DlnaBackend.Listener {
            override fun onDeviceFound(device: DlnaDevice) {
                registry.onDeviceFound(device)
            }

            override fun onDeviceLost(device: DlnaDevice) {
                registry.onDeviceLost(device)
            }

            override fun onStarted() {
                // Il bind è asincrono: al primo avvio la lista si popola da sola
                // con gli eventi del registry (nessuna UI da aggiornare qui).
            }
        })
    }

    fun stop() {
        if (!started) {
            return
        }
        started = false
        backend.stop()
        registry.clear()
    }

    /** Nuova ricerca SSDP: i dispositivi rispondono entro qualche secondo. */
    fun search() {
        backend.search()
    }

    /**
     * Avvia lo stream sul renderer. [onResult] è chiamato su un thread di
     * rete: chi chiama deve postare sul main thread se tocca la UI.
     */
    fun cast(device: DlnaDevice, streamUrl: String, onResult: DlnaBackend.CastCallback) {
        backend.cast(device, streamUrl, onResult)
    }

    companion object {
        @Volatile
        private var instance: DlnaDiscoveryManager? = null

        /** Singleton app-level (application context, nessun leak). */
        @JvmStatic
        fun get(context: Context): DlnaDiscoveryManager {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appContext = context.applicationContext
                return DlnaDiscoveryManager(JupnpDlnaBackend(appContext)).also { instance = it }
            }
        }
    }
}
