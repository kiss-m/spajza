package sk.matej.spajza.domain.profile

import sk.matej.spajza.domain.diet.DietRestrictions
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.PriceTier

enum class CookingSkill(val comfortableDifficulty: Int) {
    BEGINNER(1),
    INTERMEDIATE(2),
    ADVANCED(3),
}

enum class SpicyTolerance { NONE, MILD, HOT }

/** Taste and cooking preferences from users/{uid}. Defaults follow docs/SPEC.md. */
data class Preferences(
    val likedIngredients: Set<String> = emptySet(),
    val dislikedIngredients: Set<String> = emptySet(),
    val likedCuisines: Set<Cuisine> = emptySet(),
    val maxTimeWeekdayMin: Int = 30,
    val maxTimeWeekendMin: Int = 60,
    val cookingSkill: CookingSkill = CookingSkill.INTERMEDIATE,
    val spicyTolerance: SpicyTolerance = SpicyTolerance.MILD,
    val budget: PriceTier = PriceTier.MID,
    val defaultServings: Int = 1,
    val mealPrep: Boolean = true,
)

data class UserProfile(
    val uid: String,
    val displayName: String? = null,
    val body: BodyProfile = BodyProfile(),
    val preferences: Preferences = Preferences(),
    /** Hard filters; their UI is hidden behind a feature flag for now. */
    val restrictions: DietRestrictions = DietRestrictions(),
    val householdId: String? = null,
    /** Set when onboarding is finished or skipped; until then the app resumes it after sign-in. */
    val onboardingDone: Boolean = false,
)
