package sk.matej.spajza.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.CookingSkill
import sk.matej.spajza.domain.profile.DailyTargets
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.Sex
import sk.matej.spajza.domain.profile.SpicyTolerance
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.budgetLabelRes
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.durationText
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.components.ChipGroup
import sk.matej.spajza.ui.components.FormSection
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.Stepper
import sk.matej.spajza.ui.components.SuggestionRow
import kotlin.math.roundToInt

/** Searches catalog ingredients for a picker, leaving out the ids already chosen. */
typealias IngredientLookup = (query: String, exclude: Set<String>) -> List<IngredientOption>

@Composable
fun BodyEditor(draft: BodyDraft, targets: DailyTargets?, onChange: (BodyDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormSection(stringResource(R.string.body_sex)) {
            ChipGroup(Sex.entries, setOfNotNull(draft.sex), { stringResource(it.labelRes()) }, { onChange(draft.copy(sex = it)) })
        }
        NumberField(draft.birthYear, { onChange(draft.copy(birthYear = it.take(4))) }, stringResource(R.string.body_birth_year), decimals = false)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(draft.heightCm, { onChange(draft.copy(heightCm = it.take(5))) }, stringResource(R.string.body_height), Modifier.weight(1f))
            NumberField(draft.weightKg, { onChange(draft.copy(weightKg = it.take(5))) }, stringResource(R.string.body_weight), Modifier.weight(1f))
        }
        FormSection(stringResource(R.string.body_activity)) {
            ChipGroup(ActivityLevel.entries, setOfNotNull(draft.activityLevel), { stringResource(it.labelRes()) }, { onChange(draft.copy(activityLevel = it)) })
        }
        FormSection(stringResource(R.string.body_goal)) {
            ChipGroup(Goal.entries, setOf(draft.goal), { stringResource(it.labelRes()) }, { onChange(draft.copy(goal = it)) })
        }
        TargetsCard(targets)
        NumberField(draft.kcalOverride, { onChange(draft.copy(kcalOverride = it.take(5))) }, stringResource(R.string.targets_override_label), decimals = false)
    }
}

