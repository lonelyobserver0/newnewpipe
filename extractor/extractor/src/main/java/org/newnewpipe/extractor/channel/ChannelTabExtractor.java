package org.newnewpipe.extractor.channel;

import org.newnewpipe.extractor.InfoItem;
import org.newnewpipe.extractor.ListExtractor;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;

import javax.annotation.Nonnull;

public abstract class ChannelTabExtractor extends ListExtractor<InfoItem> {

    public ChannelTabExtractor(final StreamingService service,
                               final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Nonnull
    public String getTab() {
        return getLinkHandler().getContentFilters().get(0).getName();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getTab();
    }

}
