package org.newnewpipe.app.fragments.list;

import org.newnewpipe.app.fragments.ViewContract;

public interface ListViewContract<I, N> extends ViewContract<I> {
    void showListFooter(boolean show);

    void handleNextItems(N result);
}
