package org.newnewpipe.app.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Typed access to the sync preferences (server, credentials, optional blob
 * passphrase, interval).
 *
 * Credentials are stored in an [EncryptedSharedPreferences] backed by an
 * Android Keystore master key, so server password and blob passphrase are
 * never written in clear text (plan 022 S7). The class itself is a thin
 * wrapper over a [SharedPreferences] instance, so the JVM unit tests use an
 * in-memory fake.
 */
class SyncSettings(private val prefs: SharedPreferences) {

    /** Master switch for the WebDAV sync. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Base WebDAV URL (HTTPS required by [WebDavClient]). */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value).apply()
        }

    /** WebDAV account username. */
    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value).apply()
        }

    /** WebDAV account password (kept in EncryptedSharedPreferences). */
    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PASSWORD, value).apply()
        }

    /**
     * Optional passphrase that encrypts the payloads on the server
     * (AES-GCM via [SyncCipher]). Empty/null = plain payloads.
     */
    var blobPassphrase: String?
        get() = prefs.getString(KEY_BLOB_PASSPHRASE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_BLOB_PASSPHRASE, value).apply()
        }

    /** Periodic sync interval in minutes (WorkManager minimum is 15). */
    var intervalMinutes: Long
        get() = prefs.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) {
            prefs.edit().putLong(KEY_INTERVAL_MINUTES, value).apply()
        }

    /** True when sync is enabled AND server/credentials are filled in. */
    val isConfigured: Boolean
        get() = enabled &&
            !serverUrl.isNullOrBlank() &&
            !username.isNullOrBlank() &&
            !password.isNullOrBlank()

    companion object {
        const val PREFS_NAME = "newnewpipe_sync"
        const val DEFAULT_INTERVAL_MINUTES = 60L

        const val KEY_ENABLED = "sync_enabled"
        const val KEY_SERVER_URL = "sync_server_url"
        const val KEY_USERNAME = "sync_username"
        const val KEY_PASSWORD = "sync_password"
        const val KEY_BLOB_PASSPHRASE = "sync_blob_passphrase"
        const val KEY_INTERVAL_MINUTES = "sync_interval_minutes"

        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        /**
         * The encrypted preferences instance (cached process-wide).
         * Keys are obfuscated (AES256-SIV) and values encrypted (AES256-GCM)
         * with a Keystore-backed master key.
         */
        fun encrypted(context: Context): SharedPreferences {
            cachedPrefs?.let { return it }
            synchronized(this) {
                cachedPrefs?.let { return it }
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                cachedPrefs = prefs
                return prefs
            }
        }
    }
}
