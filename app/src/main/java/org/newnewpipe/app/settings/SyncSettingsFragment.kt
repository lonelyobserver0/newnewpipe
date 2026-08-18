package org.newnewpipe.app.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import kotlinx.coroutines.runBlocking
import androidx.preference.Preference
import org.newnewpipe.app.R
import org.newnewpipe.app.sync.SyncManager
import org.newnewpipe.app.sync.SyncResult
import org.newnewpipe.app.sync.SyncSettings
import org.newnewpipe.app.sync.SyncWorker
import org.newnewpipe.app.sync.appSyncCodecs

/**
 * "Sincronizzazione" settings section (plan 022 S7): server/credentials
 * (encrypted prefs), optional blob passphrase, periodic interval and the
 * manual "sync now" trigger. The values live in the encrypted
 * [SyncSettings] prefs via the custom Sync*Preference classes.
 */
class SyncSettingsFragment : BasePreferenceFragment(), OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.sync_settings)
        updateSummaries()
        findPreference<Preference>(getString(R.string.sync_now_key))?.setOnPreferenceClickListener {
            runSyncNow()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        SyncSettings.encrypted(requireContext().applicationContext)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        SyncSettings.encrypted(requireContext().applicationContext)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateSummaries()
        if (key == getString(R.string.sync_enabled_key) ||
            key == getString(R.string.sync_interval_key)
        ) {
            val context = context ?: return
            SyncWorker.schedule(context.applicationContext)
        }
    }

    private fun updateSummaries() {
        val settings = SyncSettings(SyncSettings.encrypted(requireContext().applicationContext))
        findPreference<EditTextPreference>(getString(R.string.sync_server_url_key))?.summary =
            settings.serverUrl ?: getString(R.string.sync_value_not_set)
        findPreference<EditTextPreference>(getString(R.string.sync_username_key))?.summary =
            settings.username ?: getString(R.string.sync_value_not_set)
        findPreference<EditTextPreference>(getString(R.string.sync_password_key))?.summary =
            if (settings.password.isNullOrBlank()) {
                getString(R.string.sync_value_not_set)
            } else {
                "••••••••"
            }
        findPreference<EditTextPreference>(getString(R.string.sync_blob_passphrase_key))?.summary =
            if (settings.blobPassphrase.isNullOrBlank()) {
                getString(R.string.sync_passphrase_off)
            } else {
                "••••••••"
            }
    }

    private fun runSyncNow() {
        val context = requireContext().applicationContext
        val activity = activity
        Thread {
            val manager = SyncManager(SyncSettings(SyncSettings.encrypted(context)))
            val result = runBlocking { manager.syncNow(appSyncCodecs(context)) }
            val message = when (result) {
                is SyncResult.Skipped -> context.getString(R.string.sync_now_skipped)
                is SyncResult.Completed -> context.getString(
                    R.string.sync_now_completed,
                    result.entities.count { it.uploaded },
                    result.entities.count { it.downloaded },
                    result.entities.count { !it.ok },
                )
            }
            activity?.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
