package org.newnewpipe.extractor.services.peertube.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.downloader.Response;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.linkhandler.SearchQueryHandler;
import org.newnewpipe.extractor.MultiInfoItemsCollector;
import org.newnewpipe.extractor.search.SearchExtractor;
import org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper;
import org.newnewpipe.extractor.utils.Utils;

import java.io.IOException;

import javax.annotation.Nonnull;

import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.COUNT_KEY;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.ITEMS_PER_PAGE;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.START_KEY;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.collectStreamsFrom;
import static org.newnewpipe.extractor.utils.Utils.isNullOrEmpty;

public class PeertubeSearchExtractor extends SearchExtractor {

    // if we should use PeertubeSepiaStreamInfoItemExtractor
    private final boolean sepia;

    public PeertubeSearchExtractor(final StreamingService service,
                                   final SearchQueryHandler linkHandler) {
        this(service, linkHandler, false);
    }

    public PeertubeSearchExtractor(final StreamingService service,
                                   final SearchQueryHandler linkHandler,
                                   final boolean sepia) {
        super(service, linkHandler);
        this.sepia = sepia;
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        return getPage(new Page(getUrl() + "&" + START_KEY + "=0&"
                + COUNT_KEY + "=" + ITEMS_PER_PAGE));
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final Response response = getDownloader().get(page.getUrl());

        JsonObject json = null;
        if (response != null && !Utils.isBlank(response.responseBody())) {
            try {
                json = JsonParser.object().from(response.responseBody());
            } catch (final Exception e) {
                throw new ParsingException("Could not parse json data for search info", e);
            }
        }

        if (json != null) {
            PeertubeParsingHelper.validate(json);
            final long total = json.getLong("total");

            final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
            collectStreamsFrom(collector, json, getBaseUrl(), sepia);

            return new InfoItemsPage<>(collector,
                    PeertubeParsingHelper.getNextPage(page.getUrl(), total));
        } else {
            throw new ExtractionException("Unable to get PeerTube search info");
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
    }
}
