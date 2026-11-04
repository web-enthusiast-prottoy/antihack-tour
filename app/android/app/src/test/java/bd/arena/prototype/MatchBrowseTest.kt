package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class MatchBrowseTest {
    private val now=java.time.Instant.parse("2026-11-03T17:30:00Z").toEpochMilli()
    private val free=sampleMatches.first().copy(startsAtMillis=now,fee=0)
    private val paid=free.copy(id="paid",title="Next day",fee=100,startsAtMillis=now+3600000)
    @Test fun dateFilterUsesBangladeshMidnight() {
        assertEquals(listOf(free),browseMatches(listOf(free,paid),"","All","All","All","Today",now))
        assertEquals(listOf(paid),browseMatches(listOf(free,paid),"","All","All","All","Tomorrow",now))
    }
    @Test fun combinedFiltersExcludeClosedAndFullMatches() {
        assertEquals(listOf(free),browseMatches(listOf(free,paid,free.copy(id="closed",closed=true),free.copy(id="full",joined=free.capacity)),"evening","Solo","Free","Available","Any time",now))
    }
    @Test fun searchMatchesIdCaseInsensitivelyAndTrimsWhitespace() {
        assertEquals(listOf(paid),browseMatches(listOf(free,paid)," PAID ","All","Paid","All","Any time",now))
    }
    @Test fun unknownDatesAreExcludedFromDateFilter() {
        assertTrue(browseMatches(sampleMatches,"","All","All","All","Today",now).isEmpty())
    }
    @Test fun fullFilterDoesNotIncludeClosedMatches() {
        val full=free.copy(joined=free.capacity)
        assertEquals(listOf(full),browseMatches(listOf(full,full.copy(closed=true)),"","All","All","Full","Any time",now))
    }
}
