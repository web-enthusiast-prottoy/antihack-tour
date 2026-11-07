package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class MatchLifecycleTest {
    @Test fun newMatchesStartOpenAndLaterTransitionsCannotSkipStates() {
        assertEquals(listOf("open"),matchStatusChoices(null,0))
        assertEquals(listOf("open","closed","cancelled"),matchStatusChoices("open",0))
        assertEquals(listOf("closed","live","cancelled"),matchStatusChoices("closed",0))
        assertEquals(listOf("live","review","cancelled"),matchStatusChoices("live",0))
    }
    @Test fun paidCancellationIsAvailableAndSettlementUsesResults() {
        assertTrue(matchStatusChoices("open",100).contains("cancelled"))
        assertEquals(listOf("review"),matchStatusChoices("review",0))
        assertEquals(listOf("cancelled"),matchStatusChoices("cancelled",0))
    }
}
