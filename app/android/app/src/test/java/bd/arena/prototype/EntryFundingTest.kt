package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class EntryFundingTest {
    @Test fun depositIsUsedBeforeWinnings() {
        assertEquals(EntryFunding(1000,0),entryFunding(1000,3000,500))
        assertEquals(EntryFunding(300,700),entryFunding(1000,300,700))
        assertEquals(EntryFunding(0,1000),entryFunding(1000,0,1000))
    }
    @Test fun insufficientOrInvalidFundsAreRejected() {
        assertTrue(runCatching{entryFunding(1000,300,699)}.isFailure)
        assertTrue(runCatching{entryFunding(1000,-1,2000)}.isFailure)
        assertTrue(runCatching{entryFunding(0,1000,0)}.isFailure)
    }
    @Test fun largeBalancesDoNotOverflowTheFeeSplit() {
        assertEquals(EntryFunding(maxPaisa,0),entryFunding(maxPaisa,maxPaisa,maxPaisa))
    }
}
