package org.newnewpipe.app.sync

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter

/**
 * One playlist item as carried by the sync layer.
 *
 * The stream is identified by (serviceId, url) — the local auto-increment
 * stream uid differs across devices. [joinIndex] is the position inside the
 * playlist on the device that last changed the item, and [updatedAtMs] is
 * the epoch-millis change time used for the last-write-wins merge.
 */
data class SyncPlaylistItem(
    val serviceId: Int,
    val url: String,
    val title: String,
    val joinIndex: Int,
    val updatedAtMs: Long,
)

/**
 * One playlist as carried by the sync layer.
 *
 * Identity across devices is the playlist [name] (NewPipe playlists have no
 * stable cross-device id: the local uid is auto-incremented). [updatedAtMs]
 * is the epoch-millis change time used for the last-write-wins merge.
 */
data class SyncPlaylist(
    val name: String,
    val thumbnailUrl: String?,
    val updatedAtMs: Long,
    val streams: List<SyncPlaylistItem>,
)

/**
 * Pure last-write-wins merger for the `playlists.json` sync file
 * (decision D-4). Playlists have no JSON import/export in the app (see S4),
 * so this defines the sync format:
 *
 * ```
 * {"playlists": [{"name":"…","thumbnail_url":"…","updated_at_ms":…,
 *                 "streams":[{"service_id":0,"url":"…","title":"…",
 *                             "join_index":0,"updated_at_ms":…}]}]}
 * ```
 *
 * Merge rules:
 * - identity of a playlist: name; the playlist with the strictly newer
 *   [SyncPlaylist.updatedAtMs] wins (ties prefer the local side);
 * - identity of an item: serviceId + url; the item with the strictly newer
 *   [SyncPlaylistItem.updatedAtMs] wins, carrying its own [joinIndex];
 * - surviving local playlists/items keep their order, remote-only entries
 *   are appended in their remote order — so an item added on device B lands
 *   at the end of the list instead of reshuffling the user's ordering;
 * - unparseable JSON is treated as an empty side instead of failing the
 *   merge, so a corrupt remote file can never wipe the local playlists.
 *
 * The merger is pure: JSON to JSON, no Android framework, no I/O.
 */
object PlaylistMerger {

    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_NAME = "name"
    private const val KEY_THUMBNAIL_URL = "thumbnail_url"
    private const val KEY_UPDATED_AT_MS = "updated_at_ms"
    private const val KEY_STREAMS = "streams"
    private const val KEY_SERVICE_ID = "service_id"
    private const val KEY_URL = "url"
    private const val KEY_TITLE = "title"
    private const val KEY_JOIN_INDEX = "join_index"

    /**
     * Merges the local and remote `playlists.json` payloads.
     *
     * @return the merged JSON, or `null` when both sides are null/blank
     * (nothing to sync). A blank side is treated as "no data yet" and the
     * other side is returned, re-serialized canonically.
     */
    fun merge(localJson: String?, remoteJson: String?): String? {
        if (localJson.isNullOrBlank() && remoteJson.isNullOrBlank()) return null
        return serialize(mergeItems(parse(localJson), parse(remoteJson)))
    }

    /** Pure list merge; see the KDoc of [PlaylistMerger] for the rules. */
    fun mergeItems(
        local: List<SyncPlaylist>,
        remote: List<SyncPlaylist>,
    ): List<SyncPlaylist> {
        val remoteByName = remote.associateBy { it.name }
        val merged = LinkedHashMap<String, SyncPlaylist>()
        for (playlist in local) {
            val remotePlaylist = remoteByName[playlist.name]
            merged[playlist.name] = if (remotePlaylist == null) {
                playlist
            } else {
                winner(playlist, remotePlaylist)
            }
        }
        for (playlist in remote) {
            merged.putIfAbsent(playlist.name, playlist)
        }
        return merged.values.toList()
    }

    /**
     * Parses a `playlists.json` payload into playlists. Lenient: blank or
     * unparseable input yields an empty list; playlists without a usable
     * name are skipped, as are items without a usable url/title.
     */
    fun parse(json: String?): List<SyncPlaylist> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.`object`().from(json)
            val array = root.getArray(KEY_PLAYLISTS) ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj.getString(KEY_NAME)?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                SyncPlaylist(
                    name = name,
                    thumbnailUrl = obj.getString(KEY_THUMBNAIL_URL),
                    updatedAtMs = obj.getLong(KEY_UPDATED_AT_MS, 0L),
                    streams = parseStreams(obj.getArray(KEY_STREAMS)),
                )
            }
        } catch (e: JsonParserException) {
            emptyList()
        }
    }

    /** Serializes playlists to the canonical `playlists.json` payload. */
    fun serialize(items: List<SyncPlaylist>): String {
        val writer = JsonWriter.string().`object`().`array`(KEY_PLAYLISTS)
        for (playlist in items) {
            writer.`object`()
                .value(KEY_NAME, playlist.name)
                .value(KEY_THUMBNAIL_URL, playlist.thumbnailUrl)
                .value(KEY_UPDATED_AT_MS, playlist.updatedAtMs)
                .`array`(KEY_STREAMS)
            for (item in playlist.streams) {
                writer.`object`()
                    .value(KEY_SERVICE_ID, item.serviceId)
                    .value(KEY_URL, item.url)
                    .value(KEY_TITLE, item.title)
                    .value(KEY_JOIN_INDEX, item.joinIndex)
                    .value(KEY_UPDATED_AT_MS, item.updatedAtMs)
                    .end()
            }
            writer.end().end()
        }
        return writer.end().end().done()
    }

    private fun parseStreams(array: JsonArray?): List<SyncPlaylistItem> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = obj.getString(KEY_URL)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val title = obj.getString(KEY_TITLE)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            SyncPlaylistItem(
                serviceId = obj.getInt(KEY_SERVICE_ID, 0),
                url = url,
                title = title,
                joinIndex = obj.getInt(KEY_JOIN_INDEX, 0),
                updatedAtMs = obj.getLong(KEY_UPDATED_AT_MS, 0L),
            )
        }
    }

    private fun winner(local: SyncPlaylist, remote: SyncPlaylist): SyncPlaylist {
        val playlistWinner =
            if (remote.updatedAtMs > local.updatedAtMs) remote
            else if (remote.updatedAtMs < local.updatedAtMs) local
            else local // tie: prefer local
        // Items are merged independently of the playlist-level winner: an
        // item added on the "older" device must not be dropped just because
        // the playlist metadata was last touched on the other side.
        return playlistWinner.copy(streams = mergeStreams(local.streams, remote.streams))
    }

    private fun mergeStreams(
        local: List<SyncPlaylistItem>,
        remote: List<SyncPlaylistItem>,
    ): List<SyncPlaylistItem> {
        val remoteByKey = remote.associateBy { it.idKey() }
        val merged = LinkedHashMap<String, SyncPlaylistItem>()
        for (item in local) {
            val remoteItem = remoteByKey[item.idKey()]
            merged[item.idKey()] = remoteItem?.let { streamWinner(item, it) } ?: item
        }
        for (item in remote) {
            merged.putIfAbsent(item.idKey(), item)
        }
        return merged.values.toList()
    }

    private fun streamWinner(local: SyncPlaylistItem, remote: SyncPlaylistItem): SyncPlaylistItem =
        if (remote.updatedAtMs > local.updatedAtMs) {
            remote
        } else if (remote.updatedAtMs < local.updatedAtMs) {
            local
        } else {
            local // tie: prefer local
        }

    private fun SyncPlaylistItem.idKey(): String = "$serviceId:$url"
}
