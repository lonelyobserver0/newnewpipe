package org.newnewpipe.app.sync

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsTest {

    @Test
    fun defaults_areSane() {
        val settings = SyncSettings(FakePrefs())

        assertFalse(settings.enabled)
        assertNull(settings.serverUrl)
        assertNull(settings.username)
        assertNull(settings.password)
        assertNull(settings.blobPassphrase)
        assertEquals(SyncSettings.DEFAULT_INTERVAL_MINUTES, settings.intervalMinutes)
        assertFalse(settings.isConfigured)
    }

    @Test
    fun roundtrip_persistsEveryField() {
        val settings = SyncSettings(FakePrefs())
        settings.enabled = true
        settings.serverUrl = "https://dav.example.com/remote.php/dav/"
        settings.username = "alice"
        settings.password = "s3cret"
        settings.blobPassphrase = "correct horse battery staple"
        settings.intervalMinutes = 360

        val reread = SyncSettings(FakePrefs(settings))
        assertTrue(reread.enabled)
        assertEquals("https://dav.example.com/remote.php/dav/", reread.serverUrl)
        assertEquals("alice", reread.username)
        assertEquals("s3cret", reread.password)
        assertEquals("correct horse battery staple", reread.blobPassphrase)
        assertEquals(360L, reread.intervalMinutes)
        assertTrue(reread.isConfigured)
    }

    @Test
    fun blankValues_areTreatedAsUnset() {
        val settings = SyncSettings(FakePrefs())
        settings.serverUrl = "   "
        settings.username = ""
        settings.password = "x"

        assertNull(settings.serverUrl)
        assertNull(settings.username)
        // isConfigured requires all three non-blank.
        assertFalse(settings.isConfigured)
    }

    @Test
    fun isConfigured_requiresEnabled() {
        val settings = SyncSettings(FakePrefs())
        settings.enabled = false
        settings.serverUrl = "https://dav.example.com/"
        settings.username = "alice"
        settings.password = "s3cret"

        assertFalse(settings.isConfigured)

        settings.enabled = true
        assertTrue(settings.isConfigured)
    }
}

/** Minimal in-memory SharedPreferences used by the JVM tests. */
class FakePrefs(
    source: SyncSettings? = null,
) : SharedPreferences {

    private val map = LinkedHashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        source?.let { s ->
            map[SyncSettings.KEY_ENABLED] = s.enabled
            map[SyncSettings.KEY_SERVER_URL] = s.serverUrl
            map[SyncSettings.KEY_USERNAME] = s.username
            map[SyncSettings.KEY_PASSWORD] = s.password
            map[SyncSettings.KEY_BLOB_PASSPHRASE] = s.blobPassphrase
            map[SyncSettings.KEY_INTERVAL_MINUTES] = s.intervalMinutes
        }
    }

    override fun getString(key: String, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (map[key] as? Long) ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        (map[key] as? Int) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (map[key] as? Float) ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        (map[key] as? Set<String>) ?: defValues

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun getAll(): Map<String, *> = map

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            if (clearAll) map.clear()
            pending.forEach { (k, v) -> map[k] = v }
            listeners.forEach { it.onSharedPreferenceChanged(this@FakePrefs, null) }
            return true
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            pending[key] = null
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
    }
}
