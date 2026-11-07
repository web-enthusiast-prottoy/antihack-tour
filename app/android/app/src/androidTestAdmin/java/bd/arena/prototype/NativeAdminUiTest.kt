package bd.arena.prototype

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class NativeAdminUiTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()

    private fun waitText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollTo(text: String) {
        ui.onNode(hasScrollAction() and !hasAnyAncestor(hasScrollAction())).performScrollToNode(hasText(text))
    }

    @Test fun standaloneSignInDeniesPlayersAndClearsRevokedStaff() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val context = ui.activity.applicationContext
        val app = FirebaseApp.getInstance("arena-native")
        val auth = FirebaseAuth.getInstance(app)
        val ownerApp = FirebaseApp.initializeApp(context, app.options, "role-test-owner")!!
        val ownerAuth = FirebaseAuth.getInstance(ownerApp).apply { useEmulator("10.0.2.2", 9099) }
        val ownerDb = FirebaseFirestore.getInstance(ownerApp).apply { useEmulator("10.0.2.2", 8080) }
        try {
            auth.signOut()
            ui.activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("bangla", false).commit()
            ui.activityRule.scenario.recreate()
            waitText("Sign in")
            val email = "admin-access-${System.currentTimeMillis()}@arena.test"
            val password = "PlayerLocal123!"
            val user = Tasks.await(auth.createUserWithEmailAndPassword(email, password), 20, TimeUnit.SECONDS).user!!
            waitText("Staff access required")
            ui.onNodeWithText("Needs review").assertDoesNotExist()
            ui.onNodeWithText("Sign out").performClick()
            waitText("Sign in")

            val owner = Tasks.await(ownerAuth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"), 20, TimeUnit.SECONDS).user!!
            val role = ownerDb.document("staffRoles/${user.uid}")
            fun setRole(enabled: Boolean) {
                Tasks.await(role.set(mapOf(
                    "admin" to enabled, "notices" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to owner.uid
                )), 20, TimeUnit.SECONDS)
            }
            setRole(true)
            scrollTo("Email")
            ui.onNodeWithText("Email").performTextInput(email)
            scrollTo("Password")
            ui.onNodeWithText("Password").performTextInput(password)
            scrollTo("Sign in")
            ui.onNodeWithText("Sign in").performClick()
            waitText("Needs review")
            ui.onNodeWithTag("admin-nav-Settings").performClick()
            waitText("Manage announcements")
            ui.onNodeWithText("Staff").assertDoesNotExist()

            setRole(false)
            waitText("Staff access required")
            ui.onNodeWithText("Manage announcements").assertDoesNotExist()
            ui.onNodeWithText("Sign out").performClick()
            waitText("Sign in")
        } finally {
            auth.signOut()
            ownerApp.delete()
        }
    }
}
