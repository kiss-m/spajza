package sk.matej.spajza.ui.household

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sk.matej.spajza.R
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.ui.common.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The household's current invite while it is still valid. */
data class InviteUi(val code: String, val expiresAt: Instant)

data class HouseholdUiState(
    val name: String = "",
    val members: List<String> = emptyList(),
    /** Someone else lives here too, so leaving is possible. */
    val isShared: Boolean = false,
    val invite: InviteUi? = null,
)

/** Callbacks of [JoinFlow]. */
class JoinActions(
    val onCodeChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val onJoinedShown: () -> Unit,
) {
    companion object {
        fun of(flow: JoinFlow) = JoinActions(flow::onCodeChange, flow::onSubmit, flow::onConfirm, flow::onDismiss, flow::onJoinedShown)
    }
}

class HouseholdActions(
    val onCreateInvite: () -> Unit,
    val onLeave: () -> Unit,
    val join: JoinActions,
)

/** Members, the invite for a housemate, joining another household and leaving this one. */
@Composable
fun HouseholdSection(state: HouseholdUiState, join: JoinUiState, actions: HouseholdActions) {
    var confirmLeave by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.profile_household_members, state.members.joinToString()), style = MaterialTheme.typography.bodyMedium)
        InviteBlock(state.invite, actions.onCreateInvite)
        Text(stringResource(R.string.household_join_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.household_join_hint_profile),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        JoinCodeField(join, actions.join)
        if (state.isShared) {
            TextButton(onClick = { confirmLeave = true }) {
                Text(stringResource(R.string.household_leave), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    JoinDialogs(join, alone = !state.isShared, actions.join)
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.household_leave_title)) },
            text = { Text(stringResource(R.string.household_leave_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        actions.onLeave()
                    },
                ) { Text(stringResource(R.string.household_leave_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun InviteBlock(invite: InviteUi?, onCreate: () -> Unit) {
    val context = LocalContext.current
    if (invite == null) {
        Text(
            stringResource(R.string.household_invite_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCreate) { Text(stringResource(R.string.household_invite)) }
        return
    }
    val expires = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(currentLocale())
        .format(invite.expiresAt.atZone(ZoneId.systemDefault()))
    val message = stringResource(R.string.household_invite_share_text, invite.code, expires)
    Text(stringResource(R.string.household_invite_code_label), style = MaterialTheme.typography.labelLarge)
    Text(invite.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
    Text(stringResource(R.string.household_invite_valid_until, expires), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { share(context, message) }) { Text(stringResource(R.string.household_invite_share)) }
        OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.household_invite_new)) }
    }
}

/** The code field with its button and the problem, if any. */
@Composable
fun JoinCodeField(state: JoinUiState, actions: JoinActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.code,
                onValueChange = actions.onCodeChange,
                label = { Text(stringResource(R.string.household_join_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { actions.onSubmit() }),
                modifier = Modifier.weight(1f),
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else {
                Button(onClick = actions.onSubmit, enabled = state.canSubmit) { Text(stringResource(R.string.household_join)) }
            }
        }
        state.problem?.let { problem ->
            Text(problemText(problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Confirmation before joining and the welcome after it. */
@Composable
fun JoinDialogs(state: JoinUiState, alone: Boolean, actions: JoinActions) {
    val pending: Invite? = state.pending
    if (pending != null) {
        AlertDialog(
            onDismissRequest = actions.onDismiss,
            title = { Text(stringResource(R.string.household_join_confirm_title, pending.householdName)) },
            text = {
                Text(stringResource(if (alone) R.string.household_join_confirm_alone else R.string.household_join_confirm_shared))
            },
            confirmButton = { TextButton(onClick = actions.onConfirm) { Text(stringResource(R.string.household_join)) } },
            dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val joined = state.joined
    if (joined != null) {
        AlertDialog(
            onDismissRequest = actions.onJoinedShown,
            title = { Text(stringResource(R.string.household_joined_title, joined.name)) },
            text = {
                Text(
                    if (joined.movedItems > 0) {
                        pluralStringResource(R.plurals.household_joined_moved, joined.movedItems, joined.movedItems)
                    } else {
                        stringResource(R.string.household_joined_body)
                    },
                )
            },
            confirmButton = { TextButton(onClick = actions.onJoinedShown) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

@Composable
private fun problemText(problem: JoinProblem): String = stringResource(
    when (problem) {
        JoinProblem.NOT_FOUND -> R.string.household_join_not_found
        JoinProblem.OFFLINE -> R.string.household_join_offline
        JoinProblem.ALREADY_MEMBER -> R.string.household_join_already_member
        JoinProblem.EXPIRED -> R.string.household_join_expired
        JoinProblem.FAILED -> R.string.household_join_failed
    },
)

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
