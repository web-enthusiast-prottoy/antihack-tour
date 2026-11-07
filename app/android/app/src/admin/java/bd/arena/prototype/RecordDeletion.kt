package bd.arena.prototype

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.firestore.*

internal class RecordDeletion(private val session: AdminSession) {
    fun delete(path: String, id: String, done: (Exception?) -> Unit) {
        if (!session.owner || path !in listOf("categories", "matches")) {
            done(IllegalArgumentException("Owner access required.")); return
        }
        val ref = session.db.collection(path).document(id)
        ref.get(Source.SERVER).continueWithTask { loaded ->
            if (!loaded.isSuccessful) throw loaded.exception ?: IllegalStateException()
            val original = loaded.result
            require(original.exists()) { "This record no longer exists. Reload the list." }
            if (path == "categories") require(original.getBoolean("active") == false) {
                "Turn off Active and save this category before deleting it."
            }
            // An inactive category cannot receive new assignments; check every match, not just the visible page.
            val unlinked = if (path == "categories") {
                session.db.collection("matches").whereEqualTo("categoryId", id).limit(1)
                    .get(Source.SERVER).continueWith { query ->
                        if (!query.isSuccessful) throw query.exception ?: IllegalStateException()
                        require(query.result.isEmpty) {
                            "Move this category's matches to another category before deleting it."
                        }
                    }
            } else com.google.android.gms.tasks.Tasks.forResult(Unit)
            unlinked.continueWithTask { checked ->
                if (!checked.isSuccessful) throw checked.exception ?: IllegalStateException()
                session.db.runTransaction { tx ->
                    val current = tx.get(ref)
                    require(current.exists() && current.data == original.data) { "The record changed. Reload and try again." }
                    val action = if (path == "categories") "deleteCategory" else "deleteMatch"
                    tx.delete(ref)
                    tx.set(session.db.document("auditRecords/${action}_$id"), mapOf(
                        "action" to action, "path" to ref.path, "actor" to checkNotNull(session.uid),
                        "at" to FieldValue.serverTimestamp()))
                }
            }
        }.addOnCompleteListener { task -> done(if (task.isSuccessful) null else task.exception) }
    }
}

@Composable
internal fun DeleteRecordButton(session: AdminSession, path: String, id: String, title: String, deleted: () -> Unit) {
    if (!session.owner || id.isBlank()) return
    var confirming by remember(id) { mutableStateOf(false) }
    var busy by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf("") }
    val label = adminText(if (path == "categories") "Delete category" else "Delete match")
    TextButton(onClick = { error = ""; confirming = true }) { Text(label, color = MaterialTheme.colorScheme.error) }
    if (confirming) AlertDialog(
        onDismissRequest = { if (!busy) confirming = false },
        title = { Text(label) },
        text = { androidx.compose.foundation.layout.Column {
            Text(title)
            Text(adminText(if (path == "matches")
                "This permanently deletes the match in any status. Registration, payment and audit history are retained. Deleting does not refund entry fees."
                else "This permanently deletes the record. Audit history is retained."))
            if (error.isNotBlank()) Text(adminText(error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            RecordDeletion(session).delete(path, id) { failure ->
                busy = false
                if (failure == null) { confirming = false; deleted() }
                else error = (failure as? IllegalArgumentException)?.message
                    ?: "Could not delete. Check connection, owner access and deployed database permissions."
            }
        }) { Text(adminText(if (busy) "Deleting…" else "Delete")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirming = false }) { Text(adminText("Cancel")) } }
    )
}
