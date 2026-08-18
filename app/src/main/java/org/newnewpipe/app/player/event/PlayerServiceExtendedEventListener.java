package org.newnewpipe.app.player.event;

import org.newnewpipe.app.player.PlayerService;
import org.newnewpipe.app.player.Player;
import org.newnewpipe.app.player.mediasession.PlayerServiceInterface;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(Player player,
                            PlayerServiceInterface playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
