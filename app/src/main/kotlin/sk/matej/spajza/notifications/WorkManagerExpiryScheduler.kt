package sk.matej.spajza.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sk.matej.spajza.domain.pantry.ExpiryDigest
import sk.matej.spajza.domain.pantry.ExpiryReminder
import sk.matej.spajza.domain.repository.ExpiryReminderScheduler
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** A daily WorkManager job that starts at the reminder's time; WorkManager keeps it across reboots. */
class WorkManagerExpiryScheduler(private val context: Context, private val clock: Clock) : ExpiryReminderScheduler {

    override fun apply(reminder: ExpiryReminder) {
        val work = WorkManager.getInstance(context)
        if (!reminder.enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = ExpiryDigest.delayUntil(reminder.time, ZonedDateTime.now(clock))
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // A new time replaces the old schedule instead of adding a second one.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    private companion object {
        const val WORK_NAME = "expiry-reminder"
    }
}
