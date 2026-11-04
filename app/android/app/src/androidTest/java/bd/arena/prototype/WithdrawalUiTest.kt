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
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
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
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun largeBanglaForm() {
        val oldFont=shell("settings get system font_scale")
        val oldSize=Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val motion=listOf("animator_duration_scale","window_animation_scale","transition_animation_scale").associateWith{shell("settings get global $it")}
        try {
            ui.onNodeWithText("Profile").performClick();ui.onNodeWithContentDescription("App language: Bangla").performClick()
            motion.keys.forEach{shell("settings put global $it 0")};shell("wm size 984x2132");shell("settings put system font_scale 2.0")
            ui.waitUntil(20_000){ui.activity.resources.configuration.fontScale>1.9f&&ui.onAllNodesWithContentDescription("ওয়ালেট").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithContentDescription("ওয়ালেট").performClick();ui.onNodeWithTag("player-content").performScrollToNode(hasText("জয়ী অর্থ তুলুন"));click("জয়ী অর্থ তুলুন")
            waitText("উত্তোলনের পরিমাণ (টাকা)");node("উত্তোলনের পরিমাণ (টাকা)").performTextReplacement("200.00");node("টাকা গ্রহণের বিকাশ নম্বর").performTextReplacement("01700000000");node("টাকা গ্রহণের বিকাশ নম্বর").performImeAction()
            node("উত্তোলন যাচাই করুন").assertIsEnabled();click("উত্তোলন যাচাই করুন");waitText("উত্তোলন নিশ্চিত করুন");capture("bangla-large")
            ui.onNodeWithText("অনুরোধ নিশ্চিত করুন").assertIsDisplayed();ui.onNodeWithText("ফিরুন").performClick()
            ui.activityRule.scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            ui.waitUntil(20_000){ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            android.os.SystemClock.sleep(600);ui.waitForIdle();waitText("উত্তোলন যাচাই করুন")
            node("উত্তোলন যাচাই করুন").assertIsDisplayed();capture("landscape-large")
        } catch(error:Throwable){capture("large-failure");throw error
        } finally {
            shell(if(oldSize==null)"wm size reset" else "wm size $oldSize");shell(if(oldFont=="null")"settings delete system font_scale" else "settings put system font_scale $oldFont")
            motion.forEach{(key,value)->shell(if(value=="null")"settings delete global $key" else "settings put global $key $value")}
            ui.activityRule.scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
            ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        }
    }
    private fun get(session:AdminSession,path:String)=Tasks.await(session.db.document(path).get(Source.SERVER),20,TimeUnit.SECONDS)
    private fun action(error:()->String,work:((()->Unit))->Unit){var done=false;work{done=true};ui.waitUntil(20_000){done||error().isNotBlank()};assertEquals("",error());assertTrue(done)}
    private fun admin(){ui.onNodeWithText("Profile").performClick();click("Administration");waitText("Operations overview")}
    private fun drawer(title:String){ui.onNodeWithContentDescription("Admin navigation").performClick();waitText(title);ui.onNodeWithText(title).performScrollTo().performClick()}
    private fun review(id:String){
        waitText("Search loaded records");ui.onNodeWithText("Search loaded records").performTextReplacement(id)
        click("Review request");waitText("Review withdrawal request")
    }
    private fun leaveAdmin(){ui.onNodeWithTag("admin-records").performScrollToIndex(0);ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Wallet").performClick();waitText("Withdraw winnings")}
    private fun request(session:AdminSession):DocumentSnapshot {
        ui.onNodeWithTag("player-content").performScrollToIndex(0);click("Withdraw winnings");waitText("Withdrawal amount (taka)")
        node("Withdrawal amount (taka)").performTextReplacement("200.00");node("Payout bKash number").performTextReplacement("01700000000");node("Payout bKash number").performImeAction()
        click("Review withdrawal");waitText("Confirm withdrawal");capture("confirm")
        click("Confirm request");waitText("Withdrawal request received.")
        val uid=session.uid!!;val result=Tasks.await(session.db.collection("withdrawals").whereEqualTo("uid",uid).orderBy("createdAt",Query.Direction.DESCENDING).limit(1).get(Source.SERVER),20,TimeUnit.SECONDS).documents.single()
        click("Done");return result
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
            admin();drawer("Withdrawal settings");waitText("Minimum withdrawal (taka)")
            ui.waitUntil(20_000){ui.onAllNodes(hasText("Minimum withdrawal (taka)") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            node("Minimum withdrawal (taka)").performTextReplacement("100.00");node("Withdrawal fee (taka)").performTextReplacement("5.00")
            val toggle=ui.onNodeWithContentDescription("Enable withdrawals");if(toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]!=androidx.compose.ui.state.ToggleableState.On)toggle.performClick()
            click("Save withdrawal settings");waitText("Withdrawal settings saved.");ui.onNodeWithContentDescription("Back").performClick();leaveAdmin()
            val rejected=request(session)
            assertEquals(winnings-20500,get(session,"wallets/$uid").getLong("winnings"));assertEquals(reserved+20500,get(session,"wallets/$uid").getLong("reservedWinnings"))
            // Recover a committed request after the previous screen was lost; never reserve twice.
            ui.activity.getSharedPreferences("withdrawal-intent-$uid",0).edit().putString("id",rejected.id).putString("amount","200.00").putString("destination","01700000000").commit()
            click("Withdraw winnings");waitText("Withdrawal request received.");ui.onNodeWithText("Withdrawal amount (taka)").assertDoesNotExist();click("Done")
            assertEquals(winnings-20500,get(session,"wallets/$uid").getLong("winnings"))
            admin();drawer("Withdrawals");review(rejected.id)
            node("Reject and release funds").assertIsNotEnabled();node("Rejection reason").performTextReplacement("Local sample: confirmed no transfer.")
            ui.onNodeWithContentDescription("Confirm no payout occurred").performScrollTo().performClick();click("Reject and release funds");ui.waitUntil(20_000){ui.onAllNodesWithText("Review withdrawal request").fetchSemanticsNodes().isEmpty()};waitText("Search loaded records")
            assertEquals(winnings,get(session,"wallets/$uid").getLong("winnings"));assertEquals(reserved,get(session,"wallets/$uid").getLong("reservedWinnings"))
            leaveAdmin();val sent=request(session);admin();drawer("Withdrawals");review(sent.id)
            click("Approve withdrawal");waitText("Actual payout reference")
            assertEquals("approved",get(session,"withdrawals/${sent.id}").getString("status"));assertEquals(reserved+20500,get(session,"wallets/$uid").getLong("reservedWinnings"))
            node("Mark as Sent").assertIsNotEnabled();node("Actual payout reference").performTextReplacement("SAMPLE-${sent.id}");node("Actual payout reference").performImeAction()
            ui.onNodeWithContentDescription("Confirm successful payout").performScrollTo().performClick();node("Mark as Sent").performScrollTo();capture("admin");click("Mark as Sent");ui.waitUntil(20_000){ui.onAllNodesWithText("Review withdrawal request").fetchSemanticsNodes().isEmpty()};waitText("Search loaded records")
            val sentRecord=get(session,"withdrawals/${sent.id}");assertEquals("sent",sentRecord.getString("status"))
            val withdrawals=Withdrawals(session)
            action({withdrawals.error}){done->withdrawals.decide(sent,"sent",reference="SAMPLE-${sent.id}",sentConfirmed=true,done=done)}
            val after=get(session,"wallets/$uid");assertEquals(deposit,after.getLong("balance"));assertEquals(winnings-20500,after.getLong("winnings"));assertEquals(reserved,after.getLong("reservedWinnings"))
            assertEquals(-20500L,get(session,"walletLedger/withdrawal_${sent.id}").getLong("delta"))
            leaveAdmin();scrollUntil("player-content","Payout reference: SAMPLE-${sent.id.uppercase(java.util.Locale.ROOT)}");capture("wallet")
            largeBanglaForm()
            session.auth.signOut();ui.waitUntil(20_000){ui.onAllNodesWithText("Wallet").fetchSemanticsNodes().isEmpty()}
        }catch(error:Throwable){capture("failure");throw error}finally{session.auth.signOut();session.close()}
    }
}
