package org.newnewpipe.app.util

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.grack.nanojson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.newnewpipe.app.R
import org.newnewpipe.extractor.services.peertube.PeertubeInstance

/**
 * Failover automatico delle istanze PeerTube (piano 022-S9, ricalibrato 2026-08-18).
 *
 * - Probe periodico dell'istanza attiva: GET `<url>/api/v1/config` con timeout
 *   OkHttp breve ([OkHttpPeertubeHealthProbe]); sana = 2xx + JSON con "instance".
 * - Su fallimento: switch automatico alla prima istanza sana della lista via
 *   [PeertubeHelper.selectInstance], con backoff esponenziale tra i retry e
 *   notifica all'utente.
 * - Rispetto della scelta esplicita: se l'utente ha selezionato un'istanza
 *   manualmente negli ultimi [manualGraceMs], nessuno switch automatico.
 * - Niente monitoraggio se l'utente non ha mai configurato una lista di istanze.
 */
class PeertubeFailoverManager private constructor(
    private val context: Context,
    private val probe: PeertubeHealthProbe = OkHttpPeertubeHealthProbe(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val initialDelayMs: Long = INITIAL_DELAY_MS,
    private val intervalMs: Long = INTERVAL_MS,
    private val baseBackoffMs: Long = BASE_BACKOFF_MS,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
    private val manualGraceMs: Long = MANUAL_GRACE_MS,
) {

    private var job: Job? = null
    private var consecutiveFailures = 0

    /** Avvia (o riavvia) il loop di monitoraggio; idempotente. */
    fun start() {
        if (job?.isActive == true) {
            return
        }
        job = scope.launch {
            delay(initialDelayMs)
            while (isActive) {
                runOnce()
                delay(nextDelayMs())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun nextDelayMs(): Long =
        if (consecutiveFailures > 0) {
            PeertubeFailoverPolicy.backoffDelayMs(consecutiveFailures, baseBackoffMs, maxBackoffMs)
        } else {
            intervalMs
        }

    /** Un giro di monitoraggio: probe + eventuale switch. Internal per i test. */
    internal fun runOnce() {
        // L'utente non ha mai configurato istanze: niente da monitorare.
        if (!PeertubeHelper.hasConfiguredInstances(context)) {
            consecutiveFailures = 0
            return
        }

        val current = PeertubeHelper.getCurrentInstance()
        if (probe.isHealthy(current)) {
            consecutiveFailures = 0
            return
        }

        consecutiveFailures++

        // Rispetto della scelta manuale recente (es. l'utente ha appena scelto
        // un'istanza nelle impostazioni): niente auto-switch.
        val manualSelectionMs = PeertubeHelper.lastManualSelectionMs(context)
        if (!PeertubeFailoverPolicy.shouldAutoSwitch(
                manualSelectionMs, System.currentTimeMillis(), manualGraceMs,
            )
        ) {
            return
        }

        val instances = PeertubeHelper.getInstanceList(context)
        val next = PeertubeFailoverSelector.selectNext(current, instances, probe::isHealthy)
        if (next != null && next.url != current.url) {
            PeertubeHelper.selectInstance(next, context)
            notifySwitch(current, next)
        }
    }

    private fun notifySwitch(from: PeertubeInstance, to: PeertubeInstance) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info_outline)
            .setContentTitle(context.getString(R.string.peertube_failover_notification_title))
            .setContentText(context.getString(
                R.string.peertube_failover_notification_message,
                to.name ?: to.url,
            ))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(
                R.string.peertube_failover_notification_message,
                to.name ?: to.url,
            )))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "peertube_failover"
        private const val NOTIFICATION_ID = 4102

        private const val INITIAL_DELAY_MS = 60_000L
        private const val INTERVAL_MS = 10 * 60_000L
        private const val BASE_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_MS = 15 * 60_000L
        private const val MANUAL_GRACE_MS = 10 * 60_000L

        @Volatile
        private var instance: PeertubeFailoverManager? = null

        /** Singleton app-level; [start] è idempotente. */
        @JvmStatic
        fun start(context: Context) {
            val appContext = context.applicationContext
            val manager = instance ?: synchronized(this) {
                instance ?: PeertubeFailoverManager(appContext).also { instance = it }
            }
            manager.start()
        }
    }
}

/** Probe di salute di un'istanza PeerTube; iniettabile per i test. */
fun interface PeertubeHealthProbe {
    fun isHealthy(instance: PeertubeInstance): Boolean
}

/** Probe OkHttp con timeout brevi: GET /api/v1/config, 2xx + JSON con "instance". */
class OkHttpPeertubeHealthProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build(),
) : PeertubeHealthProbe {

    override fun isHealthy(instance: PeertubeInstance): Boolean = try {
        val url = instance.url.trimEnd('/') + "/api/v1/config"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return false
            }
            val body = response.body?.string() ?: return false
            JsonParser.`object`().from(body).has("instance")
        }
    } catch (e: Exception) {
        false
    }
}
