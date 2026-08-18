package org.newnewpipe.app.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.newnewpipe.app.database.LocalItem;
import org.newnewpipe.app.database.playlist.PlaylistDuplicatesEntry;
import org.newnewpipe.app.database.playlist.PlaylistMetadataEntry;
import org.newnewpipe.app.local.LocalItemBuilder;
import org.newnewpipe.app.local.history.HistoryRecordManager;
import org.newnewpipe.app.util.PicassoHelper;
import org.newnewpipe.app.util.Localization;

import java.time.format.DateTimeFormatter;

public class LocalPlaylistItemHolder extends PlaylistItemHolder {

    private static final float GRAYED_OUT_ALPHA = 0.6f;

    public LocalPlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, parent);
    }

    LocalPlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                            final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistMetadataEntry)) {
            return;
        }
        final PlaylistMetadataEntry item = (PlaylistMetadataEntry) localItem;

        itemTitleView.setText(item.name);
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.streamCount));
        itemUploaderView.setVisibility(View.INVISIBLE);

        PicassoHelper.loadPlaylistThumbnail(item.thumbnailUrl).into(itemThumbnailView);

        if (item instanceof PlaylistDuplicatesEntry
                && ((PlaylistDuplicatesEntry) item).timesStreamIsContained > 0) {
            itemView.setAlpha(GRAYED_OUT_ALPHA);
        } else {
            itemView.setAlpha(1.0f);
        }

        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter);
    }
}
