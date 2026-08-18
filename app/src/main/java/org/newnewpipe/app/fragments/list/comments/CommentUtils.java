package org.newnewpipe.app.fragments.list.comments;

import org.newnewpipe.extractor.comments.CommentsInfo;
import org.newnewpipe.extractor.comments.CommentsInfoItem;
import org.newnewpipe.app.util.SerializedUtils;

import java.io.IOException;

final class CommentUtils {
    private CommentUtils() {
    }

    public static CommentsInfo clone(
            final CommentsInfo item
    ) throws IOException, SecurityException, NullPointerException, ClassNotFoundException {
        return SerializedUtils.clone(item, CommentsInfo.class);
    }

    public static CommentsInfoItem clone(
            final CommentsInfoItem item
    ) throws IOException, SecurityException, NullPointerException, ClassNotFoundException {
        return SerializedUtils.clone(item, CommentsInfoItem.class);
    }
}
