package org.newnewpipe.extractor.services.soundcloud.linkHandler;

import org.newnewpipe.extractor.search.filter.Filter;
import org.newnewpipe.extractor.search.filter.FilterItem;
import org.newnewpipe.extractor.services.soundcloud.search.filter.SoundcloudFilters;

import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.exceptions.ParsingException;
import org.newnewpipe.extractor.exceptions.ReCaptchaException;
import org.newnewpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import static org.newnewpipe.extractor.services.soundcloud.SoundcloudParsingHelper.SOUNDCLOUD_API_V2_URL;
import static org.newnewpipe.extractor.utils.Utils.UTF_8;

public class SoundcloudSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    public static final int ITEMS_PER_PAGE = 10;
    private static SoundcloudSearchQueryHandlerFactory instance = null;

    private final SoundcloudFilters searchFilters = new SoundcloudFilters();

    public static synchronized SoundcloudSearchQueryHandlerFactory getInstance() {
        if (instance == null) {
            instance = new SoundcloudSearchQueryHandlerFactory();
        }
        return instance;
    }
    @Override
    public String getUrl(final String id,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter)
            throws ParsingException {

        String url = SOUNDCLOUD_API_V2_URL + "search";
        String sortQuery = "";

        searchFilters.setSelectedContentFilter(selectedContentFilter);
        searchFilters.setSelectedSortFilter(selectedSortFilter);
        url += searchFilters.evaluateSelectedContentFilters();
        sortQuery = searchFilters.evaluateSelectedSortFilters();

        try {
            return url + "?q=" + URLEncoder.encode(id, UTF_8) + sortQuery + "&client_id="
                    + SoundcloudParsingHelper.clientId() + "&limit=" + ITEMS_PER_PAGE
                    + "&offset=0";

        } catch (final UnsupportedEncodingException e) {
            throw new ParsingException("Could not encode query", e);
        } catch (final ReCaptchaException e) {
            throw new ParsingException("ReCaptcha required", e);
        } catch (final IOException | ExtractionException e) {
            throw new ParsingException("Could not get client id", e);
        }
    }

    @Override
    public Filter getAvailableContentFilter() {
        return searchFilters.getContentFilters();
    }

    @Override
    public Filter getAvailableSortFilter() {
        return searchFilters.getSortFilters();
    }

    @Override
    public Filter getContentFilterSortFilterVariant(final int contentFilterId) {
        return searchFilters.getContentFilterSortFilterVariant(contentFilterId);
    }

    @Override
    public FilterItem getFilterItem(final int filterId) {
        return searchFilters.getFilterItem(filterId);
    }
}
