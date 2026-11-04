package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class AccountRegistrationTest {
    @Test fun validRegistration() { assertNull(registrationError(" player@arena.test ","abcdef","abcdef")) }
    @Test fun invalidEmails() { for (email in listOf("", "player", "player@", "a b@arena.test")) assertNotNull(registrationError(email,"abcdef","abcdef")) }
    @Test fun shortPassword() { assertNotNull(registrationError("p@arena.test","12345","12345")) }
    @Test fun mismatchedPasswords() { assertNotNull(registrationError("p@arena.test","abcdef","abcdeg")) }
}
