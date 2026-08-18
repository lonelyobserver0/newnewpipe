package org.newnewpipe.app.casting

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.IBinder
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.message.header.STAllHeader
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.RemoteService
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry

/**
 * Backend jupnp (org.jupnp 3.0.4, D-2): discovery SSDP via
 * [AndroidUpnpServiceImpl] (servizio bound, nessuna dipendenza GMS) e
 * controllo del renderer via AVTransport (SetAVTransportURI + Play).
 *
 * I callback jupnp arrivano su thread di rete: [DlnaBackend.Listener] e
 * [DlnaBackend.cast] sono chiamati fuori dal main thread — chi consuma il
 * backend deve postare sul main thread se tocca la UI.
 */
class JupnpDlnaBackend(private val context: Context) : DlnaBackend {

    private var listener: DlnaBackend.Listener? = null
    private var upnpService: AndroidUpnpService? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** Dispositivi remoti per UDN: servono al cast (AVTransport) oltre che alla lista. */
    private val devicesByUdn = LinkedHashMap<String, RemoteDevice>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder as? AndroidUpnpService ?: return
            upnpService = service
            service.registry.addListener(registryListener)
            // Seed dei dispositivi già noti (il servizio poteva essere già attivo).
            for (device in service.registry.devices) {
                if (device is RemoteDevice) {
                    deviceAdded(device)
                }
            }
            listener?.onStarted()
            service.controlPoint.search(STAllHeader())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            upnpService = null
        }
    }

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            deviceAdded(device)
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            val removed = devicesByUdn.remove(device.identity.udn.identifierString)
            if (removed != null) {
                listener?.onDeviceLost(removed.toDlnaDevice())
            }
        }
    }

    override fun start(listener: DlnaBackend.Listener) {
        this.listener = listener
        acquireMulticastLock()
        val intent = Intent(context, AndroidUpnpServiceImpl::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun stop() {
        listener = null
        upnpService?.registry?.removeListener(registryListener)
        runCatching { context.unbindService(serviceConnection) }
        upnpService = null
        devicesByUdn.clear()
        releaseMulticastLock()
    }

    override fun search() {
        upnpService?.controlPoint?.search(STAllHeader())
    }

    override fun cast(
        device: DlnaDevice,
        streamUrl: String,
        onResult: DlnaBackend.CastCallback,
    ) {
        val upnp = upnpService
        val remote = devicesByUdn[device.udn]
        if (upnp == null) {
            onResult.onResult(false, "UPnP non connesso")
            return
        }
        if (remote == null) {
            onResult.onResult(false, "Dispositivo non più in rete")
            return
        }
        val avTransport = remote.findService(UDAServiceType("AVTransport", 1))
        if (avTransport == null) {
            onResult.onResult(false, "Il dispositivo non espone AVTransport")
            return
        }
        val setUri = ActionInvocation(avTransport.getAction("SetAVTransportURI"))
        DlnaCastInputs.setAvTransportUri(streamUrl).forEach { (name, value) ->
            setUri.setInput(name, value)
        }
        upnp.controlPoint.execute(object : ActionCallback(setUri) {
            override fun success(invocation: ActionInvocation<*>) {
                play(upnp, avTransport, onResult)
            }

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse,
                defaultMsg: String,
            ) {
                onResult.onResult(false, defaultMsg)
            }
        })
    }

    private fun play(
        upnp: AndroidUpnpService,
        avTransport: RemoteService,
        onResult: DlnaBackend.CastCallback,
    ) {
        val play = ActionInvocation(avTransport.getAction("Play"))
        DlnaCastInputs.play().forEach { (name, value) ->
            play.setInput(name, value)
        }
        upnp.controlPoint.execute(object : ActionCallback(play) {
            override fun success(invocation: ActionInvocation<*>) {
                onResult.onResult(true, null)
            }

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse,
                defaultMsg: String,
            ) {
                onResult.onResult(false, defaultMsg)
            }
        })
    }

    private fun deviceAdded(device: RemoteDevice) {
        val normalized = device.toDlnaDevice()
        devicesByUdn[normalized.udn] = device
        listener?.onDeviceFound(normalized)
    }

    /** Su Wi-Fi serve il multicast lock per ricevere le risposte SSDP. */
    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            multicastLock = wifi.createMulticastLock(TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private fun RemoteDevice.toDlnaDevice(): DlnaDevice = DlnaDevice(
        udn = identity.udn.identifierString,
        friendlyName = details?.friendlyName ?: displayString,
        hasAvTransport = findService(UDAServiceType("AVTransport", 1)) != null,
    )

    companion object {
        private const val TAG = "JupnpDlnaBackend"
    }
}
