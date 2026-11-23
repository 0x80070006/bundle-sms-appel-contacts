package app.people.contacts.sync

import android.content.Context
import android.provider.ContactsContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs the sync in the background: every 6 hours, and a minute after the phone's contacts change.
 * Only with a network; a network failure is retried with a growing delay, a problem the user must
 * fix (login, passphrase…) is not.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ContactSync.run(applicationContext)
        // Watch the contacts again: a content trigger only fires once.
        if (tags.contains(TAG_ON_CHANGE)) watchChanges(applicationContext)
        when {
            ContactSync.status.value.problem == null -> Result.success()
            ContactSync.retryable(applicationContext) && runAttemptCount < 5 -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "bundle-sync-periodic"
        private const val ON_CHANGE = "bundle-sync-on-change"
        private const val NOW = "bundle-sync-now"
        private const val TAG_ON_CHANGE = "on-change"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Turns background syncing on (or keeps it on). */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build(),
            )
            watchChanges(context)
        }

        private fun watchChanges(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .addContentUriTrigger(ContactsContract.Contacts.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(60, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_CHANGE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).addTag(TAG_ON_CHANGE).build(),
            )
        }

        /** A sync as soon as there's a network. */
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(ON_CHANGE)
                cancelUniqueWork(NOW)
            }
        }
    }
}
