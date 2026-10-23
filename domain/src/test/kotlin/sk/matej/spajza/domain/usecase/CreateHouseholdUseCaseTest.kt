package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.matching.MatcherFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CreateHouseholdUseCaseTest {
    @Test
    fun `creates a household with default staples and links the user to it`() = runTest {
        val users = FakeUserRepository()
        val households = FakeHouseholdRepository()

        val id = CreateHouseholdUseCase(users, households)(
            uid = "me",
            displayName = "Alex",
            householdName = "Doma",
            catalog = MatcherFixture.catalog(),
        )

        val household = households.observe(id).first()!!
        assertEquals("Doma", household.name)
        assertEquals(listOf("me"), household.memberUids)
        assertEquals(mapOf("me" to "Alex"), household.memberNames)
        assertEquals(setOf("oil", "salt"), household.staples)
        assertEquals(Household.DEFAULT_EQUIPMENT, household.equipment)
        val user = users.observe("me").first()!!
        assertEquals(id, user.householdId)
        assertFalse(user.onboardingDone)
    }
}
