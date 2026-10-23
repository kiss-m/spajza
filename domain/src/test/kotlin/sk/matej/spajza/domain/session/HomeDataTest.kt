package sk.matej.spajza.domain.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeAuthRepository
import sk.matej.spajza.domain.fakes.FakeCatalogRepository
import sk.matej.spajza.domain.fakes.FakeCookHistoryRepository
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeRatingRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val home = HomeData(sessions, catalog, pantry, FakeShoppingRepository(), FakeRatingRepository(), FakeCookHistoryRepository(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.pantry.collect {} }

        auth.user.value = AuthUser("uid-1", "Alex", null)
        val session = home.session.first { it != null }!!
        pantry.add(session.householdId, IngredientRef.Catalog("pasta"), 500.0, null, "uid-1")

        assertEquals(listOf("i_pasta"), home.pantry.first { !it.isNullOrEmpty() }!!.map { it.id })
        auth.user.value = null
        assertEquals(null, home.pantry.first { it == null })
    }
}
