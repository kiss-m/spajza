package sk.matej.spajza.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.profile.BodyDraft
import sk.matej.spajza.ui.profile.BodyEditor
import sk.matej.spajza.ui.profile.EquipmentEditor
import sk.matej.spajza.ui.profile.IngredientLookup
import sk.matej.spajza.ui.profile.StaplesEditor
import sk.matej.spajza.ui.profile.TasteEditor

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        actions = OnboardingActions(
            lookup = viewModel::searchIngredients,
            onBodyChange = viewModel::onBodyChange,
            onPreferencesChange = viewModel::onPreferencesChange,
            onToggleEquipment = viewModel::onToggleEquipment,
            onAddStaple = viewModel::onAddStaple,
            onRemoveStaple = viewModel::onRemoveStaple,
            onNext = viewModel::onNext,
            onSkip = viewModel::onSkip,
            onBack = viewModel::onBack,
        ),
    )
}

class OnboardingActions(
    val lookup: IngredientLookup,
    val onBodyChange: (BodyDraft) -> Unit,
    val onPreferencesChange: (Preferences) -> Unit,
    val onToggleEquipment: (Equipment) -> Unit,
    val onAddStaple: (IngredientOption) -> Unit,
    val onRemoveStaple: (String) -> Unit,
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val steps = OnboardingStep.entries
    val isLast = state.step == steps.last()
    BackHandler(enabled = state.step != steps.first()) { actions.onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_step, state.step.ordinal + 1, steps.size)) },
                navigationIcon = {
                    if (state.step != steps.first()) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = actions.onSkip) { Text(stringResource(R.string.action_skip)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = actions.onNext) {
                        Text(stringResource(if (isLast) R.string.onboarding_finish else R.string.action_next))
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LinearProgressIndicator(progress = { (state.step.ordinal + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
                if (state.step == OnboardingStep.BODY) {
                    val name = state.displayName
                    Text(
                        if (name != null) stringResource(R.string.onboarding_welcome_named, name) else stringResource(R.string.onboarding_welcome),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
                }
                val (title, hint) = when (state.step) {
                    OnboardingStep.BODY -> R.string.onboarding_body_title to R.string.onboarding_body_hint
                    OnboardingStep.TASTE -> R.string.onboarding_taste_title to R.string.onboarding_taste_hint
                    OnboardingStep.EQUIPMENT -> R.string.onboarding_equipment_title to R.string.onboarding_equipment_hint
                    OnboardingStep.STAPLES -> R.string.onboarding_staples_title to R.string.onboarding_staples_hint
                }
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (state.step) {
                    OnboardingStep.BODY -> BodyEditor(state.body, state.targets, actions.onBodyChange)
                    OnboardingStep.TASTE -> TasteEditor(
                        preferences = state.preferences,
                        cuisines = state.cuisines,
                        liked = state.liked,
                        disliked = state.disliked,
                        lookup = actions.lookup,
                        onChange = actions.onPreferencesChange,
                    )
                    OnboardingStep.EQUIPMENT -> EquipmentEditor(state.equipment, actions.onToggleEquipment)
                    OnboardingStep.STAPLES -> StaplesEditor(state.staples, actions.lookup, actions.onAddStaple, actions.onRemoveStaple)
                }
            }
        }
    }
}
