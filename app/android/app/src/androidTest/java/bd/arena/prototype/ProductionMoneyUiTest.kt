package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class ProductionMoneyUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(s:String){ui.waitUntil(20_000){ui.onAllNodesWithText(s).fetchSemanticsNodes().isNotEmpty()}}
    private fun click(s:String){waitText(s);val n=ui.onNodeWithText(s);if(ui.onAllNodes(hasText(s) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty())n.performScrollTo();n.performClick()}
    private fun get(s:AdminSession,path:String)=Tasks.await(s.db.document(path).get(Source.SERVER),20,TimeUnit.SECONDS)
    private fun scrollUntil(tag:String,text:String){ui.waitUntil(20_000){try{ui.onNodeWithTag(tag).performScrollToNode(hasText(text));true}catch(_:AssertionError){false}}}
    private fun drawer(s:String){ui.onNodeWithContentDescription("Admin navigation").performClick();click(s)}
    @Test fun emptySignInShowsValidationAndKeepsTheFormUsable() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session=AdminSession(ui.activity)
        try {
            session.auth.signOut();ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
            waitText("Profile");click("Profile");click("Sign in / Create account");click("Sign in");waitText("Enter a valid email address.")
            ui.onNodeWithText("Email").performTextReplacement("sample@example.invalid");click("Sign in");waitText("Use at least 6 password characters.");ui.onNodeWithText("Password").assertExists()
        }finally{session.close()}
    }
    @Test fun paymentNumberIsHiddenUntilStaffSaveAndEnableARealRecipient() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.FIREBASE_PROJECT_ID=="demo-arena")
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        try{
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}
            ui.onNodeWithText("Profile").performClick();click("Administration");waitText("Operations overview");drawer("Payment settings");waitText("Receiving bKash number")
            ui.waitUntil(20_000){ui.onAllNodes(hasText("Receiving bKash number") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            val toggle=ui.onNodeWithContentDescription("Enable deposit instructions")
            if(toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]==androidx.compose.ui.state.ToggleableState.On)toggle.performClick()
            ui.onNodeWithText("Receiving bKash number").performTextReplacement("");click("Save number");waitText("Receiving number saved.")
            assertFalse(get(session,"paymentInstructions/bkash").getBoolean("enabled")!!)
            ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Wallet").performClick();click("Add money");waitText("Deposits are not enabled. Contact the admin.");ui.onNodeWithText("Copy number").assertDoesNotExist()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            ui.waitUntil(20_000){ui.onAllNodesWithText("Deposits are not enabled. Contact the admin.").fetchSemanticsNodes().isEmpty()}
            ui.onNodeWithText("Profile").performClick();click("Administration");waitText("Operations overview");drawer("Payment settings");waitText("Receiving bKash number")
            ui.onNodeWithText("Receiving bKash number").performTextReplacement("01700000000");ui.onNodeWithContentDescription("Enable deposit instructions").performClick();click("Save number");waitText("Receiving number saved.")
            assertTrue(get(session,"paymentInstructions/bkash").getBoolean("enabled")!!)
            ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Wallet").performClick();click("Add money");waitText("01700000000");ui.onNodeWithText("Copy number").assertExists()
        }finally{session.auth.signOut();session.close()}
    }
    @Test fun productionEntryTransactionsChargeOnceAndCancellationRefundsThroughAdmin() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.FIREBASE_PROJECT_ID=="demo-arena")
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit();ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity);val id="production-entry-${System.currentTimeMillis()}";val title="Sample production entry $id"
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}
            val uid=session.uid!!;val before=get(session,"wallets/$uid").getLong("balance")?:0;assertTrue(before>=1000)
            saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Local acceptance of production transaction path. No real money.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+86400000),"capacity" to 2L,"joined" to 0L,"entryPaisa" to 1000L,"prizePaisa" to 5000L,"perKillPaisa" to 0L,"rules" to "Sample only.","cancellationTerms" to "Full entry refund on cancellation.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
            ui.onNodeWithText("Explore").performClick();ui.onNodeWithContentDescription("Search match ID or title").performTextInput(id);ui.onNodeWithContentDescription("Search match ID or title").performImeAction();click(title);click("Join match");click("Confirm roster and join");waitText("Entry saved. Paid entries await confirmation.");click("OK")
            assertEquals(before,get(session,"wallets/$uid").getLong("balance"));assertEquals(0L,get(session,"matches/$id").getLong("joined"))
            ui.onNodeWithTag("player-content").performScrollToIndex(0);ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Profile").performClick();click("Administration");waitText("Operations overview");drawer("Entries")
            waitText("Search loaded records");ui.onNodeWithText("Search loaded records").performTextReplacement(id)
            scrollUntil("admin-records","Approve after review");click("Approve after review");waitText("Review paid entry");click("Confirm and charge entry")
            ui.waitUntil(20_000){ui.onAllNodesWithText("Review paid entry").fetchSemanticsNodes().isEmpty()}
            assertEquals(before-1000,get(session,"wallets/$uid").getLong("balance"));assertEquals(1L,get(session,"matches/$id").getLong("joined"))
            val engine=PaidEntries(session);var done=false;var error:Exception?=null
            engine.operate("confirmEntry",mapOf("id" to "${uid}_$id")){_,e->error=e;done=true};ui.waitUntil(20_000){done};assertNull(error)
            assertEquals(before-1000,get(session,"wallets/$uid").getLong("balance"))
            val ref=session.db.document("matches/$id")
            Tasks.await(session.db.runTransaction{tx->val m=tx.get(ref);val rev=m.getLong("revision")!!+1
                tx.update(ref,mapOf("status" to "cancelled","revision" to rev,"cancellationReason" to "Local sample cancellation.","cancelledAt" to FieldValue.serverTimestamp(),"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
                tx.set(ref.collection("history").document(rev.toString()),mapOf("from" to "open","to" to "cancelled","reason" to "Local sample cancellation.","at" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/match_${id}_$rev"),mapOf("action" to "saveMatch","path" to ref.path,"actor" to uid,"at" to FieldValue.serverTimestamp()))
            },20,TimeUnit.SECONDS)
            ui.onNodeWithTag("admin-records").performScrollToIndex(0);drawer("Matches");waitText("Search loaded records");ui.onNodeWithText("Search loaded records").performTextReplacement(id)
            scrollUntil("admin-records","Room and results");click("Room and results");click("Entry refunds")
            ui.waitUntil(20_000){ui.onAllNodes(hasText("Refund remaining entries") and isEnabled()).fetchSemanticsNodes().isNotEmpty()};click("Refund remaining entries");waitText("All loaded paid entries refunded.")
            assertEquals(before,get(session,"wallets/$uid").getLong("balance"));assertEquals("refunded",get(session,"entries/${uid}_$id").getString("status"))
            done=false;engine.operate("refundEntry",mapOf("id" to "${uid}_$id")){_,e->error=e;done=true};ui.waitUntil(20_000){done};assertNull(error);assertEquals(before,get(session,"wallets/$uid").getLong("balance"))
            assertEquals(-1000L,get(session,"walletLedger/entry_${uid}_$id").getLong("delta"));assertEquals(1000L,get(session,"walletLedger/refund_${uid}_$id").getLong("delta"))
        }finally{session.auth.signOut();session.close()}
    }
}
