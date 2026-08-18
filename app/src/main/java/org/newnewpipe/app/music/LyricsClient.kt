package org.newnewpipe.app.music

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Errore applicativo lrclib (risposta non-JSON, HTTP error non gestito). */
class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Errore HTTP lrclib: [code] è lo status code (es. 404 = nessun record). */
class LyricsHttpException(val code: Int, message: String) : Exception(message)

/** Layer HTTP iniettabile: GET e ritorna il body della risposta. */
fun interface LyricsHttpGetter {
    fun get(url: String): String
}

/** Implementazione OkHttp (già nel progetto, nessuna dipendenza nuova). */
class OkHttpLyricsGetter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : LyricsHttpGetter {

    override fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw LyricsHttpException(response.code, "lrclib HTTP ${response.code}: $body")
            }
            return body
        }
    }
}

/**
 * Client di testi sincronizzati via lrclib.net (https://lrclib.net/docs) —
 * API pubblica e gratuita, nessuna API key.
 *
 * - [fetchSynced] interroga `GET /api/get` con artista/titolo/album/durata
 *   e ritorna il testo LRC (righe `[mm:ss.xx]`), oppure null se lrclib non
 *   ha un record (HTTP 404) o il record è strumentale / senza testi
 *   sincronizzati.
 * - [fetchSyncedLines] è la scorciatoia per la UI: testo LRC già parsato
 *   in righe ordinate per timestamp ([LrcLine]).
 *
 * La durata aiuta la disambiguazione: lrclib restituisce i testi solo se
 * corrisponde al proprio record (tolleranza ±2s).
 */
class LyricsClient(
    private val getter: LyricsHttpGetter,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    companion object {
        const val DEFAULT_ENDPOINT = "https://lrclib.net/api/get"
    }

    /**
     * Fetch dei testi sincronizzati (formato LRC) per il brano in riproduzione.
     *
     * @return il testo LRC grezzo, oppure null se non trovato (HTTP 404) o
     * se il record non ha `syncedLyrics` (strumentale / solo testo plain).
     * @throws LyricsException su risposta non-JSON; [LyricsHttpException]
     * (diversa da 404) propagata dal layer HTTP.
     */
    fun fetchSynced(
        artist: String,
        track: String,
        album: String? = null,
        durationSec: Int? = null,
    ): String? {
        val url = buildUrl(artist, track, album, durationSec)
        val body = try {
            getter.get(url)
        } catch (e: LyricsHttpException) {
            // 404 = nessun record lrclib per questa query: non è un errore.
            if (e.code == 404) {
                return null
            }
            throw e
        }
        val root = try {
            JsonParser.`object`().from(body)
        } catch (e: JsonParserException) {
            throw LyricsException("risposta lrclib non-JSON: ${e.message}", e)
        }
        val synced = root.getString("syncedLyrics", null)
        if (synced.isNullOrBlank()) {
            return null
        }
        return synced
    }

    /**
     * Come [fetchSynced], ma con il testo LRC già parsato in righe
     * ordinate per timestamp (pronto per l'overlay del player, S11).
     */
    fun fetchSyncedLines(
        artist: String,
        track: String,
        album: String? = null,
        durationSec: Int? = null,
    ): List<LrcLine>? {
        val lrc = fetchSynced(artist, track, album, durationSec) ?: return null
        return LrcParser.parse(lrc)
    }

    private fun buildUrl(
        artist: String,
        track: String,
        album: String?,
        durationSec: Int?,
    ): String {
        val query = buildString {
            append("artist_name=").append(encode(artist))
            append("&track_name=").append(encode(track))
            if (!album.isNullOrBlank()) {
                append("&album_name=").append(encode(album))
            }
            if (durationSec != null && durationSec > 0) {
                append("&duration=").append(durationSec)
            }
        }
        return "$endpoint?$query"
    }

    /** URL-encoding per query string (spazio → %20, come nel resto dell'app). */
    private fun encode(value: String?): String =
        URLEncoder.encode(value ?: "", "UTF-8").replace("+", "%20")
}
