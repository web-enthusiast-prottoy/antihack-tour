package bd.arena.prototype

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class LiveSmsApprovalTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()

    @Test fun liveWalletRulesAllowOneExactCreditAndHoldCompetingClaims() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session = AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"), 20, TimeUnit.SECONDS)
            ui.waitUntil(20_000) { session.owner }
            val uid = checkNotNull(session.uid)
            fun read(path: String) = Tasks.await(session.db.document(path).get(Source.SERVER), 10, TimeUnit.SECONDS)
            val settings = read("paymentInstructions/bkash")
            check(settings.getBoolean("enabled") == true) { "Run the local seed with enabled sample deposit instructions." }
            val receiver = checkNotNull(settings.getString("number"))
            val wallet = LiveWallet(session)
            fun operate(action: String, values: Map<String, Any>): Exception? {
                val done = java.util.concurrent.CompletableFuture<Exception?>()
                ui.runOnUiThread { wallet.operate(action, values) { _, error -> done.complete(error) } }
                return done.get(20, TimeUnit.SECONDS)
            }
            val reference = "LIVESMS${System.currentTimeMillis()}"
            val sender = "01700000000"
            val claim = mapOf("amount" to 1234L, "reference" to reference, "sender" to sender, "receiver" to receiver)
            val first = "FIRST-$reference"
            val second = "SECOND-$reference"
            val firstId = "${uid}_$first"
            val secondId = "${uid}_$second"
            val before = read("wallets/$uid").getLong("balance") ?: 0
            val receipt = mapOf(
                "id" to firstId, "receiptConfirmed" to true, "verifiedAmount" to 1234L,
                "verifiedReference" to reference, "verifiedSender" to sender,
                "verifiedReceiver" to receiver, "paidAtMillis" to System.currentTimeMillis()
            )
            assertNull(operate("deposit", claim + ("requestId" to first)))
            assertNotNull(operate("autoApproveDeposit", receipt + ("verifiedAmount" to 1233L)))
            assertNotNull(operate("autoApproveDeposit", receipt +
                ("paidAtMillis" to System.currentTimeMillis() - 15 * 24 * 60 * 60 * 1000L)))
            assertEquals("pending", read("walletDeposits/$firstId").getString("status"))
            assertEquals(before, read("wallets/$uid").getLong("balance") ?: 0)
            assertNull(operate("deposit", claim + ("requestId" to second)))
            assertNotNull(operate("autoApproveDeposit", receipt))
            assertEquals("pending", read("walletDeposits/$firstId").getString("status"))
            assertEquals(before, read("wallets/$uid").getLong("balance") ?: 0)
            assertNull(operate("rejectDeposit", mapOf("id" to secondId, "reason" to "Sample competing claim")))
            assertNull(operate("autoApproveDeposit", receipt))
            assertEquals("Receipt matched on the receiving phone.", read("walletDeposits/$firstId").getString("decisionReason"))
            assertEquals(reference, read("walletLedger/live_deposit_$firstId").getString("reference"))
            assertEquals(before + 1234, read("wallets/$uid").getLong("balance"))
            val replay = "REPLAY-$reference"
            assertNull(operate("deposit", claim + ("requestId" to replay)))
            assertNotNull(operate("autoApproveDeposit", receipt + ("id" to "${uid}_$replay")))
            assertEquals(before + 1234, read("wallets/$uid").getLong("balance"))
        } finally {
            session.auth.signOut()
            session.close()
        }
    }
}
