package org.newnewpipe.app.sync

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter

/**
 * One history entry as carried by the sync layer.
 *
 * The stream is identified by (serviceId, url) — the same pair the streams
 * table uses — because the local auto-increment stream uid differs across
 * devices. [accessDateMs] is the epoch-millis last-access time: it is both
 * the display order key and the last-write-wins timestamp.
 */
data class SyncHistoryEntry(
    val serviceId: Int,
    val url: String,
    val title: String,
    val accessDateMs: Long,
    val repeatCount: Long,
)

/**
 * Pure last-write-wins merger for the `history.json` sync file
 * (decision D-4). History has no JSON import/export in the app (see S4), so
 * this defines the sync format:
 *
 * ```
 * {"history": [{"service_id":0,"url":"…","title":"…",
 *               "access_date_ms":…,"repeat_count":…}]}
 * ```
 *
 * Merge rules:
 * - identity of an entry: serviceId + url;
 * - the entry with the strictly newer [SyncHistoryEntry.accessDateMs] wins
 *   (a later watch supersedes an earlier one; ties prefer the local side);
 * - the merged list is sorted by access date descending (newest first),
 *   which is the natural display order of the history UI and makes the
 *   output deterministic regardless of which side is local;
 * - unparseable JSON is treated as an empty side instead of failing the
 *   merge, so a corrupt remote file can never wipe the local history.
 *
 * The merger is pure: JSON to JSON, no Android framework, no I/O.
 */
object HistoryMerger {

    private const val KEY_HISTORY = "history"
    private const val KEY_SERVICE_ID = "service_id"
    private const val KEY_URL = "url"
    private const val KEY_TITLE = "title"
    private const val KEY_ACCESS_DATE_MS = "access_date_ms"
    private const val KEY_REPEAT_COUNT = "repeat_count"

    /**
     * Merges the local and remote `history.json` payloads.
     *
     * @return the merged JSON, or `null` when both sides are null/blank
     * (nothing to sync). A blank side is treated as "no data yet" and the
     * other side is returned, re-serialized canonically.
     */
    fun merge(localJson: String?, remoteJson: String?): String? {
        if (localJson.isNullOrBlank() && remoteJson.isNullOrBlank()) return null
        return serialize(mergeItems(parse(localJson), parse(remoteJson)))
    }

    /** Pure list merge; see the KDoc of [HistoryMerger] for the rules. */
    fun mergeItems(
        local: List<SyncHistoryEntry>,
        remote: List<SyncHistoryEntry>,
    ): List<SyncHistoryEntry> {
        val remoteById = remote.associateBy { it.idKey() }
        val merged = HashMap<String, SyncHistoryEntry>()
        for (item in local) {
            val remoteItem = remoteById[item.idKey()]
            merged[item.idKey()] = remoteItem?.let { winner(item, it) } ?: item
        }
        for (item in remote) {
            merged.putIfAbsent(item.idKey(), item)
        }
        return merged.values
            .sortedWith(compareByDescending<SyncHistoryEntry> { it.accessDateMs }.thenBy { it.url })
    }

    /**
     * Parses a `history.json` payload into entries. Lenient: blank or
     * unparseable input yields an empty list; entries without a usable
     * url/title are skipped.
     */
    fun parse(json: String?): List<SyncHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.`object`().from(json)
            val array = root.getArray(KEY_HISTORY) ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = obj.getString(KEY_URL)?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val title = obj.getString(KEY_TITLE)?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                SyncHistoryEntry(
                    serviceId = obj.getInt(KEY_SERVICE_ID, 0),
                    url = url,
                    title = title,
                    accessDateMs = obj.getLong(KEY_ACCESS_DATE_MS, 0L),
                    repeatCount = obj.getLong(KEY_REPEAT_COUNT, 0L),
                )
            }
        } catch (e: JsonParserException) {
            emptyList()
        }
    }

    /** Serializes entries to the canonical `history.json` payload. */
    fun serialize(items: List<SyncHistoryEntry>): String {
        val writer = JsonWriter.string().`object`().`array`(KEY_HISTORY)
        for (item in items) {
            writer.`object`()
                .value(KEY_SERVICE_ID, item.serviceId)
                .value(KEY_URL, item.url)
                .value(KEY_TITLE, item.title)
                .value(KEY_ACCESS_DATE_MS, item.accessDateMs)
                .value(KEY_REPEAT_COUNT, item.repeatCount)
                .end()
        }
        return writer.end().end().done()
    }

    private fun winner(local: SyncHistoryEntry, remote: SyncHistoryEntry): SyncHistoryEntry =
        if (remote.accessDateMs > local.accessDateMs) {
            remote
        } else if (remote.accessDateMs < local.accessDateMs) {
            local
        } else {
            local // tie: prefer local
        }

    private fun SyncHistoryEntry.idKey(): String = "$serviceId:$url"
}
