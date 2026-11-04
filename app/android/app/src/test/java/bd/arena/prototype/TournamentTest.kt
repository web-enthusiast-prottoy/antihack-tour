package bd.arena.prototype
import org.junit.Assert.assertEquals
import org.junit.Test
class TournamentTest {
    private val match = sampleMatches.first()
    @Test fun availableMatchAcceptsRegistration() { assertEquals(JoinResult.REGISTERED, registrationResult(match, emptySet())) }
    @Test fun repeatRegistrationIsRejected() { assertEquals(JoinResult.DUPLICATE, registrationResult(match, setOf(match.id))) }
    @Test fun fullMatchIsRejected() { assertEquals(JoinResult.FULL, registrationResult(match.copy(joined = match.capacity), emptySet())) }
    @Test fun closedMatchIsRejected() { assertEquals(JoinResult.CLOSED, registrationResult(match.copy(closed = true), emptySet())) }
}
