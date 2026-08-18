package org.newnewpipe.extractor.channel;

import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.ListExtractor;
import org.newnewpipe.extractor.ListInfo;
import org.newnewpipe.extractor.Page;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.utils.ExtractorHelper;

import java.io.IOException;

public class ChannelTabInfo extends ListInfo<InfoItem> {
    public ChannelTabInfo(final int serviceId, final ListLinkHandler linkHandler) {
        super(serviceId, linkHandler, linkHandler.getContentFilters().get(0).getName());
    }

    public static ChannelTabInfo getInfo(final StreamingService service,
                                         final ListLinkHandler linkHandler)
            throws ExtractionException, IOException {
        final ChannelTabExtractor extractor = service.getChannelTabExtractor(linkHandler);
        extractor.fetchPage();
        return getInfo(extractor);
    }

    public static ChannelTabInfo getInfo(final ChannelTabExtractor extractor) {
        final ChannelTabInfo info =
                new ChannelTabInfo(extractor.getServiceId(), extractor.getLinkHandler());

        try {
            info.setOriginalUrl(extractor.getOriginalUrl());
        } catch (final Exception e) {
            info.addError(e);
        }

        final ListExtractor.InfoItemsPage<InfoItem> page
                = ExtractorHelper.getItemsPageOrLogError(info, extractor);
        info.setRelatedItems(page.getItems());
        info.setNextPage(page.getNextPage());

        return info;
    }

    public static ListExtractor.InfoItemsPage<InfoItem> getMoreItems(
            final StreamingService service, final ListLinkHandler linkHandler, final Page page)
            throws ExtractionException, IOException {
        return service.getChannelTabExtractor(linkHandler).getPage(page);
    }
}
