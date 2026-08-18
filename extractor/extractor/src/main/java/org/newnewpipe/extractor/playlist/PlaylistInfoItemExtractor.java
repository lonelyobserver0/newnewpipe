package org.newnewpipe.extractor.playlist;

import org.newnewpipe.extractor.InfoItemExtractor;
import org.newnewpipe.extractor.exceptions.ParsingException;

import javax.annotation.Nonnull;

public interface PlaylistInfoItemExtractor extends InfoItemExtractor {

    /**
     * Get the uploader name
     * @return the uploader name
     */
    String getUploaderName() throws ParsingException;

    /**
     * Get the number of streams
     * @return the number of streams
     */
    long getStreamCount() throws ParsingException;

    /**
     * @return the type of this playlist, see {@link PlaylistInfo.PlaylistType} for a description
     *         of types. If not overridden always returns {@link PlaylistInfo.PlaylistType#NORMAL}.
     */
    @Nonnull
    default PlaylistInfo.PlaylistType getPlaylistType() throws ParsingException {
        return PlaylistInfo.PlaylistType.NORMAL;
    }
}
