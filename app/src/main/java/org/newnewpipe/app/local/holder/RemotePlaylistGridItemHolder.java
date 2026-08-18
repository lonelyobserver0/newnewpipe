package org.newnewpipe.app.local.holder;

import android.view.ViewGroup;

import org.newnewpipe.app.R;
import org.newnewpipe.app.local.LocalItemBuilder;

public class RemotePlaylistGridItemHolder extends RemotePlaylistItemHolder {
    public RemotePlaylistGridItemHolder(final LocalItemBuilder infoItemBuilder,
                                        final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_playlist_grid_item, parent);
    }
}
