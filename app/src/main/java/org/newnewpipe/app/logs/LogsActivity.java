package org.newnewpipe.app.logs;

import static org.newnewpipe.app.util.Localization.assureCorrectAppLanguage;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.newnewpipe.app.R;
import org.newnewpipe.app.databinding.ActivityLogsBinding;
import org.newnewpipe.app.util.ThemeHelper;
import org.newnewpipe.app.util.external_communication.ShareUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Debug page showing the logcat entries produced by this app's own process.
 * <p>
 * Reachable from the navigation drawer. Reads the last {@link #MAX_LOG_LINES} lines
 * of the app's log buffer; since Android 4.2 an app can read the log lines of its
 * own pid without the READ_LOGS permission, so this works on stock devices too.
 */
public class LogsActivity extends AppCompatActivity {
    private static final int MAX_LOG_LINES = 1500;

    private ActivityLogsBinding binding;
    private boolean loadedOnce = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);
        setTitle(getString(R.string.title_activity_logs));

        binding = ActivityLogsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.logsToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.logsRefreshButton.setOnClickListener(v -> loadLogs());
        binding.logsCopyButton.setOnClickListener(v -> {
            final String text = binding.logsText.getText().toString();
            if (!text.isEmpty() && !text.equals(getString(R.string.logs_loading))) {
                ShareUtils.copyToClipboard(this, text);
            }
        });
        binding.logsShareButton.setOnClickListener(v -> {
            final String text = binding.logsText.getText().toString();
            if (!text.isEmpty() && !text.equals(getString(R.string.logs_loading))) {
                ShareUtils.shareText(this, getString(R.string.app_name) + " log", text);
            }
        });

        loadLogs();
    }

    private void loadLogs() {
        binding.logsProgress.setVisibility(View.VISIBLE);
        final int scrollY = binding.logsScroll.getScrollY();

        new Thread(() -> {
            final String logs = readOwnLogcat();
            runOnUiThread(() -> {
                binding.logsProgress.setVisibility(View.GONE);
                if (logs == null) {
                    binding.logsText.setText(R.string.logs_error);
                } else if (logs.isEmpty()) {
                    binding.logsText.setText(R.string.logs_empty);
                } else {
                    binding.logsText.setText(logs);
                }

                if (!loadedOnce) {
                    loadedOnce = true;
                    // First load: show the newest entries at the bottom.
                    binding.logsScroll.post(() -> binding.logsScroll.fullScroll(View.FOCUS_DOWN));
                } else {
                    // Refresh: keep the position the user was reading.
                    binding.logsScroll.setScrollY(scrollY);
                }
            });
        }).start();
    }

    /**
     * Dumps the logcat buffer filtered to this app's own process.
     *
     * @return the log lines, or {@code null} if the logcat command failed
     */
    private String readOwnLogcat() {
        try {
            final java.lang.Process process = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d", "--pid=" + android.os.Process.myPid(),
                    "-t", String.valueOf(MAX_LOG_LINES)});
            final StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            process.waitFor();
            return sb.toString();
        } catch (final IOException | InterruptedException e) {
            return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
