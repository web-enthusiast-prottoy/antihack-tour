package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class UserAccountsUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun scroll(text: String) { ui.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }
    @Test fun registerSignOutAndSignInWithoutAdminAccess() {
        val auth = FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        auth.signOut(); ui.waitForIdle()
        val email = "signup-${System.currentTimeMillis()}@arena.test"
        val password = "PlayerLocal123!"
        ui.onNodeWithText("New here? Create account").performClick()
        scroll("Display name"); ui.onNodeWithText("Display name").performTextInput("Test Player")
        scroll("Email"); ui.onNodeWithText("Email").performTextInput(email)
        scroll("Password"); ui.onNodeWithText("Password", substring = false).performTextInput(password)
        scroll("Confirm password"); ui.onNodeWithText("Confirm password").performTextInput("Mismatch123!")
        ui.onNodeWithText("Create account").performClick()
        ui.onNodeWithText("Passwords do not match.").assertExists()
        ui.onNodeWithText("Confirm password").performTextReplacement(password)
        ui.onNodeWithText("Create account").performClick()
        ui.waitUntil(30_000) { auth.currentUser?.email == email }
        val uid = auth.currentUser?.uid; assertNotNull(uid)
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Profile").performClick()
        ui.onNodeWithText("Admin").assertDoesNotExist()
        scroll("Sign out"); ui.onNodeWithText("Sign out").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty() }
        scroll("Email"); ui.onNodeWithText("Email").performTextReplacement(email)
        scroll("Password"); ui.onNodeWithText("Password").performTextInput(password)
        scroll("Sign in"); ui.onNodeWithText("Sign in").performClick()
        ui.waitUntil(30_000) { auth.currentUser?.uid == uid }
        assertEquals(uid,auth.currentUser?.uid)
        ui.onNodeWithText("Admin").assertDoesNotExist()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertEquals(uid,auth.currentUser?.uid)
        ui.onNodeWithText("Admin").assertDoesNotExist()
        val session=AdminSession(ui.activity)
        ui.waitUntil(10_000) {session.uid==uid}
        val data=PlayerData(session)
        val finished=java.util.concurrent.atomic.AtomicBoolean(false)
        ui.runOnUiThread {data.operation("deposit",mapOf("sender" to "01700000000","amount" to 1000L,"reference" to "UITEST${System.currentTimeMillis()}")) {finished.set(true)}}
        ui.waitUntil(30_000) {finished.get()||data.error.isNotEmpty()}
        org.junit.Assert.assertTrue(data.error,finished.get())
        session.close()
        auth.signOut()
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("বাংলা").performClick()
        ui.onNodeWithText("সাইন ইন").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("সাইন ইন").assertExists()
        ui.onNodeWithText("English").performClick()
    }
}
