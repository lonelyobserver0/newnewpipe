package org.newnewpipe.extractor.services.peertube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.channel.ChannelExtractor;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.downloader.Response;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.exceptions.ReCaptchaException;
import org.newnewpipe.extractor.linkhandler.ChannelTabs;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.search.filter.Filter;
import org.newnewpipe.extractor.search.filter.FilterItem;
import org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper;
import org.newnewpipe.extractor.services.peertube.linkHandler.PeertubeChannelLinkHandlerFactory;
import org.newnewpipe.extractor.services.peertube.linkHandler.PeertubeChannelTabLinkHandlerFactory;
import org.newnewpipe.extractor.stream.StreamInfoItem;
import org.newnewpipe.extractor.stream.StreamInfoItemsCollector;
import org.newnewpipe.extractor.utils.JsonUtils;
import org.newnewpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.COUNT_KEY;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.ITEMS_PER_PAGE;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.START_KEY;
import static org.newnewpipe.extractor.services.peertube.PeertubeParsingHelper.collectStreamsFrom;
import static org.newnewpipe.extractor.utils.Utils.isNullOrEmpty;

public class PeertubeAccountExtractor extends ChannelExtractor {
    private JsonObject json;
    private final String baseUrl;
    private static final String ACCOUNTS = "accounts/";

    public PeertubeAccountExtractor(final StreamingService service,
                                    final ListLinkHandler linkHandler) throws ParsingException {
        super(service, linkHandler);
        this.baseUrl = getBaseUrl();
    }

    @Override
    public String getAvatarUrl() {
        String value;
        try {
            value = JsonUtils.getString(json, "avatar.path");
        } catch (final Exception e) {
            value = "/client/assets/images/default-avatar.png";
        }
        return baseUrl + value;
    }

    @Override
    public String getBannerUrl() {
        return null;
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        // The subscriber count cannot be retrieved directly. It needs to be calculated.
        // An accounts subscriber count is the number of the channel owner's subscriptions
        // plus the sum of all sub channels subscriptions.
        long subscribersCount = json.getLong("followersCount");
        String accountVideoChannelUrl = baseUrl + PeertubeChannelLinkHandlerFactory.API_ENDPOINT;
        if (getId().contains(ACCOUNTS)) {
            accountVideoChannelUrl += getId();
        } else {
            accountVideoChannelUrl += ACCOUNTS + getId();
        }
        accountVideoChannelUrl += "/video-channels";

        try {
            final String responseBody = getDownloader().get(accountVideoChannelUrl).responseBody();
            final JsonObject jsonResponse = JsonParser.object().from(responseBody);
            final JsonArray videoChannels = jsonResponse.getArray("data");
            for (final Object videoChannel : videoChannels) {
                final JsonObject videoChannelJsonObject = (JsonObject) videoChannel;
                subscribersCount += videoChannelJsonObject.getInt("followersCount");
            }
        } catch (final IOException | JsonParserException | ReCaptchaException ignored) {
            // something went wrong during video channels extraction,
            // only return subscribers of ownerAccount
        }
        return subscribersCount;
    }

    @Override
    public String getDescription() {
        try {
            return JsonUtils.getString(json, "description");
        } catch (final ParsingException e) {
            return "No description";
        }
    }

    @Override
    public String getParentChannelName() {
        return "";
    }

    @Override
    public String getParentChannelUrl() {
        return "";
    }

    @Override
    public String getParentChannelAvatarUrl() {
        return "";
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return false;
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        return Collections.singletonList(
                PeertubeChannelTabLinkHandlerFactory.getInstance().fromQuery(getId(),
                        Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.CHANNELS)), null, getBaseUrl())
        );
    }
    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(baseUrl + "/api/v1/" + getId() + "/videos?" + START_KEY + "=0&"
                + COUNT_KEY + "=" + ITEMS_PER_PAGE));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final Response response = getDownloader().get(page.getUrl());

        JsonObject pageJson = null;
        if (response != null && !Utils.isBlank(response.responseBody())) {
            try {
                pageJson = JsonParser.object().from(response.responseBody());
            } catch (final Exception e) {
                throw new ParsingException("Could not parse json data for account info", e);
            }
        }

        if (pageJson != null) {
            PeertubeParsingHelper.validate(pageJson);
            final long total = pageJson.getLong("total");

            final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
            collectStreamsFrom(collector, pageJson, getBaseUrl());

            return new InfoItemsPage<>(collector,
                    PeertubeParsingHelper.getNextPage(page.getUrl(), total));
        } else {
            throw new ExtractionException("Unable to get PeerTube account info");
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        String accountUrl = baseUrl + PeertubeChannelLinkHandlerFactory.API_ENDPOINT;
        if (getId().contains(ACCOUNTS)) {
            accountUrl += getId();
        } else {
            accountUrl += ACCOUNTS + getId();
        }

        final Response response = downloader.get(accountUrl);
        if (response != null) {
            setInitialData(response.responseBody());
        } else {
            throw new ExtractionException("Unable to extract PeerTube account data");
        }
    }

    private void setInitialData(final String responseBody) throws ExtractionException {
        try {
            json = JsonParser.object().from(responseBody);
        } catch (final JsonParserException e) {
            throw new ExtractionException("Unable to extract PeerTube account data", e);
        }
        if (json == null) {
            throw new ExtractionException("Unable to extract PeerTube account data");
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return JsonUtils.getString(json, "displayName");
    }
}
