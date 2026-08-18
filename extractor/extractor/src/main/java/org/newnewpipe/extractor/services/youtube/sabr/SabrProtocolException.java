package org.newnewpipe.extractor.services.youtube.sabr;

import org.newnewpipe.extractor.exceptions.ExtractionException;

public class SabrProtocolException extends ExtractionException {
    public SabrProtocolException(final String message) {
        super(message);
    }

    public SabrProtocolException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
