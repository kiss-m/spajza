package sk.matej.spajza.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import sk.matej.spajza.R
import sk.matej.spajza.domain.pantry.ExpiryReminder
import sk.matej.spajza.notifications.ExpiryNotifications
import sk.matej.spajza.ui.common.currentLocale
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The daily expiry reminder: on/off, its time, and help when the phone blocks notifications. */
@Composable
fun ExpiryReminderSettings(reminder: ExpiryReminder, onChange: (ExpiryReminder) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ExpiryNotifications.allowed(context)) }
    var pickTime by remember { mutableStateOf(false) }
    // The user may come back from the system settings with notifications allowed.
    LifecycleResumeEffect(Unit) {
        allowed = ExpiryNotifications.allowed(context)
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ExpiryNotifications.allowed(context)
        if (granted) onChange(reminder.copy(enabled = true))
    }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notifications_expiry_switch), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = reminder.enabled && allowed,
                onCheckedChange = { on ->
                    when {
                        !on -> onChange(reminder.copy(enabled = false))
                        needsPermission -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> onChange(reminder.copy(enabled = true))
                    }
                },
            )
        }
        Text(
            stringResource(R.string.notifications_expiry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reminder.enabled && allowed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_expiry_time, timeText(reminder.time)), Modifier.weight(1f))
                TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.notifications_expiry_change_time)) }
            }
        }
        if (reminder.enabled && !allowed) {
            Text(stringResource(R.string.notifications_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
            ) { Text(stringResource(R.string.notifications_open_settings)) }
        }
    }
    if (pickTime) {
        ReminderTimeDialog(
            initial = reminder.time,
            onDismiss = { pickTime = false },
            onPicked = {
                pickTime = false
                onChange(reminder.copy(time = it))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_expiry_time_title)) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun timeText(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time)
