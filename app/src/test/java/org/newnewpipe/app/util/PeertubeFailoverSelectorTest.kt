package org.newnewpipe.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.newnewpipe.extractor.services.peertube.PeertubeInstance

class PeertubeFailoverSelectorTest {

    private fun isHealthy(vararg healthyUrls: String): (PeertubeInstance) -> Boolean =
        { instance -> instance.url in healthyUrls }

    private fun instance(url: String, name: String = url) = PeertubeInstance(url, name)

    @Test
    fun emptyList_fallsBackToDefaultInstance() {
        val next = PeertubeFailoverSelector.selectNext(
            current = instance("https://down.example"),
            instances = emptyList(),
            isHealthy = isHealthy(),
        )
        assertSame(PeertubeInstance.DEFAULT_INSTANCE, next)
    }

    @Test
    fun currentHealthy_noSwitch() {
        val current = instance("https://a.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(current, instance("https://b.example")),
            isHealthy = isHealthy("https://a.example", "https://b.example"),
        )
        assertNull(next)
    }

    @Test
    fun currentDown_switchesToFirstHealthyInListOrder() {
        val current = instance("https://a.example")
        val down1 = instance("https://b.example")
        val healthy1 = instance("https://c.example")
        val healthy2 = instance("https://d.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(current, down1, healthy1, healthy2),
            isHealthy = isHealthy("https://c.example", "https://d.example"),
        )
        assertEquals("https://c.example", next!!.url)
    }

    @Test
    fun currentDown_skipsUnhealthyAndCurrent() {
        val current = instance("https://a.example")
        val healthy1 = instance("https://b.example")
        val down1 = instance("https://c.example")
        val healthy2 = instance("https://d.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(current, healthy1, down1, healthy2),
            isHealthy = isHealthy("https://b.example", "https://d.example"),
        )
        assertEquals("https://b.example", next!!.url)
    }

    @Test
    fun noHealthyInstance_returnsNull() {
        val current = instance("https://a.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(current, instance("https://b.example")),
            isHealthy = isHealthy(),
        )
        assertNull(next)
    }

    @Test
    fun currentIsOnlyInstance_returnsNull() {
        val current = instance("https://a.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(current),
            isHealthy = isHealthy(),
        )
        assertNull(next)
    }

    @Test
    fun nullCurrent_picksFirstHealthy() {
        val next = PeertubeFailoverSelector.selectNext(
            current = null,
            instances = listOf(
                instance("https://a.example"),
                instance("https://b.example"),
            ),
            isHealthy = isHealthy("https://b.example"),
        )
        assertEquals("https://b.example", next!!.url)
    }

    @Test
    fun sameUrlDifferentObject_stillExcluded() {
        // Il confronto è per URL, non per identità dell'oggetto.
        val current = instance("https://a.example")
        val sameUrlCopy = instance("https://a.example")
        val healthyOther = instance("https://b.example")
        val next = PeertubeFailoverSelector.selectNext(
            current = current,
            instances = listOf(sameUrlCopy, healthyOther),
            isHealthy = isHealthy("https://b.example"),
        )
        assertEquals("https://b.example", next!!.url)
    }
}
