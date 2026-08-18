package org.newnewpipe.app.info_list.holder

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.newnewpipe.extractor.InfoItem
import org.newnewpipe.extractor.comments.CommentsInfoItem
import org.newnewpipe.app.error.ErrorUtil
import org.newnewpipe.app.info_list.CommentItem
import org.newnewpipe.app.info_list.CommonItem
import org.newnewpipe.app.info_list.InfoItemBuilder
import org.newnewpipe.app.info_list.ItemViewMode
import org.newnewpipe.app.info_list.NewNewPipeComposeTheme
import org.newnewpipe.app.info_list.buildCommentItemState
import org.newnewpipe.app.info_list.buildInfoItemState
import org.newnewpipe.app.local.history.HistoryRecordManager
import org.newnewpipe.app.util.DeviceUtils
import org.newnewpipe.app.util.ImageViewerActivity
import org.newnewpipe.app.util.NavigationHelper
import org.newnewpipe.app.util.ThemeHelper
import org.newnewpipe.app.util.external_communication.InternalUrlsHandler
import org.newnewpipe.app.util.external_communication.ShareUtils

class ComposeInfoItemHolder(
    private val infoItemBuilder: InfoItemBuilder,
    parent: ViewGroup,
    private val itemViewMode: ItemViewMode
) : InfoItemHolder(
    infoItemBuilder,
    ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
) {

    private val composeView = itemView as ComposeView

    override fun updateFromItem(infoItem: InfoItem, historyRecordManager: HistoryRecordManager) {
        if (infoItem is CommentsInfoItem) {
            updateFromComment(infoItem)
            return
        }
        val state = buildInfoItemState(composeView.context, infoItem, historyRecordManager) ?: return
        composeView.setContent {
            NewNewPipeComposeTheme(composeView.context) {
                CommonItem(
                    state = state,
                    isGridLayout = ThemeHelper.isGrid(itemViewMode),
                    isCardLayout = itemViewMode == ItemViewMode.CARD,
                    showDragHandle = false,
                    onClick = {
                        when (infoItem) {
                            is org.newnewpipe.extractor.stream.StreamInfoItem -> infoItemBuilder.getOnStreamSelectedListener()?.selected(infoItem)
                            is org.newnewpipe.extractor.channel.ChannelInfoItem -> infoItemBuilder.getOnChannelSelectedListener()?.selected(infoItem)
                            is org.newnewpipe.extractor.playlist.PlaylistInfoItem -> infoItemBuilder.getOnPlaylistSelectedListener()?.selected(infoItem)
                        }
                    },
                    onLongClick = {
                        when (infoItem) {
                            is org.newnewpipe.extractor.stream.StreamInfoItem -> infoItemBuilder.getOnStreamSelectedListener()?.held(infoItem)
                            is org.newnewpipe.extractor.channel.ChannelInfoItem -> infoItemBuilder.getOnChannelSelectedListener()?.held(infoItem)
                            is org.newnewpipe.extractor.playlist.PlaylistInfoItem -> infoItemBuilder.getOnPlaylistSelectedListener()?.held(infoItem)
                        }
                    }
                )
            }
        }
    }

    private fun updateFromComment(item: CommentsInfoItem) {
        val state = buildCommentItemState(composeView.context, item)
        val pictures = item.pictures
        composeView.setContent {
            NewNewPipeComposeTheme(composeView.context) {
                CommentItem(
                    state = state,
                    onItemClick = {
                        // Come il holder View: il tap espande/chiude il testo e apre le risposte.
                        infoItemBuilder.getOnCommentsSelectedListener()?.selected(item)
                    },
                    onItemLongClick = {
                        if (DeviceUtils.isTv(composeView.context)) {
                            openCommentAuthor(item)
                        } else {
                            ShareUtils.copyToClipboard(composeView.context, state.commentText)
                        }
                    },
                    onAvatarClick = { openCommentAuthor(item) },
                    onReplyClick = {
                        infoItemBuilder.getOnCommentsReplyListener()?.selected(item)
                    },
                    onPicturesClick = {
                        composeView.context.startActivity(ImageViewerActivity.intent(composeView.context, pictures))
                    },
                    onLinkClick = { url ->
                        if (!InternalUrlsHandler.handleUrlCommentsTimestamp(
                                CompositeDisposable(), composeView.context, url)) {
                            ShareUtils.openUrlInBrowser(composeView.context, url, false)
                        }
                    }
                )
            }
        }
    }

    private fun openCommentAuthor(item: CommentsInfoItem) {
        if (item.uploaderUrl.isNullOrEmpty()) {
            return
        }
        val activity = composeView.context as? AppCompatActivity ?: return
        try {
            NavigationHelper.openChannelFragment(
                activity.supportFragmentManager,
                item.serviceId,
                item.uploaderUrl,
                item.uploaderName ?: ""
            )
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(activity, "Opening channel fragment", e)
        }
    }
}
