package org.newnewpipe.app.util;

import org.newnewpipe.app.error.ErrorInfo;

public interface DebounceSavable {

    /**
     * Execute operations to save the data. <br>
     * Must set {@link DebounceSaver#setIsModified(boolean)} false in this method manually
     * after the data has been saved.
     */
    void saveImmediate();

    void showError(ErrorInfo errorInfo);
}
