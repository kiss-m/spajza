package sk.matej.spajza.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.pantry.ExpiryReminder
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.components.FormSection
import sk.matej.spajza.ui.household.HouseholdActions
import sk.matej.spajza.ui.household.JoinActions
import sk.matej.spajza.ui.household.HouseholdSection
import sk.matej.spajza.ui.household.JoinUiState
import sk.matej.spajza.ui.signin.GoogleSignInResult
import sk.matej.spajza.ui.signin.requestGoogleIdToken

@Composable
fun ProfileRoute(onOpenAbout: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val join by viewModel.join.state.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(AppLanguage.current()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val newHouseholdName = stringResource(R.string.default_household_name)
    ProfileScreen(
        state = state,
        join = join,
        language = language,
        actions = ProfileActions(
            lookup = viewModel::searchIngredients,
            onBodyChange = viewModel::onBodyChange,
            onSaveBody = viewModel::onSaveBody,
            onPreferencesChange = viewModel::onPreferencesChange,
            onToggleEquipment = viewModel::onToggleEquipment,
            onAddStaple = viewModel::onAddStaple,
            onRemoveStaple = viewModel::onRemoveStaple,
            onLanguageSelected = {
                language = it
                it.apply()
            },
            onOpenAbout = onOpenAbout,
            onSignOut = viewModel::signOut,
            onDeleteConfirmed = {
                // Firebase deletes only recently signed-in accounts, so ask Google once more.
                scope.launch {
                    val result = requestGoogleIdToken(context)
                    if (result is GoogleSignInResult.Token) viewModel.onDeleteAccount(result.idToken)
                }
            },
            onDeletionErrorShown = viewModel::onDeletionErrorShown,
            onExpiryReminderChange = viewModel::onExpiryReminderChange,
            household = HouseholdActions(
                onCreateInvite = viewModel::onCreateInvite,
                onLeave = { viewModel.onLeaveHousehold(newHouseholdName) },
                join = JoinActions.of(viewModel.join),
            ),
        ),
    )
}

class ProfileActions(
    val lookup: IngredientLookup,
    val onBodyChange: (BodyDraft) -> Unit,
    val onSaveBody: () -> Unit,
    val onPreferencesChange: (Preferences) -> Unit,
    val onToggleEquipment: (Equipment) -> Unit,
    val onAddStaple: (IngredientOption) -> Unit,
    val onRemoveStaple: (String) -> Unit,
    val onLanguageSelected: (AppLanguage) -> Unit,
    val onOpenAbout: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeletionErrorShown: () -> Unit,
    val onExpiryReminderChange: (ExpiryReminder) -> Unit,
    val household: HouseholdActions,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileUiState, join: JoinUiState, language: AppLanguage, actions: ProfileActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) }) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.profile_signed_in_as, state.accountLabel), style = MaterialTheme.typography.bodyMedium)
                FormSection(stringResource(R.string.profile_section_body)) {
                    BodyEditor(state.body, state.targets, actions.onBodyChange)
                    if (state.bodyChanged) {
                        Button(onClick = actions.onSaveBody) { Text(stringResource(R.string.action_save)) }
                    }
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_taste)) {
                    TasteEditor(state.preferences, state.cuisines, state.liked, state.disliked, actions.lookup, actions.onPreferencesChange)
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_equipment)) {
                    EquipmentEditor(state.equipment, actions.onToggleEquipment)
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_staples)) {
                    Text(stringResource(R.string.onboarding_staples_hint), style = MaterialTheme.typography.bodySmall)
                    StaplesEditor(state.staples, actions.lookup, actions.onAddStaple, actions.onRemoveStaple)
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_household)) {
                    HouseholdSection(state.household, join, actions.household)
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_notifications)) {
                    ExpiryReminderSettings(state.expiryReminder, actions.onExpiryReminderChange)
                }
                HorizontalDivider()
                FormSection(stringResource(R.string.profile_section_app)) {
                    Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.titleSmall)
                    LanguageSelector(language, actions.onLanguageSelected)
                    TextButton(onClick = actions.onOpenAbout) { Text(stringResource(R.string.profile_about)) }
                    OutlinedButton(onClick = actions.onSignOut) { Text(stringResource(R.string.sign_out)) }
                    when (state.deletion) {
                        DeletionState.DELETING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.profile_deleting))
                        }
                        DeletionState.IDLE, DeletionState.FAILED -> TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.profile_delete_account), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.deletion == DeletionState.FAILED) {
                        Text(stringResource(R.string.profile_delete_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.onDeletionErrorShown()
                        actions.onDeleteConfirmed()
                    },
                ) { Text(stringResource(R.string.profile_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LanguageSelector(language: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(Modifier.selectableGroup()) {
        AppLanguage.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == language, onClick = { onSelected(option) }, role = Role.RadioButton)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == language, onClick = null)
                Text(
                    stringResource(
                        when (option) {
                            AppLanguage.SYSTEM -> R.string.language_system
                            AppLanguage.SK -> R.string.language_sk
                            AppLanguage.EN -> R.string.language_en
                        },
                    ),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
