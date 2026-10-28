package sk.matej.spajza.domain.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeAuthRepository
import sk.matej.spajza.domain.fakes.FakeCatalogRepository
import sk.matej.spajza.domain.fakes.FakeCookHistoryRepository
import sk.matej.spajza.domain.fakes.FakeCustomIngredientRepository
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakeOwnRecipeRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeRatingRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.own.OwnIngredientLine
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDataTest {
    @Test
    fun `pantry follows the signed-in household and clears on sign-out`() = runTest {
        val auth = FakeAuthRepository()
        val users = FakeUserRepository()
        val households = FakeHouseholdRepository()
        val catalog = FakeCatalogRepository(MatcherFixture.catalog())
        val pantry = FakePantryRepository()
        val sessions = SessionManager(
            auth, users, households, catalog, CreateHouseholdUseCase(users, households),
            defaultHouseholdName = { "Doma" }, scope = backgroundScope, dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val home = HomeData(
            sessions, catalog, pantry, FakeShoppingRepository(), FakeRatingRepository(), FakeCookHistoryRepository(), FakeCustomIngredientRepository(),
            FakeOwnRecipeRepository(), backgroundScope,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.pantry.collect {} }

        auth.user.value = AuthUser("uid-1", "Alex", null)
        val session = home.session.first { it != null }!!
        pantry.add(session.householdId, IngredientRef.Catalog("pasta"), 500.0, null, "uid-1")

        assertEquals(listOf("i_pasta"), home.pantry.first { !it.isNullOrEmpty() }!!.map { it.id })
        auth.user.value = null
        assertEquals(null, home.pantry.first { it == null })
    }

    @Test
    fun `the household's own recipes join the catalog`() = runTest {
        val auth = FakeAuthRepository()
        val users = FakeUserRepository()
        val households = FakeHouseholdRepository()
        val bundled = MatcherFixture.catalog(MatcherFixture.tomatoPasta)
        val catalog = FakeCatalogRepository(bundled)
        val recipes = FakeOwnRecipeRepository()
        val sessions = SessionManager(
            auth, users, households, catalog, CreateHouseholdUseCase(users, households),
            defaultHouseholdName = { "Doma" }, scope = backgroundScope, dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val home = HomeData(
            sessions, catalog, FakePantryRepository(), FakeShoppingRepository(), FakeRatingRepository(), FakeCookHistoryRepository(),
            FakeCustomIngredientRepository(), recipes, backgroundScope,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.catalog.collect {} }

        auth.user.value = AuthUser("uid-1", "Alex", null)
        val session = home.session.first { it != null }!!
        assertSame(bundled, home.catalog.first { it != null }, "without own recipes the catalog is the bundled one")

        recipes.save(session.householdId, OwnRecipe("x1", "Babkina rajčinová", ingredients = listOf(OwnIngredientLine("200 g cestovín", "pasta", 200.0, MeasureUnit.G))), "uid-1")
        val merged = home.catalog.first { it?.recipesById?.containsKey("own:x1") == true }!!
        assertEquals(listOf("tomato-pasta", "own:x1"), merged.recipes.map { it.id })
    }
}
