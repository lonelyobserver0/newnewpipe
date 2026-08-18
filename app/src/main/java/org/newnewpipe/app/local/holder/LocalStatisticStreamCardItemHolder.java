package org.newnewpipe.app.local.holder;

import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.local.LocalItemBuilder;

public class LocalStatisticStreamCardItemHolder extends LocalStatisticStreamItemHolder {
    public LocalStatisticStreamCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                              final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
    }
}
