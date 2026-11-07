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

class AdminMatchEditorUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
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
        ui.waitUntil(20_000){ui.onAllNodesWithText("Needs review").fetchSemanticsNodes().isNotEmpty()}
        return session
    }
    private fun fixture(session:AdminSession,id:String,title:String,status:String="open") {
        saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Fictitious UI review match.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(),"capacity" to 3L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 10000L,"perKillPaisa" to 0L,"rules" to "Local review only.","cancellationTerms" to "No live funds.","status" to status,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
    }
    @Test fun adminAmountsUseTakaAndRejectRounding() {
        val session=owner();val id="ui-amount-${System.currentTimeMillis()}";val title="Taka preview $id"
        fixture(session,id,title,"draft")
        ui.onNodeWithTag("admin-nav-Matches").performClick()
        ui.waitUntil(20_000) { ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        scroll(title);ui.onNodeWithText(title).performClick()
        scroll("Edit match");ui.onNodeWithText("Edit match").performClick()
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
}
