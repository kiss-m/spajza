package sk.matej.spajza.domain.profile

/** Used only for the Mifflin–St Jeor constant; UNSPECIFIED uses the midpoint of the two. */
enum class Sex { FEMALE, MALE, UNSPECIFIED }

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}

enum class Goal { LOSE, MAINTAIN, GAIN }

/** Body data for computing energy targets. All fields are optional because onboarding is skippable. */
data class BodyProfile(
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val goal: Goal = Goal.MAINTAIN,
    val kcalOverride: Int? = null,
) {
    val isComplete: Boolean
        get() = sex != null && birthYear != null && heightCm != null && weightKg != null && activityLevel != null
}
