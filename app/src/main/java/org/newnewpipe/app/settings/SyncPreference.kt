package org.newnewpipe.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import org.newnewpipe.app.sync.SyncSettings

/**
 * Preferences that persist to the ENCRYPTED sync preferences
 * (EncryptedSharedPreferences + Keystore, see [SyncSettings]) instead of the
 * default plain preference file — server credentials and the blob passphrase
 * are never written in clear text.
 *
 * The androidx preference persistence goes through the overridable
 * [androidx.preference.Preference.getSharedPreferences], so overriding it is
 * the single hook needed to redirect the values.
 */

/** EditText preference backed by the encrypted sync preferences. */
class SyncTextPreference(context: Context, attrs: AttributeSet?) :
    EditTextPreference(context, attrs) {

    override fun getSharedPreferences(): SharedPreferences =
        SyncSettings.encrypted(context)
}

/** Switch preference backed by the encrypted sync preferences. */
class SyncSwitchPreference(context: Context, attrs: AttributeSet?) :
    SwitchPreferenceCompat(context, attrs) {

    override fun getSharedPreferences(): SharedPreferences =
        SyncSettings.encrypted(context)
}

/** List preference backed by the encrypted sync preferences. */
class SyncListPreference(context: Context, attrs: AttributeSet?) :
    ListPreference(context, attrs) {

    override fun getSharedPreferences(): SharedPreferences =
        SyncSettings.encrypted(context)
}
