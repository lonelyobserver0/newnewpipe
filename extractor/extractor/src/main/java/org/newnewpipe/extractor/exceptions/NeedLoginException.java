package org.newnewpipe.extractor.exceptions;

public class NeedLoginException extends ParsingException {

    public NeedLoginException(String message) {
        super(message);
    }

    public NeedLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
