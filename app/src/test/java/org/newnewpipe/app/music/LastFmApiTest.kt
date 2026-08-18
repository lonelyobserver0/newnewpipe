package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LastFmApiTest {

    private class FakePoster(
        private val responses: ArrayDeque<String>,
    ) : LastFmHttpPoster {
        val postedParams = mutableListOf<Map<String, String>>()

        override fun post(url: String, params: Map<String, String>): String {
            postedParams.add(params)
            return responses.removeFirst()
        }
    }

    private fun api(poster: LastFmHttpPoster) =
        LastFmApi(apiKey = "api-key-1234", secret = "my-secret", poster = poster)

    @Test
    fun authMobileSession_parsesSessionAndSignsRequest() {
        val poster = FakePoster(ArrayDeque(listOf(
            """{"session":{"name":"joebloggs","key":"session-key-abc"},"status":"ok"}""",
        )))
        val session = api(poster).authMobileSession("joebloggs", "password")

        assertEquals("session-key-abc", session.sessionKey)
        assertEquals("joebloggs", session.userName)

        val params = poster.postedParams.single()
        // format va sul wire ma NON nella firma (authspec §8); la firma è
        // calcolata sui parametri pre-firma (senza format e senza api_sig).
        assertTrue(params.containsKey("format"))
        val apiSig = params.getValue("api_sig")
        assertEquals(32, apiSig.length)
        assertEquals(
            LastFmSignature.signature(params - "format" - "api_sig", "my-secret"),
            apiSig,
        )
    }

    @Test
    fun authMobileSession_errorResponse_throwsLastFmException() {
        val poster = FakePoster(ArrayDeque(listOf(
            """{"error":26,"message":"There was a temporary error processing your request. Please try again later.","links":[]}""",
        )))
        try {
            api(poster).authMobileSession("u", "p")
            fail("atteso LastFmException")
        } catch (e: LastFmException) {
            assertTrue(e.message.orEmpty().contains("26") || e.message.orEmpty().contains("temporary"))
        }
    }

    @Test
    fun scrobble_returnsTrueOnOk() {
        val poster = FakePoster(ArrayDeque(listOf(
            """{"status":"ok","ignoredMessage":{"code":0,"message":""}}""",
        )))
        val session = LastFmSession("session-key-abc", "joebloggs")
        val ok = api(poster).scrobble(
            session = session,
            artist = "Artist",
            track = "Song",
            album = "Album",
            timestampSec = 1_700_000_000L,
        )

        assertTrue(ok)
        val params = poster.postedParams.single()
        assertEquals("track.scrobble", params["method"])
        assertEquals("session-key-abc", params["sk"])
        assertEquals("1700000000", params["timestamp"])
        assertNotNull(params["api_sig"])
    }

    @Test
    fun scrobble_failedStatus_returnsFalse() {
        val poster = FakePoster(ArrayDeque(listOf(
            """{"status":"failed","error":9,"message":"Invalid method signature supplied"}""",
        )))
        val session = LastFmSession("session-key-abc", "joebloggs")
        assertFalse(api(poster).scrobble(session, "Artist", "Song", null, 1L))
    }

    @Test
    fun nonJsonResponse_throwsLastFmException() {
        val poster = FakePoster(ArrayDeque(listOf("not json at all")))
        try {
            api(poster).authMobileSession("u", "p")
            fail("atteso LastFmException")
        } catch (e: LastFmException) {
            assertTrue(e.message.orEmpty().contains("non-JSON"))
        }
    }
}
