package sk.matej.spajza.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sk.matej.spajza.MainActivity
import sk.matej.spajza.R
import sk.matej.spajza.domain.pantry.ExpiringItem
import sk.matej.spajza.domain.pantry.ExpiryReminderConfig
import sk.matej.spajza.ui.navigation.AppLink

/** The daily "use it up" notification. */
object ExpiryNotifications {
    private const val CHANNEL_ID = "expiry"
    private const val NOTIFICATION_ID = 1

    /** Whether the app may post notifications at all (Android 13 asks the user). */
    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [names] gives the shown name of each pantry item id. */
    fun show(context: Context, items: List<ExpiringItem>, names: (String) -> String) {
        if (items.isEmpty() || !allowed(context)) return
        val localized = localizedContext(context)
        ensureChannel(localized)
        val lines = items.take(ExpiryReminderConfig.MAX_NAMED).map { line(localized, names(it.item.id), it.daysLeft) }
        val more = items.size - lines.size
        val title = localized.resources.getQuantityString(R.plurals.notification_expiry_title, items.size, items.size)
        val summary = lines + listOfNotNull(
            more.takeIf { it > 0 }?.let { localized.resources.getQuantityString(R.plurals.notification_expiry_more, it, it) },
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        summary.forEach { style.addLine(it) }

        val open = Intent(localized, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AppLink.EXTRA_OPEN, AppLink.Pantry.key)
        val pending = PendingIntent.getActivity(
            localized,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(localized, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spajza)
            .setContentTitle(title)
            .setContentText(summary.joinToString(", "))
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Checked by allowed() above; the explicit check keeps lint and older devices happy.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(localized).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun line(context: Context, name: String, daysLeft: Long): String = context.getString(
        when {
            daysLeft < 0 -> R.string.notification_expiry_expired
            daysLeft == 0L -> R.string.notification_expiry_today
            else -> R.string.notification_expiry_tomorrow
        },
        name,
    )

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_expiry),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_expiry_description) }
        manager.createNotificationChannel(channel)
    }

    /** Workers run without an activity, so the per-app language is applied by hand. */
    fun localizedContext(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }
}
