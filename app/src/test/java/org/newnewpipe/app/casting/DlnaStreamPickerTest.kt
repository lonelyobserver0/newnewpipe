package org.newnewpipe.app.casting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaStreamPickerTest {

    @Test
    fun picksFirstProgressiveUrl() {
        val url = DlnaStreamPicker.pick(
            progressiveUrls = listOf("https://cdn/v1.mp4", "https://cdn/v2.webm"),
            hlsUrl = "https://cdn/master.m3u8",
        )
        assertEquals("https://cdn/v1.mp4", url)
    }

    @Test
    fun skipsBlankProgressiveUrls() {
        val url = DlnaStreamPicker.pick(
            progressiveUrls = listOf("", "  ", "https://cdn/v1.mp4"),
            hlsUrl = "https://cdn/master.m3u8",
        )
        assertEquals("https://cdn/v1.mp4", url)
    }

    @Test
    fun fallsBackToHlsWhenNoProgressive() {
        val url = DlnaStreamPicker.pick(
            progressiveUrls = emptyList(),
            hlsUrl = "https://cdn/master.m3u8",
        )
        assertEquals("https://cdn/master.m3u8", url)
    }

    @Test
    fun blankHls_returnsNull() {
        assertNull(DlnaStreamPicker.pick(progressiveUrls = emptyList(), hlsUrl = " "))
        assertNull(DlnaStreamPicker.pick(progressiveUrls = emptyList(), hlsUrl = null))
    }

    @Test
    fun ignoresHlsWhenProgressiveExists() {
        val url = DlnaStreamPicker.pick(
            progressiveUrls = listOf("https://cdn/v1.mp4"),
            hlsUrl = null,
        )
        assertEquals("https://cdn/v1.mp4", url)
    }
}
