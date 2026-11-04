package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class MatchWorkflowTest {
    @Test fun releaseTimeUsesBangladeshTimezone() {
        assertEquals("2026-11-03T14:00:00Z",releaseTimestamp("2026-11-03 20:00").toDate().toInstant().toString())
    }
    @Test fun impossibleReleaseDateIsRejected() {
        assertThrows(java.time.format.DateTimeParseException::class.java){releaseTimestamp("2026-02-30 20:00")}
    }
    @Test fun awardsUseExactPaisaWithoutRounding() {
        assertEquals(12345L,awardPaisa("123.45"))
        assertThrows(ArithmeticException::class.java){awardPaisa("0.001")}
        assertThrows(IllegalArgumentException::class.java){awardPaisa("-1")}
    }
    @Test fun nestedValidationFailureKeepsAnActionableMessage() {
        assertEquals("Move the match to results review first.",workflowFailure(RuntimeException(IllegalArgumentException("Move the match to results review first."))))
    }
}
