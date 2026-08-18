package org.newnewpipe.app.music

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Facade dello scrobbling Last.fm.
 *
 * - Config (toggle, credenziali, session key) in SharedPreferences dedicate.
 * - [login] esegue l'handshake `auth.getMobileSession` e salva la session key.
 * - [onTrackChanged]/[onPosition] alimentano lo [ScrobbleTracker] dal player;
 *   al superamento della soglia (50% o 4 minuti) parte `track.scrobble` su
 *   Dispatchers.IO con retry a backoff esponenziale.
 *
 * Nota dipendenza (022-S12): la sessione e le credenziali sono in prefs
 * PLAINE perché `androidx.security:security-crypto` (EncryptedSharedPreferences)
 * non è nel progetto e la rete è bloccata per aggiungere dipendenze — vedi
 * decisions.md D-7. TODO: migrare a EncryptedSharedPreferences appena possibile.
 */
class LastFmScrobbler private constructor(
    private val prefs: SharedPreferences,
    private val api: LastFmApi,
    private val tracker: ScrobbleTracker = ScrobbleTracker(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseRetryDelayMs: Long = DEFAULT_BASE_RETRY_MS,
) {

    val isEnabled: Boolean
        get() = prefs.getBoolean(PREF_ENABLED, false)

    fun sessionKey(): String? = prefs.getString(PREF_SESSION_KEY, null)

    /**
     * Handshake con username/password. In caso di successo salva username e
     * session key e invoca [onResult] con la sessione (su Dispatchers.IO:
     * chi chiama deve postare sul main thread se serve UI).
     */
    fun login(username: String, password: String, onResult: (LastFmSession?) -> Unit) {
        scope.launch {
            val session = runCatching {
                api.authMobileSession(username, password)
            }.getOrNull()
            if (session != null) {
                prefs.edit()
                    .putString(PREF_USERNAME, username)
                    .putString(PREF_SESSION_KEY, session.sessionKey)
                    .apply()
            }
            onResult(session)
        }
    }

    /** Cambio brano: da chiamare a ogni tick con i metadati correnti. */
    fun onTrackChanged(title: String, artist: String, durationMs: Long) {
        tracker.onTrackChanged(title, artist, durationMs)
    }

    /** Posizione di riproduzione: può innescare lo scrobble (una sola volta per brano). */
    fun onPosition(positionMs: Long) {
        if (!isEnabled) {
            return
        }
        val sessionKey = sessionKey() ?: return
        if (!tracker.onPosition(positionMs)) {
            return
        }
        val artist = tracker.lastArtist ?: return
        val title = tracker.lastTitle ?: return
        val timestampSec = tracker.startTimestampSec
        val session = LastFmSession(sessionKey, userName = "")
        scrobbleWithRetry(session, artist, title, album = null, timestampSec = timestampSec)
    }

    /** Logout: rimuove la session key (le credenziali restano per il prossimo login). */
    fun logout() {
        prefs.edit().remove(PREF_SESSION_KEY).apply()
    }

    private fun scrobbleWithRetry(
        session: LastFmSession,
        artist: String,
        title: String,
        album: String?,
        timestampSec: Long,
    ) {
        scope.launch {
            var attempt = 0
            while (true) {
                try {
                    // "failed" (status!=ok) = parametri/permessi errati: non retryable.
                    if (api.scrobble(session, artist, title, album, timestampSec)) {
                        return@launch
                    }
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    if (attempt > maxRetries) {
                        return@launch
                    }
                    delay(baseRetryDelayMs * 2.0.pow((attempt - 1).toDouble()).toLong())
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "newnewpipe_lastfm"

        const val PREF_ENABLED = "lastfm_scrobble_enabled"
        const val PREF_USERNAME = "lastfm_username"
        const val PREF_PASSWORD = "lastfm_password"
        const val PREF_API_KEY = "lastfm_api_key"
        const val PREF_API_SECRET = "lastfm_api_secret"
        const val PREF_SESSION_KEY = "lastfm_session_key"

        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_BASE_RETRY_MS = 1_000L

        @Volatile
        private var instance: LastFmScrobbler? = null

        /**
         * Singleton app-level (application context, nessun leak). Se la API key
         * non è ancora configurata nelle prefs, [login] fallirà con un
         * [LastFmException] gestibile dall'UI delle impostazioni.
         */
        @JvmStatic
        fun get(context: Context): LastFmScrobbler {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appContext = context.applicationContext
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val api = LastFmApi(
                    apiKey = prefs.getString(PREF_API_KEY, null).orEmpty(),
                    secret = prefs.getString(PREF_API_SECRET, null).orEmpty(),
                    poster = OkHttpLastFmPoster(),
                )
                return LastFmScrobbler(prefs, api).also { instance = it }
            }
        }
    }
}
