package org.newnewpipe.app.casting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del discovery "mockato" (Verify S2): il registro è alimentato con gli
 * stessi eventi che il backend jupnp genera (onDeviceFound/onDeviceLost) e si
 * verifica dedup, ordinamento e notifiche. Nessuna rete né stack UPnP.
 */
class DlnaDeviceRegistryTest {

    private val tv = DlnaDevice(udn = "uuid:tv-1", friendlyName = "Living Room TV", hasAvTransport = true)
    private val server = DlnaDevice(udn = "uuid:server-1", friendlyName = "NAS Media", hasAvTransport = false)
    private val renderer = DlnaDevice(udn = "uuid:amp-1", friendlyName = "Amplifier", hasAvTransport = true)

    @Test
    fun deviceFound_appearsInSnapshot() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(tv)

        assertEquals(listOf(tv), registry.devices)
    }

    @Test
    fun deviceLost_removedFromSnapshot() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(tv)
        registry.onDeviceFound(server)

        registry.onDeviceLost(tv)

        assertEquals(listOf(server), registry.devices)
    }

    @Test
    fun sameUdn_isDeduplicatedAndUpdated() {
        val registry = DlnaDeviceRegistry()
        // Prima scoperta come media server, poi come renderer (AVTransport ora noto):
        // il registro deve tenere UNA voce, con lo stato più recente.
        val nas = DlnaDevice(udn = "uuid:nas-1", friendlyName = "NAS", hasAvTransport = false)
        registry.onDeviceFound(nas)
        registry.onDeviceFound(DlnaDevice("uuid:nas-1", "NAS", hasAvTransport = true))

        assertEquals(1, registry.devices.size)
        assertTrue(registry.devices.single().isCastable)
    }

    @Test
    fun ordering_castableFirstThenByName() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(server)   // non castabile, nome "NAS Media"
        registry.onDeviceFound(tv)       // castabile, "Living Room TV"
        registry.onDeviceFound(renderer) // castabile, "Amplifier"

        val names = registry.devices.map { it.friendlyName }
        // Castabili prima (Amplifier < Living Room TV), poi i non castabili.
        assertEquals(listOf("Amplifier", "Living Room TV", "NAS Media"), names)
    }

    @Test
    fun ordering_isCaseInsensitive() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(DlnaDevice("uuid:a", "alpha", hasAvTransport = true))
        registry.onDeviceFound(DlnaDevice("uuid:b", "Beta", hasAvTransport = true))

        assertEquals(listOf("alpha", "Beta"), registry.devices.map { it.friendlyName })
    }

    @Test
    fun listener_notifiedOnEveryChange() {
        val registry = DlnaDeviceRegistry()
        val snapshots = mutableListOf<List<DlnaDevice>>()
        registry.onDevicesChanged = { snapshots.add(it) }

        registry.onDeviceFound(tv)
        registry.onDeviceFound(tv) // aggiornamento stesso UDN con stato identico: no notify
        registry.onDeviceLost(tv)

        // 2 notifiche: found + lost (l'update identico è un no-op).
        assertEquals(2, snapshots.size)
        assertEquals(1, snapshots[0].size)
        assertTrue(snapshots[1].isEmpty())
    }

    @Test
    fun clear_emptiesRegistryAndNotifies() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(tv)
        var notified = 0
        registry.onDevicesChanged = { notified++ }

        registry.clear()

        assertTrue(registry.devices.isEmpty())
        assertEquals(1, notified)
    }

    @Test
    fun deviceLost_unknownUdn_doesNotNotify() {
        val registry = DlnaDeviceRegistry()
        registry.onDeviceFound(tv)
        var notified = 0
        registry.onDevicesChanged = { notified++ }

        registry.onDeviceLost(DlnaDevice("uuid:never-seen", "Ghost", hasAvTransport = true))

        assertEquals(0, notified)
        assertEquals(1, registry.devices.size)
    }

    @Test
    fun isCastable_mirrorsAvTransportFlag() {
        assertTrue(tv.isCastable)
        assertFalse(server.isCastable)
    }
}
