package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit

class EntryFundingUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun <T> await(task:com.google.android.gms.tasks.Task<T>)=Tasks.await(task,30,TimeUnit.SECONDS)
    private fun waitText(text:String)=ui.waitUntil(30_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    @Test fun winningsAloneCanPayForEntry()=joinWith(0,1000)
    @Test fun depositAndWinningsCanTogetherPayForEntry()=joinWith(300,700)
    private fun joinWith(deposit:Long,winnings:Long) {
        check(BuildConfig.LOCAL_OPERATIONS&&BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val match="funding-${System.currentTimeMillis()}"
        val title="Sample entry funding $match"
        try {
            await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"))
            ui.waitUntil(20_000){session.allowed}
            saveLifecycleFixture(session,match,mapOf("title" to title,"description" to "Local sample only.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+3600000),"capacity" to 8L,"joined" to 0L,"entryPaisa" to 1000L,"prizePaisa" to 5000L,"perKillPaisa" to 0L,"rules" to "Local sample only.","cancellationTerms" to "No real money.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
            await(session.auth.createUserWithEmailAndPassword("$match@arena.test","PlayerLocal123!"))
            val uid=session.auth.currentUser!!.uid
            ui.waitUntil(20_000){session.uid==uid}
            await(session.db.document("users/$uid").set(mapOf("ffUsername" to "SamplePlayer","ffUid" to "")))
            // Disposable emulator funds only. Owner REST bypass is never available in production.
            val fields=JSONObject()
            for((key,value) in mapOf("balance" to deposit,"winnings" to winnings,"reservedWinnings" to 5000L))fields.put(key,JSONObject().put("integerValue",value.toString()))
            val connection=URL("http://10.0.2.2:8080/v1/projects/demo-arena/databases/(default)/documents/wallets/$uid").openConnection() as HttpURLConnection
            connection.requestMethod="PATCH";connection.setRequestProperty("Authorization","Bearer owner");connection.setRequestProperty("Content-Type","application/json");connection.doOutput=true
            connection.outputStream.use{it.write(JSONObject().put("fields",fields).toString().toByteArray())}
            try{assertEquals(200,connection.responseCode)}finally{connection.disconnect()}
            waitText("Explore");ui.onNodeWithText("Explore").performClick()
            ui.onNodeWithContentDescription("Search match ID or title").performTextReplacement(match)
            ui.onNodeWithContentDescription("Search match ID or title").performImeAction();waitText(title)
            ui.onNodeWithText(title).performClick();waitText("Join match")
            ui.waitUntil(20_000){runCatching{ui.onNodeWithText("Join match").assertIsEnabled()}.isSuccess}
            ui.onNodeWithText("Join match").performClick();waitText("Review entry")
            ui.onNodeWithTag("join-confirm").performScrollTo().performClick();waitText("Entry confirmed. Wallet charged once.")
            val wallet=await(session.db.document("wallets/$uid").get(Source.SERVER))
            assertEquals(0L,wallet.getLong("balance"));assertEquals(0L,wallet.getLong("winnings"));assertEquals(5000L,wallet.getLong("reservedWinnings"))
            val ledger=await(session.db.document("walletLedger/entry_${uid}_$match").get(Source.SERVER))
            assertEquals(deposit,ledger.getLong("depositPortion"));assertEquals(winnings,ledger.getLong("winningsPortion"))
            assertEquals("confirmed",await(session.db.document("entries/${uid}_$match").get(Source.SERVER)).getString("status"))
        }finally{session.auth.signOut();session.close()}
    }
}
