package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SmsDepositCheckUiTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()

    @Test fun ownerCanCheckDepositsWithoutBackgroundMatching() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "pm grant ${ui.activity.packageName} android.permission.READ_SMS"
        )).use { it.readBytes() }
        val session = AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"), 20, TimeUnit.SECONDS)
            ui.waitUntil(20_000) { session.owner }
            ui.activity.getSharedPreferences("settings", 0).edit()
                .putBoolean("bangla", false).remove("sms-deposit-receiver-${session.uid}").commit()
            ui.activityRule.scenario.recreate()
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("admin-nav-Finance").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("admin-nav-Finance").performClick()
            ui.onNodeWithText("Deposits").performScrollTo().performClick()
            ui.onNodeWithText("Check deposits now").performScrollTo()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Check deposits now") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Check deposits now").performScrollTo().performClick()
            ui.waitUntil(30_000) {
                ui.onAllNodes(hasText("matching requests processed.", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithText("Check deposits now").assertIsEnabled()
            val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(ui.activity.getExternalFilesDir(null), "sms-check.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "cp ${ui.activity.getExternalFilesDir(null)}/sms-check.png /data/local/tmp/sms-check.png"
            )).use { it.readBytes() }
        } finally {
            session.auth.signOut()
            session.close()
        }
    }
}
