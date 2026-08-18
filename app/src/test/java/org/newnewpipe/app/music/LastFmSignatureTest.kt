package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LastFmSignatureTest {

    @Test
    fun md5_emptyString() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", LastFmSignature.md5(""))
    }

    @Test
    fun md5_abc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", LastFmSignature.md5("abc"))
    }

    /**
     * Esempio canonico dell'Authentication API di Last.fm (authspec §8):
     * secret "ilovecher", params api_key=xxxxxxxx / method=auth.getMobileSession
     * / password=secret / username=joebloggs.
     * api_sig = md5("api_keyxxxxxxxxmethodauth.getMobileSessionpasswordsecretusernamejoebloggsilovecher")
     * Verificato: 62f0a2b33db77d535fa86c6183957045.
     */
    @Test
    fun signature_authspecExample() {
        val params = mapOf(
            "api_key" to "xxxxxxxx",
            "method" to "auth.getMobileSession",
            "password" to "secret",
            "username" to "joebloggs",
        )
        assertEquals(
            "62f0a2b33db77d535fa86c6183957045",
            LastFmSignature.signature(params, "ilovecher"),
        )
    }

    /** L'ordine di inserimento non conta: la firma ordina i parametri per nome. */
    @Test
    fun signature_isOrderIndependent() {
        val a = mapOf("b" to "2", "a" to "1")
        val b = mapOf("a" to "1", "b" to "2")
        assertEquals(LastFmSignature.signature(a, "s"), LastFmSignature.signature(b, "s"))
    }

    @Test
    fun signature_changesWithSecret() {
        val params = mapOf("api_key" to "abc", "method" to "track.scrobble")
        assertNotEquals(
            LastFmSignature.signature(params, "secret1"),
            LastFmSignature.signature(params, "secret2"),
        )
    }

    @Test
    fun signature_utf8() {
        // "artist" con carattere non-ASCII: la firma usa UTF-8.
        val params = mapOf("artist" to "Jägermeister", "method" to "track.scrobble")
        assertEquals(32, LastFmSignature.signature(params, "s").length)
    }
}
