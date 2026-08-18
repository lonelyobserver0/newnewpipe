package org.newnewpipe.extractor.services.bilibili.extractors;

import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.localization.DateWrapper;
import org.newnewpipe.extractor.stream.StreamInfoItemExtractor;
import org.newnewpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

public class BilibiliSameContentInfoItemExtractor implements StreamInfoItemExtractor {
    private final String name;
    private final String url;
    private final String thumbnailUrl;
    private final String uploaderName;
    private final long viewCount;

    public BilibiliSameContentInfoItemExtractor(String name, String url, String thumbnailUrl, String uploaderName, long viewCount) {
        this.name = name;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.uploaderName = uploaderName;
        this.viewCount = viewCount;
    }

    @Override
    public String getName() throws ParsingException {
        return name;
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return thumbnailUrl;
    }

    @Override
    public long getViewCount() throws ParsingException {
        return viewCount;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return uploaderName;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public long getDuration() throws ParsingException {
        return -1;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }
}
