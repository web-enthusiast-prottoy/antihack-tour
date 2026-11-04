package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class NativeAdminUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun adminTabRequiresAuthorizedAccountAndUsesNativeScreens() {
        val auth = FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        auth.signOut()
        ui.waitForIdle()
        ui.onNodeWithText("Admin").assertDoesNotExist()
        ui.onNodeWithText("Profile",useUnmergedTree = true).performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("ইমেইল"))
        ui.onNodeWithText("ইমেইল").performTextInput("admin@arena.test")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("পাসওয়ার্ড"))
        ui.onNodeWithText("পাসওয়ার্ড").performTextInput("ArenaLocal123!")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("সাইন ইন"))
        ui.onNodeWithText("সাইন ইন").performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithText("Admin").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Admin").performClick()
        ui.onNodeWithText("অ্যাডমিন").assertIsDisplayed()
        ui.onAllNodesWithText("ড্যাশবোর্ড").onFirst().assertExists()
        val screenshot = ui.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(ui.activity.getExternalFilesDir(null),"native-admin.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        screenshot.recycle()
        auth.signOut()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Admin").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("অ্যাডমিন").assertDoesNotExist()
        // A valid authenticated player account has no staff role.
        Tasks.await(auth.createUserWithEmailAndPassword("player-${System.currentTimeMillis()}@arena.test","ArenaPlayer123!"),30,TimeUnit.SECONDS)
        ui.waitForIdle()
        ui.onNodeWithText("Admin").assertDoesNotExist()
        ui.onNodeWithText("Profile",useUnmergedTree = true).performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("সাইন আউট"))
        ui.onNodeWithText("সাইন আউট").assertIsDisplayed()
        ui.onNodeWithText("Admin").assertDoesNotExist()
        auth.signOut()
    }
}
