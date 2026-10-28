package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.model.Equipment

/**
 * Households shared by their members. Staples and equipment change one entry at a time, so two
 * members editing the lists at once both keep their changes.
 */
interface HouseholdRepository {
    /** Emits null when the household does not exist or the user is no longer a member. */
    fun observe(householdId: String): Flow<Household?>

    /** Creates a household with [ownerUid] as the only member and returns its id. */
    suspend fun create(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): String

    suspend fun setStaple(householdId: String, ingredientId: String, isStaple: Boolean)

    suspend fun setEquipment(householdId: String, equipment: Equipment, available: Boolean)

    /** Publishes [invite], shows its code on the household and withdraws [previousCode]. */
    suspend fun createInvite(invite: Invite, previousCode: String?)

    /**
     * Reads an invite from the server, never from the cache, so a deleted or expired code is not
     * accepted by mistake. Fails without a connection; null when the code does not exist.
     */
    suspend fun findInvite(code: String): Result<Invite?>

    /**
     * Adds [uid] to the invite's household. Unlike other writes this one waits for the server,
     * whose rules check the invite.
     */
    suspend fun join(invite: Invite, uid: String, displayName: String?): Result<Unit>

    /** Removes [uid] and their name from a shared household; its data stays with the others. */
    suspend fun leave(householdId: String, uid: String)

    /** Deletes the household with everything in it; only its last member may do this. */
    suspend fun delete(householdId: String, inviteCode: String?)
}
