package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class DepositValidationTest {
    @Test fun parsesExactPaisaWithoutRounding() {assertEquals(1025L,depositPaisa("10.25"));for(value in listOf("0","-1","1.001","1e999","100000.01","NaN"))assertEquals(0L,depositPaisa(value))}
    @Test fun requiresBangladeshSendingNumberFormat() {assertTrue(validDepositSender("01700000000"));for(value in listOf("0170000000","01200000000","+8801700000000","abc"))assertFalse(validDepositSender(value))}
}
