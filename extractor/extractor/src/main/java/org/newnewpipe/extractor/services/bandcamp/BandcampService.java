// Created by Fynn Godau 2019, licensed GNU GPL version 3 or later

package org.newnewpipe.extractor.services.bandcamp;

import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.channel.ChannelExtractor;
import org.newnewpipe.extractor.channel.ChannelTabExtractor;
import org.newnewpipe.extractor.comments.CommentsExtractor;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.kiosk.KioskList;
import org.newnewpipe.extractor.linkhandler.LinkHandler;
import org.newnewpipe.extractor.linkhandler.LinkHandlerFactory;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.newnewpipe.extractor.linkhandler.SearchQueryHandler;
import org.newnewpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.newnewpipe.extractor.playlist.PlaylistExtractor;
import org.newnewpipe.extractor.search.SearchExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampChannelExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampChannelTabExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampCommentsExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampExtractorHelper;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampFeaturedExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampPlaylistExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampRadioExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampRadioStreamExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampSearchExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampStreamExtractor;
import org.newnewpipe.extractor.services.bandcamp.extractors.BandcampSuggestionExtractor;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampChannelLinkHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampChannelTabLinkHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampCommentsLinkHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampFeaturedLinkHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampPlaylistLinkHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampSearchQueryHandlerFactory;
import org.newnewpipe.extractor.services.bandcamp.linkHandler.BandcampStreamLinkHandlerFactory;
import org.newnewpipe.extractor.stream.StreamExtractor;
import org.newnewpipe.extractor.subscription.SubscriptionExtractor;
import org.newnewpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Arrays;

import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.newnewpipe.extractor.services.bandcamp.extractors.BandcampExtractorHelper.BASE_URL;
import static org.newnewpipe.extractor.services.bandcamp.extractors.BandcampFeaturedExtractor.FEATURED_API_URL;
import static org.newnewpipe.extractor.services.bandcamp.extractors.BandcampFeaturedExtractor.KIOSK_FEATURED;
import static org.newnewpipe.extractor.services.bandcamp.extractors.BandcampRadioExtractor.KIOSK_RADIO;
import static org.newnewpipe.extractor.services.bandcamp.extractors.BandcampRadioExtractor.RADIO_API_URL;

public class BandcampService extends StreamingService {

    public BandcampService(final int id) {
        super(id, "Bandcamp", Arrays.asList(AUDIO, COMMENTS));
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return new BandcampStreamLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return BandcampChannelLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return BandcampChannelTabLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return new BandcampPlaylistLinkHandlerFactory();
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return new BandcampSearchQueryHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return new BandcampCommentsLinkHandlerFactory();
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new BandcampSearchExtractor(this, queryHandler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return new BandcampSuggestionExtractor(this);
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {

        final KioskList kioskList = new KioskList(this);

        try {
            kioskList.addKioskEntry(
                    (streamingService, url, kioskId) -> new BandcampFeaturedExtractor(
                            BandcampService.this,
                            new BandcampFeaturedLinkHandlerFactory().fromUrl(FEATURED_API_URL),
                            kioskId
                    ),
                    new BandcampFeaturedLinkHandlerFactory(),
                    KIOSK_FEATURED
            );

            kioskList.addKioskEntry(
                    (streamingService, url, kioskId) -> new BandcampRadioExtractor(
                            BandcampService.this,
                            new BandcampFeaturedLinkHandlerFactory().fromUrl(RADIO_API_URL),
                            kioskId
                    ),
                    new BandcampFeaturedLinkHandlerFactory(),
                    KIOSK_RADIO
            );

            kioskList.setDefaultKiosk(KIOSK_FEATURED);

        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return kioskList;
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler) {
        return new BandcampChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler) {
        return new BandcampChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler) {
        return new BandcampPlaylistExtractor(this, linkHandler);
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        if (BandcampExtractorHelper.isRadioUrl(linkHandler.getUrl())) {
            return new BandcampRadioStreamExtractor(this, linkHandler);
        }
        return new BandcampStreamExtractor(this, linkHandler);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler) {
        return new BandcampCommentsExtractor(this, linkHandler);
    }
}
