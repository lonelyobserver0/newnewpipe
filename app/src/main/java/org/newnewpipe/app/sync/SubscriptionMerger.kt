package org.newnewpipe.app.sync

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import org.newnewpipe.app.local.subscription.services.ImportExportJsonHelper
import java.io.ByteArrayInputStream

/**
 * One subscription as carried by the sync layer.
 *
 * Identity is (serviceId, url) — the same unique key used by the
 * subscriptions table. [updatedAtMs] is the epoch-millis timestamp of the
 * last local change and drives the last-write-wins merge.
 */
data class SyncSubscription(
    val serviceId: Int,
    val url: String,
    val name: String,
    val updatedAtMs: Long,
)

/**
 * Pure last-write-wins merger for the `subscriptions.json` sync file
 * (decision D-4: one JSON file per entity, per-item LWW, never a blind
 * overwrite of the whole file).
 *
 * File format (envelope deliberately minimal so the payload stays compatible
 * with the app's import feature — [ImportExportJsonHelper] only needs the
 * `subscriptions` array):
 *
 * ```
 * {"subscriptions": [{"service_id":0,"url":"…","name":"…","updated_at_ms":…}]}
 * ```
 *
 * Merge rules:
 * - identity of an item: serviceId + url;
 * - the item with the strictly newer [SyncSubscription.updatedAtMs] wins
 *   (ties prefer the local side — deterministic, no clock-dependent flip);
 * - surviving local items keep their order, remote-only items are appended
 *   in their remote order;
 * - legacy payloads written by [ImportExportJsonHelper] (no per-item
 *   timestamp) are parsed through the shared import parser and treated as
 *   snapshots with `updated_at_ms = 0`, so a timestamped item from the
 *   other side wins;
 * - unparseable JSON is treated as an empty side instead of failing the
 *   merge, so a corrupt remote file can never wipe the local data.
 *
 * The merger is pure: it only maps JSON to JSON, no Android framework, no
 * I/O. The DB and the WebDAV transport are handled by the sync manager.
 */
object SubscriptionMerger {

    private const val KEY_SUBSCRIPTIONS = "subscriptions"
    private const val KEY_SERVICE_ID = "service_id"
    private const val KEY_URL = "url"
    private const val KEY_NAME = "name"
    private const val KEY_UPDATED_AT_MS = "updated_at_ms"

    /**
     * Merges the local and remote `subscriptions.json` payloads.
     *
     * @return the merged JSON, or `null` when both sides are null/blank
     * (nothing to sync). A blank side is treated as "no data yet" and the
     * other side is returned, re-serialized canonically.
     */
    fun merge(localJson: String?, remoteJson: String?): String? {
        if (localJson.isNullOrBlank() && remoteJson.isNullOrBlank()) return null
        return serialize(mergeItems(parse(localJson), parse(remoteJson)))
    }

    /** Pure list merge; see the KDoc of [SubscriptionMerger] for the rules. */
    fun mergeItems(
        local: List<SyncSubscription>,
        remote: List<SyncSubscription>,
    ): List<SyncSubscription> {
        val remoteById = remote.associateBy { it.idKey() }
        val merged = LinkedHashMap<String, SyncSubscription>()
        for (item in local) {
            val remoteItem = remoteById[item.idKey()]
            merged[item.idKey()] = remoteItem?.let { winner(item, it) } ?: item
        }
        for (item in remote) {
            merged.putIfAbsent(item.idKey(), item)
        }
        return merged.values.toList()
    }

    /**
     * Parses a `subscriptions.json` payload into items. Lenient: blank or
     * unparseable input yields an empty list; items without a usable
     * url/name are skipped, like [ImportExportJsonHelper] does on import.
     */
    fun parse(json: String?): List<SyncSubscription> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.`object`().from(json)
            val array = root.getArray(KEY_SUBSCRIPTIONS) ?: return emptyList()
            val hasSyncTimestamps = array.any { it is JsonObject && it.has(KEY_UPDATED_AT_MS) }
            if (!hasSyncTimestamps) {
                // Payload written by the app's own export feature (no
                // per-item timestamp): reuse the shared import parser.
                return parseLegacy(json)
            }
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = obj.getString(KEY_URL)?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val name = obj.getString(KEY_NAME)?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                SyncSubscription(
                    serviceId = obj.getInt(KEY_SERVICE_ID, 0),
                    url = url,
                    name = name,
                    updatedAtMs = obj.getLong(KEY_UPDATED_AT_MS, 0L),
                )
            }
        } catch (e: JsonParserException) {
            emptyList()
        }
    }

    /** Serializes items to the canonical `subscriptions.json` payload. */
    fun serialize(items: List<SyncSubscription>): String {
        val writer = JsonWriter.string().`object`().`array`(KEY_SUBSCRIPTIONS)
        for (item in items) {
            writer.`object`()
                .value(KEY_SERVICE_ID, item.serviceId)
                .value(KEY_URL, item.url)
                .value(KEY_NAME, item.name)
                .value(KEY_UPDATED_AT_MS, item.updatedAtMs)
                .end()
        }
        return writer.end().end().done()
    }

    private fun parseLegacy(json: String): List<SyncSubscription> = try {
        ImportExportJsonHelper.readFrom(
            ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)),
            null,
        ).map { item ->
            SyncSubscription(
                serviceId = item.serviceId,
                url = item.url,
                name = item.name,
                updatedAtMs = 0L,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun winner(local: SyncSubscription, remote: SyncSubscription): SyncSubscription =
        if (remote.updatedAtMs > local.updatedAtMs) {
            remote
        } else if (remote.updatedAtMs < local.updatedAtMs) {
            local
        } else {
            local // tie: prefer local
        }

    private fun SyncSubscription.idKey(): String = "$serviceId:$url"
}
