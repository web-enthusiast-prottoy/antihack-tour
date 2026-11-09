package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdatesUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val update=PlayerUpdate("bd.arena.prototype",21,"1.0.9","$UPDATE_REPOSITORY/releases/download/player-v1.0.9/antihack-tour-1.0.9.apk")
    private fun capture(name:String) {
        ui.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        java.io.File(ui.activity.getExternalFilesDir(null),"update-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun releaseRecordRejectsCurrentAndWrongPackage() {
        fun json(packageId:String="bd.arena.prototype",code:Int=21)="""{"packageId":"$packageId","versionCode":$code,"versionName":"1.0.9","apkUrl":"${update.apkUrl}"}"""
        assertNotNull(parsePlayerUpdate(json(),20))
        assertNull(parsePlayerUpdate(json(code=20),20))
        assertNull(parsePlayerUpdate(json(packageId="bd.arena.prototype.admin"),20))
        assertTrue(runCatching {parsePlayerUpdate("broken",20)}.isFailure)
    }
    @Test fun updateActionAndLaterAreAvailable() {
        var downloads=0
        ui.activity.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {if(visible) PlayerUpdateDialog(update,false,false,{visible=false},{downloads++})}
        }
        ui.onNodeWithText("Update available").assertIsDisplayed()
        capture("english")
        ui.onNodeWithText("Download update").performClick()
        ui.runOnIdle {assertEquals(1,downloads)}
        ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Update available").assertDoesNotExist()
    }
    @Test fun banglaPromptSupportsLargeText() {
        ui.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                MaterialTheme {PlayerUpdateDialog(update,true,false,{},{})}
            }
        }
        ui.onNodeWithText("নতুন আপডেট এসেছে").assertIsDisplayed()
        ui.onNodeWithText("আপডেট ডাউনলোড করুন").assertIsDisplayed().assertHasClickAction()
        ui.onNodeWithText("পরে").assertIsDisplayed().assertHasClickAction()
        capture("bangla-large")
    }
    @Test fun realPublishedReleaseShowsAlertForVersion108() {
        var failure=""
        ui.activity.setContent {
            var available by remember { mutableStateOf<PlayerUpdate?>(null) }
            LaunchedEffect(Unit) {
                try {available=fetchPlayerUpdate(20)}catch(error:Exception){failure=error.toString()}
            }
            MaterialTheme {available?.let{PlayerUpdateDialog(it,false,false,{},{})}}
        }
        ui.waitUntil(60_000){failure.isNotEmpty()||ui.onAllNodesWithText("Update available").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("",failure)
        ui.onNodeWithText("Update available").assertIsDisplayed()
        ui.onNodeWithText("Download update").assertIsDisplayed().assertHasClickAction()
        capture("real-published-release")
    }

}
