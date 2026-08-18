package org.newnewpipe.app.casting

/**
 * Registro dei dispositivi DLNA scoperti — PURO (nessuna dipendenza
 * Android/jupnp), è la parte coperta dagli unit test ("discovery mockato":
 * i test alimentano il registro con eventi di discovery simulati).
 *
 * - dedup per UDN (un dispositivo = un'unica voce, l'ultimo stato vince);
 * - ordinamento stabile: renderer (AVTransport) per primi, poi per nome;
 * - [onDevicesChanged] notificato a ogni mutazione.
 */
class DlnaDeviceRegistry {

    /** Callback su ogni cambiamento dello snapshot (chiamato dal chiamante del mutate). */
    var onDevicesChanged: ((List<DlnaDevice>) -> Unit)? = null

    private val byUdn = LinkedHashMap<String, DlnaDevice>()

    /** Dispositivo scoperto o aggiornato (es. AVTransport ora noto). */
    fun onDeviceFound(device: DlnaDevice) {
        if (byUdn[device.udn] == device) {
            return
        }
        byUdn[device.udn] = device
        notifyChanged()
    }

    /** Dispositivo uscito dalla rete. */
    fun onDeviceLost(device: DlnaDevice) {
        if (byUdn.remove(device.udn) != null) {
            notifyChanged()
        }
    }

    /** Svuota il registro (es. nuova ricerca da zero). */
    fun clear() {
        if (byUdn.isEmpty()) {
            return
        }
        byUdn.clear()
        notifyChanged()
    }

    /** Snapshot ordinato: castabili (AVTransport) per primi, poi per nome. */
    val devices: List<DlnaDevice>
        get() = byUdn.values.sortedWith(
            compareByDescending<DlnaDevice> { it.isCastable }
                .thenBy { it.friendlyName.lowercase() },
        )

    private fun notifyChanged() {
        onDevicesChanged?.invoke(devices)
    }
}
