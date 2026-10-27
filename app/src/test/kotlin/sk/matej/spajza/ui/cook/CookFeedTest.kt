package sk.matej.spajza.ui.cook

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.browse.FeedFilter
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.matching.MatcherFixture.MONDAY
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.session.Session
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookFeedTest {
    private val catalog = MatcherFixture.catalog(
        MatcherFixture.tomatoPasta,
        MatcherFixture.garlicPasta,
        MatcherFixture.chickenRice,
        MatcherFixture.salmonRice,
    )
    private val session = Session(
        user = AuthUser("uid-1", "Alex", null),
        profile = UserProfile(uid = "uid-1", householdId = "h1", onboardingDone = true),
        household = Household(id = "h1", name = "Doma", memberUids = listOf("uid-1"), staples = setOf("oil", "salt")),
    )
    private val pantry = MatcherFixture.pantry("pasta" to null, "tomato" to null, "garlic" to null, "rice" to null)
    private val feed = CookFeed(catalog)

    @Test
    fun `ready and to-buy sections with a purchase suggestion`() {
        val state = feed.state(session, pantry, emptyMap(), emptyList(), query = "", filter = FeedFilter(), today = MONDAY)

        assertEquals(setOf("tomato-pasta", "garlic-pasta"), state.ready.map { it.recipeId }.toSet())
        assertEquals(setOf("chicken-rice", "salmon-rice"), state.toBuy.map { it.recipeId }.toSet())
        assertEquals(listOf("salmon"), state.toBuy.first { it.recipeId == "salmon-rice" }.missing.map { it.ingredientId })
        assertNotNull(state.purchase)
        assertNull(state.searchResults)
        assertTrue(state.ready.all { it.kcal > 0 })
    }

    @Test
    fun `filters apply to both sections`() {
        val state = feed.state(session, pantry, emptyMap(), emptyList(), "", FeedFilter(cuisines = setOf(Cuisine.CHINESE)), MONDAY)

        assertTrue(state.ready.isEmpty())
        assertEquals(listOf("chicken-rice"), state.toBuy.map { it.recipeId })
    }

    @Test
    fun `search results respect the chips`() {
        // "rice" matches chicken-rice (Chinese) and salmon-rice (Japanese).
        val all = feed.state(session, pantry, emptyMap(), emptyList(), query = "rice", filter = FeedFilter(), today = MONDAY)
        assertEquals(setOf("chicken-rice", "salmon-rice"), all.searchResults!!.map { it.recipeId }.toSet())

        val chinese = feed.state(session, pantry, emptyMap(), emptyList(), "rice", FeedFilter(cuisines = setOf(Cuisine.CHINESE)), MONDAY)

        assertEquals(listOf("chicken-rice"), chinese.searchResults!!.map { it.recipeId })
    }

    @Test
    fun `search covers recipes that need more than three items and marks favorites`() {
        val ratings = mapOf("salmon-rice" to Rating("salmon-rice", favorite = true))
        val state = feed.state(session, emptyList(), ratings, emptyList(), query = "salmon", filter = FeedFilter(), today = MONDAY)

        val result = state.searchResults!!.single()
        assertEquals("salmon-rice", result.recipeId)
        assertEquals(setOf("salmon", "rice", "garlic"), result.missing.map { it.ingredientId }.toSet())
        assertTrue(result.favorite)
        assertTrue(state.pantryEmpty)
    }
}
