package org.newnewpipe.app.local.holder;

import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.local.LocalItemBuilder;

/**
 * Playlist card UI for list item.
 */
public class RemotePlaylistCardItemHolder extends RemotePlaylistItemHolder {

    public RemotePlaylistCardItemHolder(final LocalItemBuilder infoItemBuilder,
                                        final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_card_item, parent);
    }
}
