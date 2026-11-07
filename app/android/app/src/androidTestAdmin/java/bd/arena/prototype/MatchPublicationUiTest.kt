package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class MatchPublicationUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun field(text:String):SemanticsNodeInteraction {
        ui.onNodeWithTag("admin-records").performScrollToNode(hasText(text))
        return ui.onNodeWithText(text).performScrollTo()
    }
    @Test fun ordinaryCreationIsOpenAndLegacyDraftCanBeOpenedWithoutChangingItsTime() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val title="Sample open creation ${System.currentTimeMillis()}"
        val id=session.db.collection("matches").document().id
        val original=Timestamp(Date(System.currentTimeMillis()+86_400_123L))
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.allowed}
            saveLifecycleFixture(session,id,mapOf("title" to "$title legacy","description" to "Local only","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to original,"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 0L,"perKillPaisa" to 0L,"rules" to "Sample","cancellationTerms" to "Sample","status" to "draft"))
            ui.onNodeWithTag("admin-nav-Matches").performClick()
            waitText("Create match");field("Create match").performClick()
            field("Title").performTextReplacement(title)
            ui.onNodeWithText("Prizes").performClick()
            field("Total prize (taka)").performTextReplacement("100")
            field("Add prize position").performClick()
            field("Prize (taka) · #1").performTextReplacement("50")
            ui.onNodeWithText("Publication").performClick()
            ui.onNodeWithText("Save").performClick()
            waitText("Saved.")
            val created=Tasks.await(session.db.collection("matches").whereEqualTo("title",title).limit(10).get(Source.SERVER),20,TimeUnit.SECONDS).documents.single()
            assertEquals("open",created.getString("status"));assertEquals(1L,created.getLong("revision"))
            assertEquals(5000L,Tasks.await(created.reference.collection("prizes").document("1").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("amountPaisa"))
            waitText("$title legacy")
            field("$title legacy").performClick()
            ui.onNodeWithText("Edit match").performScrollTo().performClick()
            ui.onNodeWithText("Publication").performClick()
            // Use the existing status picker to publish a legacy draft.
            field("Status: draft").performClick()
            ui.onNodeWithText("open",useUnmergedTree=true).performClick()
            ui.onNodeWithText("Save").performClick();waitText("Saved.")
            val opened=Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals("open",opened.getString("status"));assertEquals(original,opened.getTimestamp("startsAt"))
        }finally{session.auth.signOut();session.close()}
    }
}
