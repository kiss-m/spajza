package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.repository.HouseholdRepository

class FakeHouseholdRepository(initial: List<Household> = emptyList()) : HouseholdRepository {
    val households = MutableStateFlow(initial.associateBy { it.id })
    val invites = MutableStateFlow<Map<String, Invite>>(emptyMap())
    val deleted = mutableListOf<String>()

    /** Makes [findInvite] and [join] fail like they do without a connection. */
    var offline = false
    private var nextId = 1

    override fun observe(householdId: String): Flow<Household?> = households.map { it[householdId] }

    override suspend fun create(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): String {
        val id = "household-${nextId++}"
        val household = Household(
            id = id,
            name = name,
            memberUids = listOf(ownerUid),
            memberNames = ownerName?.let { mapOf(ownerUid to it) }.orEmpty(),
            staples = staples,
            equipment = equipment,
        )
        households.value = households.value + (id to household)
        return id
    }

    override suspend fun setStaple(householdId: String, ingredientId: String, isStaple: Boolean) = update(householdId) {
        it.copy(staples = if (isStaple) it.staples + ingredientId else it.staples - ingredientId)
    }

    override suspend fun setEquipment(householdId: String, equipment: Equipment, available: Boolean) = update(householdId) {
        it.copy(equipment = if (available) it.equipment + equipment else it.equipment - equipment)
    }

    override suspend fun createInvite(invite: Invite, previousCode: String?) {
        invites.value = invites.value - previousCode.orEmpty() + (invite.code to invite)
        update(invite.householdId) { it.copy(inviteCode = invite.code, inviteExpiresAt = invite.expiresAt) }
    }

    override suspend fun findInvite(code: String): Result<Invite?> =
        if (offline) Result.failure(IllegalStateException("offline")) else Result.success(invites.value[code])

    override suspend fun join(invite: Invite, uid: String, displayName: String?): Result<Unit> {
        if (offline || invites.value[invite.code]?.householdId != invite.householdId) {
            return Result.failure(IllegalStateException("rejected"))
        }
        update(invite.householdId) {
            it.copy(memberUids = it.memberUids + uid, memberNames = it.memberNames + (uid to displayName.orEmpty()))
        }
        return Result.success(Unit)
    }

    override suspend fun leave(householdId: String, uid: String) = update(householdId) {
        it.copy(memberUids = it.memberUids - uid, memberNames = it.memberNames - uid)
    }

    override suspend fun delete(householdId: String, inviteCode: String?) {
        households.value = households.value - householdId
        invites.value = invites.value - inviteCode.orEmpty()
        deleted += householdId
    }

    private fun update(id: String, change: (Household) -> Household) {
        val household = households.value[id] ?: return
        households.value = households.value + (id to change(household))
    }
}
