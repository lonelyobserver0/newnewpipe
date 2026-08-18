package org.newnewpipe.app.music

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal Last.fm API client (https://www.last.fm/api).
 *
 * - [LastFmSignature.signature]: `api_sig = md5("namevalue" pairs sorted by name + secret)`
 *   (https://www.last.fm/api/authspec).
 * - [LastFmApi.authMobileSession]: `auth.getMobileSession` handshake (username + password).
 * - [LastFmApi.scrobble]: `track.scrobble` with an existing session key.
 *
 * The HTTP layer is injectable ([LastFmHttpPoster]) so unit tests run without network.
 */
object LastFmSignature {

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }
    }

    /**
     * Signature per l'Authentication API: concatenazione delle coppie "namevalue"
     * ordinate alfabeticamente per nome, seguita dal secret dell'applicazione.
     */
    fun signature(params: Map<String, String>, secret: String): String {
        val base = params.toSortedMap()
            .entries
            .joinToString("") { "${it.key}${it.value}" } + secret
        return md5(base)
    }
}

/** Errore applicativo Last.fm (handshake fallito, risposta non-JSON, HTTP error). */
class LastFmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Session key ottenuta dall'handshake [LastFmApi.authMobileSession]. */
data class LastFmSession(val sessionKey: String, val userName: String)

/** Layer HTTP iniettabile: POST urlencoded e ritorna il body della risposta. */
fun interface LastFmHttpPoster {
    fun post(url: String, params: Map<String, String>): String
}

/** Implementazione OkHttp (già nel progetto, nessuna dipendenza nuova). */
class OkHttpLastFmPoster(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : LastFmHttpPoster {

    override fun post(url: String, params: Map<String, String>): String {
        val form = FormBody.Builder().apply {
            params.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder().url(url).post(form).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw LastFmException("Last.fm HTTP ${response.code}: $body")
            }
            return body
        }
    }
}

/**
 * Client Last.fm: firma MD5 di ogni richiesta e parsing delle risposte (nanojson).
 */
class LastFmApi(
    private val apiKey: String,
    private val secret: String,
    private val poster: LastFmHttpPoster,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    companion object {
        const val DEFAULT_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    }

    /**
     * Handshake `auth.getMobileSession`: crea la sessione dell'utente con
     * username + password. Ritorna [LastFmSession] o lancia [LastFmException].
     */
    fun authMobileSession(username: String, password: String): LastFmSession {
        val params = linkedMapOf(
            "method" to "auth.getMobileSession",
            "api_key" to apiKey,
            "username" to username,
            "password" to password,
            "format" to "json",
        )
        val root = parse(post(params))
        // NB: nanojson getObject() non ritorna MAI null (oggetto vuoto se assente).
        if (!root.has("session")) {
            throw LastFmException(
                root.getString("message", "handshake fallito (status=${root.getString("status", "?")})"),
            )
        }
        val session = root.getObject("session")
        val sessionKey = session.getString("key", "")
        if (sessionKey.isBlank()) {
            throw LastFmException(
                "handshake fallito: nessuna session key (status=${root.getString("status", "?")})",
            )
        }
        return LastFmSession(
            sessionKey = sessionKey,
            userName = session.getString("name", ""),
        )
    }

    /**
     * Scrobble `track.scrobble` di un brano ascoltato. Ritorna true se
     * Last.fm ha accettato la submission (status=ok).
     */
    fun scrobble(
        session: LastFmSession,
        artist: String,
        track: String,
        album: String?,
        timestampSec: Long,
    ): Boolean {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to session.sessionKey,
            "artist" to artist,
            "track" to track,
            "timestamp" to timestampSec.toString(),
            "format" to "json",
        )
        val withAlbum = if (album.isNullOrBlank()) params else params + ("album" to album)
        val root = parse(post(withAlbum))
        return root.getString("status", "") == "ok"
    }

    private fun post(params: Map<String, String>): String {
        // Il formato NON va incluso nella firma (authspec §8: "You must not
        // include the format and callback parameters").
        val signed = params + ("api_sig" to LastFmSignature.signature(params - "format", secret))
        return poster.post(endpoint, signed)
    }

    private fun parse(json: String) = try {
        JsonParser.`object`().from(json)
    } catch (e: JsonParserException) {
        throw LastFmException("risposta Last.fm non-JSON: ${e.message}", e)
    }
}
