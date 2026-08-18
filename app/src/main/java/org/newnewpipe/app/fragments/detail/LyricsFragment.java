package org.newnewpipe.app.fragments.detail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.newnewpipe.app.R;
import org.newnewpipe.app.databinding.FragmentLyricsBinding;
import org.newnewpipe.app.fragments.StateSaverFragment;
import org.newnewpipe.app.music.LyricsClient;
import org.newnewpipe.app.music.LyricsOverlay;
import org.newnewpipe.app.music.OkHttpLyricsGetter;
import org.newnewpipe.app.player.Player;
import org.newnewpipe.app.player.helper.PlayerHolder;
import org.newnewpipe.app.player.playqueue.PlayQueue;
import org.newnewpipe.app.player.playqueue.PlayQueueItem;
import org.newnewpipe.extractor.stream.StreamInfo;

import java.util.Queue;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Tab "Testi" della pagina video (stesso pattern di Descrizione/Commenti).
 * <p>
 * Carica i testi sincronizzati (LRC) da lrclib via {@link LyricsClient} usando
 * titolo/artista/durata dello stream, e li mostra con la riga corrente
 * evidenziata e scroll automatico (reso delegato a {@link LyricsOverlay},
 * riusato dall'overlay del player). La posizione di riproduzione viene letta
 * dal player via {@link PlayerHolder#getPlayer()} con un tick leggero
 * (500 ms) solo mentre il tab è attivo.
 */
public class LyricsFragment extends StateSaverFragment {
    private static final String TAG = "LyricsFragment";
    private static final long SYNC_INTERVAL_MS = 500L;

    private StreamInfo streamInfo;
    private FragmentLyricsBinding binding;
    private LyricsOverlay lyricsOverlay;
    private LyricsClient lyricsClient;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PlayerHolder playerHolder = PlayerHolder.getInstance();

    private final Runnable syncTicker = new Runnable() {
        @Override
        public void run() {
            updateSyncHighlight();
            mainHandler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };

    public LyricsFragment() {
    }

    @Override
    public String generateSuffix() {
        return "." + System.nanoTime() + ".lyrics";
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        objectsToSave.add(streamInfo);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        streamInfo = (StreamInfo) savedObjects.poll();
    }

    public LyricsFragment(final StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentLyricsBinding.inflate(inflater, container, false);
        lyricsOverlay = new LyricsOverlay(binding.lyricsRoot, binding.lyricsScrollView,
                binding.lyricsTextView);
        if (streamInfo != null) {
            fetchLyrics();
        }
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        mainHandler.post(syncTicker);
    }

    @Override
    public void onStop() {
        mainHandler.removeCallbacks(syncTicker);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(syncTicker);
        disposables.clear();
        super.onDestroy();
    }

    private void fetchLyrics() {
        if (binding == null || streamInfo == null) {
            return;
        }
        binding.lyricsProgress.setVisibility(View.VISIBLE);
        if (lyricsClient == null) {
            lyricsClient = new LyricsClient(new OkHttpLyricsGetter(), LyricsClient.DEFAULT_ENDPOINT);
        }
        disposables.add(Observable.fromCallable(() ->
                        lyricsClient.fetchSyncedLines(
                                streamInfo.getUploaderName(),
                                streamInfo.getName(),
                                null,
                                (int) Math.round(streamInfo.getDuration())))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lines -> {
                    if (binding == null) {
                        return;
                    }
                    binding.lyricsProgress.setVisibility(View.GONE);
                    if (lines == null || lines.isEmpty()) {
                        lyricsOverlay.setEmpty();
                    } else {
                        lyricsOverlay.setLines(lines);
                    }
                }, error -> {
                    if (binding == null) {
                        return;
                    }
                    binding.lyricsProgress.setVisibility(View.GONE);
                    Log.e(TAG, "Lyrics fetch failed for " + streamInfo.getUrl()
                            + " [" + streamInfo.getUploaderName() + " - " + streamInfo.getName()
                            + "]: " + error, error);
                    lyricsOverlay.setError();
                }));
    }

    /**
     * Evidenzia la riga corrente leggendo la posizione del player, ma solo se
     * il player sta riproducendo proprio questo stream.
     */
    private void updateSyncHighlight() {
        if (binding == null || lyricsOverlay == null || streamInfo == null) {
            return;
        }
        final Player player = playerHolder.getPlayer();
        if (player == null) {
            return;
        }
        final PlayQueue playQueue = player.getPlayQueue();
        final PlayQueueItem currentItem = playQueue == null ? null : playQueue.getItem();
        if (currentItem == null || !streamInfo.getUrl().equals(currentItem.getUrl())) {
            return;
        }
        lyricsOverlay.updatePosition(player.getPlayerPosition());
    }
}
