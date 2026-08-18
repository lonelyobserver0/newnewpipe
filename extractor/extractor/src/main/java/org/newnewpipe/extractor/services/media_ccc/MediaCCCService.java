package org.newnewpipe.extractor.services.media_ccc;

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
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCConferenceExtractor;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCConferenceKiosk;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCLiveStreamExtractor;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCLiveStreamKiosk;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCParsingHelper;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCRecentKiosk;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCSearchExtractor;
import org.newnewpipe.extractor.services.media_ccc.extractors.MediaCCCStreamExtractor;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCConferenceLinkHandlerFactory;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCConferencesListLinkHandlerFactory;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCLiveListLinkHandlerFactory;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCRecentListLinkHandlerFactory;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCSearchQueryHandlerFactory;
import org.newnewpipe.extractor.services.media_ccc.linkHandler.MediaCCCStreamLinkHandlerFactory;
import org.newnewpipe.extractor.stream.StreamExtractor;
import org.newnewpipe.extractor.subscription.SubscriptionExtractor;
import org.newnewpipe.extractor.suggestion.SuggestionExtractor;

import static java.util.Arrays.asList;
import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

public class MediaCCCService extends StreamingService {
    public MediaCCCService(final int id) {
        super(id, "media.ccc.de", asList(AUDIO, VIDEO));
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler query) {
        return new MediaCCCSearchExtractor(this, query);
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return new MediaCCCStreamLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return new MediaCCCConferenceLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return new MediaCCCSearchQueryHandlerFactory();
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        if (MediaCCCParsingHelper.isLiveStreamId(linkHandler.getId())) {
            return new MediaCCCLiveStreamExtractor(this, linkHandler);
        }
        return new MediaCCCStreamExtractor(this, linkHandler);
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler) {
        return new MediaCCCConferenceExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return null;
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler) {
        return null;
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList list = new KioskList(this);

        // add kiosks here e.g.:
        try {
            list.addKioskEntry(
                    (streamingService, url, kioskId) -> new MediaCCCConferenceKiosk(
                            MediaCCCService.this,
                            new MediaCCCConferencesListLinkHandlerFactory().fromUrl(url),
                            kioskId
                    ),
                    new MediaCCCConferencesListLinkHandlerFactory(),
                    "conferences"
            );

            list.addKioskEntry(
                    (streamingService, url, kioskId) -> new MediaCCCRecentKiosk(
                            MediaCCCService.this,
                            new MediaCCCRecentListLinkHandlerFactory().fromUrl(url),
                            kioskId
                    ),
                    new MediaCCCRecentListLinkHandlerFactory(),
                    "recent"
            );

            list.addKioskEntry(
                    (streamingService, url, kioskId) -> new MediaCCCLiveStreamKiosk(
                            MediaCCCService.this,
                            new MediaCCCLiveListLinkHandlerFactory().fromUrl(url),
                            kioskId
                    ),
                    new MediaCCCLiveListLinkHandlerFactory(),
                    "live"
            );

            list.setDefaultKiosk("recent");
        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return list;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return null;
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler) {
        return null;
    }

    @Override
    public String getBaseUrl() {
        return "https://media.ccc.de";
    }

}
