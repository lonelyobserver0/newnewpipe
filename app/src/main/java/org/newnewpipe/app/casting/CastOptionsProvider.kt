package org.newnewpipe.app.casting

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Provider obbligatorio del Cast SDK (022-S3): dichiarato nel manifest con la
 * meta-data `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`.
 * Usa il Default Media Receiver (nessuna app receiver custom da registrare).
 * Viene caricato SOLO quando ChromecastCastManager inizializza CastContext, che
 * avviene solo se Google Play Services è disponibile (gate runtime, D-2).
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
