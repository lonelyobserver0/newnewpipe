package org.newnewpipe.app.settings;

import org.newnewpipe.app.R;

public class ChannelFilterListActivity extends BaseFilterListActivity {
    @Override
    protected String getPreferenceKey() {
        return getString(R.string.filter_by_channel_key) + "_set";
    }

    @Override
    protected String getEmptyViewText() {
        return getString(R.string.no_filters);
    }

    @Override
    protected String getAddDialogTitle() {
        return getString(R.string.add_filter);
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.filter_by_channel_title);
    }
}
