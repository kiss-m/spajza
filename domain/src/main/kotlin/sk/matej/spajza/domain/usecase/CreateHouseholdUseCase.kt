package sk.matej.spajza.domain.usecase

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.UserRepository

/**
 * The first onboarding step: a new household with the catalog's default staples and equipment,
 * and the user's document linked to it.
 */
class CreateHouseholdUseCase(
    private val users: UserRepository,
    private val households: HouseholdRepository,
) {
    /** Returns the new household id. */
    suspend operator fun invoke(uid: String, displayName: String?, householdName: String, catalog: Catalog): String {
        val staples = catalog.ingredients.filter { it.isDefaultStaple }.mapTo(linkedSetOf()) { it.id }
        val id = households.create(householdName, uid, displayName, staples, Household.DEFAULT_EQUIPMENT)
        users.create(uid, displayName, id)
        return id
    }
}
