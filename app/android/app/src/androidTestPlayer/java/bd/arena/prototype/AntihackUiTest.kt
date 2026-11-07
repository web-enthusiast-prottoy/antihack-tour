package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class AntihackUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val outer=hasScrollAction() and !hasAnyAncestor(hasScrollAction())
    private fun auth()=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
    private fun waitText(text:String){try{ui.waitUntil(20_000){if(text=="Antihack Tour")ui.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()else ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}catch(error:Throwable){capture("failure-"+text.replace(Regex("[^A-Za-z0-9]+"),"-"));throw error}}
    private fun waitGone(text:String){try{ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()}}catch(error:Throwable){capture("failure-gone-"+text.replace(Regex("[^A-Za-z0-9]+"),"-"));throw error}}
    private fun scroll(text:String){ui.onNode(hasTestTag("player-content") and hasScrollAction()).performScrollToNode(hasText(text))}
    private fun capture(name:String){
        ui.waitForIdle();android.os.SystemClock.sleep(250)
        val bmp=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"antihack-$name.png")
        file.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        shell("cp ${file.absolutePath} /data/local/tmp/antihack-$name.png")
    }
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun guest(){
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        auth().signOut();ui.activityRule.scenario.recreate();waitText("Antihack Tour")
    }
    private fun assertHomeHeaderBounds() {
        val header=ui.onNodeWithTag("screen-header").fetchSemanticsNode().boundsInRoot
        val logo=ui.onNodeWithTag("home-logo").fetchSemanticsNode().boundsInRoot
        val action=ui.onNodeWithTag("notification-action").fetchSemanticsNode().boundsInRoot
        val badge=ui.onNodeWithTag("notification-badge",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(header.left,logo.left,1f)
        assertTrue(logo.right<=action.left)
        assertTrue(action.right<=header.right&&action.left>=header.left)
        assertTrue(badge.left>=action.left&&badge.right<=action.right)
        assertTrue(badge.top>=action.top&&badge.bottom<=action.bottom)
    }
    @Test fun homeHeaderAlignsLogoAndKeepsNotificationBadgeVisible() {
        guest()
        ui.activity.getSharedPreferences("notice-null",0).edit().remove("read").commit()
        ui.activityRule.scenario.recreate();waitText("Full Map")
        ui.waitUntil(20_000){ui.onAllNodesWithTag("notification-badge",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        assertHomeHeaderBounds();capture("home-header-fixed")
        ui.onNodeWithContentDescription("Notifications").performClick();waitText("Mark all read")
    }
    @Test fun homeHeaderContainsLargeUnreadBadgeAtDoubleTextSize() {
        guest()
        ui.activityRule.scenario.onActivity {activity->activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(activity.resources.displayMetrics.density,2f)) {
                MaterialTheme {Box(Modifier.fillMaxSize().padding(16.dp)) {ScreenHeader("Antihack Tour",actions={NotificationAction(128){}})}}
            }
        }}
        ui.waitForIdle();assertHomeHeaderBounds()
    }
    @Test fun spaciousCardsAndCategoryNavigation() {
        guest();waitText("Full Map")
        ui.onAllNodes(hasTestTag("match-card-sample-solo")).assertCountEquals(0)
        ui.onNodeWithText("Next match").assertDoesNotExist()
        capture("spacious-home")
        ui.onNodeWithText("Full Map").performScrollTo().performClick()
        waitText("Sample · Island Solo")
        ui.onNodeWithTag("match-card-sample-solo").assertHasClickAction()
        ui.onNodeWithTag("match-image-sample-solo",useUnmergedTree=true).assertExists()
        ui.onNodeWithText("Sample · Clash Squad").assertDoesNotExist()
        ui.onNodeWithText("View match details").assertDoesNotExist()
        capture("spacious-explore")
        ui.onNodeWithTag("match-card-sample-solo").performClick()
        waitText("Join match")
        scroll("Match rules");ui.onNodeWithText("Match rules").assertExists()
        capture("spacious-details")
        ui.onNodeWithTag("player-content").performScrollToIndex(0)
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNode(hasText("Explore") and hasClickAction()).performClick()
        waitText("Sample · Island Solo")
        ui.onNodeWithText("Full Map").assertDoesNotExist()
        ui.onNodeWithText("Clash Squad").assertDoesNotExist()
        ui.onNodeWithTag("player-content").performScrollToNode(hasTestTag("match-card-sample-squad"))
        ui.onNodeWithTag("match-card-sample-squad").assertExists()
        ui.onNodeWithTag("player-content").performScrollToNode(hasTestTag("match-card-sample-duo"))
        ui.onNodeWithTag("match-card-sample-duo").assertExists()
        ui.onNodeWithText("Home").performClick()
        waitText("Full Map")
        ui.onNodeWithTag("match-card-sample-squad").assertDoesNotExist()
    }
    @Test fun guestBrowseJoinSignupAndSetupOnlyAtJoin() {
        guest();waitText("Full Map");ui.onNodeWithText("Wallet").assertDoesNotExist();capture("home")
        ui.onNodeWithText("Explore").performClick()
        ui.onNodeWithText("Entry fee: All").assertDoesNotExist()
        ui.onNodeWithContentDescription("Filters").performClick();waitText("Filters");capture("filters")
        ui.onNodeWithText("Show matches").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Search match ID or title").performTextInput("Island")
        ui.onNodeWithContentDescription("Search match ID or title").performImeAction()
        waitText("Sample · Island Solo");capture("explore")
        scroll("Sample · Island Solo");ui.onNodeWithText("Sample · Island Solo").performClick()
        scroll("Participants");ui.waitUntil(20_000){ui.onAllNodes(hasText("SamplePlayer",substring=true)).fetchSemanticsNodes().isNotEmpty()};capture("participants")
        ui.onNodeWithText("Join match").performClick();waitText("Sign in")
        ui.onNodeWithText("New here? Create account").performScrollTo().performClick()
        val email="antihack-${System.currentTimeMillis()}@arena.test"
        ui.onNodeWithText("Email").performTextInput(email)
        ui.onNodeWithText("Password").performTextInput("PlayerLocal123!")
        ui.onNodeWithText("Confirm password").performTextInput("PlayerLocal123!")
        ui.onNodeWithText("Display name").assertDoesNotExist();capture("signup")
        ui.onNodeWithText("Create account").performScrollTo().performClick()
        waitText("Add username");ui.onNodeWithText("Add username").performClick()
        waitText("Edit profile");ui.onNodeWithText("Complete your profile").assertDoesNotExist()
        ui.onNodeWithText(email).assertExists();capture("profile-incomplete")
        ui.onNodeWithText("Home").performClick();waitText("Antihack Tour")
        ui.onNodeWithText("Finish setup").assertDoesNotExist()
        ui.onNodeWithText("Profile").performClick();ui.onNodeWithText("Edit profile").performClick()
        ui.onNodeWithText("Free Fire username").performTextInput("AntihackPlayer")
        ui.onNodeWithText("Save profile").performScrollTo().performClick();waitText("Edit profile")
        ui.onNodeWithText("Avatar").assertDoesNotExist();ui.onNodeWithText("Display name").assertDoesNotExist();capture("profile")
        ui.onNodeWithText("Support requests").assertDoesNotExist();ui.onNodeWithText("Privacy policy").assertDoesNotExist();ui.onNodeWithText("Contact").assertExists()
        scroll("Continue to match");ui.onNodeWithText("Continue to match").performClick();waitText("Join match")
        ui.onNodeWithText("Join match").performClick();waitText("Confirm roster and join")
        ui.onNodeWithText("Confirm roster and join").performScrollTo().performClick();waitText("Registration confirmed.")
        ui.onNodeWithText("OK").performClick();ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Matches").performClick();waitText("Sample · Island Solo");capture("matches")
        ui.onNodeWithText("Wallet").performClick();waitText("Transaction history");capture("wallet-empty")
        auth().signOut()
    }
    @Test fun publicResultsSearchMatchesBothTitleAndId() {
        guest();ui.onNodeWithText("Matches").performClick();ui.onNodeWithText("Results").performClick()
        waitText("Sample · Completed Solo")
        ui.onNodeWithContentDescription("Search match ID or title").performTextInput("no-such-result-xyz")
        waitText("No published results found.")
        ui.onNodeWithContentDescription("Search match ID or title").performTextReplacement("sample-completed")
        waitText("Sample · Completed Solo");ui.waitForIdle();capture("results-id")
        ui.onNodeWithText("Title starts with").assertDoesNotExist();ui.onNodeWithText("Exact match ID").assertDoesNotExist()
        ui.onNodeWithContentDescription("Search match ID or title").performTextReplacement("Sample · Completed")
        waitText("Sample · Completed Solo");capture("results-title")
    }
    @Test fun smallBanglaLargeTextAndLandscapeRemainUsable() {
        guest()
        val oldFont=shell("settings get system font_scale")
        val motionKeys=listOf("animator_duration_scale","window_animation_scale","transition_animation_scale")
        val oldMotion=motionKeys.associateWith{shell("settings get global $it")}
        val oldSize=Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            shell("settings put system font_scale 1.0")
            ui.waitUntil(20_000){ui.activity.resources.configuration.fontScale==1.0f}
            ui.onNodeWithText("Explore").performClick()
            ui.onNodeWithContentDescription("Search match ID or title").performTextInput("Island")
            ui.onNodeWithContentDescription("Search match ID or title").performImeAction()
            waitText("Sample · Island Solo");capture("explore-english-normal")
            ui.onNodeWithText("Profile").performClick()
            ui.onNodeWithContentDescription("App language: Bangla").performClick()
            ui.onNodeWithText("খুঁজুন").performClick();waitText("Sample · Island Solo");capture("explore-bangla-normal")
            motionKeys.forEach{shell("settings put global $it 0")}
            shell("wm size 984x2132");shell("settings put system font_scale 2.0")
            ui.waitUntil(20_000){ui.activity.resources.configuration.fontScale>1.9f&&ui.onAllNodesWithContentDescription("হোম").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithContentDescription("হোম").performClick();waitText("Antihack Tour");capture("home-small-bangla-large")
            ui.onNodeWithContentDescription("খুঁজুন").performClick()
            ui.onNodeWithTag("player-content").performScrollToNode(hasText("পুরস্কারের পরিমাণ"))
            ui.onAllNodesWithText("পুরস্কারের পরিমাণ").onFirst().assertIsDisplayed()
            capture("explore-small-bangla-large")
            ui.onNodeWithTag("player-content").performScrollToIndex(0)
            ui.onNodeWithContentDescription("ফিল্টার").performClick();waitText("ফিল্টার");capture("filters-small-bangla-large")
            ui.onNodeWithText("ম্যাচ দেখুন").performScrollTo().performClick()
            ui.activityRule.scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            ui.waitUntil(20_000){ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            ui.onNodeWithTag("player-content").performScrollToNode(hasText("পুরস্কারের পরিমাণ"))
            ui.onAllNodesWithText("পুরস্কারের পরিমাণ").onFirst().assertIsDisplayed()
            capture("explore-landscape-large")
        } finally {
            shell(if(oldSize==null)"wm size reset" else "wm size $oldSize")
            oldMotion.forEach{(key,value)->shell(if(value=="null")"settings delete global $key" else "settings put global $key $value")}
            shell(if(oldFont=="null")"settings delete system font_scale" else "settings put system font_scale $oldFont")
            ui.activityRule.scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
            ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        }
    }

    @Test fun depositSubmissionPreservesBalanceAndHistory() {
        guest();Tasks.await(auth().signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),30,TimeUnit.SECONDS)
        waitText("Wallet");ui.onNodeWithText("Wallet").performClick();waitText("Transaction history")
        ui.onNodeWithText("Amount sent (৳)").assertDoesNotExist()
        ui.onNodeWithText("Add money").performClick();waitText("Sender’s mobile number")
        val reference="UI${System.currentTimeMillis()}"
        ui.onNodeWithText("Amount sent (৳)").performTextInput("10.25")
        ui.onNodeWithText("Transaction ID").performTextInput(reference)
        ui.onNodeWithText("Sender’s mobile number").performTextInput("01700000000");capture("deposit-sheet")
        ui.onNodeWithText("Submit request").performScrollTo().performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Deposit submitted for review. No funds have been credited.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("OK").performClick()
        ui.waitUntil(20_000) {
            runCatching { scroll("Payment reference: $reference") }.isSuccess
        }
        ui.onAllNodes(hasText("pending", ignoreCase = true)).onFirst().assertExists()
        capture("wallet-pending")
        ui.onNode(hasText("Profile") and hasClickAction()).performClick()
        scroll("Sign out")
        ui.onNodeWithText("Administration").assertDoesNotExist()
        val session=AdminSession(ui.activity)
        try {assertEquals(25000L,Tasks.await(session.db.document("wallets/${auth().currentUser!!.uid}").get(com.google.firebase.firestore.Source.SERVER),20,TimeUnit.SECONDS).getLong("balance"))}finally{session.close()}
        auth().signOut()
    }

    @Test fun launcherUsesSuppliedAdaptiveLogo() {
        guest()
        val info=ui.activity.applicationInfo
        assertEquals("Antihack Tour Local",ui.activity.packageManager.getApplicationLabel(info).toString())
        val icon=ui.activity.packageManager.getApplicationIcon(info)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,256,256);icon.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"antihack-launcher-icon.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        shell("cp ${file.absolutePath} /data/local/tmp/antihack-launcher-icon.png")
    }

}
