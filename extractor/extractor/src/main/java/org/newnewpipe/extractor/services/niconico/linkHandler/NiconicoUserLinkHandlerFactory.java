package org.newnewpipe.extractor.services.niconico.linkHandler;

import static org.newnewpipe.extractor.services.niconico.NiconicoService.CHANNEL_URL;

import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.newnewpipe.extractor.search.filter.FilterItem;
import org.newnewpipe.extractor.services.niconico.NiconicoService;
import org.newnewpipe.extractor.utils.Parser;

import java.util.List;

public class NiconicoUserLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(final String url) throws ParsingException {
        if(url.contains(CHANNEL_URL)){
            return url;
        }
        return NiconicoService.USER_URL + Parser.matchGroup1(NiconicoService.USER_UPLOAD_LIST, url);
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        return Parser.isMatch(NiconicoService.USER_UPLOAD_LIST, url) || url.contains(CHANNEL_URL);
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        return id;
    }
}
