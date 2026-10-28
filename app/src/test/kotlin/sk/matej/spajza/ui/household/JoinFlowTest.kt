package sk.matej.spajza.ui.household

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.fakes.FakeCustomIngredientRepository
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakeOwnRecipeRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.usecase.JoinHouseholdUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinFlowTest {
    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val mine = Household("mine", "Doma", listOf("me"), staples = emptySet())
    private val invite = Invite("ABC234", "theirs", "Byt", now.plusSeconds(600), "sam")
    private val households = FakeHouseholdRepository(listOf(mine, Household("theirs", "Byt", listOf("sam"), staples = emptySet()))).apply {
        invites.value = mapOf(invite.code to invite)
    }
    private val users = FakeUserRepository(listOf(UserProfile(uid = "me", householdId = "mine")))
    private val session = Session(AuthUser("me", "Alex", null), UserProfile(uid = "me", householdId = "mine"), mine)

    private fun TestScope.flow() = JoinFlow(
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        joinHousehold = JoinHouseholdUseCase(
            users, households, FakePantryRepository(householdId = "mine"), FakeShoppingRepository(householdId = "mine"),
            FakeCustomIngredientRepository(householdId = "mine"), FakeOwnRecipeRepository(householdId = "mine"), Clock.fixed(now, ZoneOffset.UTC),
        ),
        session = { session },
    )

    @Test
    fun `a typed code is cleaned up and needs six characters`() = runTest {
        val flow = flow()
        flow.onCodeChange("abc")
        assertEquals("ABC", flow.state.value.code)
        assertFalse(flow.state.value.canSubmit)
        flow.onCodeChange("abc 234")
        assertTrue(flow.state.value.canSubmit)
    }

    @Test
    fun `a known code asks for confirmation and then joins`() = runTest {
        val flow = flow()
        flow.onCodeChange("ABC234")
        flow.onSubmit()
        assertEquals(invite, flow.state.value.pending)

        flow.onConfirm()
        assertEquals(JoinedHousehold("Byt", movedItems = 0), flow.state.value.joined)
        assertEquals("theirs", users.users.value.getValue("me").householdId)
        flow.onJoinedShown()
        assertNull(flow.state.value.joined)
    }

    @Test
    fun `unknown codes and missing connection are explained`() = runTest {
        val flow = flow()
        flow.onCodeChange("ZZZZZZ")
        flow.onSubmit()
        assertEquals(JoinProblem.NOT_FOUND, flow.state.value.problem)
        assertNull(flow.state.value.pending)

        households.offline = true
        flow.onCodeChange("ABC234")
        flow.onSubmit()
        assertEquals(JoinProblem.OFFLINE, flow.state.value.problem)
        assertFalse(flow.state.value.busy)
    }

    @Test
    fun `an invite to the own household is not offered`() = runTest {
        households.invites.value = mapOf("MNE234" to invite.copy(code = "MNE234", householdId = "mine"))
        val flow = flow()
        flow.onCodeChange("MNE234")
        flow.onSubmit()
        assertEquals(JoinProblem.ALREADY_MEMBER, flow.state.value.problem)
    }
}
