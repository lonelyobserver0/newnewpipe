package org.newnewpipe.extractor.services.media_ccc.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.channel.ChannelInfoItem;
import org.newnewpipe.extractor.channel.ChannelInfoItemsCollector;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.kiosk.KioskExtractor;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.services.media_ccc.extractors.infoItems.MediaCCCConferenceInfoItemExtractor;

import java.io.IOException;

import javax.annotation.Nonnull;

public class MediaCCCConferenceKiosk extends KioskExtractor<ChannelInfoItem> {
    private JsonObject doc;

    public MediaCCCConferenceKiosk(final StreamingService streamingService,
                                   final ListLinkHandler linkHandler,
                                   final String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }

    @Nonnull
    @Override
    public InfoItemsPage<ChannelInfoItem> getInitialPage() {
        final JsonArray conferences = doc.getArray("conferences");
        final ChannelInfoItemsCollector collector = new ChannelInfoItemsCollector(getServiceId());
        for (int i = 0; i < conferences.size(); i++) {
            collector.commit(new MediaCCCConferenceInfoItemExtractor(conferences.getObject(i)));
        }

        return new InfoItemsPage<>(collector, null);
    }

    @Override

    public InfoItemsPage<ChannelInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final String site = downloader.get(getLinkHandler().getUrl(), getExtractorLocalization())
                .responseBody();
        try {
            doc = JsonParser.object().from(site);
        } catch (final JsonParserException jpe) {
            throw new ExtractionException("Could not parse json.", jpe);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return doc.getString("Conferences");
    }
}
