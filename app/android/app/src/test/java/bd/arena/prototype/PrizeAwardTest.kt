package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class PrizeAwardTest {
    @Test fun exactPlacementAndKillRewardsIncludePlayersOutsideThePodium() {
        assertEquals(52000L,prizeAward(50000,500,4))
        assertEquals(1000L,prizeAward(0,500,2))
        assertEquals(0L,prizeAward(0,0,0))
        assertEquals(126L,prizeAward(123,1,3))
    }
    @Test fun invalidKillsNegativeValuesAndUnsafeAmountsAreRejected() {
        for(args in listOf(longArrayOf(-1,0,0),longArrayOf(0,-1,1),longArrayOf(0,1,-1),longArrayOf(0,1,10001),longArrayOf(maxPaisa,1,1),longArrayOf(0,Long.MAX_VALUE,10000))) {
            try{prizeAward(args[0],args[1],args[2]);fail("Invalid award accepted")}catch(_:IllegalArgumentException){}
        }
    }
}
