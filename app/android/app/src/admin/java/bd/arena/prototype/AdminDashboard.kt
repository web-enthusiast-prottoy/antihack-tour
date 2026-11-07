package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun adminMatchTime(record: DocumentSnapshot): String =
    record.getTimestamp("startsAt")?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))
        ?.format(DateTimeFormatter.ofPattern("dd MMM · h:mm a")).orEmpty()

@Composable
internal fun AdminDashboard(session: AdminSession, open: (AdminPage) -> Unit, openMatch: (DocumentSnapshot) -> Unit) {
    var pending by remember { mutableStateOf(emptyMap<AdminPage, Boolean>()) }
    var upcoming by remember { mutableStateOf<List<DocumentSnapshot>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    DisposableEffect(session.uid, retry) {
        var active = true
        failed = false
        pending = emptyMap()
        upcoming = null
        val db = session.db
        val queues = mapOf(
            AdminPage.Deposits to db.collection(depositCollection).whereEqualTo("status", "pending"),
            AdminPage.Withdrawals to db.collection("withdrawals").whereIn("status", listOf("pending", "approved")),
            AdminPage.Support to db.collection("disputes").whereIn("status", listOf("open", "review"))
        )
        queues.forEach { (page, query) ->
            query.limit(1).get(Source.SERVER).addOnCompleteListener { task ->
                if (active) {
                    if (task.isSuccessful) pending = pending + (page to !task.result.isEmpty)
                    else failed = true
                }
            }
        }
        val schedule = db.collection("matches")
            .whereGreaterThanOrEqualTo("startsAt", Timestamp.now()).orderBy("startsAt")
        fun load(query: Query, found: List<DocumentSnapshot>) {
            query.limit(25).get(Source.SERVER).addOnCompleteListener { task ->
                if (!active) return@addOnCompleteListener
                if (!task.isSuccessful) { failed = true; return@addOnCompleteListener }
                val page = task.result.documents
                val matches = (found + page.filter { it.getString("status") in listOf("open", "closed") }).take(3)
                if (matches.size == 3 || page.size < 25) upcoming = matches
                else load(schedule.startAfter(page.last()), matches)
            }
        }
        load(schedule, emptyList())
        onDispose { active = false }
    }

    RegisterRefresh { retry++ }
    AdminLanding("Overview", showLogo = true) {
        if (BuildConfig.LOCAL_OPERATIONS) {
            Text(adminText("Local sample data"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AdminGroup("Needs review") {
            listOf(AdminPage.Deposits, AdminPage.Withdrawals, AdminPage.Support).forEachIndexed { index, page ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                AdminRow(adminText(page.title), status = if (pending[page] == true) adminText("Pending") else null) { open(page) }
            }
        }
        AdminGroup("Upcoming matches") {
            val matches = upcoming
            when {
                matches == null && !failed -> LinearProgressIndicator(Modifier.fillMaxWidth())
                matches?.isEmpty() == true -> Text(adminText("No upcoming matches"), Modifier.padding(12.dp))
                else -> matches.orEmpty().forEachIndexed { index, match ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    AdminRow(match.getString("title").orEmpty(), adminMatchTime(match), adminStatus(match.getString("status").orEmpty())) {
                        openMatch(match)
                    }
                }
            }
        }
        if (failed) {
            Text(adminText("Could not refresh overview"), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text(adminText("Retry")) }
        }
    }
}
