package org.newnewpipe.app.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.local.LocalItemBuilder;

public class LocalPlaylistStreamCardItemHolder extends LocalPlaylistStreamItemHolder {

    public LocalPlaylistStreamCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                             final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);

        final View handle = itemView.findViewById(R.id.itemHandle);
        if (handle != null) {
            handle.setVisibility(View.VISIBLE);
        }
    }
}
