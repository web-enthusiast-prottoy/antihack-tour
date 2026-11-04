package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Tasks
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun WalletHistory(session:AdminSession,deposits:List<DepositActivity>) {
    var records by remember(session.uid){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    val paths=if(BuildConfig.LOCAL_OPERATIONS)listOf("ledger","walletLedger")else listOf("walletLedger")
    var cursors by remember(session.uid){mutableStateOf<Map<String,DocumentSnapshot>>(emptyMap())}
    var remaining by remember(session.uid){mutableStateOf(paths.toSet())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var cached by remember{mutableStateOf(false)}
    var generation by remember{mutableStateOf(0)}
    val uid=session.uid
    fun load(reset:Boolean) {
        if(loading||uid==null)return
        val requested=if(reset)paths else paths.filter{it in remaining}
        if(requested.isEmpty())return
        loading=true;error=false
        val version=++generation
        val queries=requested.map{path->
            var query:Query=session.db.collection(path).whereEqualTo("uid",uid).orderBy("at",Query.Direction.DESCENDING)
            if(!reset)cursors[path]?.let{query=query.startAfter(it)}
            query.limit(21).get()
        }
        Tasks.whenAllSuccess<QuerySnapshot>(queries).addOnCompleteListener{task->
            if(generation==version&&session.uid==uid) {
                loading=false
                if(task.isSuccessful){
                    val pages=requested.zip(task.result)
                    records=((if(reset)emptyList()else records)+pages.flatMap{it.second.documents.take(20)}).distinctBy{it.reference.path}
                    cursors=(if(reset)emptyMap()else cursors)+pages.mapNotNull{(path,snap)->snap.documents.take(20).lastOrNull()?.let{path to it}}
                    remaining=((if(reset)paths.toSet()else remaining)-requested.toSet())+pages.filter{it.second.size()>20}.map{it.first}
                    cached=task.result.any{it.metadata.isFromCache||it.metadata.hasPendingWrites()}
                }else error=true
            }
        }
    }
    DisposableEffect(uid){load(true);onDispose{generation++}}
    val credited=records.mapNotNull{it.getString("request")?:it.getString("deposit")}.toSet()
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row {Text(LocaleStrings.get("Transaction history"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={load(true)},enabled=!loading){Text("Refresh")}}
        if(cached)Text(LocaleStrings.get("Saved history · reconnect to refresh"),style=MaterialTheme.typography.bodySmall)
        val rows=deposits.filter{it.id !in credited}.map{WalletRow("Deposit",it.amount,it.status,it.id,it.reference,it.createdAt)}+
            records.map{WalletRow(it.getString("type").orEmpty().replace('_',' '),it.getLong("delta")?:0,it.getString("status").orEmpty(),it.id,it.getString("reference").orEmpty(),it.getTimestamp("at"))}
        rows.sortedByDescending{it.time?.toDate()?.time?:Long.MAX_VALUE}.forEach{WalletTransactionRow(it.type,it.amount,it.status,it.id,it.reference,it.time)}
        if(!loading&&records.isEmpty()&&deposits.isEmpty())Text(LocaleStrings.get("No transactions yet"),Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error){Text(LocaleStrings.get("Could not load history. Reconnect and retry."),color=MaterialTheme.colorScheme.error);TextButton(onClick={load(cursors.isEmpty())}){Text("Retry")}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(remaining.isNotEmpty()&&!loading&&!error)OutlinedButton(onClick={load(false)},modifier=Modifier.fillMaxWidth()){Text("Load more")}
    }
}
private data class WalletRow(val type:String,val amount:Long,val status:String,val id:String,val reference:String,val time:com.google.firebase.Timestamp?)
@Composable
private fun WalletTransactionRow(type:String,amount:Long,status:String,id:String,reference:String,time:com.google.firebase.Timestamp?) {
    Column(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get(type.replaceFirstChar{it.uppercase()}),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Text((if(amount<0)"−" else if(status=="completed")"+" else "")+money(kotlin.math.abs(amount)),style=MaterialTheme.typography.titleMedium,color=if(status=="completed"&&amount>0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Text(LocaleStrings.get(status),style=MaterialTheme.typography.labelMedium)
        Text(time?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))?:LocaleStrings.get("Awaiting server timestamp"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(LocaleStrings.get("Transaction ID: ").trimEnd()+" "+id,style=MaterialTheme.typography.bodySmall)
        if(reference.isNotBlank())Text((if(type.equals("prize",true))LocaleStrings.get("Match ID")+":" else LocaleStrings.get("Payment reference: ").trimEnd())+" "+reference,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.25f))
}
