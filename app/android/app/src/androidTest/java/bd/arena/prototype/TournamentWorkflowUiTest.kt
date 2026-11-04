package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
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

/** Only demo-arena. Requires local Auth/Firestore, current rules, and the seeded local owner. */
class TournamentWorkflowUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val outerScroll=hasScrollAction() and !hasAnyAncestor(hasScrollAction())
    private fun scroll(text:String){ui.onNode(outerScroll).performScrollToNode(hasText(text));ui.onNodeWithText(text).performScrollTo()}
    private fun shell(command:String):String=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun capture(name:String){
        ui.waitForIdle()
        android.os.SystemClock.sleep(350)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/arena-v10-$name.png")).use{it.readBytes()}
    }
    @Test fun staffRoomAndPublishedResultCompleteAFreeMatchWithoutWalletCredit(){
        assertEquals("emulator",BuildConfig.NOTICE_BACKEND)
        val auth=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        auth.signOut();ui.waitForIdle()
        val session=AdminSession(ui.activity)
        val id="workflow-${System.currentTimeMillis()}"
        val title="Workflow practice $id"
        val email="$id@arena.test";val password="PlayerLocal123!"
        fun loginOwner(){
            Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),30,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.allowed};ui.waitForIdle()
        }
        fun openAdminMatch(){
            ui.waitUntil(20_000){ui.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Profile").performClick()
            scroll("Administration");ui.onNodeWithText("Administration").performClick()
            ui.onNodeWithContentDescription("Choose admin section").performClick()
            ui.onNode(hasText("Matches") and hasAnyAncestor(isPopup())).performClick()
            scroll(title)
            ui.onNode(hasText("Room and results") and hasAnySibling(hasText(title))).performScrollTo().performClick()
        }
        loginOwner()
        val owner=auth.currentUser!!.uid
        Tasks.await(session.db.document("users/$owner").set(mapOf("displayName" to "Local owner","ffUsername" to "Owner","ffUid" to "","avatar" to "solo")),20,TimeUnit.SECONDS)
        saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Fictitious end-to-end practice match.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(),"capacity" to 3L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 10000L,"perKillPaisa" to 0L,"rules" to "Local acceptance only.","cancellationTerms" to "No live money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
        auth.signOut()
        Tasks.await(auth.createUserWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
        val player=auth.currentUser!!.uid
        ui.waitUntil(10_000){session.uid==player}
        Tasks.await(session.db.document("users/$player").set(mapOf("displayName" to "Workflow Player","ffUsername" to "WorkflowPlayer","ffUid" to "","avatar" to "solo")),20,TimeUnit.SECONDS)
        ui.waitUntil(20_000){ui.onAllNodesWithText("Complete your profile").fetchSemanticsNodes().isEmpty()&&ui.onAllNodesWithText("Explore").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Explore").performClick();scroll(title);ui.onNodeWithText(title).performClick()
        ui.onNodeWithText("Join match").performClick()
        ui.onNodeWithText("Confirm roster and join").performScrollTo().performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Registration confirmed.").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("OK").performClick()
        scroll("Show room");ui.onNodeWithText("Show room").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Room unavailable. Check the release time and your confirmed entry, then retry.").fetchSemanticsNodes().isNotEmpty()}
        auth.signOut();loginOwner()
        saveLifecycleFixture(session,id,mapOf("status" to "closed","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
        openAdminMatch()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Save room") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Room ID").performTextInput("TEST-ROOM-10")
        ui.onNodeWithText("Room password").performTextInput("FAKE-PASSWORD-10")
        ui.onNodeWithContentDescription("Enable room release").performScrollTo().performClick()
        ui.onNodeWithText("Save room").performScrollTo().performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Room saved.").fetchSemanticsNodes().isNotEmpty()}
        capture("admin-room")
        auth.signOut();Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
        ui.waitUntil(10_000){session.uid==player};ui.waitForIdle()
        ui.onNodeWithText("Matches").performClick();scroll(title);ui.onNodeWithText(title).performClick()
        scroll("Show room");ui.onNodeWithText("Show room").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Room password: FAKE-PASSWORD-10").fetchSemanticsNodes().isNotEmpty()}
        capture("player-room")
        Tasks.await(session.db.disableNetwork(),10,TimeUnit.SECONDS)
        ui.waitUntil(20_000){ui.onAllNodesWithText("Room password: FAKE-PASSWORD-10").fetchSemanticsNodes().isEmpty()}
        Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS)
        ui.waitUntil(20_000){ui.onAllNodesWithText("Room password: FAKE-PASSWORD-10").fetchSemanticsNodes().isNotEmpty()}
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.onNodeWithText("Room password: FAKE-PASSWORD-10").assertDoesNotExist()
        auth.signOut();loginOwner()
        saveLifecycleFixture(session,id,mapOf("status" to "review","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
        openAdminMatch();ui.onNodeWithText("Results").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("WorkflowPlayer").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("WorkflowPlayer").performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Save result draft") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Placement").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Kills").performScrollTo().performTextReplacement("2")
        ui.onNodeWithText("Announced award (taka)").performScrollTo().performTextReplacement("10")
        ui.onNodeWithText("Save result draft").performScrollTo().performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Review and publish") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Review and publish").performScrollTo().performClick()
        ui.onNodeWithText("Publish result").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("Result published. No wallet funds were credited.").fetchSemanticsNodes().isNotEmpty()}
        auth.signOut();Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
        ui.waitUntil(10_000){session.uid==player};ui.waitForIdle()
        ui.onNodeWithText("Matches").performClick();ui.onNodeWithText("Results").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
        scroll("Not credited · awaiting settlement");ui.onNodeWithText("Not credited · awaiting settlement").assertExists()
        capture("my-results")
        scroll("All published");ui.onNodeWithText("All published").performClick()
        ui.onNodeWithText("Title starts with").performClick()
        ui.onNodeWithText("Exact match ID").performClick()
        ui.onNodeWithText("Match ID",substring=false).performTextInput(id)
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Search") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Search",substring=false).performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
        assertFalse(Tasks.await(session.db.document("wallets/$player").get(Source.SERVER),20,TimeUnit.SECONDS).exists())
        ui.onNodeWithText("Profile").performClick();scroll("বাংলা")
        ui.onNode(isToggleable()).performClick()
        ui.onNodeWithText("ম্যাচ").performClick();ui.onNodeWithText("ফলাফল").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
        val settings=listOf("system font_scale","global animator_duration_scale","global transition_animation_scale","global window_animation_scale")
        val original=settings.associateWith{shell("settings get $it")}
        try {
            val beforeFont=ui.activity
            shell("settings put system font_scale 1.5")
            for(key in settings.drop(1))shell("settings put $key 0")
            ui.waitUntil(20_000){ui.activity!==beforeFont && ui.activity.resources.configuration.fontScale>1.4f}
            ui.waitForIdle()
            ui.waitUntil(20_000){ui.onAllNodes(hasContentDescription("রিফ্রেশ") and isEnabled()).fetchSemanticsNodes().size==1}
            scroll("অর্থ যোগ হয়নি · নিষ্পত্তির অপেক্ষায়")
            ui.onNodeWithText("অর্থ যোগ হয়নি · নিষ্পত্তির অপেক্ষায়").assertIsDisplayed()
            capture("results-bangla-large")
            val beforeRotate=ui.activity
            ui.runOnUiThread{ui.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            ui.waitUntil(20_000){ui.activity!==beforeRotate && ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            ui.waitForIdle()
            ui.waitUntil(20_000){ui.onAllNodes(hasContentDescription("রিফ্রেশ") and isEnabled()).fetchSemanticsNodes().size==1}
            try {
                scroll("অর্থ যোগ হয়নি · নিষ্পত্তির অপেক্ষায়")
                ui.onNodeWithText("অর্থ যোগ হয়নি · নিষ্পত্তির অপেক্ষায়").assertIsDisplayed()
            } catch(error:Throwable) {ui.onRoot().printToLog("ArenaRotationFailure");capture("rotation-failure");throw error}
            capture("results-landscape")
        } finally {
            for((key,value)in original)if(value=="null")shell("settings delete $key")else {require(value.toFloatOrNull()!=null);shell("settings put $key $value")}
            ui.runOnUiThread{ui.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
            auth.signOut();session.close()
        }
    }
}
