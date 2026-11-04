package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class WithdrawalTotalTest {
    @Test fun feeIsReservedWithTheExactPayoutAmount() {
        assertEquals(20500L,withdrawalTotal(20000,500))
        assertEquals(1L,withdrawalTotal(1,0))
        assertEquals(maxPaisa,withdrawalTotal(maxPaisa-1,1))
    }
    @Test fun invalidOrOverflowingRequestsCannotEnterTheWallet() {
        for(values in listOf(0L to 0L,-1L to 0L,1L to -1L,maxPaisa to 1L,Long.MAX_VALUE to 1L,1L to Long.MAX_VALUE)) {
            try{withdrawalTotal(values.first,values.second);fail("Unsafe total accepted")}catch(_:IllegalArgumentException){}
        }
    }
}
