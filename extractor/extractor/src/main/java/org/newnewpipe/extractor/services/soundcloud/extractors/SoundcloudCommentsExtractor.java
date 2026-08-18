package org.newnewpipe.extractor.services.soundcloud.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.newnewpipe.extractor.NewPipe;
import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.comments.CommentsExtractor;
import org.newnewpipe.extractor.comments.CommentsInfoItem;
import org.newnewpipe.extractor.comments.CommentsInfoItemsCollector;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.downloader.Response;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.exceptions.ReCaptchaException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

import static org.newnewpipe.extractor.utils.Utils.isNullOrEmpty;

public class SoundcloudCommentsExtractor extends CommentsExtractor {
    public SoundcloudCommentsExtractor(final StreamingService service,
                                       final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() throws ExtractionException,
            IOException {
        return getPage(getUrl());
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page) throws ExtractionException,
            IOException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            return InfoItemsPage.emptyPage();
        }
        return getPage(page.getUrl());
    }

    @Nonnull
    private InfoItemsPage<CommentsInfoItem> getPage(@Nonnull final String url)
            throws ParsingException, IOException, ReCaptchaException {

        final Downloader downloader = NewPipe.getDownloader();
        final Response response = downloader.get(url);

        final JsonObject json;
        try {
            json = JsonParser.object().from(response.responseBody());
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse json", e);
        }

        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(
                getServiceId());

        collectStreamsFrom(collector, json.getArray("collection"));

        final String nextHref = json.getString("next_href");
        return new InfoItemsPage<>(collector, isNullOrEmpty(nextHref) ? null : new Page(nextHref));
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) { }

    private void collectStreamsFrom(final CommentsInfoItemsCollector collector,
                                    final JsonArray entries) throws ParsingException {
        final String url = getUrl();
        for (final Object comment : entries) {
            collector.commit(new SoundcloudCommentsInfoItemExtractor((JsonObject) comment, url));
        }
    }
}
