package org.newnewpipe.app.info_list.holder;

import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.info_list.InfoItemBuilder;

public class ChannelGridInfoItemHolder extends ChannelMiniInfoItemHolder {
    public ChannelGridInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_channel_grid_item, parent);
    }
}
