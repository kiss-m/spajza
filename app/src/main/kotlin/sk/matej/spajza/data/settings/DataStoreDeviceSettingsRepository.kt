package sk.matej.spajza.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.ExpiryReminder
import sk.matej.spajza.domain.repository.DeviceSettingsRepository
import java.io.IOException
import java.time.LocalTime

private val Context.settingsStore by preferencesDataStore(name = "device_settings")

class DataStoreDeviceSettingsRepository(private val context: Context) : DeviceSettingsRepository {

    override val expiryReminder: Flow<ExpiryReminder> = context.settingsStore.data
        // A corrupt file only loses the settings; defaults apply.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            val minute = preferences[EXPIRY_MINUTE] ?: ExpiryReminder.DEFAULT_TIME.toSecondOfDay() / 60
            ExpiryReminder(
                enabled = preferences[EXPIRY_ENABLED] ?: false,
                time = LocalTime.of(minute / 60 % 24, minute % 60),
            )
        }
        .distinctUntilChanged()

    override suspend fun setExpiryReminder(reminder: ExpiryReminder) {
        context.settingsStore.edit { preferences ->
            preferences[EXPIRY_ENABLED] = reminder.enabled
            preferences[EXPIRY_MINUTE] = reminder.time.hour * 60 + reminder.time.minute
        }
    }

    private companion object {
        val EXPIRY_ENABLED = booleanPreferencesKey("expiry_reminder_enabled")
        val EXPIRY_MINUTE = intPreferencesKey("expiry_reminder_minute")
    }
}
