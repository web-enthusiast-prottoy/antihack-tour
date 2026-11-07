package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Inject a current sample SMS with adb emu sms send 25274 before running this test. */
class SmsDepositUiTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()

    @Test fun submittedClaimMatchesExistingSmsAndCreditsOnce() {
        check(BuildConfig.LOCAL_OPERATIONS)
        val args = InstrumentationRegistry.getArguments()
        val reference = args.getString("smsReference")
        org.junit.Assume.assumeNotNull(reference)
        checkNotNull(reference)
        val background = args.getString("smsBackground") == "true"
        val offline = args.getString("smsOffline") == "true"
        val session = AdminSession(ui.activity)
        val preferences = ui.activity.getSharedPreferences("settings", 0)
        try {
            preferences.edit().putBoolean("bangla", false).commit()
            ui.activityRule.scenario.recreate()
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"), 20, TimeUnit.SECONDS)
            ui.waitUntil(20_000) { session.owner }
            val uid = checkNotNull(session.uid)
            val setting = "sms-deposit-receiver-$uid"
            if (preferences.contains(setting)) {
                preferences.edit().remove(setting).commit()
                ui.activityRule.scenario.recreate()
            }
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("admin-nav-Settings").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("admin-nav-Settings").performClick()
            ui.onNodeWithText("Payment settings").performScrollTo().performClick()
            val enable = hasText("Enable sample receipt matching") and isEnabled()
            ui.waitUntil(20_000) { ui.onAllNodes(enable).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(enable).performScrollTo().performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("Turn off auto-approval").fetchSemanticsNodes().isNotEmpty() }

            if (background) {
                ui.runOnIdle { ui.activity.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME)) }
            }
            val operations = PlayerData(session)
            val wallet = session.db.document("wallets/$uid")
            val balance = Tasks.await(wallet.get(Source.SERVER), 10, TimeUnit.SECONDS).getLong("balance") ?: 0
            if (offline) Tasks.await(session.db.disableNetwork(), 10, TimeUnit.SECONDS)
            try {
                var submitted: String? = null
                ui.runOnIdle {
                    operations.operation("deposit", mapOf("amount" to 12345L, "sender" to "01700000000", "reference" to reference)) {
                        submitted = it
                    }
                }
                ui.waitUntil(20_000) { submitted != null }
                assertEquals("Deposit submitted for review. No funds have been credited.", submitted)
                if (offline) {
                    var retried: String? = null
                    ui.runOnIdle {
                        operations.operation("deposit", mapOf("amount" to 12345L, "sender" to "01700000000", "reference" to reference)) {
                            retried = it
                        }
                    }
                    ui.waitUntil(20_000) { retried != null }
                    assertEquals(submitted, retried)
                }
            } finally {
                if (offline) Tasks.await(session.db.enableNetwork(), 10, TimeUnit.SECONDS)
            }
            val claim = session.db.document("depositRequests/${uid}_$reference")
            ui.waitUntil(20_000) { Tasks.await(claim.get(Source.SERVER), 5, TimeUnit.SECONDS).getString("status") == "approved" }
            val ledger = session.db.document("ledger/deposit_${uid}_$reference")
            assertEquals(reference, Tasks.await(ledger.get(Source.SERVER), 5, TimeUnit.SECONDS).getString("reference"))
            assertEquals(balance + 12345L, Tasks.await(wallet.get(Source.SERVER), 5, TimeUnit.SECONDS).getLong("balance"))
            if (!offline && !background) ui.onNodeWithText("Turn off auto-approval").performScrollTo().performClick()
        } finally {
            session.uid?.let { preferences.edit().remove("sms-deposit-receiver-$it").apply() }
            session.auth.signOut()
            session.close()
        }
    }
}
