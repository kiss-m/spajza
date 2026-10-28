package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.pantry.ExpiryReminder

/** Settings that belong to this device rather than to the account. */
interface DeviceSettingsRepository {
    val expiryReminder: Flow<ExpiryReminder>

    suspend fun setExpiryReminder(reminder: ExpiryReminder)
}

/** Runs the daily expiry check at the reminder's time, or stops it. */
interface ExpiryReminderScheduler {
    fun apply(reminder: ExpiryReminder)
}
