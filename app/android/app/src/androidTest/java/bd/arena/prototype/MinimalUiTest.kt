package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class MinimalUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val outer=hasScrollAction() and !hasAnyAncestor(hasScrollAction())
    private fun scroll(text:String){ui.onNode(outer).performScrollToNode(hasText(text));ui.onNodeWithText(text).performScrollTo()}
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun capture(name:String){
        ui.waitForIdle();android.os.SystemClock.sleep(350)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("System UI isn't responding")?.isNotEmpty()!=true){"Android System UI is blocking visual verification"}
        val bmp=automation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"$name.png")
        file.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        shell("cp ${file.absolutePath} /data/local/tmp/arena-v11-$name.png")
    }
    private fun auth()=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
    private fun owner():AdminSession {
        assertEquals("emulator",BuildConfig.NOTICE_BACKEND)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        val auth=auth();auth.signOut()
        Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),30,TimeUnit.SECONDS)
        val session=AdminSession(ui.activity);ui.waitUntil(20_000){session.allowed}
        Tasks.await(session.db.document("users/${auth.currentUser!!.uid}").set(mapOf("displayName" to "Local owner","ffUsername" to "Owner","ffUid" to "","avatar" to "solo")),20,TimeUnit.SECONDS)
        ui.waitUntil(20_000){ui.onAllNodesWithText("Complete your profile").fetchSemanticsNodes().isEmpty()&&ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()}
        return session
    }
    private fun fixture(session:AdminSession,id:String,title:String,status:String="open") {
        saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Fictitious UI review match.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(),"capacity" to 3L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 10000L,"perKillPaisa" to 0L,"rules" to "Local review only.","cancellationTerms" to "No live funds.","status" to status,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
    }
    @Test fun adminAmountsUseTakaAndRejectRounding() {
        val session=owner();val id="ui-amount-${System.currentTimeMillis()}";val title="Taka preview $id"
        fixture(session,id,title,"draft")
        ui.onNodeWithText("Profile").performClick();scroll("Administration");ui.onNodeWithText("Administration").performClick()
        ui.onNodeWithContentDescription("Choose admin section").performClick();ui.onNode(hasText("Matches") and hasAnyAncestor(isPopup())).performClick()
        scroll(title);ui.onNode(hasText("Edit") and hasAnySibling(hasText(title))).performScrollTo().performClick()
        scroll("Entry fee (taka)");ui.onNodeWithText("Entry fee (taka)").performTextReplacement("1.50")
        scroll("Prize pool (taka)");ui.onNodeWithText("Prize pool (taka)").assertTextContains("100.00");ui.onNodeWithText("Prize pool (taka)").performTextReplacement("100.255")
        scroll("Save");ui.onNodeWithText("Save").performClick()
        ui.onNodeWithText("Enter valid taka amounts with up to two decimals.").assertExists()
        assertEquals(10000L,Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("prizePaisa"))
        scroll("Prize pool (taka)");ui.onNodeWithText("Prize pool (taka)").performTextReplacement("100.25");capture("admin-taka")
        scroll("Save");ui.onNodeWithText("Save").performClick()
        ui.waitUntil(20_000){Tasks.await(session.db.document("matches/$id").get(Source.SERVER),10,TimeUnit.SECONDS).getLong("prizePaisa")==10025L}
        assertEquals(150L,Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("entryPaisa"))
        auth().signOut();session.close()
    }
    @Test fun playerScreensAreCompactAndSecondaryFormsOpenOnDemand() {
        val session=owner();val id="ui-design-${System.currentTimeMillis()}";val title="Design practice $id"
        fixture(session,id,title)
        val auth=auth();auth.signOut()
        Tasks.await(auth.createUserWithEmailAndPassword("$id@arena.test","PlayerLocal123!"),30,TimeUnit.SECONDS)
        val uid=auth.currentUser!!.uid;ui.waitUntil(10_000){session.uid==uid}
        Tasks.await(session.db.document("users/$uid").set(mapOf("displayName" to "Design Player","ffUsername" to "","ffUid" to "","avatar" to "solo")),20,TimeUnit.SECONDS)
        ui.waitUntil(20_000){ui.onAllNodesWithText("Complete your profile").fetchSemanticsNodes().isEmpty()&&ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Profile").performClick()
        scroll("Free Fire username");ui.onNodeWithText("Free Fire username").performTextInput("DesignPlayer")
        scroll("Save profile");ui.onNodeWithText("Save profile").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Edit profile").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Free Fire username").assertDoesNotExist();capture("profile")
        ui.onNodeWithText("Edit profile").performClick();ui.onNodeWithText("Free Fire username").assertExists();scroll("Cancel");ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Home").performClick();capture("home")
        ui.onNodeWithContentDescription("Notifications").performClick();ui.onNodeWithText("Notifications").assertExists();capture("notifications");ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Explore").performClick();scroll(title);capture("explore")
        ui.onNodeWithText(title).performClick();capture("details")
        ui.onNodeWithText("Join match").performClick()
        ui.onNodeWithText("Confirm roster and join").performScrollTo().performClick();ui.waitUntil(20_000){ui.onAllNodesWithText("Registration confirmed.").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText("OK").performClick()
        ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Matches").performClick();scroll(title);capture("my-matches")
        ui.onNodeWithText("Wallet").performClick();ui.onNodeWithText("Amount sent (৳)").assertDoesNotExist();ui.onNodeWithText("Add money").performClick()
        ui.onNodeWithText("Sender’s mobile number").performTextInput("01700000000");ui.onNodeWithText("Amount sent (৳)").performTextInput("10.25");ui.onNodeWithText("Transaction ID").performTextInput("UI${System.currentTimeMillis()}");capture("deposit-sheet")
        ui.onNodeWithText("Submit request").performScrollTo().performClick();ui.waitUntil(20_000){ui.onAllNodesWithText("Deposit submitted for review. No funds have been credited.",substring=true).fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText("OK").performClick()
        ui.onNode(hasText("pending",ignoreCase=true)).assertExists();capture("wallet")
        ui.onNodeWithText("Profile").performClick();scroll("বাংলা");ui.onNodeWithContentDescription("App language: Bangla").performClick()
        val originalFont=shell("settings get system font_scale");val oldSize=Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            shell("wm size 984x2132");shell("settings put system font_scale 2.0")
            ui.waitUntil(20_000){ui.activity.resources.configuration.fontScale>1.9f&&ui.onAllNodesWithContentDescription("হোম",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithContentDescription("হোম",useUnmergedTree=true).performClick();ui.waitUntil(20_000){ui.onAllNodes(outer).fetchSemanticsNodes().size==1};capture("home-small-bangla-large")
            ui.onNodeWithContentDescription("খুঁজুন",useUnmergedTree=true).performClick();scroll(title);capture("explore-small-bangla-large")
        } finally {
            shell(if(oldSize==null)"wm size reset" else "wm size $oldSize")
            shell(if(originalFont=="null")"settings delete system font_scale" else "settings put system font_scale $originalFont")
            ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
            auth.signOut();session.close()
        }
    }
}
