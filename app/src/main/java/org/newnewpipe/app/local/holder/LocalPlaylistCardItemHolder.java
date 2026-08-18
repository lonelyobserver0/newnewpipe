package org.newnewpipe.app.local.holder;

import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.local.LocalItemBuilder;

/**
 * Playlist card layout.
 */
public class LocalPlaylistCardItemHolder extends LocalPlaylistItemHolder {

    public LocalPlaylistCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                       final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_card_item, parent);
    }
}
