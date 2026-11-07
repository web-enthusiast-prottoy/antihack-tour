package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.delay

@Composable
internal fun DepositVerification(session: AdminSession, id: String, until: Long, bangla: Boolean, done: (String) -> Unit) {
    fun t(en: String, bn: String) = if (bangla) bn else en
    var status by remember(id) { mutableStateOf("") }
    var expired by remember(id) { mutableStateOf(false) }
    val finished by rememberUpdatedState(done)
    DisposableEffect(session.uid, id) {
        val listener = session.db.collection(if (BuildConfig.LOCAL_OPERATIONS) "depositRequests" else "walletDeposits").document(id)
            .addSnapshotListener(MetadataChanges.INCLUDE) { record, failure ->
                if (failure == null && record != null && !record.metadata.isFromCache && !record.metadata.hasPendingWrites())
                    status = record.getString("status").orEmpty()
            }
        onDispose { listener.remove() }
    }
    LaunchedEffect(id, until) {
        delay((until - System.currentTimeMillis()).coerceIn(0, 30_000))
        expired = true
    }
    LaunchedEffect(status, expired) {
        val message = when {
            status == "approved" -> t("Deposit successful. Your wallet has been credited.", "টাকা জমা সফল হয়েছে। আপনার ওয়ালেটে টাকা যোগ হয়েছে।")
            status == "rejected" -> t("Deposit request was rejected. Check deposit history for the reason.", "জমার অনুরোধ প্রত্যাখ্যান হয়েছে। কারণ জানতে জমার ইতিহাস দেখুন।")
            expired -> t("Request saved. Awaiting admin approval. You can check its status in deposit history.", "অনুরোধ সংরক্ষিত হয়েছে। অ্যাডমিনের অনুমোদনের অপেক্ষায় আছে। জমার ইতিহাসে অবস্থা দেখতে পারবেন।")
            else -> null
        }
        if (message != null) finished(message + "\n" + id)
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(t("Verifying deposit…", "জমার তথ্য যাচাই হচ্ছে…"), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(t("Checking for a matching payment receipt. This may take up to 30 seconds. Unverified requests stay pending for admin approval.",
            "মিলে যাওয়া পেমেন্ট রসিদ খোঁজা হচ্ছে। সর্বোচ্চ ৩০ সেকেন্ড লাগতে পারে। যাচাই না হলে অনুরোধ অ্যাডমিনের অনুমোদনের জন্য অপেক্ষায় থাকবে।"))
    }
}
