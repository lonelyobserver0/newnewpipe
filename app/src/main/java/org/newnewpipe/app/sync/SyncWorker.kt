package org.newnewpipe.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic (or manual) sync trigger (plan 022 S7): runs
 * [SyncManager.syncNow] with the app's entity codecs. Scheduled by
 * [schedule] whenever the sync settings change and by [runNow] for the
 * one-shot manual trigger.
 */
class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val manager = SyncManager(SyncSettings(SyncSettings.encrypted(context)))
            when (manager.syncNow(appSyncCodecs(context))) {
                is SyncResult.Completed, SyncResult.Skipped -> Result.success()
            }
        } catch (e: Exception) {
            // A misconfigured server must not retry-loop the worker; the
            // periodic schedule retries on the next cycle.
            Result.failure()
        }
    }

    companion object {
        private const val WORK_TAG = "newnewpipe-sync"

        /**
         * (Re)schedules the periodic sync according to the current settings;
         * cancels the periodic work when sync is disabled.
         */
        @JvmStatic
        fun schedule(context: Context) {
            val settings = SyncSettings(SyncSettings.encrypted(context))
            if (!settings.enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
                return
            }
            val request = PeriodicWorkRequest.Builder(
                SyncWorker::class.java,
                settings.intervalMinutes,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Manual one-shot trigger (used by the settings UI). */
        @JvmStatic
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().addTag(WORK_TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_TAG-manual",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
