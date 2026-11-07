package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class PaidRoomUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){try{ui.waitUntil(30_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}catch(error:Throwable){capture("paid-room-failure");throw AssertionError("Waiting for $text",error)}}
    private fun capture(name:String){
        ui.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"antihack-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/antihack-$name.png")).use{it.readBytes()}
    }
    private fun operation(session:AdminSession,action:String,values:Map<String,Any>):String {
        val data=PlayerData(session)
        var result:String?=null
        ui.runOnUiThread{data.operation(action,values){result=it}}
        ui.waitUntil(30_000){result!=null||data.error.isNotBlank()}
        check(data.error.isBlank()){data.error}
        return result!!
    }
    @Test fun walletChargeUnlocksReleasedRoomForConfirmedPaidParticipant() {
        check(BuildConfig.LOCAL_OPERATIONS&&BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val auth=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        val session=AdminSession(ui.activity)
        val match="paid-room-${System.currentTimeMillis()}"
        val title="Sample paid room $match"
        val email="$match@arena.test";val password="PlayerLocal123!"
        fun owner(){Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),30,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}}
        try {
            owner()
            val owner=session.uid!!
            val receiver=Tasks.await(session.db.document("paymentInstructions/bkash").get(Source.SERVER),20,TimeUnit.SECONDS).getString("number")?:defaultBkashNumber
            saveLifecycleFixture(session,match,mapOf("title" to title,"description" to "Fictitious paid-room acceptance match.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+3600000),"capacity" to 8L,"joined" to 0L,"entryPaisa" to 1000L,"prizePaisa" to 5000L,"perKillPaisa" to 0L,"rules" to "Local sample only.","cancellationTerms" to "No real money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to owner))
            val rooms=MatchOperations(session)
            ui.runOnUiThread{rooms.saveRoom(match,"PAID-ROOM-123","PAID-PASS-456",Timestamp(Date(System.currentTimeMillis()-60000)),true,0){}}
            ui.waitUntil(20_000){rooms.message.isNotBlank()||rooms.error.isNotBlank()};check(rooms.error.isBlank()){rooms.error}
            Tasks.await(auth.createUserWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
            val player=auth.currentUser!!.uid
            ui.waitUntil(20_000){session.uid==player}
            Tasks.await(session.db.document("users/$player").set(mapOf("ffUsername" to "PaidPlayer","ffUid" to "")),20,TimeUnit.SECONDS)
            val reference="PAY${System.currentTimeMillis()}";val request=UUID.randomUUID().toString().uppercase()
            operation(session,"deposit",mapOf("amount" to 3000L,"reference" to reference,"sender" to "01700000000","receiver" to receiver,"requestId" to request))
            owner()
            operation(session,"approveDeposit",mapOf("id" to "${player}_$request","verifiedAmount" to 3000L,"verifiedReference" to reference,"verifiedSender" to "01700000000","verifiedReceiver" to receiver,"receiptConfirmed" to true))
            Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS);ui.waitUntil(20_000){session.uid==player}
            waitText("Explore");ui.onNodeWithText("Explore").performClick()
            ui.onNodeWithContentDescription("Search match ID or title").performTextReplacement(match)
            ui.onNodeWithContentDescription("Search match ID or title").performImeAction();waitText(title)
            ui.onNodeWithText(title).performClick();waitText("Join match")
            ui.onNodeWithText("Join match").performClick();waitText("Confirm roster and join")
            ui.onNodeWithText("Confirm roster and join").performScrollTo().performClick();waitText("Entry saved. Paid entries await confirmation.")
            ui.onNodeWithText("OK").performClick()
            assertEquals(3000L,Tasks.await(session.db.document("wallets/$player").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("balance"))
            ui.onNodeWithText("PAID-ROOM-123").assertDoesNotExist();capture("paid-entry-pending")
            ui.onNodeWithTag("player-content").performScrollToIndex(0)
            ui.onNodeWithContentDescription("Back").performClick()
            owner();operation(session,"confirmEntry",mapOf("id" to "${player}_$match"))
            saveLifecycleFixture(session,match,mapOf("status" to "closed","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
            Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS);ui.waitUntil(20_000){session.uid==player}
            waitText("Matches");ui.onNodeWithText("Matches").performClick();waitText(title);ui.onNodeWithText(title).performClick()
            waitText("Room ready");ui.onNodeWithText("PAID-ROOM-123").assertIsDisplayed();ui.onNodeWithText("PAID-PASS-456").assertIsDisplayed()
            assertEquals(2000L,Tasks.await(session.db.document("wallets/$player").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("balance"))
            ui.onNodeWithText("Copy room ID").performClick()
            val clipboard=ui.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            ui.runOnUiThread{assertEquals("PAID-ROOM-123",clipboard.primaryClip!!.getItemAt(0).text.toString())}
            ui.onNodeWithText("Copy password").performClick()
            ui.runOnUiThread{assertEquals("PAID-PASS-456",clipboard.primaryClip!!.getItemAt(0).text.toString())}
            capture("paid-room-ready")
            Tasks.await(session.db.disableNetwork(),10,TimeUnit.SECONDS)
            ui.waitUntil(20_000){ui.onAllNodesWithText("PAID-PASS-456").fetchSemanticsNodes().isEmpty()}
            Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS);waitText("PAID-PASS-456")
            ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED);ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            ui.onNodeWithText("PAID-PASS-456").assertDoesNotExist()
        } finally {Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS);auth.signOut();session.close()}
    }
}
