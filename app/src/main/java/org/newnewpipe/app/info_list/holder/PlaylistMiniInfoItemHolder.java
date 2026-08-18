package org.newnewpipe.app.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.newnewpipe.app.R;
import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.playlist.PlaylistInfoItem;
import org.newnewpipe.app.info_list.InfoItemBuilder;
import org.newnewpipe.app.local.history.HistoryRecordManager;
import org.newnewpipe.app.util.PicassoHelper;
import org.newnewpipe.app.util.Localization;

public class PlaylistMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    private final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                      final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                      final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof PlaylistInfoItem)) {
            return;
        }
        final PlaylistInfoItem item = (PlaylistInfoItem) infoItem;

        itemTitleView.setText(item.getName());
        itemStreamCountView.setText(Localization
                .localizeStreamCountMini(itemStreamCountView.getContext(), item.getStreamCount()));
        itemUploaderView.setText(item.getUploaderName());

        PicassoHelper.loadPlaylistThumbnail(item.getThumbnailUrl()).into(itemThumbnailView);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener().selected(item);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnPlaylistSelectedListener() != null) {
                itemBuilder.getOnPlaylistSelectedListener().held(item);
            }
            return true;
        });
    }
}
