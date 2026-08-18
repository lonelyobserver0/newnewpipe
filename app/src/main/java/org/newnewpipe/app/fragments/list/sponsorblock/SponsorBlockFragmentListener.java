package org.newnewpipe.app.fragments.list.sponsorblock;

import org.newnewpipe.extractor.sponsorblock.SponsorBlockSegment;

public interface SponsorBlockFragmentListener {
    void onSkippingEnabledChanged(boolean newValue);
    void onRequestNewPendingSegment(int startTime, int endTime);
    void onRequestClearPendingSegment();
    void onRequestSubmitPendingSegment(SponsorBlockSegment newSegment);
    void onSeekToRequested(long positionMillis);
}
