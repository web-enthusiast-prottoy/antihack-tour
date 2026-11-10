package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class MatchHistoryUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitFor(check:()->Boolean)=ui.waitUntil(30_000,check)
    private fun capture(name:String) {
        ui.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"match-history-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun expiredParticipationShowsPendingThenUpdatesToPublishedResult() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session=AdminSession(ui.activity)
        val history=MyMatchResults(session)
        val id="history-device-${System.currentTimeMillis()}"
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            waitFor{session.allowed}
            val uid=session.uid!!
            Tasks.await(session.db.document("users/$uid").set(mapOf("ffUsername" to "History player","ffUid" to ""),SetOptions.merge()),20,TimeUnit.SECONDS)
            fun fixture(key:String)=saveLifecycleFixture(session,key,mapOf("title" to "Sample history $key","description" to "Local sample only.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()-360000),"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 0L,"perKillPaisa" to 0L,"rules" to "Sample","cancellationTerms" to "Sample","status" to "open"))
            fixture(id);fixture("$id-other")
            val data=PlayerData(session)
            var joined=false
            ui.runOnUiThread{data.join(Tournament(id,"","Solo","",0,48),listOf("History player")){joined=true}}
            waitFor{joined||data.error.isNotEmpty()};assertEquals("",data.error)
            ui.runOnUiThread{history.load()}
            waitFor{!history.loading||history.error};assertFalse(history.error)
            assertTrue(history.entries.containsKey(id));assertFalse(history.entries.containsKey("$id-other"))
            assertTrue(history.matches.any{it.id==id});assertFalse(history.published.containsKey(id))
            assertTrue(browseMatches(history.matches,"","All","All","All","Any time",System.currentTimeMillis()).none{it.id==id})
            fun show(scale:Float){ui.activityRule.scenario.onActivity{activity->activity.setContent{
                CompositionLocalProvider(LocalDensity provides Density(activity.resources.displayMetrics.density,scale)) {
                    MaterialTheme{ResultsScreen(session){}}
                }
            }}}
            show(1f)
            ui.onNodeWithText("Search match ID or title").performTextInput(id)
            waitFor{ui.onAllNodesWithText("Sample history $id").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Results pending").assertExists();capture("pending")
            val entry=Tasks.await(session.db.document("entries/${uid}_$id").get(Source.SERVER),20,TimeUnit.SECONDS)
            val rewards=ParticipantRewards(session)
            var reviewed:List<ReviewedKills>?=null
            ui.runOnUiThread{rewards.saveDrafts(listOf(KillDraftInput(entry,listOf("0"),0)),0){reviewed=it}}
            waitFor{reviewed!=null||rewards.error.isNotEmpty()};assertEquals("",rewards.error)
            var credited=false
            ui.runOnUiThread{rewards.creditKills(reviewed!!,""){credited=true}}
            waitFor{credited||rewards.error.isNotEmpty()};assertEquals("",rewards.error)
            waitFor{history.published.containsKey(id)}
            waitFor{ui.onAllNodesWithText("Placement not recorded").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Results pending").assertDoesNotExist();capture("published")
            show(2f);waitFor{ui.onAllNodesWithText("Sample history $id").fetchSemanticsNodes().isNotEmpty()};capture("large-text")
        } finally {ui.runOnUiThread{history.close()};session.auth.signOut();session.close()}
    }
}
