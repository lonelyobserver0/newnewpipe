package org.newnewpipe.app.casting

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contratto wire di UPnP-AV (DIDL-Lite/AVTransport:1): i nomi degli input
 * devono corrispondere ESATTAMENTE a quelli del protocollo, altrimenti il
 * renderer risponde 402/501. Test puro, senza stack jupnp.
 */
class DlnaCastInputsTest {

    @Test
    fun setAvTransportUri_inputNamesFollowUpnpAv() {
        val inputs = DlnaCastInputs.setAvTransportUri("https://cdn.example/video.mp4")

        // UPnP-AV:1 SetAVTransportURI(InstanceID, CurrentURI, CurrentURIMetaData)
        assertEquals(
            setOf("InstanceID", "CurrentURI", "CurrentURIMetaData"),
            inputs.keys,
        )
        assertEquals(0, inputs["InstanceID"])
        assertEquals("https://cdn.example/video.mp4", inputs["CurrentURI"])
        assertEquals("", inputs["CurrentURIMetaData"])
    }

    @Test
    fun play_inputNamesFollowUpnpAv() {
        val inputs = DlnaCastInputs.play()

        // UPnP-AV:1 Play(InstanceID, Speed)
        assertEquals(setOf("InstanceID", "Speed"), inputs.keys)
        assertEquals(0, inputs["InstanceID"])
        assertEquals(1, inputs["Speed"])
    }
}
