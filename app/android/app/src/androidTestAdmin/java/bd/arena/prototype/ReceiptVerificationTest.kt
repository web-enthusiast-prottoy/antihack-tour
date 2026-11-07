package bd.arena.prototype

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ReceiptVerificationTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun command(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8)}
    @Test fun brandedInboxAndNotificationFallbackCreditOnceAndExplainMismatches() {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.LOCAL_OPERATIONS)
        val reference=InstrumentationRegistry.getArguments().getString("receiptReference")
        org.junit.Assume.assumeNotNull(reference)
        checkNotNull(reference)
        command("pm grant ${ui.activity.packageName} android.permission.READ_SMS")
        command("cmd notification allow_listener ${ui.activity.packageName}/bd.arena.prototype.BkashNotificationService")
        val session=AdminSession(ui.activity)
        val preferences=ui.activity.getSharedPreferences("settings",0)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.owner}
            val uid=session.uid!!
            val receiver=Tasks.await(session.db.document("paymentInstructions/bkash").get(Source.SERVER),10,TimeUnit.SECONDS).getString("number")!!
            val player=PlayerData(session)
            fun operate(action:String,values:Map<String,Any>) {
                val done=CompletableFuture<Unit>()
                ui.runOnUiThread{player.operation(action,values){done.complete(Unit)}}
                try{done.get(20,TimeUnit.SECONDS)}catch(failure:Exception){throw AssertionError(player.error,failure)}
            }
            fun balance()=Tasks.await(session.db.document("wallets/$uid").get(Source.SERVER),10,TimeUnit.SECONDS).getLong("balance")?:0
            val before=balance()
            val now=System.currentTimeMillis()
            val time=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Dhaka")).format(Instant.ofEpochMilli(now))
            val body="You have received Tk 10.00 from 01600000000. Fee Tk 0.00. Balance Tk 1,710.70. TrxID $reference at $time"
            // The host injects a synthetic bKash-labelled SMS through the emulator modem.
            ui.waitUntil(10_000){recentReceipts(ui.activity,session,receiver,System.currentTimeMillis()).receipts.any{it.reference==reference}}
            preferences.edit().putString("sms-deposit-receiver-$uid",receiver).commit()
            ui.runOnUiThread{ui.activity.startForegroundService(android.content.Intent(ui.activity,SmsDepositService::class.java))}
            operate("deposit",mapOf("amount" to 1000L,"sender" to "01600000000","reference" to reference,"receiver" to receiver))
            val scan=recentReceipts(ui.activity,session,receiver,now)
            assertTrue(scan.error,scan.receipts.any{it.reference==reference})
            ui.waitUntil(30_000){Tasks.await(session.db.document("depositRequests/${uid}_$reference").get(Source.SERVER),5,TimeUnit.SECONDS).getString("status")=="approved"}
            assertEquals(before+1000,balance())
            operate("deposit",mapOf("amount" to 1000L,"sender" to "01600000000","reference" to reference,"receiver" to receiver))
            assertEquals(before+1000,balance())
            ui.waitUntil(30_000){preferences.getString("receipt-report-$uid","").orEmpty().contains("Pending requests:")}
            preferences.edit().remove("sms-deposit-receiver-$uid").commit()
            ui.runOnUiThread{ui.activity.stopService(android.content.Intent(ui.activity,SmsDepositService::class.java))}
            ui.waitUntil(10_000){preferences.getString("receipt-status-$uid","").orEmpty().startsWith("Background verification stopped")}
            // A complete notification can arrive before the request and survive a new store instance.
            val notificationRef="NOTICE${System.currentTimeMillis()}"
            val receipt=checkNotNull(parseBkashNotification(bkashAppPackage,listOf(body.replace(reference,notificationRef))))
            NotificationReceiptStore(ui.activity,uid,receiver).add(receipt,now)
            assertTrue(NotificationReceiptStore(ui.activity,uid,receiver).read(now).contains(receipt))
            assertFalse(NotificationReceiptStore(ui.activity,"another-account",receiver).read(now).contains(receipt))
            operate("deposit",mapOf("amount" to 1000L,"sender" to "01600000000","reference" to notificationRef,"receiver" to receiver))
            val notificationClaim=Tasks.await(session.db.document("depositRequests/${uid}_$notificationRef").get(Source.SERVER),10,TimeUnit.SECONDS)
            val report=CompletableFuture<ReceiptCheckReport>()
            scope.launch{report.complete(checkSmsClaims(session,receiver,listOf(notificationClaim),recentReceipts(ui.activity,session,receiver,System.currentTimeMillis()),System.currentTimeMillis(),player) {})}
            assertEquals(1,report.get(30,TimeUnit.SECONDS).credited)
            assertEquals(before+2000,balance())
            assertEquals("Amount does not match",receiptMismatch(receiver,receiver,reference,"01600000000",1001,now,mapOf(reference to listOf(receipt.copy(reference=reference))),now))
            assertTrue(preferences.getLong("receipt-heartbeat-$uid",0)>0)
            assertFalse(preferences.getString("receipt-report-$uid","").orEmpty().contains(reference))
        }finally {
            scope.cancel()
            session.uid?.let{preferences.edit().remove("sms-deposit-receiver-$it").commit()}
            ui.activity.stopService(android.content.Intent(ui.activity,SmsDepositService::class.java))
            command("cmd notification disallow_listener ${ui.activity.packageName}/bd.arena.prototype.BkashNotificationService")
            session.auth.signOut();session.close()
        }
    }
}
