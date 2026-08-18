package org.newnewpipe.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Bridges one entity between the local database and its sync file on the
 * WebDAV server (decision D-4: one JSON file per entity).
 */
interface SyncCodec {
    /** File name inside the sync collection, e.g. "subscriptions.json". */
    val fileName: String

    /** Local snapshot as sync JSON, or null when there is nothing to upload yet. */
    fun localSnapshot(): String?

    /** Merges the local and remote snapshots (last-write-wins per item — see the S6 mergers). */
    fun merge(local: String?, remote: String?): String?

    /** Applies a merged snapshot to the local database. */
    fun apply(merged: String?)
}

/** Outcome of syncing a single entity file. */
data class EntitySyncResult(
    val fileName: String,
    /** True when the merged snapshot was uploaded to the server. */
    val uploaded: Boolean,
    /** True when a remote snapshot existed and was merged into the local data. */
    val downloaded: Boolean,
    /** Non-null when this entity failed; sync continues with the other entities. */
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/** Outcome of a full [SyncManager.syncNow] run. */
sealed interface SyncResult {
    /** Sync is disabled or not configured: nothing was done. */
    data object Skipped : SyncResult

    /** Every entity file was processed; check [entities] for per-file results. */
    data class Completed(val entities: List<EntitySyncResult>) : SyncResult
}

/**
 * Orchestrates one sync run: for each entity it pulls the remote file
 * (missing → treated as empty), merges it with the local snapshot using the
 * S6 mergers (per-item last-write-wins), applies the merged snapshot locally
 * and pushes it back to the server.
 *
 * - First sync: remote 404 + local data → local snapshot is uploaded as-is.
 * - Fresh device: no local data + remote present → remote is downloaded.
 * - Optional passphrase (see [SyncSettings.blobPassphrase]): payloads are
 *   encrypted with [SyncCipher] before PUT and decrypted after GET.
 * - Each entity is processed independently: a failure on one file is
 *   recorded in [EntitySyncResult.error] and does not abort the others.
 *
 * The manager is pure orchestration over the sync JSON files; entity
 * (de)serialization lives in the codecs ([SubscriptionSyncCodec]).
 */
class SyncManager(
    private val settings: SyncSettings,
    private val httpClient: OkHttpClient = WebDavClient.defaultClient(),
) {

    /** Builds the WebDAV client from the current settings, or null when not configured. */
    fun createClient(): WebDavClient? {
        if (!settings.isConfigured) return null
        return WebDavClient(
            settings.serverUrl!!,
            settings.username!!,
            settings.password!!,
            httpClient,
        )
    }

    /**
     * Runs the sync for the given codecs.
     *
     * @return [SyncResult.Skipped] when sync is disabled or not configured,
     * [SyncResult.Completed] otherwise.
     */
    suspend fun syncNow(codecs: List<SyncCodec>): SyncResult = withContext(Dispatchers.IO) {
        val client = createClient()
        if (client == null) {
            SyncResult.Skipped
        } else {
            SyncResult.Completed(codecs.map { syncEntity(client, it) })
        }
    }

    private fun syncEntity(client: WebDavClient, codec: SyncCodec): EntitySyncResult {
        val path = "$SYNC_DIR/${codec.fileName}"
        return try {
            val local = codec.localSnapshot()
            val remoteBytes = try {
                client.get(path)
            } catch (e: WebDavException) {
                if (e.statusCode == 404) null else throw e
            }
            val remote = remoteBytes?.let { decryptIfNeeded(it) }?.toString(Charsets.UTF_8)
            val merged = codec.merge(local, remote)
            if (merged == null) {
                // Nothing on either side: nothing to store.
                EntitySyncResult(codec.fileName, uploaded = false, downloaded = remote != null)
            } else {
                codec.apply(merged)
                ensureCollection(client)
                client.put(path, encryptIfNeeded(merged.toByteArray(Charsets.UTF_8)), JSON_CONTENT_TYPE)
                EntitySyncResult(codec.fileName, uploaded = true, downloaded = remote != null)
            }
        } catch (e: Exception) {
            EntitySyncResult(
                codec.fileName,
                uploaded = false,
                downloaded = false,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private fun ensureCollection(client: WebDavClient) {
        try {
            client.mkcol(SYNC_DIR)
        } catch (e: WebDavException) {
            // Already exists on most servers: nothing to do.
        }
    }

    private fun encryptIfNeeded(bytes: ByteArray): ByteArray {
        val passphrase = settings.blobPassphrase
        return if (passphrase.isNullOrBlank()) bytes else SyncCipher(passphrase).encrypt(bytes)
    }

    private fun decryptIfNeeded(bytes: ByteArray): ByteArray {
        val passphrase = settings.blobPassphrase
        return if (passphrase.isNullOrBlank()) bytes else SyncCipher(passphrase).decrypt(bytes)
    }

    companion object {
        /** WebDAV collection holding the per-entity sync files (decision D-4). */
        const val SYNC_DIR = "NewNewPipe/sync"
        private const val JSON_CONTENT_TYPE = "application/json"
    }
}
