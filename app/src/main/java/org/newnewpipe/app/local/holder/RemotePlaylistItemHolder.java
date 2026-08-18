package org.newnewpipe.app.local.holder;

import android.text.TextUtils;
import android.view.ViewGroup;

import org.newnewpipe.app.database.LocalItem;
import org.newnewpipe.app.database.playlist.model.PlaylistRemoteEntity;
import org.newnewpipe.extractor.NewPipe;
import org.newnewpipe.app.local.LocalItemBuilder;
import org.newnewpipe.app.local.history.HistoryRecordManager;
import org.newnewpipe.app.util.PicassoHelper;
import org.newnewpipe.app.util.Localization;

import java.time.format.DateTimeFormatter;

public class RemotePlaylistItemHolder extends PlaylistItemHolder {

    public RemotePlaylistItemHolder(final LocalItemBuilder infoItemBuilder,
                                    final ViewGroup parent) {
        super(infoItemBuilder, parent);
    }

    RemotePlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistRemoteEntity)) {
            return;
        }
        final PlaylistRemoteEntity item = (PlaylistRemoteEntity) localItem;

        itemTitleView.setText(item.getName());
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.getStreamCount()));
        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(item.getUploader())) {
            itemUploaderView.setText(Localization.concatenateStrings(item.getUploader(),
                    NewPipe.getNameOfService(item.getServiceId())));
        } else {
            itemUploaderView.setText(NewPipe.getNameOfService(item.getServiceId()));
        }

        PicassoHelper.loadPlaylistThumbnail(item.getThumbnailUrl()).into(itemThumbnailView);

        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter);
    }
}
