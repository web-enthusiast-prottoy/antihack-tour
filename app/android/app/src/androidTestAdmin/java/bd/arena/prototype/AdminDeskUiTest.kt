package bd.arena.prototype

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class AdminDeskUiTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun waitText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun press(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val target = ui.onNodeWithText(text)
        if (ui.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            target.performScrollTo()
        }
        target.performClick()
    }

    private fun nav(page: String) = ui.onNodeWithTag("admin-nav-$page").performClick()

    private fun owner(): FirebaseAuth {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings", 0).edit().putBoolean("bangla", false).commit()
        ui.activityRule.scenario.recreate()
        val auth = FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"), 20, TimeUnit.SECONDS)
        waitText("Needs review")
        return auth
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().toString(Charsets.UTF_8).trim()
        }

    private fun capture(name: String) {
        ui.waitForIdle()
        SystemClock.sleep(400)
        val file = File(ui.activity.getExternalFilesDir(null), "operations-desk-$name.png")
        val image = automation.takeScreenshot()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
    }

    private fun playerShows(text: String, absent: String? = null) {
        shell("am force-stop bd.arena.prototype.local")
        val launch = shell("am start -W -n bd.arena.prototype.local/bd.arena.prototype.MainActivity")
        assertTrue(launch, launch.contains("Status: ok"))
        fun contains(node: AccessibilityNodeInfo?, value: String): Boolean {
            if (node == null) return false
            if (node.text?.toString()?.contains(value) == true) return true
            return (0 until node.childCount).any { contains(node.getChild(it), value) }
        }
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root?.packageName == "bd.arena.prototype.local" &&
                contains(root, text) && (absent == null || !contains(root, absent))) return
            SystemClock.sleep(250)
        }
        throw AssertionError("Player app did not show $text. Foreground: ${automation.rootInActiveWindow?.packageName}")
    }

    @Test fun publishesAndHidesAnnouncementsAcrossInstalledApps() {
        val auth = owner()
        try {
            val previous = Tasks.await(
                FirebaseFirestore.getInstance(FirebaseApp.getInstance("arena-native")).collection("notices")
                    .whereEqualTo("published", true).orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(1).get(Source.SERVER), 10, TimeUnit.SECONDS
            ).documents.single().getString("title")!!
            val title = "Sample notice ${System.currentTimeMillis()}"
            nav("Settings")
            press("Manage announcements")
            press("New announcement")
            ui.onNodeWithText("Title").performTextInput(title)
            ui.onNodeWithText("Details").performTextInput("Local Operations Desk verification.")
            press("Save")
            waitText(title)
            playerShows(title)
            shell("input keyevent KEYCODE_BACK")
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("admin-nav-Settings").fetchSemanticsNodes().isNotEmpty() }
            nav("Settings")
            press("Manage announcements")
            waitText(title)
            press(title)
            ui.onNodeWithContentDescription("Published").performScrollTo().performClick()
            press("Save")
            waitText("Hidden")
            playerShows(previous, absent = title)
        } finally {
            shell("am start -W -n bd.arena.prototype.admin.local/bd.arena.prototype.AdminActivity")
            auth.signOut()
        }
    }

    @Test fun depositQueueKeepsSampleReviewAndAudit() {
        val auth = owner()
        val session = AdminSession(ui.activity)
        try {
            ui.waitUntil(20_000) { session.allowed }
            val reference = "DESK${System.currentTimeMillis()}"
            val operations = PlayerData(session)
            var submitted = false
            operations.operation("deposit", mapOf("amount" to 1025L, "reference" to reference, "sender" to "01700000000")) { submitted = true }
            ui.waitUntil(20_000) { submitted || operations.error.isNotBlank() }
            assertEquals("", operations.error)
            nav("Finance")
            press("Deposits")
            waitText("Search loaded records")
            ui.onNodeWithText("Search loaded records").performTextReplacement(reference)
            press(money(1025))
            waitText("Review test deposit")
            capture("deposit-review")
            ui.onNodeWithText("Rejection or discrepancy reason").performScrollTo().performTextInput("Local sample discrepancy.")
            press("Reject with reason")
            val query = session.db.collection("depositRequests").whereEqualTo("reference", reference).limit(1)
            ui.waitUntil(20_000) { Tasks.await(query.get(Source.SERVER), 10, TimeUnit.SECONDS).documents.firstOrNull()?.getString("status") == "rejected" }
            val request = Tasks.await(query.get(Source.SERVER), 10, TimeUnit.SECONDS).documents.single()
            assertEquals("Local sample discrepancy.", request.getString("decisionReason"))
        } finally {
            session.close()
            auth.signOut()
        }
    }

    @Test fun overviewAndSettingsFitPhoneAndWideBanglaAtDoubleText() {
        val auth = owner()
        val initialFont = shell("settings get system font_scale")
        val initialSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            waitText("Upcoming matches")
            waitText("Sample · Island Solo")
            capture("overview-phone")
            nav("Settings")
            waitText("Payments")
            capture("settings-phone")
            press("Language")
            shell("settings put system font_scale 2.0")
            ui.waitUntil(20_000) { ui.activity.resources.configuration.fontScale > 1.9f }
            waitText("পেমেন্ট")
            capture("settings-phone-bangla")
            nav("Finance")
            press("জমা")
            capture("queue-phone-bangla")
            nav("Overview")
            waitText("পর্যালোচনা প্রয়োজন")
            waitText("Sample · Island Solo")
            capture("overview-phone-bangla")
            shell("wm size 2400x2560")
            ui.waitUntil(20_000) { ui.activity.resources.configuration.screenWidthDp >= 840 }
            waitText("পর্যালোচনা প্রয়োজন")
            waitText("সেটিংস")
            waitText("Sample · Island Solo")
            capture("overview-wide-bangla")
            nav("Finance")
            press("জমা")
            waitText("জমা")
            capture("queue-wide-bangla")
            nav("Settings")
            waitText("পেমেন্ট")
            capture("settings-wide-bangla")
            press("ভাষা")
            waitText("Payments")
            capture("settings-wide")
            nav("Overview")
            waitText("Needs review")
            waitText("Sample · Island Solo")
            capture("overview-wide")
            nav("Finance")
            press("Deposits")
            waitText("Deposits")
            capture("queue-wide")
        } finally {
            shell(if (initialSize == null) "wm size reset" else "wm size $initialSize")
            shell(if (initialFont == "null") "settings delete system font_scale" else "settings put system font_scale $initialFont")
            ui.activity.getSharedPreferences("settings", 0).edit().putBoolean("bangla", false).commit()
            auth.signOut()
        }
    }
}
