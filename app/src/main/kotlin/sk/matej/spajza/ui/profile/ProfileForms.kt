package sk.matej.spajza.ui.profile

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.Sex
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.quantityInput
import sk.matej.spajza.ui.common.toOption
import sk.matej.spajza.ui.common.parseNumber

/** The body profile as typed; numbers stay text until saved so editing feels natural. */
data class BodyDraft(
    val sex: Sex? = null,
    val birthYear: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val activityLevel: ActivityLevel? = null,
    val goal: Goal = Goal.MAINTAIN,
    val kcalOverride: String = "",
) {
    /** Out-of-range values count as not filled in. */
    fun toProfile() = BodyProfile(
        sex = sex,
        birthYear = birthYear.trim().toIntOrNull()?.takeIf { it in MIN_BIRTH_YEAR..MAX_BIRTH_YEAR },
        heightCm = parseNumber(heightCm)?.takeIf { it in 80.0..250.0 },
        weightKg = parseNumber(weightKg)?.takeIf { it in 25.0..350.0 },
        activityLevel = activityLevel,
        goal = goal,
        kcalOverride = kcalOverride.trim().toIntOrNull()?.takeIf { it in 800..8000 },
    )

    companion object {
        const val MIN_BIRTH_YEAR = 1920
        const val MAX_BIRTH_YEAR = 2015

        fun of(body: BodyProfile) = BodyDraft(
            sex = body.sex,
            birthYear = body.birthYear?.toString().orEmpty(),
            heightCm = quantityInput(body.heightCm),
            weightKg = quantityInput(body.weightKg),
            activityLevel = body.activityLevel,
            goal = body.goal,
            kcalOverride = body.kcalOverride?.toString().orEmpty(),
        )
    }
}

/** Liked and disliked ingredients as options for the pickers. */
fun Preferences.likedOptions(catalog: Catalog): List<IngredientOption> =
    likedIngredients.mapNotNull { catalog.ingredientsById[it]?.toOption() }

fun Preferences.dislikedOptions(catalog: Catalog): List<IngredientOption> =
    dislikedIngredients.mapNotNull { catalog.ingredientsById[it]?.toOption() }

fun stapleOptions(catalog: Catalog, staples: Set<String>): List<IngredientOption> =
    staples.mapNotNull { catalog.ingredientsById[it]?.toOption() }
