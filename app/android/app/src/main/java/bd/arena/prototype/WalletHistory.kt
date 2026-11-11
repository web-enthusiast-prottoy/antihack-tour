package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Tasks
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletHistory(session:AdminSession,deposits:List<DepositActivity>) {
    var records by remember(session.uid){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    val paths=if(BuildConfig.LOCAL_OPERATIONS)listOf("ledger","walletLedger")else listOf("walletLedger")
    val historyPaths=paths+"withdrawals"
    var cursors by remember(session.uid){mutableStateOf<Map<String,DocumentSnapshot>>(emptyMap())}
    var remaining by remember(session.uid){mutableStateOf(historyPaths.toSet())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var cached by remember{mutableStateOf(false)}
    var generation by remember{mutableStateOf(0)}
    val uid=session.uid
    fun load(reset:Boolean) {
        if(loading||uid==null)return
        val requested=if(reset)historyPaths else historyPaths.filter{it in remaining}
        if(requested.isEmpty())return
        loading=true;error=false
        val version=++generation
        val queries=requested.map{path->
            var query:Query=session.db.collection(path).whereEqualTo("uid",uid).orderBy(if(path=="withdrawals")"createdAt" else "at",Query.Direction.DESCENDING)
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
                    remaining=((if(reset)historyPaths.toSet()else remaining)-requested.toSet())+pages.filter{it.second.size()>20}.map{it.first}
                    cached=task.result.any{it.metadata.isFromCache||it.metadata.hasPendingWrites()}
                }else error=true
            }
        }
    }
    DisposableEffect(uid){load(true);onDispose{generation++}}
    val withdrawals=records.filter{it.reference.parent.id=="withdrawals"}
    val withdrawalIds=withdrawals.map{it.id}.toSet()
    val ledger=records.filter{it.reference.parent.id!="withdrawals"&&it.getString("withdrawal") !in withdrawalIds}
    val credited=ledger.mapNotNull{it.getString("request")?:it.getString("deposit")}.toSet()
    val rows=deposits.filter{it.id !in credited}.map{WalletRow("Deposit",it.amount,it.status,it.id,it.reference,it.createdAt,source=(if(BuildConfig.LOCAL_OPERATIONS)"depositRequests/" else "walletDeposits/")+it.id)}+
            ledger.map{WalletRow(it.getString("type").orEmpty().replace('_',' '),it.getLong("delta")?:0,it.getString("status").orEmpty(),it.id,it.getString("reference").orEmpty(),it.getTimestamp("at"),source=it.reference.path)}+
            withdrawals.map{WalletRow("Withdrawal",0,it.getString("status").orEmpty(),it.id,"",
                it.getTimestamp(if(it.getString("status")=="sent")"decidedAt" else "createdAt")?:it.getTimestamp("createdAt"),it,source=it.reference.path)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var filter by rememberSaveable{mutableStateOf("All activity")}
    fun key(row:WalletRow)=row.source
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        WalletActivityHeader(filter,loading,choose={filter=it},refresh={load(true)})
        if(cached)Text(LocaleStrings.get("Saved history · reconnect to refresh"),style=MaterialTheme.typography.bodySmall)
        val visible=rows.filter { walletActivityMatches(it.type,filter) }
        visible.sortedByDescending{it.time?.toDate()?.time?:Long.MAX_VALUE}.forEach { row ->
            WalletActivityItem(ActivitySummary(row.id,activityTitle(row.type),activityAmount(row),row.status,walletTime(row.time),row.withdrawal==null&&row.amount>0&&row.status=="completed",key=row.source)){selected=key(row)}
        }
        if(visible.isEmpty()&&rows.isNotEmpty())Text(LocaleStrings.get("No loaded activity matches this filter."),style=MaterialTheme.typography.bodySmall)
        if(!loading&&!error&&rows.isEmpty())Text(LocaleStrings.get("No transactions yet"),Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error){Text(LocaleStrings.get("Could not load history. Reconnect and retry."),color=MaterialTheme.colorScheme.error);TextButton(onClick={load(cursors.isEmpty())}){Text(LocaleStrings.get("Retry"))}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(remaining.isNotEmpty()&&!loading&&!error)OutlinedButton(onClick={load(false)},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Load more"))}
    }
    rows.find{key(it)==selected}?.let {row->ModalBottomSheet(onDismissRequest={selected=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(row.withdrawal!=null) {
                Text(LocaleStrings.get("Transaction details"),style=MaterialTheme.typography.titleLarge)
                Text(activityAmount(row),style=MaterialTheme.typography.headlineLarge)
                Text(LocaleStrings.get(row.status),style=MaterialTheme.typography.labelLarge)
                WithdrawalDetails(row.withdrawal,compact=true)
            } else WalletActivityDetails(ActivitySummary(row.id,activityTitle(row.type),activityAmount(row),row.status,walletTime(row.time,true),row.amount>0&&row.status=="completed",key=row.source,reference=row.reference,
                referenceLabel=if(row.type.equals("prize",true))"Match ID:" else "Payment reference: "))
        }
    }}
}
private data class WalletRow(val type:String,val amount:Long,val status:String,val id:String,val reference:String,val time:com.google.firebase.Timestamp?,val withdrawal:DocumentSnapshot?=null,val source:String=type+":"+id)
private fun walletTime(time:com.google.firebase.Timestamp?,full:Boolean=false)=time?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(DateTimeFormatter.ofPattern(if(full)"dd MMM yyyy · h:mm a" else "dd MMM · h:mm a"))?:LocaleStrings.get("Awaiting server timestamp")
private fun activityAmount(row:WalletRow):String {
    row.withdrawal?.let{return (if(row.status=="sent")"−" else "")+compactMoney(it.getLong(if(row.status=="sent")"totalPaisa" else "amountPaisa")?:0)}
    return (if(row.amount<0)"−" else if(row.status=="completed")"+" else "")+compactMoney(kotlin.math.abs(row.amount))
}
