package org.newnewpipe.extractor.feed;

import org.newnewpipe.extractor.ListExtractor;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.linkhandler.ListLinkHandler;
import org.newnewpipe.extractor.stream.StreamInfoItem;

/**
 * This class helps to extract items from lightweight feeds that the services may provide.
 * <p>
 * YouTube is an example of a service that has this alternative available.
 */
public abstract class FeedExtractor extends ListExtractor<StreamInfoItem> {
    public FeedExtractor(final StreamingService service, final ListLinkHandler listLinkHandler) {
        super(service, listLinkHandler);
    }
}