@Composable
private fun TargetsCard(targets: DailyTargets?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.targets_title), style = MaterialTheme.typography.labelLarge)
            if (targets == null) {
                Text(stringResource(R.string.targets_incomplete), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.kcal_value, targets.kcal), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.targets_macros, targets.proteinG, targets.fatG, targets.carbsG), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (targets.isOverridden) R.string.targets_overridden else R.string.targets_computed),
                    style = MaterialTheme.typography.labelMedium,
                )
                val bmr = targets.bmr?.roundToInt()
                if (bmr != null && targets.overrideBelowBmr) {
                    Text(stringResource(R.string.targets_override_below_bmr, bmr), style = MaterialTheme.typography.bodySmall)
                } else if (bmr != null && targets.isFlooredAtBmr) {
                    Text(stringResource(R.string.targets_floored, bmr), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Taste and cooking preferences; every change is reported at once through [onChange]. */
@Composable
fun TasteEditor(
    preferences: Preferences,
    cuisines: List<Cuisine>,
    liked: List<IngredientOption>,
    disliked: List<IngredientOption>,
    lookup: IngredientLookup,
    onChange: (Preferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormSection(stringResource(R.string.taste_liked_cuisines)) {
            ChipGroup(cuisines, preferences.likedCuisines, { stringResource(it.labelRes()) }, { cuisine ->
                val chosen = preferences.likedCuisines
                onChange(preferences.copy(likedCuisines = if (cuisine in chosen) chosen - cuisine else chosen + cuisine))
            })
        }
        IngredientPicker(
            title = stringResource(R.string.taste_liked_ingredients),
            selected = liked,
            lookup = lookup,
            exclude = preferences.likedIngredients + preferences.dislikedIngredients,
            onAdd = { onChange(preferences.copy(likedIngredients = preferences.likedIngredients + it.ingredientId)) },
            onRemove = { onChange(preferences.copy(likedIngredients = preferences.likedIngredients - it)) },
        )
        IngredientPicker(
            title = stringResource(R.string.taste_disliked_ingredients),
            selected = disliked,
            lookup = lookup,
            exclude = preferences.likedIngredients + preferences.dislikedIngredients,
            onAdd = { onChange(preferences.copy(dislikedIngredients = preferences.dislikedIngredients + it.ingredientId)) },
            onRemove = { onChange(preferences.copy(dislikedIngredients = preferences.dislikedIngredients - it)) },
        )
        FormSection(stringResource(R.string.taste_spicy)) {
            ChipGroup(SpicyTolerance.entries, setOf(preferences.spicyTolerance), { stringResource(it.labelRes()) }, {
                onChange(preferences.copy(spicyTolerance = it))
            })
        }
        FormSection(stringResource(R.string.taste_skill)) {
            ChipGroup(CookingSkill.entries, setOf(preferences.cookingSkill), { stringResource(it.labelRes()) }, {
                onChange(preferences.copy(cookingSkill = it))
            })
        }
        MinutesSlider(stringResource(R.string.taste_time_weekday), preferences.maxTimeWeekdayMin) {
            onChange(preferences.copy(maxTimeWeekdayMin = it))
        }
        MinutesSlider(stringResource(R.string.taste_time_weekend), preferences.maxTimeWeekendMin) {
            onChange(preferences.copy(maxTimeWeekendMin = it))
        }
        FormSection(stringResource(R.string.taste_budget)) {
            ChipGroup(PriceTier.entries, setOf(preferences.budget), { stringResource(it.budgetLabelRes()) }, {
                onChange(preferences.copy(budget = it))
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.taste_servings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Stepper(
                value = preferences.defaultServings,
                onValueChange = { onChange(preferences.copy(defaultServings = it)) },
                range = 1..8,
                decreaseLabel = stringResource(R.string.detail_servings_decrease),
                increaseLabel = stringResource(R.string.detail_servings_increase),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.taste_meal_prep), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = preferences.mealPrep, onCheckedChange = { onChange(preferences.copy(mealPrep = it)) })
        }
    }
}

/** 10–120 minutes in steps of 5; reports the value when the drag ends. */
@Composable
private fun MinutesSlider(title: String, minutes: Int, onChange: (Int) -> Unit) {
    var value by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    FormSection("$title: ${durationText(value.roundToInt())}") {
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 10f..120f,
            steps = 21,
            onValueChangeFinished = { onChange(value.roundToInt()) },
        )
    }
}

@Composable
fun EquipmentEditor(selected: Set<Equipment>, onToggle: (Equipment) -> Unit, modifier: Modifier = Modifier) {
    ChipGroup(Equipment.entries, selected, { stringResource(it.labelRes()) }, onToggle, modifier)
}

@Composable
fun StaplesEditor(
    staples: List<IngredientOption>,
    lookup: IngredientLookup,
    onAdd: (IngredientOption) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    IngredientPicker(
        title = null,
        selected = staples,
        lookup = lookup,
        exclude = staples.map { it.ingredientId }.toSet(),
        onAdd = onAdd,
        onRemove = onRemove,
        placeholder = stringResource(R.string.staples_add_hint),
        modifier = modifier,
    )
}

/** Chosen ingredients as removable chips, with a search field to add more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientPicker(
    title: String?,
    selected: List<IngredientOption>,
    lookup: IngredientLookup,
    exclude: Set<String>,
    onAdd: (IngredientOption) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.taste_ingredient_search),
) {
    val language = currentLanguage()
    var query by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(query, exclude) { if (query.isBlank()) emptyList() else lookup(query, exclude).take(5) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.sortedBy { it.name[language] }.forEach { option ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(option.ingredientId) },
                        label = { Text(option.name[language]) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
        SearchField(query, { query = it }, placeholder)
        suggestions.forEach { option ->
            SuggestionRow(option.name[language], stringResource(option.category.labelRes()), onAdd = {
                onAdd(option)
                query = ""
            })
        }
    }
}
