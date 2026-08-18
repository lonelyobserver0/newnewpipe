package org.newnewpipe.extractor;

import org.newnewpipe.extractor.exceptions.WebViewUnavailableException;

public interface WebViewAvailabilityChecker {
    void checkWebViewAvailable() throws WebViewUnavailableException;
}
