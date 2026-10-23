package sk.matej.spajza.ui.profile

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Sex
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BodyDraftTest {
    @Test
    fun `a saved profile edits and saves back unchanged`() {
        val body = BodyProfile(Sex.MALE, 1990, 181.5, 78.0, ActivityLevel.LIGHT, Goal.GAIN, kcalOverride = 2900)
        assertEquals(body, BodyDraft.of(body).toProfile())
    }

    @Test
    fun `decimal commas work and out-of-range numbers count as empty`() {
        val profile = BodyDraft(heightCm = "172,5", weightKg = "5", birthYear = "1890", kcalOverride = "100").toProfile()
        assertEquals(172.5, profile.heightCm)
        assertNull(profile.weightKg)
        assertNull(profile.birthYear)
        assertNull(profile.kcalOverride)
    }
}
