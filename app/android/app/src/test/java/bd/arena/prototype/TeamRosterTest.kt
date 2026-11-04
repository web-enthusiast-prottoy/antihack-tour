package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class TeamRosterTest {
    @Test fun formatsRequireExactRosterSizes() {
        assertTrue(validRoster("Solo",listOf("Captain"),"Captain"))
        assertTrue(validRoster("Duo",listOf("Captain","Friend"),"Captain"))
        assertTrue(validRoster("Squad",listOf("Captain","Two","Three","Four"),"Captain"))
        assertFalse(validRoster("Duo",listOf("Captain"),"Captain"))
        assertFalse(validRoster("Unknown",emptyList(),"Captain"))
    }
    @Test fun rosterRejectsDuplicatesForgedCaptainAndInvalidNames() {
        for(names in listOf(listOf("Captain","CAPTAIN"),listOf("Other","Friend"),listOf("Captain"," "),listOf("Captain"," Friend"),listOf("Captain","x".repeat(81))))assertFalse(validRoster("Duo",names,"Captain"))
    }
}
