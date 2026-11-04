package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class PrizeTotalTest {
    @Test fun replacesUnallocatedPoolAndAppliesExactDeltas() {
        assertEquals(50000L,updatedPrizeTotal(false,999L,0L,50000L))
        assertEquals(80000L,updatedPrizeTotal(true,50000L,0L,30000L))
        assertEquals(75000L,updatedPrizeTotal(true,80000L,30000L,25000L))
        assertEquals(50000L,updatedPrizeTotal(true,75000L,25000L,0L))
    }
    @Test fun rejectsInvalidAmountsAndOversizedTotals() {
        for(args in listOf(longArrayOf(0,0,-1),longArrayOf(0,0,9007199254740992),longArrayOf(9007199254740991,0,1),longArrayOf(100,200,0))) {
            try{updatedPrizeTotal(true,args[0],args[1],args[2]);fail("Invalid total accepted")}catch(_:IllegalArgumentException){}
        }
    }
}
