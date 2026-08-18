package org.newnewpipe.app.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.newnewpipe.app.R;
import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.channel.ChannelInfoItem;
import org.newnewpipe.app.info_list.InfoItemBuilder;
import org.newnewpipe.app.local.history.HistoryRecordManager;
import org.newnewpipe.app.util.PicassoHelper;
import org.newnewpipe.app.util.Localization;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return;
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.getName());
        itemAdditionalDetailView.setText(getDetailLine(item));

        PicassoHelper.loadScaledDownThumbnail(itemThumbnailView.getContext(), infoItem.getThumbnailUrl())
                .into(itemThumbnailView);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        });
    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.getSubscriberCount() >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        }
        return details;
    }
}
