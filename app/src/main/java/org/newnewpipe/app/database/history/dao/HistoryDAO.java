package org.newnewpipe.app.database.history.dao;

import org.newnewpipe.app.database.BasicDAO;

public interface HistoryDAO<T> extends BasicDAO<T> {
    T getLatestEntry();
}
