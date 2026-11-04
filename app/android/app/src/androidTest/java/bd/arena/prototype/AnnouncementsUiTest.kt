package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.Rule
import org.junit.Test

class AnnouncementsUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun adminAnnouncementArrivesAndCanBeRead() {
        assertEquals("emulator", BuildConfig.NOTICE_BACKEND)
        ui.activity.getSharedPreferences("announcements", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        ui.activityRule.scenario.recreate()
        ui.onNode(hasContentDescription("Notifications", substring = true)).performClick()
        ui.onNodeWithText("Stay in the loop.").assertIsDisplayed()
        ui.waitUntil(30_000) { ui.onAllNodesWithText("Tonight’s practice update").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Tonight’s practice update").performClick()
        ui.onNode(hasText("Solo practice starts at 8 PM. Open Explore to choose your next match. Local test announcement.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        capture("notice-detail")
        ui.onNodeWithText("Got it").performClick()
        capture("notifications")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNode(hasContentDescription("Notifications, 1 unread")).assertExists()
        capture("connected-home")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Tonight’s practice update"))
        capture("connected-announcement")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        // Native dialog animations finish outside Compose's test clock.
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(ui.activity.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/arena-v03-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }
}
