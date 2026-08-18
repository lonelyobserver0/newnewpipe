package org.newnewpipe.app.database;

import static org.newnewpipe.app.database.Migrations.DB_VER_6;
import static org.newnewpipe.app.database.Migrations.DB_VER_901;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import org.newnewpipe.app.database.feed.dao.FeedDAO;
import org.newnewpipe.app.database.feed.dao.FeedGroupDAO;
import org.newnewpipe.app.database.feed.model.FeedEntity;
import org.newnewpipe.app.database.feed.model.FeedGroupEntity;
import org.newnewpipe.app.database.feed.model.FeedGroupSubscriptionEntity;
import org.newnewpipe.app.database.feed.model.FeedLastUpdatedEntity;
import org.newnewpipe.app.database.history.dao.SearchHistoryDAO;
import org.newnewpipe.app.database.history.dao.StreamHistoryDAO;
import org.newnewpipe.app.database.history.model.SearchHistoryEntry;
import org.newnewpipe.app.database.history.model.StreamHistoryEntity;
import org.newnewpipe.app.database.playlist.dao.PlaylistDAO;
import org.newnewpipe.app.database.playlist.dao.PlaylistRemoteDAO;
import org.newnewpipe.app.database.playlist.dao.PlaylistStreamDAO;
import org.newnewpipe.app.database.playlist.model.PlaylistEntity;
import org.newnewpipe.app.database.playlist.model.PlaylistRemoteEntity;
import org.newnewpipe.app.database.playlist.model.PlaylistStreamEntity;
import org.newnewpipe.app.database.stream.dao.StreamDAO;
import org.newnewpipe.app.database.stream.dao.StreamStateDAO;
import org.newnewpipe.app.database.stream.model.StreamEntity;
import org.newnewpipe.app.database.stream.model.StreamStateEntity;
import org.newnewpipe.app.database.subscription.SubscriptionDAO;
import org.newnewpipe.app.database.subscription.SubscriptionEntity;

@TypeConverters({Converters.class})
@Database(
        entities = {
                SubscriptionEntity.class, SearchHistoryEntry.class,
                StreamEntity.class, StreamHistoryEntity.class, StreamStateEntity.class,
                PlaylistEntity.class, PlaylistStreamEntity.class, PlaylistRemoteEntity.class,
                FeedEntity.class, FeedGroupEntity.class, FeedGroupSubscriptionEntity.class,
                FeedLastUpdatedEntity.class
        },
        version = DB_VER_901
)
public abstract class AppDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "newpipe.db";

    public abstract SearchHistoryDAO searchHistoryDAO();

    public abstract StreamDAO streamDAO();

    public abstract StreamHistoryDAO streamHistoryDAO();

    public abstract StreamStateDAO streamStateDAO();

    public abstract PlaylistDAO playlistDAO();

    public abstract PlaylistStreamDAO playlistStreamDAO();

    public abstract PlaylistRemoteDAO playlistRemoteDAO();

    public abstract FeedDAO feedDAO();

    public abstract FeedGroupDAO feedGroupDAO();

    public abstract SubscriptionDAO subscriptionDAO();
}
