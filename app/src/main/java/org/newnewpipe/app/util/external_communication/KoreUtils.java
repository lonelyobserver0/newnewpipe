package org.newnewpipe.app.util.external_communication;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.newnewpipe.app.R;
import org.newnewpipe.extractor.ServiceList;
import org.newnewpipe.app.util.NavigationHelper;

/**
 * Util class that provides methods which are related to the Kodi Media Center and its Kore app.
 * @see <a href="https://kodi.tv/">Kodi website</a>
 */
public final class KoreUtils {
    private KoreUtils() { }

    public static boolean isServiceSupportedByKore(final int serviceId) {
        return (serviceId == ServiceList.YouTube.getServiceId()
                || serviceId == ServiceList.SoundCloud.getServiceId());
    }

    public static boolean shouldShowPlayWithKodi(@NonNull final Context context,
                                                 final int serviceId) {
        return false;
    }

    public static void showInstallKoreDialog(@NonNull final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (dialog, which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                });
        builder.create().show();
    }
}
