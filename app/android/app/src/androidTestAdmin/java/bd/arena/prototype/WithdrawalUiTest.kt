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

class WithdrawalUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun node(text:String):SemanticsNodeInteraction {
        if(ui.onAllNodesWithTag("admin-records").fetchSemanticsNodes().isNotEmpty()&&ui.onAllNodesWithText("Review withdrawal request").fetchSemanticsNodes().isEmpty())ui.onNodeWithTag("admin-records").performScrollToNode(hasText(text))
        val target=ui.onNodeWithText(text)
        return if(ui.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty())target.performScrollTo()else target
    }
    private fun click(text:String){waitText(text);ui.waitUntil(20_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};node(text).performClick()}
    private fun scrollUntil(tag:String,text:String){ui.waitUntil(20_000){try{ui.onNodeWithTag(tag).performScrollToNode(hasText(text));true}catch(_:AssertionError){false}}}
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(500);val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot();val file=java.io.File(ui.activity.getExternalFilesDir(null),"withdrawal-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/withdrawal-$name.png")).use{it.readBytes()}
    }
    private fun get(session:AdminSession,path:String)=Tasks.await(session.db.document(path).get(Source.SERVER),20,TimeUnit.SECONDS)
    private fun action(error:()->String,work:((()->Unit))->Unit){var done=false;work{done=true};ui.waitUntil(20_000){done||error().isNotBlank()};assertEquals("",error());assertTrue(done)}
    private fun review(id: String) {
        waitText("Search loaded records")
        ui.onNodeWithText("Search loaded records").performTextReplacement(id)
        scrollUntil("admin-records", money(20000))
        click(money(20000))
        waitText("Review withdrawal request")
    }
    private fun request(session: AdminSession, id: String): DocumentSnapshot {
        val operations = Withdrawals(session)
        val policy = get(session, "withdrawalPolicy/bkash")
        var result: DocumentSnapshot? = null
        operations.submit(id, 20000, "01700000000", policy) { result = it }
        ui.waitUntil(20_000) { result != null || operations.error.isNotBlank() }
        assertEquals("", operations.error)
        return checkNotNull(result)
    }
    @Test fun requestRejectApproveSendAndRecoveryPreserveExactBalances() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.FIREBASE_PROJECT_ID=="demo-arena")
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}
            val uid=session.uid!!;val name=get(session,"users/$uid").getString("ffUsername").orEmpty().ifBlank{"Local sample winner"}
            if(get(session,"users/$uid").getString("ffUsername").isNullOrBlank())Tasks.await(session.db.document("users/$uid").set(mapOf("ffUsername" to name,"ffUid" to ""),SetOptions.merge()),20,TimeUnit.SECONDS)
            // Make legitimate, clearly labelled sample winnings through the same tested result flow.
            val id="withdrawal-device-${System.currentTimeMillis()}"
            saveLifecycleFixture(session,id,mapOf("title" to "Sample winnings $id","description" to "Local withdrawal acceptance. No real money.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+86400000),"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 50000L,"perKillPaisa" to 0L,"rules" to "Local sample only.","cancellationTerms" to "No real money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
            val player=PlayerData(session)
            action({player.error}){done->player.join(Tournament(id,"Sample","Solo","",0,48),listOf(name)){done()}}
            saveLifecycleFixture(session,id,mapOf("status" to "review"))
            val matches=MatchOperations(session)
            action({matches.error}){done->matches.saveDraft(get(session,"entries/${uid}_$id"),1,0,50000,0,done)}
            val draft=get(session,"resultDrafts/${uid}_$id")
            action({matches.error}){done->matches.publish(draft,done=done)}
            val settlement=WinningsSettlement(session)
            action({settlement.error}){done->settlement.credit(get(session,"results/${draft.getString("publicId")}"),done=done)}
            val before=get(session,"wallets/$uid");val winnings=before.getLong("winnings")!!;val reserved=before.getLong("reservedWinnings")?:0;val deposit=before.getLong("balance")?:0
            val policy = get(session, "withdrawalPolicy/bkash")
            if (!policy.exists()) {
                val operations = Withdrawals(session)
                action({ operations.error }) { done -> operations.policy(10000, 500, true, 0, done) }
            }
            waitText("Needs review");ui.onNodeWithTag("admin-nav-Settings").performClick();click("Withdrawal settings");waitText("Minimum withdrawal (taka)")
            ui.waitUntil(20_000){ui.onAllNodes(hasText("Minimum withdrawal (taka)") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            node("Minimum withdrawal (taka)").performTextReplacement("100.00");node("Withdrawal fee (taka)").performTextReplacement("5.00")
            val toggle=ui.onNodeWithContentDescription("Enable withdrawals");if(toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]!=androidx.compose.ui.state.ToggleableState.On)toggle.performClick()
            click("Save withdrawal settings");click("Save");waitText("Withdrawal settings saved.");ui.onNodeWithTag("admin-nav-Finance").performClick();click("Withdrawals")
            val requestId = session.db.collection("withdrawals").document().id
            val rejected=request(session, requestId)
            ui.onNodeWithContentDescription("Refresh").performClick()
            assertEquals(winnings-20500,get(session,"wallets/$uid").getLong("winnings"));assertEquals(reserved+20500,get(session,"wallets/$uid").getLong("reservedWinnings"))
            // Recover a committed request after the previous screen was lost; never reserve twice.
            assertEquals(rejected.id, request(session, requestId).id)
            assertEquals(winnings-20500, get(session, "wallets/$uid").getLong("winnings"))
            review(rejected.id)
            node("Reject and release funds").assertIsNotEnabled();node("Rejection reason").performTextReplacement("Local sample: confirmed no transfer.")
            ui.onNodeWithContentDescription("Confirm no payout occurred").performScrollTo().performClick();click("Reject and release funds");ui.waitUntil(20_000){ui.onAllNodesWithText("Review withdrawal request").fetchSemanticsNodes().isEmpty()};waitText("Search loaded records")
            assertEquals(winnings,get(session,"wallets/$uid").getLong("winnings"));assertEquals(reserved,get(session,"wallets/$uid").getLong("reservedWinnings"))
            val sent=request(session, session.db.collection("withdrawals").document().id);ui.onNodeWithContentDescription("Refresh").performClick();review(sent.id)
            click("Approve withdrawal");waitText("Actual payout reference")
            assertEquals("approved",get(session,"withdrawals/${sent.id}").getString("status"));assertEquals(reserved+20500,get(session,"wallets/$uid").getLong("reservedWinnings"))
            node("Mark as Sent").assertIsNotEnabled();node("Actual payout reference").performTextReplacement("SAMPLE-${sent.id}");node("Actual payout reference").performImeAction()
            ui.onNodeWithContentDescription("Confirm successful payout").performScrollTo().performClick();node("Mark as Sent").performScrollTo();capture("admin");click("Mark as Sent");ui.waitUntil(20_000){ui.onAllNodesWithText("Review withdrawal request").fetchSemanticsNodes().isEmpty()};waitText("Search loaded records")
            val sentRecord=get(session,"withdrawals/${sent.id}");assertEquals("sent",sentRecord.getString("status"))
            val withdrawals=Withdrawals(session)
            action({withdrawals.error}){done->withdrawals.decide(sent,"sent",reference="SAMPLE-${sent.id}",sentConfirmed=true,done=done)}
            val after=get(session,"wallets/$uid");assertEquals(deposit,after.getLong("balance"));assertEquals(winnings-20500,after.getLong("winnings"));assertEquals(reserved,after.getLong("reservedWinnings"))
            assertEquals(-20500L,get(session,"walletLedger/withdrawal_${sent.id}").getLong("delta"))
        }catch(error:Throwable){capture("failure");throw error}finally{session.auth.signOut();session.close()}
    }
}
