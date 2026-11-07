package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AdminCleanupUiTest {
    @get:Rule val ui = createAndroidComposeRule<AdminActivity>()
    private fun capture(name: String) {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val file = java.io.File(ui.activity.getExternalFilesDir(null), "$name.png")
        val screenshot = automation.takeScreenshot()
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "cp ${file.absolutePath} /data/local/tmp/$name.png"
        )).use { it.readBytes() }
    }
    private fun <T> await(task: com.google.android.gms.tasks.Task<T>) = Tasks.await(task, 20, TimeUnit.SECONDS)
    private fun owner(): AdminSession {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        return AdminSession(ui.activity).also {
            await(it.auth.signInWithEmailAndPassword("admin@arena.test", "ArenaLocal123!"))
            ui.waitUntil(20_000) { it.owner }
        }
    }

    @Test fun cleanupBlocksLinkedCategoryThenDeletesDraftAndUnusedCategory() {
        val session = owner()
        try {
            val category = session.db.collection("categories").document()
            val match = session.db.collection("matches").document()
            await(category.set(mapOf("name" to "Disposable cleanup sample", "order" to 0, "active" to true,
                "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to session.uid)))
            val matchData = mapOf("title" to "Disposable draft sample", "categoryId" to category.id,
                "description" to "Local test data", "image" to "solo", "mode" to "solo", "map" to "Bermuda",
                "startsAt" to Date(), "capacity" to 10L, "entryPaisa" to 0L, "prizePaisa" to 0L,
                "perKillPaisa" to 0L, "rules" to "Sample", "cancellationTerms" to "Sample",
                "status" to "draft", "revision" to 1L, "cancellationReason" to "",
                "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to session.uid)
            val batch = session.db.batch()
            batch.set(match, matchData)
            batch.set(match.collection("history").document("1"), mapOf("from" to "none", "to" to "draft", "reason" to "", "at" to FieldValue.serverTimestamp()))
            batch.set(session.db.document("auditRecords/match_${match.id}_1"), mapOf("action" to "saveMatch", "path" to match.path, "actor" to session.uid, "at" to FieldValue.serverTimestamp()))
            await(batch.commit())
            fun delete(path: String, id: String): Exception? {
                val result = CompletableFuture<Exception?>()
                ui.runOnUiThread { RecordDeletion(session).delete(path, id) { result.complete(it) } }
                return result.get(20, TimeUnit.SECONDS)
            }
            assertNotNull(delete("categories", category.id))
            await(category.update(mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to session.uid)))
            assertNotNull(delete("categories", category.id))
            assertTrue(await(category.get(Source.SERVER)).exists())
            assertNull(delete("matches", match.id))
            assertFalse(await(match.get(Source.SERVER)).exists())
            assertTrue(await(session.db.document("auditRecords/deleteMatch_${match.id}").get(Source.SERVER)).exists())
            var closed = false
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
                DeleteRecordButton(session, "categories", category.id, "Disposable cleanup sample") { closed = true }
            } } }
            ui.onNodeWithText("Delete category").performClick()
            capture("cleanup-delete-confirmation")
            ui.onNodeWithText("Cancel").performClick()
            assertTrue(await(category.get(Source.SERVER)).exists())
            ui.onNodeWithText("Delete category").performClick()
            ui.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            ui.waitUntil(20_000) { closed }
            assertFalse(await(category.get(Source.SERVER)).exists())
        } finally { session.auth.signOut(); session.close() }
    }

    @Test fun depositApprovalUsesSavedDetailsAndRequiresOneCheckbox() {
        val session = owner()
        try {
            val operations = PlayerData(session)
            val reference = "CLEANUP${System.currentTimeMillis()}"
            var submitted = false
            ui.runOnUiThread { operations.operation("deposit", mapOf("amount" to 12347L,
                "sender" to "01700000000", "reference" to reference)) { submitted = true } }
            ui.waitUntil(20_000) { submitted }
            val record = await(session.db.document("depositRequests/${session.uid}_$reference").get(Source.SERVER))
            val wallet = session.db.document("wallets/${session.uid}")
            val before = await(wallet.get(Source.SERVER)).getLong("balance") ?: 0L
            var closed = false
            ui.runOnUiThread { ui.activity.setContent { MaterialTheme { DepositReview(record, operations, false) { closed = true } } } }
            ui.onAllNodesWithText("Verified amount (taka)").assertCountEquals(0)
            ui.onAllNodesWithText("Verified transfer reference").assertCountEquals(0)
            ui.onNodeWithText("Approve deposit").assertIsNotEnabled()
            capture("cleanup-deposit-confirmation")
            ui.onNode(isToggleable()).performScrollTo().performClick()
            ui.onNodeWithText("Approve deposit").assertIsEnabled().performClick()
            ui.waitUntil(20_000) { closed }
            assertEquals("approved", await(record.reference.get(Source.SERVER)).getString("status"))
            assertEquals(before + 12347L, await(wallet.get(Source.SERVER)).getLong("balance"))
        } finally { session.auth.signOut(); session.close() }
    }
}
