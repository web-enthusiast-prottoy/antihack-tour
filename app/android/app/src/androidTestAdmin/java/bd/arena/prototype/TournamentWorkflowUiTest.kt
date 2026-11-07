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

/** Only demo-arena. Requires local Auth/Firestore, current rules, and the seeded local owner. */
class TournamentWorkflowUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
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
    @Test fun staffReleasesRoomAndPublishesResult(){
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
        fun openAdminMatch() {
            ui.waitUntil(20_000) { ui.onAllNodesWithText("Needs review").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("admin-nav-Matches").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(title).performScrollTo().performClick()
        }
        loginOwner()
        val owner=auth.currentUser!!.uid
        saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Fictitious end-to-end practice match.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(),"capacity" to 3L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 10000L,"perKillPaisa" to 0L,"rules" to "Local acceptance only.","cancellationTerms" to "No live money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
        auth.signOut()
        Tasks.await(auth.createUserWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
        val player=auth.currentUser!!.uid
        ui.waitUntil(10_000){session.uid==player}
        Tasks.await(session.db.document("users/$player").set(mapOf("ffUsername" to "WorkflowPlayer","ffUid" to "")),20,TimeUnit.SECONDS)
        val playerData = PlayerData(session)
        var joined = false
        playerData.join(Tournament(id, title, "Solo", "", 0, 3), listOf("WorkflowPlayer")) { joined = true }
        ui.waitUntil(20_000) { joined }
        playerData.close()
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
        val room = Tasks.await(session.db.document("rooms/$id").get(Source.SERVER), 20, TimeUnit.SECONDS)
        assertTrue(room.exists())
        ui.onNodeWithContentDescription("Back").performScrollTo().performClick()
        ui.onNodeWithTag("admin-nav-Overview").performClick()
        saveLifecycleFixture(session,id,mapOf("status" to "review","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
        openAdminMatch();ui.onNodeWithText("Results").performClick()
        ui.waitUntil(20_000){ui.onAllNodesWithText("WorkflowPlayer").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("WorkflowPlayer").performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Save result draft") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Placement").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Kills").performScrollTo().performTextReplacement("2")
        ui.onNodeWithText("Legacy award (taka)").performScrollTo().performTextReplacement("10")
        ui.onNodeWithText("Save result draft").performScrollTo().performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Review and publish") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Review and publish").performScrollTo().performClick()
        ui.onNodeWithText("Publish and credit prize").performClick()
        ui.waitUntil(20_000) { ui.onAllNodesWithText("Prize credited to winnings.").fetchSemanticsNodes().isNotEmpty() }
        val draft = Tasks.await(session.db.document("resultDrafts/${player}_$id").get(Source.SERVER), 20, TimeUnit.SECONDS)
        val result = Tasks.await(session.db.document("results/${draft.getString("publicId")}").get(Source.SERVER), 20, TimeUnit.SECONDS)
        assertEquals(1000L, result.getLong("creditedPaisa"))
        auth.signOut()
        session.close()
    }
}
