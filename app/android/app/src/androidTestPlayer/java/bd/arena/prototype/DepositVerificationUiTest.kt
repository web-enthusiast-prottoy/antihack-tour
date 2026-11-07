package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DepositVerificationUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun serverApprovalShowsSuccessAndUnmatchedRequestTimesOutPending() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session = AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.owner}
            val wallet=PlayerData(session)
            fun operation(action:String,values:Map<String,Any>) {
                val complete=CompletableFuture<Exception?>()
                ui.runOnUiThread { wallet.operation(action,values){complete.complete(null)} }
                assertNull(complete.get(20,TimeUnit.SECONDS))
            }
            val receiver=Tasks.await(session.db.document("paymentInstructions/bkash").get(),20,TimeUnit.SECONDS).getString("number")!!
            val reference="VERIFY${System.currentTimeMillis()}"
            val request="${session.uid}_$reference"
            operation("deposit",mapOf("amount" to 1234L,"sender" to "01700000000","reference" to reference,"requestId" to reference,"receiver" to receiver))
            var result=""
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
                DepositVerification(session,request,System.currentTimeMillis()+30_000,false){result=it}
            } } }
            ui.onNodeWithText("Verifying deposit…").assertIsDisplayed()
            operation("autoApproveDeposit",mapOf("id" to request,"receiptConfirmed" to true,"verifiedAmount" to 1234L,
                "verifiedReference" to reference,"verifiedSender" to "01700000000","verifiedReceiver" to receiver,"paidAtMillis" to System.currentTimeMillis()))
            ui.waitUntil(20_000){result.startsWith("Deposit successful.")}
            val pendingRef="PENDING${System.currentTimeMillis()}"
            val pendingId="${session.uid}_$pendingRef"
            operation("deposit",mapOf("amount" to 1234L,"sender" to "01700000000","reference" to pendingRef,"requestId" to pendingRef,"receiver" to receiver))
            result=""
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
                DepositVerification(session,pendingId,System.currentTimeMillis()+500,false){result=it}
            } } }
            ui.waitUntil(5_000){result.startsWith("Request saved. Awaiting admin approval.")}
            assertEquals("pending",Tasks.await(session.db.document("depositRequests/$pendingId").get(),20,TimeUnit.SECONDS).getString("status"))
        } finally { session.auth.signOut();session.close() }
    }
}
