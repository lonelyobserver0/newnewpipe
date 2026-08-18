package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LyricsClientTest {

    private class FakeGetter(
        private val responses: ArrayDeque<String>,
        private val errorCodes: ArrayDeque<Int> = ArrayDeque(),
    ) : LyricsHttpGetter {
        val requestedUrls = mutableListOf<String>()

        override fun get(url: String): String {
            requestedUrls.add(url)
            if (errorCodes.isNotEmpty()) {
                val code = errorCodes.removeFirst()
                throw LyricsHttpException(code, "lrclib HTTP $code")
            }
            return responses.removeFirst()
        }
    }

    private fun client(getter: LyricsHttpGetter) = LyricsClient(getter)

    @Test
    fun fetchSynced_parsesLrcFromMockResponse() {
        // NB: nel JSON di mock i newline dell'LRC vanno ESCAPATI (\n), altrimenti
        // la stringa JSON è invalida e nanojson lancia JsonParserException.
        val lrc = "[00:27.93] Listen to the wind blow\n" +
            "[00:30.88] Watch the sun rise\n" +
            "[00:34.62] Run in the shadows\n"
        val lrcJson = lrc.replace("\n", "\\n")
        val getter = FakeGetter(ArrayDeque(listOf(
            """{"id":151738,"name":"The Chain","trackName":"The Chain",""" +
                """"artistName":"Fleetwood Mac","albumName":"Rumours","duration":271,""" +
                """"instrumental":false,"plainLyrics":"Listen to the wind blow",""" +
                """"syncedLyrics":"$lrcJson"}""",
        )))
        val synced = client(getter).fetchSynced(
            artist = "Fleetwood Mac",
            track = "The Chain",
            album = "Rumours",
            durationSec = 271,
        )

        assertEquals(lrc, synced)
    }

    @Test
    fun fetchSynced_buildsUrlWithEncodedParams() {
        val getter = FakeGetter(ArrayDeque(listOf(
            """{"syncedLyrics":"[00:01.00] Test"}""",
        )))
        client(getter).fetchSynced(
            artist = "AC/DC",
            track = "Highway to Hell",
            album = "Highway to Hell",
            durationSec = 208,
        )

        val url = getter.requestedUrls.single()
        assertTrue(url.startsWith("https://lrclib.net/api/get?"))
        assertTrue(url.contains("artist_name=AC%2FDC"))
        assertTrue(url.contains("track_name=Highway%20to%20Hell"))
        assertTrue(url.contains("album_name=Highway%20to%20Hell"))
        assertTrue(url.contains("duration=208"))
    }

    @Test
    fun fetchSynced_omitsAlbumAndDurationWhenAbsent() {
        val getter = FakeGetter(ArrayDeque(listOf(
            """{"syncedLyrics":"[00:01.00] Test"}""",
        )))
        client(getter).fetchSynced(artist = "Artist", track = "Song")

        val url = getter.requestedUrls.single()
        assertTrue(!url.contains("album_name"))
        assertTrue(!url.contains("duration"))
    }

    @Test
    fun fetchSynced_http404_returnsNull() {
        val getter = FakeGetter(
            responses = ArrayDeque(),
            errorCodes = ArrayDeque(listOf(404)),
        )
        assertNull(client(getter).fetchSynced(artist = "Artist", track = "Missing"))
    }

    @Test
    fun fetchSynced_http500_throws() {
        val getter = FakeGetter(
            responses = ArrayDeque(),
            errorCodes = ArrayDeque(listOf(500)),
        )
        try {
            client(getter).fetchSynced(artist = "Artist", track = "Song")
            fail("atteso LyricsHttpException(500)")
        } catch (e: LyricsHttpException) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun fetchSynced_instrumentalWithoutSyncedLyrics_returnsNull() {
        val getter = FakeGetter(ArrayDeque(listOf(
            """{"id":1,"name":"Interlude","trackName":"Interlude",""" +
                """"artistName":"Artist","instrumental":true,""" +
                """"plainLyrics":null,"syncedLyrics":null}""",
        )))
        assertNull(client(getter).fetchSynced(artist = "Artist", track = "Interlude"))
    }

    @Test
    fun fetchSynced_nonJsonResponse_throwsLyricsException() {
        val getter = FakeGetter(ArrayDeque(listOf("not json at all")))
        try {
            client(getter).fetchSynced(artist = "Artist", track = "Song")
            fail("atteso LyricsException")
        } catch (e: LyricsException) {
            assertTrue(e.message.orEmpty().contains("non-JSON"))
        }
    }

    @Test
    fun fetchSyncedLines_parsesTimestamps() {
        val getter = FakeGetter(ArrayDeque(listOf(
            """{"syncedLyrics":"[00:27.93] Listen to the wind blow\n[00:30.88] Watch the sun rise"}""",
        )))
        val lines = client(getter).fetchSyncedLines(
            artist = "Fleetwood Mac",
            track = "The Chain",
        )

        assertEquals(2, lines?.size)
        assertEquals(27_930L, lines?.get(0)?.timestampMs)
        assertEquals("Listen to the wind blow", lines?.get(0)?.text)
        assertEquals(30_880L, lines?.get(1)?.timestampMs)
    }

    @Test
    fun fetchSyncedLines_notFound_returnsNull() {
        val getter = FakeGetter(
            responses = ArrayDeque(),
            errorCodes = ArrayDeque(listOf(404)),
        )
        assertNull(client(getter).fetchSyncedLines(artist = "Artist", track = "Missing"))
    }
}
