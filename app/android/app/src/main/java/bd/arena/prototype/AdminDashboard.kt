package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@Composable
internal fun AdminDashboard(session:AdminSession,open:(String)->Unit) {
    var counts by remember{mutableStateOf<Map<String,Long>>(emptyMap())}
    var failed by remember{mutableStateOf(false)}
    var retry by remember{mutableStateOf(0)}
    var activity by remember{mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    DisposableEffect(session.uid,retry) {
        var active=true
        failed=false;counts=emptyMap()
        val queries=linkedMapOf<String,Query>(
            "All matches" to session.db.collection("matches"),
            "Open matches" to session.db.collection("matches").whereEqualTo("status","open"),
            "Live matches" to session.db.collection("matches").whereEqualTo("status","live"),
            "Players" to session.db.collection("users"),
            "Pending deposits" to session.db.collection(if(BuildConfig.LOCAL_OPERATIONS)"depositRequests" else "walletDeposits").whereEqualTo("status","pending")
        )
        queries.forEach{(label,query)->query.count().get(AggregateSource.SERVER).addOnCompleteListener{task->if(active){if(task.isSuccessful)counts=counts+(label to task.result.count)else failed=true}}}
        val listener=session.db.collection("auditRecords").orderBy("at",Query.Direction.DESCENDING).limit(6).addSnapshotListener{snap,error->if(active){if(error!=null)failed=true;activity=snap?.documents?:emptyList()}}
        onDispose{active=false;listener.remove()}
    }
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(LocaleStrings.get("Operations overview"),style=MaterialTheme.typography.headlineSmall)
        Text(LocaleStrings.get("Current totals from the database"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("All matches","Open matches","Live matches","Players","Pending deposits").chunked(2).forEach{pair->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                pair.forEach{label->Card(onClick={open(when(label){"Players"->"users";"Pending deposits"->"depositRequests";else->"matches"})},modifier=Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(counts[label]?.toString()?:"—",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
                        Text(LocaleStrings.get(label),style=MaterialTheme.typography.labelLarge)
                    }
                }}
                if(pair.size==1)Spacer(Modifier.weight(1f))
            }
        }
        if(failed){Text(LocaleStrings.get("Some totals could not refresh. Reconnect and retry."),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}
        Text(LocaleStrings.get("Recent staff activity"),style=MaterialTheme.typography.titleLarge)
        activity.forEach{record->Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(record.getString("action").orEmpty(),style=MaterialTheme.typography.titleSmall)
            Text(record.getString("path").orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top=8.dp))
        }}
        if(activity.isEmpty())Text(LocaleStrings.get("No recent activity"),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
