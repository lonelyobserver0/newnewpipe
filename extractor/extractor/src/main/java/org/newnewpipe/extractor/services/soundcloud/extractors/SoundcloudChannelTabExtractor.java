package org.newnewpipe.extractor.services.soundcloud.extractors;

import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.MultiInfoItemsCollector;
import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.channel.ChannelTabExtractor;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.linkhandler.ChannelTabs;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper.SOUNDCLOUD_API_V2_URL;
import static org.newnewpipe.extractor.utils.Utils.isNullOrEmpty;

public class SoundcloudChannelTabExtractor extends ChannelTabExtractor {
    private final String userId;
    private static final String USERS_ENDPOINT = SOUNDCLOUD_API_V2_URL + "users/";

    /** Empty page cap against infinite pagination loops. */
    private static final int MAX_EMPTY_PAGES = 3;

    public SoundcloudChannelTabExtractor(final StreamingService service,
                                         final ListLinkHandler linkHandler) {
        super(service, linkHandler);
        userId = getLinkHandler().getId();
    }

    private String getEndpoint() {
        switch (getTab()) {
            case ChannelTabs.TRACKS:
                return "/tracks";
            case ChannelTabs.PLAYLISTS:
                return "/playlists_without_albums";
            case ChannelTabs.ALBUMS:
                return "/albums";
        }
        throw new IllegalArgumentException("unsupported tab: " + getTab());
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException,
            ExtractionException {
    }

    @Nonnull
    @Override
    public String getId() {
        return userId;
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(USERS_ENDPOINT + userId + getEndpoint() + "?client_id="
                + SoundcloudParsingHelper.clientId() + "&limit=20" + "&linked_partitioning=1"));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final Set<String> visitedPages = new HashSet<>();

        String currentPageUrl = page.getUrl();
        String nextPageUrl = "";
        int emptyPageCount = 0;

        while (!isNullOrEmpty(currentPageUrl)) {
            if (!visitedPages.add(currentPageUrl)) {
                nextPageUrl = "";
                break;
            }

            final int itemsBefore = collector.getItems().size();
            final String candidateNextPage = SoundcloudParsingHelper.getInfoItemsFromApi(
                    collector, currentPageUrl);
            final boolean hasNewItems = collector.getItems().size() > itemsBefore;

            if (hasNewItems) {
                nextPageUrl = candidateNextPage;
                break;
            }

            emptyPageCount++;
            if (emptyPageCount >= MAX_EMPTY_PAGES || isNullOrEmpty(candidateNextPage)) {
                nextPageUrl = "";
                break;
            }

            currentPageUrl = candidateNextPage;
        }

        final Page nextPage = isNullOrEmpty(nextPageUrl) ? null : new Page(nextPageUrl);
        return new InfoItemsPage<>(collector, nextPage);
    }
}
