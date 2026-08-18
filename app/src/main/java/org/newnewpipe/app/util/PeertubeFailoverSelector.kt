package org.newnewpipe.app.util

import org.newnewpipe.extractor.services.peertube.PeertubeInstance

/**
 * Logica pura del failover istanze PeerTube (piano 022-S9, ricalibrato 2026-08-18).
 * Nessuna dipendenza Android/network: coperta dagli unit test JVM.
 */
object PeertubeFailoverSelector {

    /**
     * Sceglie l'istanza su cui passare:
     * - lista vuota → [fallback] (DEFAULT_INSTANCE);
     * - istanza corrente sana → null (nessuno switch);
     * - altrimenti → prima istanza SANA della lista (ordine della lista,
     *   escludendo quella corrente); null se nessuna sana.
     */
    fun selectNext(
        current: PeertubeInstance?,
        instances: List<PeertubeInstance>,
        isHealthy: (PeertubeInstance) -> Boolean,
        fallback: PeertubeInstance = PeertubeInstance.DEFAULT_INSTANCE,
    ): PeertubeInstance? {
        if (instances.isEmpty()) {
            return fallback
        }
        val currentHealthy = current != null && isHealthy(current)
        if (currentHealthy) {
            return null
        }
        return instances.firstOrNull { candidate ->
            candidate.url != current?.url && isHealthy(candidate)
        }
    }
}

/**
 * Politica di retry/backoff e rispetto della scelta manuale dell'utente (pura).
 */
object PeertubeFailoverPolicy {

    /**
     * Il failover automatico è consentito solo se la scelta manuale dell'utente
     * è assente (0) o più vecchia della finestra di grazia [graceMs].
     */
    fun shouldAutoSwitch(manualSelectionMs: Long, nowMs: Long, graceMs: Long): Boolean =
        manualSelectionMs <= 0L || nowMs - manualSelectionMs >= graceMs

    /**
     * Backoff esponenziale: 1x, 2x, 4x, … il [baseMs], con cap [maxMs].
     * [consecutiveFailures] <= 0 → [baseMs] (nessun retry in corso).
     */
    fun backoffDelayMs(consecutiveFailures: Int, baseMs: Long, maxMs: Long): Long {
        if (consecutiveFailures <= 0) {
            return baseMs
        }
        val exponent = (consecutiveFailures - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
        val factor = 1L shl exponent
        return (baseMs * factor).coerceAtMost(maxMs)
    }

    private const val MAX_BACKOFF_EXPONENT = 20
}
