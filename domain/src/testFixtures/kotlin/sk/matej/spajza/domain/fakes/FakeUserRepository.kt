package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.repository.UserRepository

class FakeUserRepository(initial: List<UserProfile> = emptyList()) : UserRepository {
    val users = MutableStateFlow(initial.associateBy { it.uid })

    override fun observe(uid: String): Flow<UserProfile?> = users.map { it[uid] }

    override suspend fun create(uid: String, displayName: String?, householdId: String) {
        users.value = users.value + (uid to UserProfile(uid = uid, displayName = displayName, householdId = householdId))
    }

    override suspend fun updateBody(uid: String, body: BodyProfile) = update(uid) { it.copy(body = body) }

    override suspend fun updatePreferences(uid: String, preferences: Preferences) = update(uid) { it.copy(preferences = preferences) }

    override suspend fun setOnboardingDone(uid: String) = update(uid) { it.copy(onboardingDone = true) }

    override suspend fun setHousehold(uid: String, householdId: String) = update(uid) { it.copy(householdId = householdId) }

    private fun update(uid: String, change: (UserProfile) -> UserProfile) {
        val user = users.value[uid] ?: return
        users.value = users.value + (uid to change(user))
    }
}
