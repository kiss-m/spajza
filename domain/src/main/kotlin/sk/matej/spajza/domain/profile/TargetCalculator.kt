package sk.matej.spajza.domain.profile

import java.time.Year
import kotlin.math.max
import kotlin.math.roundToInt

data class DailyTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    /** Null when the body profile is incomplete and only an override is known. */
    val bmr: Double?,
    /** Null when the body profile is incomplete. */
    val tdee: Double?,
    val isOverridden: Boolean,
    /** The computed (not overridden) target was raised to the BMR. */
    val isFlooredAtBmr: Boolean,
    /** The user's own target is below their BMR; the UI shows a gentle note, never a block. */
    val overrideBelowBmr: Boolean,
)

/**
 * Daily energy and macro targets: Mifflin–St Jeor BMR × activity factor = TDEE, adjusted by goal
 * and never below BMR. A user-entered kcal override always wins. Macros are fixed defaults that
 * the UI does not let the user change.
 */
class TargetCalculator(private val currentYear: () -> Int = { Year.now().value }) {

    fun bmr(profile: BodyProfile): Double? {
        val sex = profile.sex ?: return null
        val birthYear = profile.birthYear ?: return null
        val height = profile.heightCm ?: return null
        val weight = profile.weightKg ?: return null
        val age = currentYear() - birthYear
        val sexConstant = when (sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> (5.0 - 161.0) / 2
        }
        return 10 * weight + 6.25 * height - 5 * age + sexConstant
    }

    fun targets(profile: BodyProfile): DailyTargets? {
        val weight = profile.weightKg ?: return null
        val bmr = bmr(profile)
        val activity = profile.activityLevel
        val tdee = if (bmr != null && activity != null) bmr * activity.factor else null
        val goalKcal = tdee?.let { it * profile.goal.factor }
        val computed = if (bmr != null && goalKcal != null) max(goalKcal, bmr) else null
        val override = profile.kcalOverride
        val kcal = override?.toDouble() ?: computed ?: return null

        val protein = weight * PROTEIN_G_PER_KG
        val fat = kcal * FAT_ENERGY_SHARE / KCAL_PER_G_FAT
        val carbs = max(0.0, (kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) / KCAL_PER_G_CARBS)
        return DailyTargets(
            kcal = kcal.roundToInt(),
            proteinG = protein.roundToInt(),
            fatG = fat.roundToInt(),
            carbsG = carbs.roundToInt(),
            bmr = bmr,
            tdee = tdee,
            isOverridden = override != null,
            isFlooredAtBmr = override == null && bmr != null && goalKcal != null && goalKcal < bmr,
            overrideBelowBmr = override != null && bmr != null && override < bmr,
        )
    }

    private val Goal.factor: Double
        get() = when (this) {
            Goal.LOSE -> 0.8
            Goal.MAINTAIN -> 1.0
            Goal.GAIN -> 1.1
        }

    companion object {
        const val PROTEIN_G_PER_KG = 1.8
        const val FAT_ENERGY_SHARE = 0.30
        const val KCAL_PER_G_PROTEIN = 4.0
        const val KCAL_PER_G_CARBS = 4.0
        const val KCAL_PER_G_FAT = 9.0
    }
}
