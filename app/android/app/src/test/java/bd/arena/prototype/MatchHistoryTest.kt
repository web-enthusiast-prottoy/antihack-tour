package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class MatchHistoryTest {
    private val start=1_800_000_000_000L
    private val match=sampleMatches.first().copy(startsAtMillis=start)
    private fun browse(now:Long)=browseMatches(listOf(match),"","All","All","All","Any time",now)

    @Test fun matchStaysVisibleThroughExactlyFiveMinutesThenLeavesBrowse() {
        assertEquals(listOf(match),browse(start-1))
        assertEquals(listOf(match),browse(start))
        assertEquals(listOf(match),browse(start+299_999))
        assertEquals(listOf(match),browse(start+300_000))
        assertTrue(browse(start+300_001).isEmpty())
    }
    @Test fun missingScheduleDoesNotExpire() {
        assertFalse(matchInResultHistory(0,start))
        assertFalse(matchInResultHistory(-1,start))
    }
    @Test fun onlyConfirmedParticipationEntersHistoryAfterCutoff() {
        val matches=listOf(match,match.copy(id="requested"),match.copy(id="other-user"))
        val entries=mapOf(match.id to "confirmed","requested" to "requested")
        assertTrue(participantHistoryMatches(matches,entries,emptySet(),"",start+300_000).isEmpty())
        assertEquals(listOf(match),participantHistoryMatches(matches,entries,emptySet(),"",start+300_001))
        assertTrue(participantHistoryMatches(matches,emptyMap(),emptySet(),"",start+300_001).isEmpty())
    }
    @Test fun publishedResultIsRetainedBeforeCutoffAndDoesNotDuplicateMatch() {
        assertEquals(listOf(match),participantHistoryMatches(listOf(match),mapOf(match.id to "confirmed"),setOf(match.id),"",start))
        assertEquals(listOf(match),participantHistoryMatches(listOf(match),mapOf(match.id to "confirmed"),setOf(match.id),"",start+300_001))
    }
    @Test fun historySearchUsesIdOrTitleAndSortsNewestFirst() {
        val newer=match.copy(id="new",title="Daily Squad",startsAtMillis=start+1)
        val rows=listOf(match,newer)
        val entries=rows.associate{it.id to "confirmed"}
        assertEquals(listOf(newer,match),participantHistoryMatches(rows,entries,emptySet(),"",start+400_000))
        assertEquals(listOf(newer),participantHistoryMatches(rows,entries,emptySet()," SQUAD ",start+400_000))
        assertEquals(listOf(match),participantHistoryMatches(rows,entries,emptySet(),"FF-SOLO",start+400_000))
    }
}
