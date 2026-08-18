package org.newnewpipe.extractor.services.soundcloud;

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
import org.newnewpipe.extractor.localization.ContentCountry;
import org.newnewpipe.extractor.playlist.PlaylistExtractor;
import org.newnewpipe.extractor.search.SearchExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudChannelExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudChannelTabExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudChartsExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudCommentsExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudPlaylistExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudSearchExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudStreamExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudSubscriptionExtractor;
import org.newnewpipe.extractor.services.soundcloud.extractors.SoundcloudSuggestionExtractor;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudChannelLinkHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudChannelTabLinkHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudChartsLinkHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudCommentsLinkHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudPlaylistLinkHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.linkHandler.SoundcloudStreamLinkHandlerFactory;
import org.newnewpipe.extractor.stream.StreamExtractor;
import org.newnewpipe.extractor.subscription.SubscriptionExtractor;

import java.util.List;

import static java.util.Arrays.asList;
import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.newnewpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;

public class SoundcloudService extends StreamingService {

    public SoundcloudService(final int id) {
        super(id, "SoundCloud", asList(AUDIO, COMMENTS));
    }

    @Override
    public String getBaseUrl() {
        return "https://soundcloud.com";
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return SoundcloudSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return SoundcloudStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return SoundcloudChannelLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return SoundcloudChannelTabLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return SoundcloudPlaylistLinkHandlerFactory.getInstance();
    }

    @Override
    public List<ContentCountry> getSupportedCountries() {
        // Country selector here: https://soundcloud.com/charts/top?genre=all-music
        return ContentCountry.listFrom(
                "AU", "CA", "DE", "FR", "GB", "IE", "NL", "NZ", "US"
        );
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        return new SoundcloudStreamExtractor(this, linkHandler);
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler) {
        return new SoundcloudChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler) {
        return new SoundcloudChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler) {
        return new SoundcloudPlaylistExtractor(this, linkHandler);
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new SoundcloudSearchExtractor(this, queryHandler);
    }

    @Override
    public SoundcloudSuggestionExtractor getSuggestionExtractor() {
        return new SoundcloudSuggestionExtractor(this);
    }
    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList.KioskExtractorFactory chartsFactory = (streamingService, url, id) ->
                new SoundcloudChartsExtractor(SoundcloudService.this,
                        new SoundcloudChartsLinkHandlerFactory().fromUrl(url), id);

        final KioskList list = new KioskList(this);

        // add kiosks here e.g.:
        final SoundcloudChartsLinkHandlerFactory h = new SoundcloudChartsLinkHandlerFactory();
        try {
            list.addKioskEntry(chartsFactory, h, "Top 50");
            list.addKioskEntry(chartsFactory, h, "New & hot");
            list.setDefaultKiosk("New & hot");
        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return list;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return new SoundcloudSubscriptionExtractor(this);
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return SoundcloudCommentsLinkHandlerFactory.getInstance();
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return new SoundcloudCommentsExtractor(this, linkHandler);
    }
}
