package org.newnewpipe.extractor.services.peertube;

import org.newnewpipe.extractor.search.filter.FilterItem;
import org.newnewpipe.extractor.services.peertube.search.filter.PeertubeFilters;

import java.util.List;
import java.util.Optional;

public final class PeertubeHelpers {
    private PeertubeHelpers() { }
    public static Optional<FilterItem> getSepiaFilter(final List<FilterItem> selectedFilters) {
        final Optional<FilterItem> sepiaFilter = selectedFilters.stream()
                .filter(filterItem -> filterItem instanceof PeertubeFilters.PeertubeSepiaFilterItem)
                .findFirst();

        return sepiaFilter;
    }

    public static Optional<FilterItem> getSpecificFilter(final List<FilterItem> selectedFilters, final Class clazz) {
        final Optional<FilterItem> sepiaFilter = selectedFilters.stream()
                .filter(filterItem -> filterItem.getClass().isInstance(clazz))
                .findFirst();

        return sepiaFilter;
    }
}
