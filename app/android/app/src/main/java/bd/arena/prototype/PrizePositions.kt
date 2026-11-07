package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

internal fun updatedPrizeTotal(enabled:Boolean,pool:Long,oldAmount:Long,newAmount:Long):Long {
    require(newAmount in 0..9007199254740991L){"Enter a valid prize amount."}
    return Math.addExact(Math.subtractExact(if(enabled)pool else 0,oldAmount),newAmount).also {
        require(it in 0..9007199254740991L){"Prize total is too large."}
    }
}

@Composable
internal fun PrizePanel(session:AdminSession,match:String,editable:Boolean=false,onSaved:(Long,Long)->Unit={_,_->}) {
    fun t(text:String)=LocaleStrings.get(text)
    val operations=remember(session.uid,match){MatchOperations(session)}
    var record by remember(match,session.uid){mutableStateOf<DocumentSnapshot?>(null)}
    var cached by remember(match){mutableStateOf(true)}
    var failed by remember(match){mutableStateOf(false)}
    var retry by remember(match){mutableStateOf(0)}
    var rows by remember(match){mutableStateOf(BoundedFeedState())}
    var rank by remember(match){mutableStateOf("")}
    var amount by remember(match){mutableStateOf("")}
    var expected by remember(match){mutableStateOf<Long?>(null)}
    var rankError by remember(match){mutableStateOf("")}
    var amountError by remember(match){mutableStateOf("")}
    var confirm by remember(match){mutableStateOf(false)}
    DisposableEffect(session.uid,match,retry) {
        failed=false
        val listener=session.db.document("matches/$match").addSnapshotListener(MetadataChanges.INCLUDE){snapshot,failure->
            failed=failure!=null
            if(snapshot!=null){record=snapshot;cached=snapshot.metadata.isFromCache||snapshot.metadata.hasPendingWrites()}
        }
        onDispose{listener.remove()}
    }
    val enabled=record?.getBoolean("prizesEnabled")==true
    DisposableEffect(session.uid,match,enabled,retry) {
        if(enabled) {
            val feed=BoundedFeed(session.db.collection("matches/$match/prizes"),automatic=true){rows=it}
            feed.start();onDispose{feed.close()}
        } else {rows=BoundedFeedState(loading=false,cached=false);onDispose{}}
    }
    val unlocked=editable&&session.allowed&&!cached&&!failed&&record?.exists()==true
    fun save(value:Long) {
        val position=rank.toLongOrNull()
        if(position==null||position !in 1..(record?.getLong("capacity")?:0)){rankError=t("Position must be within the saved match capacity.");return}
        val revision=expected?:record?.getLong("revision")?:return
        rankError="";amountError=""
        operations.savePrizePosition(match,position,value,revision){rev,total->rank="";amount="";expected=null;onSaved(rev,total)}
    }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(t("Prize distribution"),style=MaterialTheme.typography.titleLarge)
            if(record==null&&!failed||rows.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(cached||enabled&&rows.cached)Text(t("Saved prizes · reconnect to confirm"),style=MaterialTheme.typography.bodySmall)
            if(enabled) {
                rows.rows.filter{(it.getLong("amountPaisa")?:0)>0}.sortedBy{it.getLong("rank")}.forEach {row->
                    val position=row.getLong("rank")?:0
                    val value=row.getLong("amountPaisa")?:0
                    if(editable)OutlinedButton(onClick={rank=position.toString();amount=java.math.BigDecimal.valueOf(value,2).toPlainString();expected=record?.getLong("revision");rankError="";amountError=""},enabled=unlocked&&!operations.busy,modifier=Modifier.fillMaxWidth()){Text("#$position · "+money(value))}
                    else LabeledValue("#$position",money(value))
                }
                if(!rows.loading&&!rows.error&&rows.rows.none{(it.getLong("amountPaisa")?:0)>0})Text(t("No placement prizes."))
            } else if(record!=null)Text(t("Prize breakdown not published."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if((record?.getLong("perKillPaisa")?:0)>0)LabeledValue(t("Per kill"),money(record?.getLong("perKillPaisa")?:0))
            if(editable) {
                LabeledValue(t(if(record?.getBoolean("independentPrizeTotal")==true)"Total prize (taka)" else if(enabled)"Placement total" else "Prize pool"),money(record?.getLong("prizePaisa")?:0))
                Text("Changes apply to future result saves. Already credited awards stay unchanged.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(rank,{if(expected==null)expected=record?.getLong("revision");rank=it;rankError=""},label={Text(t("Position"))},isError=rankError.isNotBlank(),supportingText=if(rankError.isNotBlank())({Text(rankError)})else null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=unlocked&&!operations.busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(amount,{if(expected==null)expected=record?.getLong("revision");amount=it;amountError=""},label={Text(t("Prize (taka)"))},isError=amountError.isNotBlank(),supportingText=if(amountError.isNotBlank())({Text(amountError)})else null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=unlocked&&!operations.busy,modifier=Modifier.fillMaxWidth())
                if(operations.error.isNotBlank())Text(t(operations.error),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
                if(operations.message.isNotBlank())Text(t(operations.message),style=MaterialTheme.typography.bodySmall)
                Button(onClick={
                    try {awardPaisa(amount);if(!enabled&&record?.getBoolean("independentPrizeTotal")!=true&&(record?.getLong("prizePaisa")?:0)>0)confirm=true else save(awardPaisa(amount))}
                    catch(_:Exception){amountError=t("Enter valid taka amounts with up to two decimals.")}
                },enabled=unlocked&&!operations.busy,modifier=Modifier.fillMaxWidth()){Text(t(if(operations.busy)"Saving…" else "Save prize"))}
                if(rank.isNotBlank())TextButton(onClick={save(0)},enabled=enabled&&unlocked&&!operations.busy){Text(t("Remove prize"))}
                Text(t("Prize positions save separately from match details."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(failed||rows.error)Text(t("Could not load prizes."),color=MaterialTheme.colorScheme.error)
            if(failed||rows.error||editable)RegisterRefresh(!operations.busy){retry++;expected=null;rank="";amount="";rankError="";amountError=""}
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(t("Start a prize breakdown?"))},text={Text(t("This replaces the unallocated prize pool. The total starts with this position and updates as you add prizes."))},confirmButton={TextButton(onClick={confirm=false;save(awardPaisa(amount))}){Text(t("Save prize"))}},dismissButton={TextButton(onClick={confirm=false}){Text(t("Cancel"))}})
}
