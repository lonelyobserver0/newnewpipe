package org.newnewpipe.app.casting

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastQueueMapperTest {

    private fun input(
        index: Int,
        title: String = "item-$index",
        url: String = "https://example.com/watch?v=$index",
        progressive: List<String> = listOf("https://cdn/$index.mp4"),
        hls: String? = null,
    ) = CastQueueInput(
        queueIndex = index,
        title = title,
        originalUrl = url,
        thumbnailUrl = "https://img/$index.jpg",
        durationMs = 60_000L * (index + 1),
        progressiveUrls = progressive,
        hlsUrl = hls,
    )

    @Test
    fun mapsAllItemsPreservingQueueIndex() {
        val entries = CastQueueMapper.map(
            listOf(input(0), input(1), input(2)),
        )
        assertEquals(3, entries.size)
        assertEquals(listOf(0, 1, 2), entries.map { it.queueIndex })
        assertEquals(listOf("item-0", "item-1", "item-2"), entries.map { it.title })
        assertEquals(listOf("https://cdn/0.mp4", "https://cdn/1.mp4", "https://cdn/2.mp4"),
                entries.map { it.contentUrl })
    }

    @Test
    fun skipsItemsWithoutPlayableUrl() {
        val entries = CastQueueMapper.map(
            listOf(input(0, progressive = emptyList(), hls = null), input(1), input(2)),
        )
        assertEquals(2, entries.size)
        // L'indice LOCALE resta quello della queue (customData + startIndex corretti)
        assertEquals(listOf(1, 2), entries.map { it.queueIndex })
    }

    @Test
    fun fallsBackToHls() {
        val entries = CastQueueMapper.map(
            listOf(input(0, progressive = emptyList(), hls = "https://cdn/0.m3u8")),
        )
        assertEquals(1, entries.size)
        assertEquals("https://cdn/0.m3u8", entries[0].contentUrl)
    }

    @Test
    fun customDataCarriesQueueIndexAndOriginalUrl() {
        val entries = CastQueueMapper.map(listOf(input(3, url = "https://example.com/v=abc")))
        assertEquals(1, entries.size)
        val obj = JsonParser.`object`().from(entries[0].customDataJson)
        assertEquals(3, obj.getInt("queueIndex"))
        assertEquals("https://example.com/v=abc", obj.getString("originalUrl"))
    }

    @Test
    fun queueIndexFromCustomData_roundtrip() {
        val entries = CastQueueMapper.map(listOf(input(5)))
        assertEquals(5, CastQueueMapper.queueIndexFromCustomData(entries[0].customDataJson))
    }

    @Test
    fun queueIndexFromCustomData_toleratesInvalidJson() {
        assertNull(CastQueueMapper.queueIndexFromCustomData(null))
        assertNull(CastQueueMapper.queueIndexFromCustomData(""))
        assertNull(CastQueueMapper.queueIndexFromCustomData("not json"))
        assertNull(CastQueueMapper.queueIndexFromCustomData("{\"other\": 1}"))
    }

    @Test
    fun emptyInputs_yieldEmptyEntries() {
        assertTrue(CastQueueMapper.map(emptyList()).isEmpty())
    }

    @Test
    fun blankOriginalUrl_isStillKeptInCustomData() {
        val entries = CastQueueMapper.map(
            listOf(CastQueueInput(0, "t", "", null, 0, listOf("https://cdn/0.mp4"), null)),
        )
        assertEquals(1, entries.size)
        val obj = JsonParser.`object`().from(entries[0].customDataJson)
        assertEquals("", obj.getString("originalUrl"))
        assertEquals(0, obj.getInt("queueIndex"))
    }
}
