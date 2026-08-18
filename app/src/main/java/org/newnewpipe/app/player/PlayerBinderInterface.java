package org.newnewpipe.app.player;

import org.newnewpipe.app.player.mediasession.PlayerServiceInterface;

public interface PlayerBinderInterface {
    PlayerServiceInterface getService();
    Player getPlayer();
}
