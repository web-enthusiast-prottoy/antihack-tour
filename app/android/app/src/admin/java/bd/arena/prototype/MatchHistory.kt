package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@Composable
internal fun MatchHistory(session:AdminSession,match:String) {
    var rows by remember(match){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    var cursor by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var page by remember(match){mutableStateOf(0)}
    var more by remember(match){mutableStateOf(false)}
    var error by remember(match){mutableStateOf(false)}
    var loading by remember(match){mutableStateOf(true)}
    DisposableEffect(match,page,session.uid) {
        var active=true;loading=true;error=false
        var query:Query=session.db.collection("matches/$match/history").orderBy("at",Query.Direction.DESCENDING)
        cursor?.let{query=query.startAfter(it)}
        query.limit(21).get(Source.SERVER).addOnCompleteListener {task -> if(active){loading=false;if(task.isSuccessful){val result=task.result.documents.take(20);rows=(rows+result).distinctBy{it.id};cursor=result.lastOrNull()?:cursor;more=task.result.size()>20}else error=true}}
        onDispose{active=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(LocaleStrings.get("Match history"),style=MaterialTheme.typography.titleLarge)
        rows.forEach {row -> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(LocaleStrings.get(row.getString("from").orEmpty())+" → "+LocaleStrings.get(row.getString("to").orEmpty()))
            if(!row.getString("reason").isNullOrBlank())Text(row.getString("reason").orEmpty())
            Text(row.getTimestamp("at")?.toDate()?.toInstant()?.atZone(java.time.ZoneId.of("Asia/Dhaka"))?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a")).orEmpty(),style=MaterialTheme.typography.bodySmall)
        }}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(LocaleStrings.get("Could not load match history. Reconnect and retry."),color=MaterialTheme.colorScheme.error)
        if(!loading&&!error&&rows.isEmpty())Text(LocaleStrings.get("No recorded changes yet."))
        if(more||error)TextButton(onClick={page++},enabled=!loading){Text(LocaleStrings.get(if(error)"Retry" else "Load more"))}
        RegisterRefresh(!loading){rows=emptyList();cursor=null;page++}
    }
}
