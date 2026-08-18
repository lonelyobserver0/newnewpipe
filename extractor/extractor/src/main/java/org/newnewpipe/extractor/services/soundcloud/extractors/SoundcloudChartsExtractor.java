package org.newnewpipe.extractor.services.soundcloud.extractors;

import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.downloader.Downloader;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.kiosk.KioskExtractor;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.localization.ContentCountry;
import org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper;
import org.newnewpipe.extractor.stream.StreamInfoItem;
import org.newnewpipe.extractor.stream.StreamInfoItemsCollector;

import javax.annotation.Nonnull;
import java.io.IOException;

import static org.newnewpipe.extractor.ServiceList.SoundCloud;
import static org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper.SOUNDCLOUD_API_V2_URL;
import static org.newnewpipe.extractor.utils.Utils.isNullOrEmpty;

public class SoundcloudChartsExtractor extends KioskExtractor<StreamInfoItem> {
    public SoundcloudChartsExtractor(final StreamingService service,
                                     final ListLinkHandler linkHandler,
                                     final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    @Nonnull
    @Override
    public String getName() {
        return getId();
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final String nextPageUrl = SoundcloudParsingHelper.getStreamsFromApi(collector,
                page.getUrl(), true);

        return new InfoItemsPage<>(collector, new Page(nextPageUrl));
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        String apiUrl = SOUNDCLOUD_API_V2_URL + "charts" + "?genre=soundcloud:genres:all-music"
                + "&client_id=" + SoundcloudParsingHelper.clientId();

        if (getId().equals("Top 50")) {
            apiUrl += "&kind=top";
        } else {
            apiUrl += "&kind=trending";
        }

        final ContentCountry contentCountry = SoundCloud.getContentCountry();
        String apiUrlWithRegion = null;
        if (getService().getSupportedCountries().contains(contentCountry)) {
            apiUrlWithRegion = apiUrl + "&region=soundcloud:regions:"
                    + contentCountry.getCountryCode();
        }

        String nextPageUrl;
        try {
            nextPageUrl = SoundcloudParsingHelper.getStreamsFromApi(collector,
                    apiUrlWithRegion == null ? apiUrl : apiUrlWithRegion, true);
        } catch (final IOException e) {
            // Request to other region may be geo-restricted.
            // See https://github.com/TeamNewPipe/NewPipeExtractor/issues/537.
            // We retry without the specified region.
            nextPageUrl = SoundcloudParsingHelper.getStreamsFromApi(collector, apiUrl, true);
        }

        return new InfoItemsPage<>(collector, new Page(nextPageUrl));
    }
}
