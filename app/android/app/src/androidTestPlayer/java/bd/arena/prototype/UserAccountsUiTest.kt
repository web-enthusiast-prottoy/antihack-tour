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
    private fun scroll(text: String) {
        if (text == "Sign in / Create account" || text == "Sign out") {
            ui.onNodeWithTag("player-content").performScrollToNode(hasText(text))
        } else {
            ui.onNodeWithText(text).performScrollTo()
        }
    }
    private fun openSignIn() {
        ui.onNode(hasText("Profile") and hasClickAction()).performClick()
        scroll("Sign in / Create account")
        ui.onNodeWithText("Sign in / Create account").performClick()
    }
    @Test fun registerSignOutAndSignInWithoutAdminAccess() {
        val auth = FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        auth.signOut(); ui.waitForIdle()
        val email = "signup-${System.currentTimeMillis()}@arena.test"
        val password = "PlayerLocal123!"
        openSignIn()
        scroll("New here? Create account"); ui.onNodeWithText("New here? Create account").performClick()
        scroll("Email"); ui.onNodeWithText("Email").performTextInput(email)
        scroll("Password"); ui.onNodeWithText("Password", substring = false).performTextInput(password)
        scroll("Confirm password"); ui.onNodeWithText("Confirm password").performTextInput("Mismatch123!")
        scroll("Create account"); ui.onNodeWithText("Create account").performClick()
        ui.onNodeWithText("Passwords do not match.").assertExists()
        ui.onNodeWithText("Confirm password").performTextReplacement(password)
        scroll("Create account"); ui.onNodeWithText("Create account").performClick()
        ui.waitUntil(30_000) { auth.currentUser?.email == email }
        val uid = auth.currentUser?.uid; assertNotNull(uid)
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasText("Profile") and hasClickAction()).performClick()
        ui.onNodeWithText("Administration").assertDoesNotExist()
        scroll("Sign out"); ui.onNodeWithText("Sign out").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Sign in / Create account").fetchSemanticsNodes().isNotEmpty() }
        scroll("Sign in / Create account"); ui.onNodeWithText("Sign in / Create account").performClick()
        scroll("Email"); ui.onNodeWithText("Email").performTextReplacement(email)
        scroll("Password"); ui.onNodeWithText("Password").performTextInput(password)
        scroll("Sign in"); ui.onNodeWithText("Sign in").performClick()
        ui.waitUntil(30_000) { auth.currentUser?.uid == uid }
        assertEquals(uid,auth.currentUser?.uid)
        ui.onNodeWithText("Administration").assertDoesNotExist()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertEquals(uid,auth.currentUser?.uid)
        ui.onNodeWithText("Administration").assertDoesNotExist()
        val session=AdminSession(ui.activity)
        ui.waitUntil(10_000) {session.uid==uid}
        val data=PlayerData(session)
        val finished=java.util.concurrent.atomic.AtomicBoolean(false)
        ui.runOnUiThread {data.operation("deposit",mapOf("sender" to "01700000000","amount" to 1000L,"reference" to "UITEST${System.currentTimeMillis()}")) {finished.set(true)}}
        ui.waitUntil(30_000) {finished.get()||data.error.isNotEmpty()}
        org.junit.Assert.assertTrue(data.error,finished.get())
        session.close()
        auth.signOut()
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()}
        openSignIn()
        ui.onNode(hasText("বাংলা") and hasClickAction()).performClick()
        ui.onNodeWithText("সাইন ইন").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("সাইন ইন").assertExists()
        ui.onNode(hasText("English") and hasClickAction()).performClick()
    }
}
