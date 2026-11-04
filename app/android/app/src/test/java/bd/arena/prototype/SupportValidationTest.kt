package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class SupportValidationTest {
    @Test fun acceptsMultilineRequestsWithOptionalReference() {assertTrue(validSupportText("Match question","First line\nSecond line",""))}
    @Test fun rejectsBlankAndOversizedFields() {
        assertFalse(validSupportText(" ","Issue",""));assertFalse(validSupportText("Subject"," \n ",""))
        assertFalse(validSupportText("x".repeat(81),"Issue",""));assertFalse(validSupportText("Subject","x".repeat(4001),""));assertFalse(validSupportText("Subject","Issue","x".repeat(201)))
    }
}
