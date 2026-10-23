package sk.matej.spajza.domain.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeAuthRepository
import sk.matej.spajza.domain.fakes.FakeCatalogRepository
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val alex = AuthUser("uid-1", "Alex", "alex@example.com")
    private val auth = FakeAuthRepository()
    private val users = FakeUserRepository()
    private val households = FakeHouseholdRepository()
    private val catalog = FakeCatalogRepository(MatcherFixture.catalog())

    private fun TestScope.manager() = SessionManager(
        auth = auth,
        users = users,
        households = households,
        catalog = catalog,
        createHousehold = CreateHouseholdUseCase(users, households),
        defaultHouseholdName = { "Naša domácnosť" },
        scope = backgroundScope,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `signed out without a user`() = runTest {
        assertEquals(SessionState.SignedOut, manager().state.first { it != SessionState.Loading })
    }

    @Test
    fun `first sign-in creates one household and starts onboarding`() = runTest {
        val manager = manager()
        auth.user.value = alex

        val onboarding = assertIs<SessionState.Onboarding>(manager.state.first { it is SessionState.Onboarding })
        assertEquals("Naša domácnosť", onboarding.session.household.name)
        assertEquals(setOf("oil", "salt"), onboarding.session.household.staples)
        assertEquals(1, households.households.value.size)

        // Signing out and in again must not create a second household.
        auth.user.value = null
        manager.state.first { it == SessionState.SignedOut }
        auth.user.value = alex
        manager.state.first { it is SessionState.Onboarding }
        assertEquals(1, households.households.value.size)
    }

    @Test
    fun `finished onboarding means ready`() = runTest {
        val manager = manager()
        auth.user.value = alex
        manager.state.first { it is SessionState.Onboarding }

        users.setOnboardingDone(alex.uid)

        val ready = assertIs<SessionState.Ready>(manager.state.first { it is SessionState.Ready })
        assertEquals(alex, ready.session.user)
        assertEquals(ready.session.household.id, ready.session.profile.householdId)
    }
}
