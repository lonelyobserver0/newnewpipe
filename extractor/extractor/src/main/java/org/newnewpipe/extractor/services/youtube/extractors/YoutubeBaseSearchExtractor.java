package org.newnewpipe.extractor.services.youtube.extractors;

import org.newnewpipe.extractor.search.filter.FilterItem;

import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.linkhandler.SearchQueryHandler;
import org.newnewpipe.extractor.search.SearchExtractor;

public abstract class YoutubeBaseSearchExtractor extends SearchExtractor {
    public YoutubeBaseSearchExtractor(final StreamingService service,
                                      final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @SuppressWarnings("unchecked")
    protected  <T extends FilterItem> T getSelectedContentFilterItem() {
        final FilterItem filterItem = getLinkHandler().getContentFilters().get(0);

        if (filterItem != null) {
            return (T) filterItem;
        }
        throw new RuntimeException("no content filter set");
    }
}
