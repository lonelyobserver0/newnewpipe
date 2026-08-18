package org.newnewpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.comments.CommentsExtractor;
import org.newnewpipe.extractor.comments.CommentsInfoItem;
import org.newnewpipe.extractor.comments.CommentsInfoItemsCollector;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

public class NiconicoCommentsExtractor extends CommentsExtractor {

    private JsonObject watch;
    private final NiconicoWatchDataCache watchDataCache;
    private final NiconicoCommentsCache commentsCache;

    public NiconicoCommentsExtractor(
            final StreamingService service,
            final ListLinkHandler uiHandler,
            final NiconicoWatchDataCache watchDataCache,
            final NiconicoCommentsCache commentsCache) {
        super(service, uiHandler);
        this.watchDataCache = watchDataCache;
        this.commentsCache = commentsCache;
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        watch = watchDataCache.refreshAndGetWatchData(downloader, getId());
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(getServiceId());
        for (final JsonObject comment : commentsCache.getComments(watch,
                getDownloader(), getId())) {
            collector.commit(new NiconicoCommentsInfoItemExtractor(comment, getUrl()));
        }
        this.getId();
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return null;
    }
}
