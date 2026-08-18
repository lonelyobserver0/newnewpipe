package org.newnewpipe.app.local.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.newnewpipe.app.database.LocalItem
import org.newnewpipe.app.database.playlist.PlaylistMetadataEntry
import org.newnewpipe.app.database.playlist.PlaylistStreamEntry
import org.newnewpipe.app.database.playlist.model.PlaylistRemoteEntity
import org.newnewpipe.app.database.stream.StreamStatisticsEntry
import org.newnewpipe.app.info_list.CommonItem
import org.newnewpipe.app.info_list.NewNewPipeComposeTheme
import org.newnewpipe.app.info_list.buildLocalItemState
import org.newnewpipe.app.info_list.ItemViewMode
import org.newnewpipe.app.local.LocalItemBuilder
import org.newnewpipe.app.local.history.HistoryRecordManager
import org.newnewpipe.app.util.ThemeHelper
import java.time.format.DateTimeFormatter

class ComposeLocalItemHolder(
    private val localItemBuilder: LocalItemBuilder,
    parent: ViewGroup,
    private val itemViewMode: ItemViewMode,
    private val showDragHandle: Boolean
) : LocalItemHolder(
    localItemBuilder,
    ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
) {

    private val composeView = itemView as ComposeView

    override fun updateFromItem(
        item: LocalItem,
        historyRecordManager: HistoryRecordManager,
        dateTimeFormatter: DateTimeFormatter
    ) {
        val state = buildLocalItemState(composeView.context, item, dateTimeFormatter) ?: return
        composeView.setContent {
            NewNewPipeComposeTheme(composeView.context) {
                CommonItem(
                    state = state,
                    isGridLayout = ThemeHelper.isGrid(itemViewMode),
                    isCardLayout = itemViewMode == ItemViewMode.CARD,
                    showDragHandle = showDragHandle,
                    onClick = { localItemBuilder.getOnItemSelectedListener()?.selected(item) },
                    onLongClick = { localItemBuilder.getOnItemSelectedListener()?.held(item) },
                    onDragStart = {
                        if (showDragHandle) {
                            localItemBuilder.getOnItemSelectedListener()?.drag(item, this@ComposeLocalItemHolder)
                        }
                    }
                )
            }
        }
    }
}
