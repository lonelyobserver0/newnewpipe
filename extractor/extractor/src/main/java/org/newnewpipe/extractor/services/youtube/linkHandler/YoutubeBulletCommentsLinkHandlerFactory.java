package org.newnewpipe.extractor.services.youtube.linkHandler;

import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.newnewpipe.extractor.search.filter.FilterItem;

import java.util.List;

public class YoutubeBulletCommentsLinkHandlerFactory extends ListLinkHandlerFactory {
    YoutubeStreamLinkHandlerFactory factory = new YoutubeStreamLinkHandlerFactory();
    @Override
    public String getId(String url) throws ParsingException {
        return factory.getId(url);
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        return factory.onAcceptUrl(url);
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException {
        return factory.getUrl(id);
    }
}
