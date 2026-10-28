package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeHouseholdRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.fakes.FakeUserRepository
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.household.InviteCodes
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.session.Session
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HouseholdMembershipTest {
    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val theirs = Household("theirs", "Byt na Obchodnej", listOf("sam"), mapOf("sam" to "Sam"), staples = setOf("salt"))
    private val mine = Household("mine", "Doma", listOf("me"), mapOf("me" to "Alex"), staples = setOf("oil"), inviteCode = "OLDOLD")
    private val invite = Invite("ABC234", "theirs", "Byt na Obchodnej", now.plusSeconds(3_600), createdBy = "sam")

    private val users = FakeUserRepository(listOf(UserProfile(uid = "me", displayName = "Alex", householdId = "mine")))
    private val households = FakeHouseholdRepository(listOf(theirs, mine)).apply {
        invites.value = mapOf(invite.code to invite)
    }
    private val pantry = FakePantryRepository(
        listOf(
            PantryItem(IngredientRef.Catalog("pasta"), quantity = 500.0, addedBy = "me"),
            PantryItem(IngredientRef.Catalog("tomato"), quantity = 2.0, expiresAt = LocalDate.of(2026, 11, 2), addedBy = "me"),
        ),
        householdId = "mine",
    ).apply {
        households.value = households.value + ("theirs" to mapOf("i_pasta" to PantryItem(IngredientRef.Catalog("pasta"), 250.0, addedBy = "sam")))
    }
    private val shopping = FakeShoppingRepository(
        listOf(
            ShoppingItem("i_milk", IngredientRef.Catalog("milk"), amount = 1000.0, addedBy = "me"),
            ShoppingItem("t1", ingredient = null, text = "baterky", addedBy = "me"),
            ShoppingItem("i_eggs", IngredientRef.Catalog("eggs"), checked = true, addedBy = "me"),
        ),
        householdId = "mine",
    )
    private val join = JoinHouseholdUseCase(users, households, pantry, shopping, clock)

    private fun session(household: Household = mine) =
        Session(AuthUser("me", "Alex", null), UserProfile(uid = "me", displayName = "Alex", householdId = household.id), household)

    @Test
    fun `invite codes avoid look-alikes and normalize what people type`() {
        val random = Random(42)
        repeat(200) {
            val code = InviteCodes.generate(random)
            assertTrue(InviteCodes.isComplete(code), code)
            assertTrue(code.none { it in "0O1IL" }, code)
        }
        assertEquals("ABC234", InviteCodes.normalize(" abc-234 "))
        assertEquals("ABC", InviteCodes.normalize("abc"))
        assertEquals(false, InviteCodes.isComplete("ABC"))
    }

    @Test
    fun `a new invite replaces the previous code and expires in 48 hours`() = runTest {
        val created = CreateInviteUseCase(households, clock)(session())

        assertEquals(now.plus(InviteCodes.VALIDITY), created.expiresAt)
        assertEquals("mine", created.householdId)
        assertEquals(setOf(invite.code, created.code), households.invites.value.keys, "the old code OLDOLD is gone")
        assertEquals(created.code, households.households.value.getValue("mine").inviteCode)
    }

    @Test
    fun `finding a code needs the server and hides expired invites`() = runTest {
        assertEquals(invite, join.find("ABC234").getOrThrow())
        assertNull(join.find("ZZZZZZ").getOrThrow())

        households.invites.value = mapOf(invite.code to invite.copy(expiresAt = now.minusSeconds(1)))
        assertNull(join.find("ABC234").getOrThrow())

        households.offline = true
        assertTrue(join.find("ABC234").isFailure)
    }

    @Test
    fun `a lone member brings pantry and open shopping items and the old household is deleted`() = runTest {
        val outcome = join(session(), invite)

        assertEquals(JoinOutcome.Joined("theirs", movedItems = 4), outcome)
        assertEquals("theirs", users.users.value.getValue("me").householdId)
        val joined = households.households.value.getValue("theirs")
        assertEquals(listOf("sam", "me"), joined.memberUids)
        assertEquals(setOf("salt"), joined.staples, "the joined kitchen keeps its settings")

        val merged = pantry.itemsOf("theirs")
        assertEquals(750.0, merged.getValue("i_pasta").quantity, "quantities add up")
        assertEquals(LocalDate.of(2026, 11, 2), merged.getValue("i_tomato").expiresAt)
        val list = shopping.itemsOf("theirs").values
        assertEquals(setOf("milk"), list.mapNotNull { (it.ingredient as? IngredientRef.Catalog)?.id }.toSet(), "checked eggs stay behind")
        assertEquals(listOf("baterky"), list.mapNotNull { it.text })

        assertEquals(listOf("mine"), households.deleted)
        assertTrue("OLDOLD" !in households.invites.value)
    }

    @Test
    fun `a member of a shared household leaves its data behind`() = runTest {
        val shared = mine.copy(memberUids = listOf("me", "kim"))
        households.households.value = households.households.value + ("mine" to shared)

        val outcome = join(session(shared), invite)

        assertEquals(JoinOutcome.Joined("theirs", movedItems = 0), outcome)
        assertEquals(listOf("kim"), households.households.value.getValue("mine").memberUids)
        assertEquals(2, pantry.itemsOf("mine").size)
        assertEquals(setOf("i_pasta"), pantry.itemsOf("theirs").keys)
        assertTrue(households.deleted.isEmpty())
    }

    @Test
    fun `nothing changes when the invite is refused, expired or for the own household`() = runTest {
        households.offline = true
        assertEquals(JoinOutcome.Failed, join(session(), invite))
        households.offline = false
        assertEquals(JoinOutcome.Expired, join(session(), invite.copy(expiresAt = now)))
        assertEquals(JoinOutcome.AlreadyMember, join(session(), invite.copy(householdId = "mine")))

        assertEquals("mine", users.users.value.getValue("me").householdId)
        assertEquals(listOf("sam"), households.households.value.getValue("theirs").memberUids)
        assertEquals(2, pantry.itemsOf("mine").size)
    }

    @Test
    fun `leaving a shared household starts an empty one and the last member cannot leave`() = runTest {
        val leave = LeaveHouseholdUseCase(users, households)
        assertNull(leave(session(), MatcherFixture.catalog(), "Doma"))

        val shared = mine.copy(memberUids = listOf("me", "kim"))
        households.households.value = households.households.value + ("mine" to shared)
        val newId = leave(session(shared), MatcherFixture.catalog(), "Doma")!!

        assertEquals(newId, users.users.value.getValue("me").householdId)
        assertEquals(listOf("me"), households.households.value.getValue(newId).memberUids)
        assertEquals(setOf("oil", "salt"), households.households.value.getValue(newId).staples)
        assertEquals(listOf("kim"), households.households.value.getValue("mine").memberUids)
        assertEquals(2, pantry.itemsOf("mine").size, "the pantry stays with the others")
    }
}
