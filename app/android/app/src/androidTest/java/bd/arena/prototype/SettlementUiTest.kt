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
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
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
    @Test fun reviewedPlacementAndKillsCreditWinningsOnceThenCompleteTheMatch() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.FIREBASE_PROJECT_ID=="demo-arena")
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val id="settlement-device-${System.currentTimeMillis()}";val title="Sample settlement $id"
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.allowed}
            val uid=session.uid!!
            val user=Tasks.await(session.db.document("users/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            val name=user.getString("ffUsername").orEmpty().ifBlank{"Local settlement captain"}
            if(user.getString("ffUsername").isNullOrBlank())Tasks.await(session.db.document("users/$uid").set(mapOf("ffUsername" to name,"ffUid" to ""),SetOptions.merge()),20,TimeUnit.SECONDS)
            val before=Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            val deposit=before.getLong("balance")?:0
            val initialWinnings=before.getLong("winnings")?:0
            saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Local settlement acceptance. No real money.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+86400000),"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 0L,"perKillPaisa" to 500L,"rules" to "Local sample only.","cancellationTerms" to "No real money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
            val prizes=MatchOperations(session);var saved=false
            prizes.savePrizePosition(id,1,50000,2){_,_->saved=true};ui.waitUntil(20_000){saved}
            ui.onNodeWithText("Explore").performClick();ui.onNodeWithContentDescription("Search match ID or title").performTextInput(id)
            waitText(title);ui.onNodeWithText(title).performClick();ui.onNodeWithText("Join match").performClick()
            waitText("Confirm roster and join");node("Confirm roster and join").performClick();waitText("Registration confirmed.");ui.onNodeWithText("OK").performClick()
            saveLifecycleFixture(session,id,mapOf("status" to "review"))
            ui.onNodeWithTag("player-content").performScrollToIndex(0);ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Profile").performClick();node("Administration").performClick();waitText("Operations overview")
            ui.onNodeWithContentDescription("Admin navigation").performClick();ui.onNodeWithText("Matches").performClick();waitText(title)
            ui.onNode(hasText("Room and results") and hasAnySibling(hasText(title))).performScrollTo().performClick();ui.onNodeWithText("Results").performClick()
            waitText("Complete settlement");node("Complete settlement").assertIsNotEnabled()
            enabled(name).performClick();waitText("Placement")
            enabled("Placement").performTextReplacement("1");node("Kills").performTextReplacement("4")
            ui.onNodeWithText("Legacy award (taka)").assertDoesNotExist()
            enabled("Save result draft").performClick();waitText("Review and publish")
            enabled("Review and publish").performClick();waitText("Publish and credit this result?")
            ui.onNode(hasText("Prize to winnings: "+money(52000)) and hasAnyAncestor(isDialog())).assertExists();capture("review")
            ui.onNode(hasText("Publish and credit prize") and hasAnyAncestor(isDialog())).performClick();waitText("Prize credited to winnings.")
            val credited=Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals(deposit,credited.getLong("balance"));assertEquals(initialWinnings+52000,credited.getLong("winnings"))
            node("Choose another participant").performClick();waitText("Complete settlement");enabled("Complete settlement").performClick();waitText("Match settlement complete.");capture("complete")
            val credit=Tasks.await(session.db.document("prizeCredits/${uid}_$id").get(Source.SERVER),20,TimeUnit.SECONDS)
            val result=Tasks.await(session.db.document("results/${credit.getString("result")}").get(Source.SERVER),20,TimeUnit.SECONDS)
            val engine=WinningsSettlement(session);var retried=false
            engine.credit(result){retried=true};ui.waitUntil(20_000){retried}
            assertEquals(initialWinnings+52000,Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("winnings"))
            assertEquals("settled",Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getString("status"))
            ui.onNodeWithContentDescription("Back").performScrollTo().performClick();ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Wallet").performClick();waitText("Deposit balance");ui.onNodeWithText("Available winnings").assertExists()
            scrollUntil("player-content","Transaction history");scrollUntil("player-content","Match ID: $id")
            ui.onNodeWithTag("player-content").performScrollToNode(hasText("Match ID: $id"));capture("wallet")
            ui.onNodeWithText("Matches").performClick();ui.onNodeWithText("Results").performClick();waitText("Results")
            ui.onNodeWithContentDescription("Search match ID or title").performTextInput(id);ui.onNodeWithContentDescription("Search match ID or title").performImeAction();waitText("Credited to winnings: "+money(52000));capture("result")
            session.auth.signOut();ui.waitUntil(20_000){ui.onAllNodesWithText("Wallet").fetchSemanticsNodes().isEmpty()}
        } finally {session.auth.signOut();session.close()}
    }
}
