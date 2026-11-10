package bd.arena.prototype

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

class SettlementUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun waitText(text:String){try{ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}catch(error:Throwable){capture("failure");throw error}}
    private fun enabled(text:String):SemanticsNodeInteraction {
        ui.waitUntil(20_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        return node(text)
    }
    private fun node(text:String):SemanticsNodeInteraction {
        if(ui.onAllNodesWithTag("admin-records").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("admin-records").performScrollToNode(hasText(text))
        return ui.onNodeWithText(text).performScrollTo()
    }
    private fun scrollUntil(tag:String,text:String){ui.waitUntil(20_000){try{ui.onNodeWithTag(tag).performScrollToNode(hasText(text));true}catch(_:AssertionError){false}}}
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(500)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"settlement-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/settlement-$name.png")).use{it.readBytes()}
    }
    @Test fun openMatchWinnerCreditsWinningsOnceThenCompletesAfterReview() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.FIREBASE_PROJECT_ID=="demo-arena")
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val id="settlement-device-${System.currentTimeMillis()}";val title="Sample settlement $id"
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.allowed}
            val uid=session.uid!!
            val user=Tasks.await(session.db.document("users/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            val name="Sample player"
            if(user.getString("ffUsername")!=name)Tasks.await(session.db.document("users/$uid").set(mapOf("ffUsername" to name,"ffUid" to ""),SetOptions.merge()),20,TimeUnit.SECONDS)
            val before=Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            val deposit=before.getLong("balance")?:0
            val initialWinnings=before.getLong("winnings")?:0
            saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Local settlement acceptance. No real money.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+86400000),"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 0L,"perKillPaisa" to 500L,"rules" to "Local sample only.","cancellationTerms" to "No real money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
            val prizes=MatchOperations(session);var saved=false
            prizes.savePrizePosition(id,1,50000,2){_,_->saved=true};ui.waitUntil(20_000){saved}
            val player = PlayerData(session)
            var joined = false
            player.join(Tournament(id, title, "Solo", "", 0, 48), listOf(name)) { joined = true }
            ui.waitUntil(20_000) { joined }
            player.close()
            waitText("Needs review")
            ui.onNodeWithTag("admin-nav-Matches").performClick()
            scrollUntil("admin-records", title)
            ui.onNodeWithText(title).performClick()
            ui.onNodeWithText("Results").performScrollTo().performClick()
            waitText("Prize positions")
            enabled("#1").performClick()
            ui.onNodeWithTag("winner-participant-picker").performClick()
            waitText("Account ID: $uid")
            ui.onNodeWithText(name).performClick()
            ui.onNodeWithTag("credit-placement").performClick();waitText("Placement prize credited.")
            assertEquals(initialWinnings+50000,Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("winnings"))
            ui.onNodeWithText("Player kills").performClick()
            ui.waitUntil(20_000){ui.onAllNodesWithTag("kills-${uid}_$id-0").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("kills-${uid}_$id-0").performTextReplacement("4")
            ui.onNodeWithTag("review-kill-credits").performClick()
            ui.waitUntil(20_000){ui.onAllNodes(hasTestTag("confirm-kill-credits") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            capture("review")
            ui.onNodeWithTag("confirm-kill-credits").performScrollTo().performClick();waitText("Kill rewards finalized.")
            val credited=Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals(deposit,credited.getLong("balance"));assertEquals(initialWinnings+52000,credited.getLong("winnings"))
            assertEquals("open",Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getString("status"))
            saveLifecycleFixture(session,id,mapOf("status" to "review"))
            // Reopen to fetch the updated match status without changing the winner.
            ui.onNodeWithText("Participants").performScrollTo().performClick();ui.onNodeWithText("Results").performScrollTo().performClick()
            ui.onNodeWithText("Winners").performClick()
            waitText("Complete settlement");enabled("Complete settlement").performClick();waitText("Match settlement complete.");capture("complete")
            val credit=Tasks.await(session.db.document("rewardCredits/${uid}_${id}_kills").get(Source.SERVER),20,TimeUnit.SECONDS)
            val draft=Tasks.await(session.db.document("killDrafts/${uid}_$id").get(Source.SERVER),20,TimeUnit.SECONDS)
            val entry=Tasks.await(session.db.document("entries/${uid}_$id").get(Source.SERVER),20,TimeUnit.SECONDS)
            val result=Tasks.await(session.db.document("results/${credit.getString("result")}").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals(50000L,result.getLong("placementPaisa"));assertEquals(2000L,result.getLong("killPaisa"))
            val engine=ParticipantRewards(session);var retried=false
            engine.creditKills(listOf(ReviewedKills(entry,draft.getLong("revision")!!,4,500,0)),""){retried=true};ui.waitUntil(20_000){retried}
            assertEquals(initialWinnings+52000,Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("winnings"))
            assertEquals("settled",Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getString("status"))
            session.auth.signOut()
            waitText("Sign in")
        } finally {session.auth.signOut();session.close()}
    }
}
