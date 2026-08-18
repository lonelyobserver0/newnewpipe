package org.newnewpipe.app.fragments.detail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.newnewpipe.app.R;
import org.newnewpipe.app.databinding.FragmentWatchTogetherBinding;
import org.newnewpipe.app.player.Player;
import org.newnewpipe.app.player.helper.PlayerHolder;
import org.newnewpipe.app.watchtogether.Participant;
import org.newnewpipe.app.watchtogether.WatchTogetherProtocol;
import org.newnewpipe.app.watchtogether.WatchTogetherSession;

/**
 * Tab "Guarda insieme" della pagina video (stesso pattern di Descrizione/Commenti/Testi).
 * <p>
 * Offre crea/unisci stanza quando non c'è una sessione attiva, e il pannello
 * della stanza (codice, ruolo, partecipanti, esci) quando la sessione è attiva.
 * La sessione vive nel {@link Player} (che pubblica gli snapshot e applica la
 * sync): qui la UI legge lo stato con un tick leggero (500 ms) tramite
 * {@link PlayerHolder#getPlayer()} e {@link Player#getWatchTogetherSession()}.
 */
public class WatchTogetherFragment extends Fragment {
    private static final long POLL_INTERVAL_MS = 500L;

    private FragmentWatchTogetherBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PlayerHolder playerHolder = PlayerHolder.getInstance();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updatePanel();
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public WatchTogetherFragment() {
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentWatchTogetherBinding.inflate(inflater, container, false);

        binding.wtPortInput.setText(String.valueOf(WatchTogetherProtocol.DEFAULT_PORT));

        binding.wtCreateButton.setOnClickListener(v -> {
            final Player player = playerHolder.getPlayer();
            if (player != null) {
                player.createWatchTogetherRoom();
            }
        });

        binding.wtJoinButton.setOnClickListener(v -> {
            final Player player = playerHolder.getPlayer();
            if (player == null) {
                return;
            }
            final String ip = binding.wtIpInput.getText().toString().trim();
            final String code = binding.wtCodeInput.getText().toString().trim();
            if (ip.isEmpty() || code.isEmpty()) {
                Toast.makeText(requireContext(), R.string.watch_together_join_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            int port = WatchTogetherProtocol.DEFAULT_PORT;
            try {
                port = Integer.parseInt(binding.wtPortInput.getText().toString().trim());
            } catch (final NumberFormatException ignored) {
                // porta di default
            }
            player.joinWatchTogetherRoom(ip, code, port);
        });

        binding.wtLeaveButton.setOnClickListener(v -> {
            final Player player = playerHolder.getPlayer();
            if (player != null) {
                player.leaveWatchTogetherRoom();
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        mainHandler.post(ticker);
    }

    @Override
    public void onStop() {
        mainHandler.removeCallbacks(ticker);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** Rende il pannello giusto in base allo stato della sessione sul player. */
    private void updatePanel() {
        if (binding == null) {
            return;
        }
        final Player player = playerHolder.getPlayer();
        final WatchTogetherSession session = player == null ? null
                : player.getWatchTogetherSession();
        final boolean inSession = session != null;

        binding.wtIdlePanel.setVisibility(inSession ? View.GONE : View.VISIBLE);
        binding.wtSessionPanel.setVisibility(inSession ? View.VISIBLE : View.GONE);
        if (!inSession) {
            return;
        }

        final String role = requireContext().getString(session.isHost()
                ? R.string.watch_together_host : R.string.watch_together_guest);
        binding.wtStatusText.setText(requireContext().getString(
                R.string.watch_together_status, session.getRoomId(),
                session.getParticipants().size(), role));

        final StringBuilder names = new StringBuilder();
        for (final Participant p : session.getParticipants()) {
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append(p.getDisplayName()).append(p.isHost() ? " ★" : "");
        }
        binding.wtParticipantsText.setText(names.length() > 0
                ? names.toString() : requireContext().getString(R.string.watch_together_participants_empty));
    }
}
