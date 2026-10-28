package sk.matej.spajza.domain.household

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** An invitation to a household, stored at `invites/{code}` (docs/DECISIONS.md D4). */
data class Invite(
    val code: String,
    val householdId: String,
    val householdName: String,
    val expiresAt: Instant,
    val createdBy: String,
) {
    fun isValidAt(now: Instant): Boolean = now.isBefore(expiresAt)
}

/** Invite codes: six characters without look-alikes (no 0/O, 1/I/L), as the security rules require. */
object InviteCodes {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6
    val VALIDITY: Duration = Duration.ofHours(48)

    fun generate(random: Random = SecureRandom().asKotlinRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** What the user typed, upper-cased and without spaces, dashes or characters a code never has. */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }.take(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
