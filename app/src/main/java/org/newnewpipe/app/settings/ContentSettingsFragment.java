package org.newnewpipe.app.settings;

import android.os.Bundle;
import android.widget.Toast;
import org.newnewpipe.app.R;
import org.newnewpipe.extractor.NewPipe;
import org.newnewpipe.extractor.localization.ContentCountry;
import org.newnewpipe.extractor.localization.Localization;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();


        initialSelectedLocalization = org.newnewpipe.app.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.newnewpipe.app.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = defaultPreferences.getString(getString(R.string.app_language_key), "en");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.newnewpipe.app.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.newnewpipe.app.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage =
                defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }
}
