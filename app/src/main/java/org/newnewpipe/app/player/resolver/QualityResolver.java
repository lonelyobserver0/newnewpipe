package org.newnewpipe.app.player.resolver;

import androidx.annotation.Nullable;

import org.newnewpipe.extractor.stream.AudioStream;
import org.newnewpipe.extractor.stream.VideoStream;

import java.util.List;

public interface QualityResolver {
    int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

    int getOverrideResolutionIndex(List<VideoStream> sortedVideos,
                                   String selectedResolution,
                                   @Nullable String selectedCodec);

    int getCurrentAudioQualityIndex(List<AudioStream> audioStreams);
}
