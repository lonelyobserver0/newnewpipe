package org.newnewpipe.app.player.mediabrowser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaBrowserServiceCompat.Result
import androidx.media.utils.MediaConstants
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.newnewpipe.app.MainActivity.DEBUG
import org.newnewpipe.app.NewPipeDatabase
import org.newnewpipe.app.R
import org.newnewpipe.app.database.history.model.StreamHistoryEntry
import org.newnewpipe.app.database.playlist.PlaylistLocalItem
import org.newnewpipe.app.database.playlist.PlaylistMetadataEntry
import org.newnewpipe.app.database.playlist.PlaylistStreamEntry
import org.newnewpipe.app.database.playlist.model.PlaylistRemoteEntity
import org.newnewpipe.app.database.stream.StreamWithState
import org.newnewpipe.app.database.subscription.SubscriptionEntity
import org.newnewpipe.extractor.InfoItem
import org.newnewpipe.extractor.InfoItem.InfoType
import org.newnewpipe.extractor.NewPipe
import org.newnewpipe.extractor.channel.ChannelInfoItem
import org.newnewpipe.extractor.exceptions.ContentNotAvailableException
import org.newnewpipe.extractor.playlist.PlaylistInfoItem
import org.newnewpipe.extractor.search.SearchInfo
import org.newnewpipe.extractor.search.filter.FilterItem
import org.newnewpipe.extractor.stream.StreamInfoItem
import org.newnewpipe.app.local.playlist.LocalPlaylistManager
import org.newnewpipe.app.local.playlist.RemotePlaylistManager
import org.newnewpipe.app.util.ExtractorHelper
import org.newnewpipe.app.util.ServiceHelper
import java.util.function.Consumer

/**
 * This class is used to cleanly separate the Service implementation (in
 * [org.newnewpipe.app.player.PlayerService]) and the media browser implementation (in this file).
 *
 * @param notifyChildrenChanged takes the parent id of the children that changed
 */
