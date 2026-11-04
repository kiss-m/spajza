package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.household.InviteCodes
import sk.matej.spajza.domain.repository.CustomIngredientRepository
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.session.Session
import java.time.Clock

/** A fresh invite for the session's household; it replaces the previous code. */
class CreateInviteUseCase(
    private val households: HouseholdRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(session: Session): Invite {
        val household = session.household
        val invite = Invite(
            code = InviteCodes.generate(),
            householdId = household.id,
            householdName = household.name,
            expiresAt = clock.instant().plus(InviteCodes.VALIDITY),
            createdBy = session.uid,
        )
        households.createInvite(invite, previousCode = household.inviteCode)
        return invite
    }
}

sealed interface JoinOutcome {
    /** [movedItems] pantry and shopping items came along from the household the user left. */
    data class Joined(val householdId: String, val movedItems: Int) : JoinOutcome

    data object AlreadyMember : JoinOutcome

    /** The code has expired since it was looked up. */
    data object Expired : JoinOutcome

    /** No connection, or the server refused the invite; nothing changed. */
    data object Failed : JoinOutcome
}

/**
 * Joining another household with an invite code. A user who was alone in their household brings
 * its pantry, unchecked shopping items, own foods and own recipes along, and that household is
 * deleted; a user who shared one leaves its data with the others. The joined household's kitchen
 * settings stay.
 */
class JoinHouseholdUseCase(
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
    private val customIngredients: CustomIngredientRepository,
    private val ownRecipes: OwnRecipeRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The invite behind [code] if it exists and is still valid; fails without a connection. */
    suspend fun find(code: String): Result<Invite?> =
        households.findInvite(code).map { invite -> invite?.takeIf { it.isValidAt(clock.instant()) } }

    suspend operator fun invoke(session: Session, invite: Invite): JoinOutcome {
        if (invite.householdId == session.householdId) return JoinOutcome.AlreadyMember
        if (!invite.isValidAt(clock.instant())) return JoinOutcome.Expired
        if (households.join(invite, session.uid, session.user.displayName).isFailure) return JoinOutcome.Failed

        // Once joined, finish even if the screen that started it goes away.
        return withContext(NonCancellable) {
            val old = session.household
            users.setHousehold(session.uid, invite.householdId)
            if (old.memberUids.any { it != session.uid }) {
                households.leave(old.id, session.uid)
                JoinOutcome.Joined(invite.householdId, movedItems = 0)
            } else {
                val moved = moveData(from = old.id, to = invite.householdId, uid = session.uid)
                households.delete(old.id, old.inviteCode)
                JoinOutcome.Joined(invite.householdId, moved)
            }
        }
    }

    private suspend fun moveData(from: String, to: String, uid: String): Int {
        // Own foods first: pantry and shopping items may point at them.
        customIngredients.observe(from).first().forEach { customIngredients.save(to, it, uid) }
        ownRecipes.observe(from).first().forEach { ownRecipes.save(to, it, uid) }
        val items = pantry.observe(from).first()
        items.forEach { pantry.add(to, it.ingredient, it.quantity, it.expiresAt, it.addedBy ?: uid) }
        val open = shopping.observe(from).first().filterNot { it.checked }
        open.forEach { item ->
            val ingredient = item.ingredient
            when {
                ingredient != null -> shopping.addIngredient(to, ingredient, item.amount, item.reasonRecipeIds, item.addedBy ?: uid)
                item.text != null -> shopping.addText(to, item.text, item.addedBy ?: uid)
            }
        }
        return items.size + open.size
    }
}

/**
 * Leaving a shared household: the user starts an empty one of their own and the pantry stays with
 * the others. The last member cannot leave, because nobody would be left to keep the data.
 */
class LeaveHouseholdUseCase(
    private val users: UserRepository,
    private val households: HouseholdRepository,
) {
    /** Returns the new household id, or null when the user is the only member. */
    suspend operator fun invoke(session: Session, catalog: Catalog, newHouseholdName: String): String? {
        val household = session.household
        if (household.memberUids.none { it != session.uid }) return null
        val staples = catalog.ingredients.filter { it.isDefaultStaple }.mapTo(linkedSetOf()) { it.id }
        return withContext(NonCancellable) {
            val id = households.create(newHouseholdName, session.uid, session.user.displayName, staples, Household.DEFAULT_EQUIPMENT)
            users.setHousehold(session.uid, id)
            households.leave(household.id, session.uid)
            id
        }
    }
}
