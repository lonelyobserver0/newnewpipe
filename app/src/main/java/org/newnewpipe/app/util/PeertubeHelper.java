package org.newnewpipe.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.newnewpipe.app.R;
import org.newnewpipe.extractor.ServiceList;
import org.newnewpipe.extractor.services.peertube.PeertubeInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PeertubeHelper {
    private static final String MANUAL_SELECTION_KEY = "peertube_last_manual_selection_ms";

    private PeertubeHelper() { }

    public static List<PeertubeInstance> getInstanceList(final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        final String savedInstanceListKey = context.getString(R.string.peertube_instance_list_key);
        final String savedJson = sharedPreferences.getString(savedInstanceListKey, null);
        if (null == savedJson) {
            return Collections.singletonList(getCurrentInstance());
        }

        try {
            final JsonArray array = JsonParser.object().from(savedJson).getArray("instances");
            final List<PeertubeInstance> result = new ArrayList<>();
            for (final Object o : array) {
                if (o instanceof JsonObject) {
                    final JsonObject instance = (JsonObject) o;
                    final String name = instance.getString("name");
                    final String url = instance.getString("url");
                    result.add(new PeertubeInstance(url, name));
                }
            }
            return result;
        } catch (final JsonParserException e) {
            return Collections.singletonList(getCurrentInstance());
        }

    }

    public static PeertubeInstance selectInstance(final PeertubeInstance instance,
                                                  final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        final String selectedInstanceKey
                = context.getString(R.string.peertube_selected_instance_key);
        final JsonStringWriter jsonWriter = JsonWriter.string().object();
        jsonWriter.value("name", instance.getName());
        jsonWriter.value("url", instance.getUrl());
        final String jsonToSave = jsonWriter.end().done();
        sharedPreferences.edit().putString(selectedInstanceKey, jsonToSave).apply();
        ServiceList.PeerTube.setInstance(instance);
        return instance;
    }

    public static PeertubeInstance getCurrentInstance() {
        return ServiceList.PeerTube.getInstance();
    }

    /**
     * Selezione MANUALE da parte dell'utente (piano 022-S9): registra il
     * timestamp della scelta esplicita, che il failover automatico deve
     * rispettare (nessuno switch entro la finestra di grazia).
     */
    public static PeertubeInstance selectInstanceManual(final PeertubeInstance instance,
                                                        final Context context) {
        final PeertubeInstance selected = selectInstance(instance, context);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putLong(MANUAL_SELECTION_KEY, System.currentTimeMillis()).apply();
        return selected;
    }

    /** Timestamp (epoch ms) dell'ultima selezione manuale; 0 se mai avvenuta. */
    public static long lastManualSelectionMs(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(MANUAL_SELECTION_KEY, 0L);
    }

    /** true se l'utente ha salvato una lista di istanze PeerTube (non solo il default). */
    public static boolean hasConfiguredInstances(final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return sharedPreferences.contains(context.getString(R.string.peertube_instance_list_key));
    }
}