class MediaBrowserImpl(
    private val context: Context,
    notifyChildrenChanged: Consumer<String>, // parentId
) {
    private val database = NewPipeDatabase.getInstance(context)
    private var disposables = CompositeDisposable()

    init {
        // this will listen to changes in the bookmarks until this MediaBrowserImpl is dispose()d
        disposables.add(
            getMergedPlaylists().subscribe { notifyChildrenChanged.accept(ID_BOOKMARKS) }
        )
        
        // listen to changes in history
        disposables.add(
            database.streamHistoryDAO().getHistory().subscribe {
                notifyChildrenChanged.accept(ID_HISTORY) 
            }
        )
        
        // listen to changes in local playlist contents
        disposables.add(
            database.playlistStreamDAO().getAll().subscribe { playlistStreams ->
                // group by playlist ID and notify each playlist's content has changed
                playlistStreams.groupBy { it.playlistUid }.keys.forEach { playlistId ->
                    val mediaId = buildLocalPlaylistItemMediaId(false, playlistId).build().toString()
                    notifyChildrenChanged.accept(mediaId)
                }
            }
        )
        
        // listen to changes in remote playlist metadata (content changes require re-fetching)
        disposables.add(
            database.playlistDAO().getAll().subscribe { remotePlaylists ->
                // notify that remote playlist contents might have changed
                remotePlaylists.forEach { playlist ->
                    val mediaId = buildLocalPlaylistItemMediaId(true, playlist.uid).build().toString()
                    notifyChildrenChanged.accept(mediaId)
                }
            }
        )

        // listen to changes in the feed
        disposables.add(
            database.feedDAO().getAllStreams().subscribe {
                notifyChildrenChanged.accept(ID_FEED)
            }
        )

        // listen to changes in the subscriptions
        disposables.add(
            database.subscriptionDAO().getAll().subscribe {
                notifyChildrenChanged.accept(ID_SUBSCRIPTIONS)
            }
        )
    }

    //region Cleanup
    fun dispose() {
        disposables.dispose()
    }
    //endregion

    //region onGetRoot
    fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
        if (DEBUG) {
            Log.d(TAG, "onGetRoot($clientPackageName, $clientUid, $rootHints)")
        }

        val extras = Bundle()
        extras.putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
        )
        // Default presentation hints for the whole browse tree (Android Auto/AAOS):
        // browsable items as a list, playable streams as a grid.
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        )
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )
        return MediaBrowserServiceCompat.BrowserRoot(ID_ROOT, extras)
    }
    //endregion

    //region onLoadChildren
    fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (DEBUG) {
            Log.d(TAG, "onLoadChildren($parentId)")
        }

        result.detach() // allows sendResult() to happen later
        disposables.add(
            onLoadChildren(parentId)
                .subscribe(
                    { result.sendResult(it) },
                    { throwable ->
                        // null indicates an error, see the docs of MediaSessionCompat.onSearch()
                        result.sendResult(null)
                        Log.e(TAG, "onLoadChildren error for parentId=$parentId: $throwable")
                    }
                )
        )
    }

    private fun onLoadChildren(parentId: String): Single<List<MediaBrowserCompat.MediaItem>> {
        try {
            val parentIdUri = Uri.parse(parentId)
            val path = ArrayList(parentIdUri.pathSegments)

            if (path.isEmpty()) {
                return Single.just(
                    listOf(
                        createRootMediaItem(
                            ID_BOOKMARKS,
                            context.resources.getString(R.string.playlists),
                            R.drawable.ic_bookmark_white
                        ),
                        createRootMediaItem(
                            ID_SUBSCRIPTIONS,
                            context.resources.getString(R.string.tab_subscriptions),
                            R.drawable.ic_subscriptions
                        ),
                        createRootMediaItem(
                            ID_FEED,
                            context.resources.getString(R.string.fragment_feed_title),
                            R.drawable.ic_rss_feed
                        ),
                        createRootMediaItem(
                            ID_HISTORY,
                            context.resources.getString(R.string.action_history),
                            R.drawable.ic_history_white
                        )
                    )
                )
            }

            when (/*val uriType = */path.removeAt(0)) {
                ID_BOOKMARKS -> {
                    if (path.isEmpty()) {
                        return populateBookmarks()
                    }
                    if (path.size == 2) {
                        val localOrRemote = path[0]
                        val playlistId = path[1].toLong()
                        if (localOrRemote == ID_LOCAL) {
                            return populateLocalPlaylist(playlistId)
                        } else if (localOrRemote == ID_REMOTE) {
                            return populateRemotePlaylist(playlistId)
                        }
                    }
                    Log.w(TAG, "Unknown playlist URI: $parentId")
                    throw parseError(parentId)
                }

                ID_FEED -> return populateFeed()

                ID_SUBSCRIPTIONS -> return populateSubscriptions()

                ID_HISTORY -> return populateHistory()

                else -> throw parseError(parentId)
            }
        } catch (e: ContentNotAvailableException) {
            return Single.error(e)
        }
    }

    private fun createRootMediaItem(
        mediaId: String?,
        folderName: String?,
        @DrawableRes iconResId: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(mediaId)
        builder.setTitle(folderName)
        val resources = context.resources
        builder.setIconUri(
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(iconResId))
                .appendPath(resources.getResourceTypeName(iconResId))
                .appendPath(resources.getResourceEntryName(iconResId))
                .build()
        )

        val extras = Bundle()
        extras.putString(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
            context.getString(R.string.app_name)
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createPlaylistMediaItem(playlist: PlaylistLocalItem): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val playlistId = when (playlist) {
            is PlaylistRemoteEntity -> playlist.uid
            is PlaylistMetadataEntry -> playlist.uid
            else -> throw IllegalStateException("Unknown playlist type: ${playlist::class}")
        }
        builder
            .setMediaId(createMediaIdForInfoItem(playlist is PlaylistRemoteEntity, playlistId))
            .setTitle(playlist.orderingName)
            .setIconUri(playlist.thumbnailUrl?.let { Uri.parse(it) })

        val extras = Bundle()
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )
    }

    private fun createInfoItemMediaItem(item: InfoItem): MediaBrowserCompat.MediaItem? {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForInfoItem(item))
            .setTitle(item.name)
            .setIconUri(Uri.parse(item.thumbnailUrl))

        val extras = Bundle()
        when (item.infoType) {
            InfoType.STREAM -> {
                builder.setSubtitle((item as StreamInfoItem).uploaderName)
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                )
            }

            InfoType.PLAYLIST -> {
                builder.setSubtitle((item as PlaylistInfoItem).uploaderName)
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }

            InfoType.CHANNEL -> {
                builder.setSubtitle((item as ChannelInfoItem).description)
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }

            else -> return null
        }
        builder.setExtras(extras)

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun buildMediaId(): Uri.Builder {
        return Uri.Builder().authority(ID_AUTHORITY)
    }

    private fun buildPlaylistMediaId(playlistType: String?): Uri.Builder {
        return buildMediaId()
            .appendPath(ID_BOOKMARKS)
            .appendPath(playlistType)
    }

    private fun buildLocalPlaylistItemMediaId(isRemote: Boolean, playlistId: Long): Uri.Builder {
        return buildPlaylistMediaId(if (isRemote) ID_REMOTE else ID_LOCAL)
            .appendPath(playlistId.toString())
    }

    private fun buildInfoItemMediaId(item: InfoItem): Uri.Builder {
        return buildMediaId()
            .appendPath(ID_INFO_ITEM)
            .appendPath(infoItemTypeToString(item.infoType))
            .appendPath(item.serviceId.toString())
            .appendQueryParameter(ID_URL, item.url)
    }

    private fun createMediaIdForInfoItem(isRemote: Boolean, playlistId: Long): String {
        return buildLocalPlaylistItemMediaId(isRemote, playlistId)
            .build().toString()
    }

    private fun createLocalPlaylistStreamMediaItem(
        playlistId: Long,
        item: PlaylistStreamEntry,
        index: Int,
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForPlaylistIndex(false, playlistId, index))
            .setTitle(item.streamEntity.title)
            .setSubtitle(item.streamEntity.uploader)
            .setIconUri(Uri.parse(item.streamEntity.thumbnailUrl))
        setGridItemStyleHint(builder)

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createRemotePlaylistStreamMediaItem(
        playlistId: Long,
        item: StreamInfoItem,
        index: Int,
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForPlaylistIndex(true, playlistId, index))
            .setTitle(item.name)
            .setSubtitle(item.uploaderName)
            .setIconUri(Uri.parse(item.thumbnailUrl))
        setGridItemStyleHint(builder)

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createMediaIdForPlaylistIndex(
        isRemote: Boolean,
        playlistId: Long,
        index: Int,
    ): String {
        return buildLocalPlaylistItemMediaId(isRemote, playlistId)
            .appendPath(index.toString())
            .build().toString()
    }

    private fun createMediaIdForInfoItem(item: InfoItem): String {
        return buildInfoItemMediaId(item).build().toString()
    }

    private fun populateHistory(): Single<List<MediaBrowserCompat.MediaItem>> {
        val history = database.streamHistoryDAO().getHistory().firstOrError()
        return history.map { items ->
            items.map { this.createHistoryMediaItem(it) }
        }
    }

    private fun populateFeed(): Single<List<MediaBrowserCompat.MediaItem>> {
        return database.feedDAO().getAllStreams()
            .toSingle()
            .map { streams -> streams.map { this.createFeedMediaItem(it) } }
    }

    private fun createFeedMediaItem(streamWithState: StreamWithState): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val mediaId = buildMediaId()
            .appendPath(ID_FEED)
            .appendPath(streamWithState.stream.uid.toString())
            .build().toString()
        builder.setMediaId(mediaId)
            .setTitle(streamWithState.stream.title)
            .setSubtitle(streamWithState.stream.uploader)
            .setIconUri(Uri.parse(streamWithState.stream.thumbnailUrl))
        setGridItemStyleHint(builder)

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun populateSubscriptions(): Single<List<MediaBrowserCompat.MediaItem>> {
        return database.subscriptionDAO().getAll().firstOrError()
            .map { subscriptions ->
                subscriptions.map { this.createSubscriptionMediaItem(it) }
            }
    }

    private fun createSubscriptionMediaItem(
        subscription: SubscriptionEntity
    ): MediaBrowserCompat.MediaItem {
        // Playing a subscription starts a channel play queue, so reuse the info item scheme
        return createInfoItemMediaItem(subscription.toChannelInfoItem())!!
    }

    private fun setGridItemStyleHint(builder: MediaDescriptionCompat.Builder) {
        val extras = Bundle()
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )
        builder.setExtras(extras)
    }

    private fun createHistoryMediaItem(streamHistoryEntry: StreamHistoryEntry): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val mediaId = buildMediaId()
            .appendPath(ID_HISTORY)
            .appendPath(streamHistoryEntry.streamId.toString())
            .build().toString()
        builder.setMediaId(mediaId)
            .setTitle(streamHistoryEntry.streamEntity.title)
            .setSubtitle(streamHistoryEntry.streamEntity.uploader)
            .setIconUri(Uri.parse(streamHistoryEntry.streamEntity.thumbnailUrl))
        setGridItemStyleHint(builder)

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun getMergedPlaylists(): Flowable<MutableList<PlaylistLocalItem>> {
        return PlaylistLocalItem.getMergedOrderedPlaylists(
            LocalPlaylistManager(database),
            RemotePlaylistManager(database)
        )
    }

    private fun populateBookmarks(): Single<List<MediaBrowserCompat.MediaItem>> {
        val playlists = getMergedPlaylists().firstOrError()
        return playlists.map { playlist ->
            playlist.map { this.createPlaylistMediaItem(it) }
        }
    }

    private fun populateLocalPlaylist(playlistId: Long): Single<List<MediaBrowserCompat.MediaItem>> {
        val playlist = LocalPlaylistManager(database).getPlaylistStreams(playlistId).firstOrError()
        return playlist.map { items ->
            items.mapIndexed { index, item ->
                createLocalPlaylistStreamMediaItem(playlistId, item, index)
            }
        }
    }

    private fun populateRemotePlaylist(playlistId: Long): Single<List<MediaBrowserCompat.MediaItem>> {
        return RemotePlaylistManager(database).getPlaylist(playlistId).firstOrError()
            .flatMap { ExtractorHelper.getPlaylistInfo(it.serviceId, it.url, false) }
            .map {
                // ignore it.errors, i.e. ignore errors about specific items, since there would
                // be no way to show the error properly in Android Auto anyway
                it.relatedItems.mapIndexed { index, item ->
                    createRemotePlaylistStreamMediaItem(playlistId, item, index)
                }
            }
    }
    //endregion

    //region Search
    fun onSearch(
        query: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        if (DEBUG) {
            Log.d(TAG, "onSearch($query)")
        }

        result.detach() // allows sendResult() to happen later
        disposables.add(
            searchMusicBySongTitle(query)
                // ignore it.errors, i.e. ignore errors about specific items, since there would
                // be no way to show the error properly in Android Auto anyway
                .map { it.relatedItems.mapNotNull(this::createInfoItemMediaItem) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { result.sendResult(it) },
                    { throwable ->
                        // null indicates an error, see the docs of MediaSessionCompat.onSearch()
                        result.sendResult(null)
                        Log.e(TAG, "Search error for query=\"$query\": $throwable")
                    }
                )
        )
    }

    private fun searchMusicBySongTitle(query: String?): Single<SearchInfo> {
        val serviceId = ServiceHelper.getSelectedServiceId(context)
        val defaultContentFilter: MutableList<FilterItem> = java.util.ArrayList()
        val defaultSortFilter: List<FilterItem> = java.util.ArrayList()

        try {
            val service = NewPipe.getService(serviceId)
            defaultContentFilter.add(service.searchQHFactory.getFilterItem(0)) // 默认 "all"
        } catch (e: Exception) {
            Log.e("Search", "Failed to initialize default filters", e)
        }

        return ExtractorHelper.searchFor(serviceId, query, defaultContentFilter, defaultSortFilter)
    }
    //endregion

    companion object {
        private val TAG: String = MediaBrowserImpl::class.java.getSimpleName()
    }
}
