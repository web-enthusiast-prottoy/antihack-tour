package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class MatchScheduleTimingTest {
    private val timing=MatchScheduleTiming("2026-11-07","2026-11-15",540,1140,20)
    private fun time(value:String)=Instant.parse(value).toEpochMilli()
    @Test fun fixedIntervalsIncludeTheEndTimeAndTheLastDate() {
        assertEquals(time("2026-11-07T03:00:00Z"),timing.next(time("2026-11-06T18:00:00Z")))
        assertEquals(time("2026-11-07T03:20:00Z"),timing.next(time("2026-11-07T03:01:00Z")))
        assertEquals(time("2026-11-07T13:00:00Z"),timing.next(time("2026-11-07T12:59:00Z")))
        assertEquals(time("2026-11-08T03:00:00Z"),timing.next(time("2026-11-07T13:00:00.001Z")))
        assertEquals(time("2026-11-15T13:00:00Z"),timing.next(time("2026-11-15T13:00:00Z")))
        assertNull(timing.next(time("2026-11-15T13:00:00.001Z")))
    }
    @Test fun nineToSevenCreates31FixedStartsEachDay() {
        var next=timing.next(time("2026-11-06T18:00:00Z"))!!
        val starts=mutableListOf<Long>()
        while(next<time("2026-11-07T18:00:00Z")) {
            starts+=next
            next=timing.next(next+1)!!
        }
        assertEquals(31,starts.size)
        assertTrue(starts.zipWithNext().all{(a,b)->b-a==20*60_000L})
        assertEquals(time("2026-11-08T03:00:00Z"),next)
    }
    @Test fun unalignedEndDoesNotAddAnExtraMatch() {
        assertEquals(time("2026-11-08T03:00:00Z"),timing.copy(toMinute=1139).next(time("2026-11-07T12:41:00Z")))
    }
    @Test fun invalidDatesWindowsAndDelayAreRejected() {
        for(bad in listOf(timing.copy(dateTo="2026-11-06"),timing.copy(fromMinute=1380,toMinute=540),timing.copy(delayMinutes=0),timing.copy(dateFrom="2026-02-30"))) {
            assertTrue(runCatching{bad.validate()}.isFailure)
        }
    }
}
