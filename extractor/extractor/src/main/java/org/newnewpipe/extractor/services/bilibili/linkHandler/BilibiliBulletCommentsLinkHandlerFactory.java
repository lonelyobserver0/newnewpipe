package org.newnewpipe.extractor.services.bilibili.linkHandler;

import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.newnewpipe.extractor.search.filter.FilterItem;
import org.newnewpipe.extractor.services.bilibili.WatchDataCache;
import org.newnewpipe.extractor.services.bilibili.linkHandler.BilibiliStreamLinkHandlerFactory;
import org.newnewpipe.extractor.services.bilibili.utils;

import java.util.List;

public class BilibiliBulletCommentsLinkHandlerFactory extends ListLinkHandlerFactory {

    @Override
    public String getId(String url) throws ParsingException {
        return new BilibiliStreamLinkHandlerFactory().getId(url);
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        return new BilibiliStreamLinkHandlerFactory().getUrl(id);
    }
}
