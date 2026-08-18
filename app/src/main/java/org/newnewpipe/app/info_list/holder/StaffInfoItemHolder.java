package org.newnewpipe.app.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.newnewpipe.app.R;
import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.channel.StaffInfoItem;
import org.newnewpipe.app.info_list.InfoItemBuilder;
import org.newnewpipe.app.local.history.HistoryRecordManager;
import org.newnewpipe.app.util.PicassoHelper;

public class StaffInfoItemHolder extends InfoItemHolder {

    public final ImageView itemThumbnailView;
    public final TextView itemStaffNameView;
    private final TextView itemStaffTitleView;

    public StaffInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                               final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.detail_staff_thumbnail_view);
        itemStaffNameView = itemView.findViewById(R.id.detail_staff_name_text_view);
        itemStaffTitleView = itemView.findViewById(R.id.detail_staff_title_text_view);
    }

    public StaffInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                               final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_staff_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {

        if (!(infoItem instanceof StaffInfoItem)) {
            return;
        }
        StaffInfoItem item = (StaffInfoItem) infoItem;
        itemStaffNameView.setText(item.getName());
        itemStaffTitleView.setText(item.getTitle());

        PicassoHelper.loadScaledDownThumbnail(itemThumbnailView.getContext(), infoItem.getThumbnailUrl())
                .into(itemThumbnailView);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item.toChannelInfoItem());
            }
        });
    }
}
