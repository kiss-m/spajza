package sk.matej.spajza.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import sk.matej.spajza.domain.pantry.ExpiringItem
import sk.matej.spajza.domain.pantry.ExpiryDigest
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.CatalogRepository
import sk.matej.spajza.domain.repository.DeviceSettingsRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.ui.common.languageOf
import java.time.Clock
import java.time.LocalDate

/** What the worker needs from the app graph; workers are created by WorkManager, not Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExpiryWorkerEntryPoint {
    fun settings(): DeviceSettingsRepository

    fun auth(): AuthRepository

    fun users(): UserRepository

    fun pantry(): PantryRepository

    fun catalog(): CatalogRepository

    fun clock(): Clock
}

/**
 * The daily check: reads the household pantry (from the offline cache when there is no connection)
 * and posts one notification about what to use up. Only expiry, nothing else.
 */
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = EntryPointAccessors.fromApplication(applicationContext, ExpiryWorkerEntryPoint::class.java)
        if (!app.settings().expiryReminder.first().enabled || !ExpiryNotifications.allowed(applicationContext)) {
            return Result.success()
        }
        val today = LocalDate.now(app.clock())
        val found = withTimeoutOrNull(LOAD_TIMEOUT_MS) { expiring(app, today) } ?: return Result.success()
        val (items, names) = found
        ExpiryNotifications.show(applicationContext, items, names)
        return Result.success()
    }

    private suspend fun expiring(app: ExpiryWorkerEntryPoint, today: LocalDate): Pair<List<ExpiringItem>, (String) -> String>? {
        val uid = app.auth().currentUser.first()?.uid ?: return null
        val householdId = app.users().observe(uid).first()?.householdId ?: return null
        val items = ExpiryDigest.of(app.pantry().observe(householdId).first(), today)
        if (items.isEmpty()) return null
        val catalog = app.catalog().catalog.first()
        val language = languageOf(ExpiryNotifications.localizedContext(applicationContext).resources.configuration.locales[0])
        val names = items.associate { expiring ->
            val ref = expiring.item.ingredient
            val name = when (ref) {
                is IngredientRef.Catalog -> catalog.ingredientsById[ref.id]?.name?.get(language)
                is IngredientRef.Custom -> null
            }
            expiring.item.id to (name ?: ref.docId)
        }
        return items to { id: String -> names[id] ?: id }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 60_000L
    }
}
