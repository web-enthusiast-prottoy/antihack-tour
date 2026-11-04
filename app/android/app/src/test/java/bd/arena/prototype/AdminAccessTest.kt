package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class AdminAccessTest {
    @Test fun verifiedAdminHasAccess() { assertTrue(canOpenAdmin(true,true,true)) }
    @Test fun regularUserCannotOpenAdmin() { assertFalse(canOpenAdmin(true,true,false)) }
    @Test fun cachedRoleDoesNotGrantAccess() { assertFalse(canOpenAdmin(true,false,true)) }
    @Test fun signOutRemovesAccess() { assertFalse(canOpenAdmin(false,true,true)) }
}
