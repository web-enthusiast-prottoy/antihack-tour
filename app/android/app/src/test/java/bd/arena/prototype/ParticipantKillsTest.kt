package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class ParticipantKillsTest {
    @Test fun unsetCountsAreZeroAndTeamCountsSumExactly() {
        assertEquals(0L,participantKills(listOf("")))
        assertEquals(0L,participantKills(listOf("", "0", "", "")))
        assertEquals(7L,participantKills(listOf("4", "", "3", "0")))
        assertEquals(3500L,prizeAward(0,500,participantKills(listOf("4", "", "3", "0"))))
    }
    @Test fun negativeFractionalInvalidAndExcessiveCountsCannotPay() {
        for(values in listOf(listOf("-1"),listOf("1.5"),listOf("oops"),listOf("10001"),listOf("9000","2000"))) {
            try{participantKills(values);fail("Invalid kills accepted")}catch(_:IllegalArgumentException){}
        }
    }
}
