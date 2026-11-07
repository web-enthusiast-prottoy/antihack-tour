package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class DepositValidationTest {
    @Test fun parsesExactPaisaWithoutRounding() {assertEquals(1025L,depositPaisa("10.25"));for(value in listOf("0","-1","1.001","1e999","100000.01","NaN"))assertEquals(0L,depositPaisa(value))}
    @Test fun requiresBangladeshSendingNumberFormat() {assertTrue(validDepositSender("01700000000"));for(value in listOf("0170000000","01200000000","+8801700000000","abc"))assertFalse(validDepositSender(value))}
    @Test fun smsDatesMustAgreeWithServerClaimAndCurrentClock() {
        val now = 1_800_000_000_000L
        val day = 24 * 60 * 60 * 1000L
        assertTrue(validSmsReviewTimes(now, now - day, now))
        assertTrue(validSmsReviewTimes(now, now - 7 * day, now))
        assertFalse(validSmsReviewTimes(now, now - 15 * day, now))
        assertFalse(validSmsReviewTimes(now, now - 15 * day, now - 15 * day))
        assertFalse(validSmsReviewTimes(now, now + 61_000, now))
        assertFalse(validSmsReviewTimes(0, now, now))
    }

}
