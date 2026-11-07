package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlayerStateRestorationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String) {ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun fields() {
        ui.onNodeWithText("Amount sent (৳)").assertTextContains("123.45")
        ui.onNodeWithText("Transaction ID").assertTextContains("SAMPLE12345")
        ui.onNodeWithText("Sender’s mobile number").assertTextContains("01700000000")
        ui.onNodeWithText("Connect to verify account access.").assertDoesNotExist()
    }
    @Test fun depositSurvivesAppSwitchOfflineCheckAndActivityRecreation() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity,verifyStaff=false)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            waitFor("Wallet")
            ui.onNode(hasText("Wallet") and hasClickAction()).performClick()
            ui.onNodeWithTag("player-content").performScrollToNode(hasText("Add money"))
            ui.onNodeWithText("Add money").performClick()
            waitFor("Amount sent (৳)")
            ui.onNodeWithText("Amount sent (৳)").performTextInput("123.45")
            ui.onNodeWithText("Transaction ID").performTextInput("SAMPLE12345")
            ui.onNodeWithText("Sender’s mobile number").performTextInput("01700000000")
            ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            fields()
            // A cached/reconnecting ban snapshot used to tear down the whole player composition.
            Tasks.await(session.db.disableNetwork(),10,TimeUnit.SECONDS)
            ui.waitForIdle();fields()
            ui.activityRule.scenario.recreate()
            waitFor("Amount sent (৳)");fields()
            Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS)
            ui.waitForIdle();fields()
        } finally {Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS);session.auth.signOut();session.close()}
    }
    @Test fun signedInAndGuestStartupNeverShowAccountVerificationPrompt() {
        val session=AdminSession(ui.activity,verifyStaff=false)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.activityRule.scenario.recreate()
            ui.onNodeWithText("Connect to verify account access.").assertDoesNotExist()
            waitFor("Home")
            session.auth.signOut()
            ui.activityRule.scenario.recreate()
            waitFor("Home")
            ui.onNodeWithText("Connect to verify account access.").assertDoesNotExist()
        } finally {session.auth.signOut();session.close()}
    }
}
