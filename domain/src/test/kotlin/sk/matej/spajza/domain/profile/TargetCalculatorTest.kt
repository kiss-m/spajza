package sk.matej.spajza.domain.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetCalculatorTest {
    private val calculator = TargetCalculator(currentYear = { 2026 })

    // 30-year-old, 180 cm, 80 kg man: BMR = 800 + 1125 - 150 + 5 = 1780.
    private val man = BodyProfile(Sex.MALE, 1996, 180.0, 80.0, ActivityLevel.MODERATE, Goal.MAINTAIN)

    // 25-year-old, 165 cm, 60 kg woman: BMR = 600 + 1031.25 - 125 - 161 = 1345.25.
    private val woman = BodyProfile(Sex.FEMALE, 2001, 165.0, 60.0, ActivityLevel.SEDENTARY, Goal.MAINTAIN)

    @Test
    fun `bmr follows Mifflin-St Jeor`() {
        assertEquals(1780.0, calculator.bmr(man)!!, 1e-9)
        assertEquals(1345.25, calculator.bmr(woman)!!, 1e-9)
        assertEquals(1780.0 - 83.0, calculator.bmr(man.copy(sex = Sex.UNSPECIFIED))!!, 1e-9)
    }

    @Test
    fun `tdee multiplies bmr by the activity factor`() {
        val targets = calculator.targets(man)!!
        assertEquals(1780.0 * 1.55, targets.tdee!!, 1e-9)
        assertEquals(2759, targets.kcal)
    }

    @Test
    fun `goal adjusts the target`() {
        assertEquals(2207, calculator.targets(man.copy(goal = Goal.LOSE))!!.kcal) // 2759 × 0.8
        assertEquals(3035, calculator.targets(man.copy(goal = Goal.GAIN))!!.kcal) // 2759 × 1.1
    }

    @Test
    fun `computed target never goes below bmr`() {
        // Sedentary woman losing weight: 1345.25 × 1.2 × 0.8 = 1291.4, below BMR.
        val targets = calculator.targets(woman.copy(goal = Goal.LOSE))!!
        assertEquals(1345, targets.kcal)
        assertTrue(targets.isFlooredAtBmr)
    }

    @Test
    fun `override wins and flags a value below bmr`() {
        val low = calculator.targets(man.copy(kcalOverride = 1500))!!
        assertEquals(1500, low.kcal)
        assertTrue(low.isOverridden)
        assertTrue(low.overrideBelowBmr)
        val fine = calculator.targets(man.copy(kcalOverride = 2500))!!
        assertFalse(fine.overrideBelowBmr)
    }

    @Test
    fun `macros use fixed defaults`() {
        val targets = calculator.targets(man)!!
        assertEquals(144, targets.proteinG) // 1.8 g × 80 kg
        assertEquals(92, targets.fatG) // 30 % of 2759 kcal / 9
        assertEquals(339, targets.carbsG) // (2759 - 576 - 827.7) / 4
    }

    @Test
    fun `carbs never go negative`() {
        val heavy = man.copy(weightKg = 200.0, kcalOverride = 1200)
        assertEquals(0, calculator.targets(heavy)!!.carbsG)
    }

    @Test
    fun `incomplete profile has no computed targets but an override still works`() {
        assertNull(calculator.targets(BodyProfile()))
        val overridden = calculator.targets(BodyProfile(kcalOverride = 2000, weightKg = 70.0))!!
        assertEquals(2000, overridden.kcal)
        assertNull(overridden.bmr)
        assertEquals(126, overridden.proteinG)
    }
}
