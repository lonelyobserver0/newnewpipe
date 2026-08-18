package org.newnewpipe.app.player.event;


import androidx.media3.common.PlaybackParameters;

import org.newnewpipe.extractor.stream.StreamInfo;
import org.newnewpipe.app.player.playqueue.PlayQueue;

public interface PlayerEventListener {
    void onQueueUpdate(PlayQueue queue);
    void onPlaybackUpdate(int state, int repeatMode, boolean shuffled,
                          PlaybackParameters parameters);
    void onProgressUpdate(int currentProgress, int duration, int bufferPercent);
    void onMetadataUpdate(StreamInfo info, PlayQueue queue);
    void onServiceStopped();
}
