package sk.matej.spajza.data.firestore

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.CookingSkill
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.Sex
import sk.matej.spajza.domain.profile.SpicyTolerance
import sk.matej.spajza.domain.rating.Verdict
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreMappersTest {
    @Test
    fun `user profile survives writing and reading, numbers come back as Long`() {
        val body = BodyProfile(Sex.FEMALE, 1995, 168.5, 61.0, ActivityLevel.MODERATE, Goal.LOSE, kcalOverride = 1900)
        val preferences = Preferences(
            likedIngredients = setOf("tomato", "basil"),
            dislikedIngredients = setOf("liver"),
            likedCuisines = setOf(Cuisine.ITALIAN, Cuisine.THAI),
            maxTimeWeekdayMin = 25,
            maxTimeWeekendMin = 90,
            cookingSkill = CookingSkill.ADVANCED,
            spicyTolerance = SpicyTolerance.HOT,
            budget = PriceTier.CHEAP,
            defaultServings = 2,
            mealPrep = false,
        )
        val stored = mapOf(
            "displayName" to "Alex",
            "householdId" to "h1",
            "onboardingDone" to true,
            "profile" to asFirestore(FirestoreMappers.bodyTo(body)),
            "preferences" to asFirestore(FirestoreMappers.preferencesTo(preferences)),
        )

        val user = FirestoreMappers.userFrom("uid-1", stored)

        assertEquals(body, user.body)
        assertEquals(preferences, user.preferences)
        assertEquals("h1", user.householdId)
        assertEquals(true, user.onboardingDone)
    }

    @Test
    fun `missing fields take defaults and unknown enum names are skipped`() {
        val user = FirestoreMappers.userFrom(
            "uid-1",
            mapOf("preferences" to mapOf("likedCuisines" to listOf("ITALIAN", "MARTIAN"), "budget" to "LUXURY")),
        )
        assertEquals(setOf(Cuisine.ITALIAN), user.preferences.likedCuisines)
        assertEquals(Preferences().budget, user.preferences.budget)
        assertEquals(BodyProfile(), user.body)
        assertEquals(false, user.onboardingDone)
    }

    @Test
    fun `new household reads back with its owner, staples and equipment`() {
        val data = FirestoreMappers.newHousehold("Doma", "uid-1", "Alex", setOf("salt", "oil"), setOf(Equipment.STOVE, Equipment.MICROWAVE))
        val household = FirestoreMappers.householdFrom("h1", data)
        assertEquals(listOf("uid-1"), household.memberUids)
        assertEquals(mapOf("uid-1" to "Alex"), household.memberNames)
        assertEquals(setOf("oil", "salt"), household.staples)
        assertEquals(setOf(Equipment.STOVE, Equipment.MICROWAVE), household.equipment)
        assertEquals(Household.DEFAULT_EQUIPMENT, FirestoreMappers.householdFrom("h2", mapOf("name" to "x")).equipment)
    }

    @Test
    fun `pantry items round trip and keep the earliest expiry`() {
        val item = PantryItem(IngredientRef.Catalog("milk"), quantity = 1000.0, expiresAt = LocalDate.of(2026, 11, 2), addedBy = "uid-1")
        assertEquals(item, FirestoreMappers.pantryItemFrom(asFirestore(FirestoreMappers.pantryItemTo(item))))
        assertNull(FirestoreMappers.pantryItemFrom(mapOf("quantity" to 3L)), "no ingredient reference")

        val stored = mapOf<String, Any?>("expiresAt" to "2026-11-02")
        assertEquals(LocalDate.of(2026, 10, 30), FirestoreMappers.earlierExpiry(stored, LocalDate.of(2026, 10, 30)))
        assertNull(FirestoreMappers.earlierExpiry(stored, LocalDate.of(2026, 11, 5)))
        assertNull(FirestoreMappers.earlierExpiry(stored, null))
        assertEquals(LocalDate.of(2026, 11, 5), FirestoreMappers.earlierExpiry(null, LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun `shopping items need an ingredient or text`() {
        val item = FirestoreMappers.shoppingItemFrom(
            "i_milk",
            mapOf("ingredientId" to "milk", "amount" to 2L, "checked" to true, "reasonRecipeIds" to listOf("pancakes")),
        )
        assertEquals(IngredientRef.Catalog("milk"), item?.ingredient)
        assertEquals(2.0, item?.amount)
        assertEquals(setOf("pancakes"), item?.reasonRecipeIds)
        assertEquals("foil", FirestoreMappers.shoppingItemFrom("x", mapOf("text" to "foil"))?.text)
        assertNull(FirestoreMappers.shoppingItemFrom("y", mapOf("text" to " ")))
    }

    @Test
    fun `ratings read verdict and favorite`() {
        val rating = FirestoreMappers.ratingFrom("dal", mapOf("verdict" to "LIKE", "favorite" to true))
        assertEquals(Verdict.LIKE, rating.verdict)
        assertEquals(true, rating.favorite)
        assertNull(FirestoreMappers.ratingFrom("dal", mapOf("verdict" to null)).verdict)
    }

    /** Firestore returns whole numbers as Long and drops nothing else we use. */
    private fun asFirestore(data: Map<String, Any?>): Map<String, Any?> = data.mapValues { (_, value) ->
        when (value) {
            is Int -> value.toLong()
            else -> value
        }
    }
}
