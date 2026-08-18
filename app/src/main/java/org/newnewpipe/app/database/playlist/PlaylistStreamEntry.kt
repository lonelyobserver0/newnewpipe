package org.newnewpipe.app.database.playlist

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.newnewpipe.app.database.LocalItem
import org.newnewpipe.app.database.playlist.model.PlaylistStreamEntity
import org.newnewpipe.app.database.stream.model.StreamEntity
import org.newnewpipe.app.database.stream.model.StreamStateEntity
import org.newnewpipe.extractor.stream.StreamInfoItem

data class PlaylistStreamEntry(
    @Embedded
    val streamEntity: StreamEntity,

    @ColumnInfo(name = StreamStateEntity.STREAM_PROGRESS_MILLIS, defaultValue = "0")
    val progressMillis: Long,

    @ColumnInfo(name = PlaylistStreamEntity.JOIN_STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = PlaylistStreamEntity.JOIN_INDEX)
    val joinIndex: Int
) : LocalItem {

    @Throws(IllegalArgumentException::class)
    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(streamEntity.serviceId, streamEntity.url, streamEntity.title, streamEntity.streamType)
        item.duration = streamEntity.duration
        item.uploaderName = streamEntity.uploader
        item.uploaderUrl = streamEntity.uploaderUrl
        item.thumbnailUrl = streamEntity.thumbnailUrl

        return item
    }

    override fun getLocalItemType(): LocalItem.LocalItemType {
        return LocalItem.LocalItemType.PLAYLIST_STREAM_ITEM
    }
}
