package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*

@Composable
internal fun ResultEditor(session:AdminSession,match:String) {
    fun t(text:String)=LocaleStrings.get(text)
    val operations=remember(match,session.uid){MatchOperations(session)}
    val settlement=remember(match,session.uid){WinningsSettlement(session)}
    var record by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var progress by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var entries by remember(match){mutableStateOf(BoundedFeedState())}
    var prizes by remember(match){mutableStateOf(BoundedFeedState())}
    var selected by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var draft by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var published by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var credited by remember(match){mutableStateOf(false)}
    var placement by remember(match){mutableStateOf("")}
    var kills by remember(match){mutableStateOf("0")}
    var award by remember(match){mutableStateOf("0.00")}
    var search by remember(match){mutableStateOf("")}
    var shown by remember(match){mutableStateOf(20)}
    var retry by remember(match){mutableStateOf(0)}
    var loadingMatch by remember(match){mutableStateOf(true)}
    var loadingResult by remember(match){mutableStateOf(false)}
    var flowBusy by remember(match){mutableStateOf(false)}
    var error by remember(match){mutableStateOf("")}
    var confirm by remember(match){mutableStateOf(false)}
    val fixed=record?.getBoolean("prizesEnabled")==true
    DisposableEffect(match,session.uid,retry) {
        var active=true;loadingMatch=true;error=""
        Tasks.whenAllSuccess<DocumentSnapshot>(session.db.document("matches/$match").get(Source.SERVER),session.db.document("matchSettlements/$match").get(Source.SERVER)).addOnCompleteListener {task->
            if(active){loadingMatch=false;if(task.isSuccessful){record=task.result[0];progress=task.result[1]}else error="Could not load the record. Reconnect and retry."}
        }
        val feed=BoundedFeed(session.db.collection("entries").whereEqualTo("match",match).whereEqualTo("status","confirmed"),automatic=true){entries=it}
        feed.start();onDispose{active=false;feed.close()}
    }
    DisposableEffect(match,session.uid,fixed,retry) {
        if(fixed){val feed=BoundedFeed(session.db.collection("matches/$match/prizes"),automatic=true){prizes=it};feed.start();onDispose{feed.close()}}
        else {prizes=BoundedFeedState(loading=false,cached=false);onDispose{}}
    }
    DisposableEffect(selected?.id,retry) {
        var active=true;draft=null;published=null;credited=false
        val entry=selected
        loadingResult=entry!=null
        if(entry!=null)session.db.document("resultDrafts/${entry.id}").get(Source.SERVER).addOnCompleteListener {task->
            if(active){
                if(!task.isSuccessful){loadingResult=false;error="Could not load the record. Reconnect and retry."}
                else {
                    draft=task.result;placement=task.result.getLong("placement")?.toString().orEmpty();kills=(task.result.getLong("kills")?:0).toString();award=java.math.BigDecimal.valueOf(task.result.getLong("awardPaisa")?:0,2).toPlainString()
                    val id=task.result.getString("publicId")
                    if(id==null)loadingResult=false else Tasks.whenAllSuccess<DocumentSnapshot>(session.db.document("results/$id").get(Source.SERVER),session.db.document("prizeCredits/${entry.id}").get(Source.SERVER)).addOnCompleteListener {loaded->
                        if(active){loadingResult=false;if(loaded.isSuccessful){published=loaded.result[0].takeIf{it.exists()};credited=loaded.result[1].exists()}else error="Could not load the record. Reconnect and retry."}
                    }
                }
            }
        }
        onDispose{active=false}
    }
    val busy=operations.busy||settlement.busy||flowBusy
    val loading=loadingMatch||loadingResult||(fixed&&(prizes.loading||prizes.cached))
    val review=record?.getString("status")=="review"
    val processed=progress?.getLong("processed")?:0
    val joined=record?.getLong("joined")?:0
    val calculated=runCatching{prizeAward(prizes.rows.find{it.getLong("rank")==placement.toLongOrNull()}?.getLong("amountPaisa")?:0,record?.getLong("perKillPaisa")?:0,kills.toLong())}.getOrNull()
    fun credit(result:DocumentSnapshot) {
        flowBusy=true
        settlement.credit(result,failed={flowBusy=false}){flowBusy=false;retry++}
    }
    fun publishAndCredit() {
        val saved=draft?:return
        flowBusy=true
        operations.publish(saved,failed={flowBusy=false}){
            session.db.document("results/${saved.getString("publicId")}").get(Source.SERVER).addOnCompleteListener {task->
                if(task.isSuccessful)credit(task.result)else{flowBusy=false;error="Could not load the record. Reconnect and retry."}
            }
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(t("Settled results")+": $processed / $joined",style=MaterialTheme.typography.titleMedium)
        if(record?.getString("status")=="settled")Text(t("Match settlement complete."))
        else if(!review&&!loadingMatch)Text(t("Move the match to results review first."),style=MaterialTheme.typography.bodySmall)
        if(loading||entries.loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(t(error),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
        if(operations.error.isNotBlank())Text(t(operations.error),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
        if(settlement.error.isNotBlank())Text(t(settlement.error),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
        if(entries.error||prizes.error)Text(t("Could not load the record. Reconnect and retry."),color=MaterialTheme.colorScheme.error)
        if(selected==null) {
            CompactSearch(search,{search=it;shown=20},t("Search participants"))
            val visible=entries.rows.filter{search.isBlank()||it.getString("username").orEmpty().contains(search.trim(),true)||it.getString("uid").orEmpty().contains(search.trim(),true)}.sortedBy{it.getString("username")}
            if(!entries.loading&&visible.isEmpty())Text(t("No confirmed participants found."))
            visible.take(shown).forEach {entry->OutlinedButton(onClick={selected=entry;error=""},enabled=!busy&&!entries.cached&&!entries.error,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()){Text(entry.getString("username").orEmpty());Text(t("Account reference")+": "+entry.getString("uid").orEmpty(),style=MaterialTheme.typography.labelSmall)}
            }}
            if(visible.size>shown)TextButton(onClick={shown+=20}){Text(t("Load more"))}
            Button(onClick={settlement.complete(match){retry++}},enabled=review&&!loading&&!busy&&processed==joined){Text(t("Complete settlement"))}
        } else {
            Text(selected!!.getString("username").orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text(t("Account reference")+": "+selected!!.getString("uid").orEmpty(),style=MaterialTheme.typography.bodySmall)
            (selected!!.get("roster") as? List<*>)?.filterIsInstance<String>()?.forEach{Text(it)}
            OutlinedTextField(placement,{placement=it},label={Text(t("Placement"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),enabled=review&&!loading&&!busy&&published==null)
            OutlinedTextField(kills,{kills=it},label={Text(t("Kills"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),enabled=review&&!loading&&!busy&&published==null)
            if(fixed)LabeledValue(t("Calculated award"),calculated?.let{money(it)}?:"—")
            else OutlinedTextField(award,{award=it},label={Text(t("Legacy award (taka)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),enabled=review&&!loading&&!busy&&published==null)
            if(published==null)Button(onClick={try{error="";operations.saveDraft(selected!!,placement.toLong(),kills.toLong(),if(fixed)null else awardPaisa(award),draft?.getLong("revision")?:0){retry++}}catch(_:Exception){error="Check placement, kills and award."}},enabled=review&&!loading&&!busy){Text(t("Save result draft"))}
            if(draft?.exists()==true)Text(t("Saved draft")+": #${draft!!.getLong("placement")} · ${draft!!.getLong("kills")} "+t("Kills")+" · "+money(draft!!.getLong("awardPaisa")?:0))
            if(credited)Text(t(if((published?.getLong("creditedPaisa")?:0)>0)"Prize credited to winnings." else "Zero-prize result settled."),color=MaterialTheme.colorScheme.primary)
            else if(draft?.exists()==true)Button(onClick={confirm=true},enabled=review&&!loading&&!busy){Text(t(if(published==null)"Review and publish" else "Review pending credit"))}
            TextButton(onClick={selected=null},enabled=!busy){Text(t("Choose another participant"))}
        }
        TextButton(onClick={retry++},enabled=!busy){Text(t("Reload record"))}
    }
    if(confirm&&draft!=null) {
        val outcome=published?:draft!!
        AlertDialog(onDismissRequest={confirm=false},title={Text(t("Publish and credit this result?"))},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(outcome.getString("username").orEmpty());Text(t("Account reference")+": "+selected?.getString("uid").orEmpty())
            Text(t("Placement")+": #${outcome.getLong("placement")} · "+t("Kills")+": ${outcome.getLong("kills")}")
            Text(t("Prize to winnings")+": "+money(outcome.getLong("awardPaisa")?:0))
            Text(t("Published placements and kills cannot be edited. Pending credits can be retried safely."))
        }},confirmButton={TextButton(onClick={confirm=false;if(published!=null)credit(published!!)else publishAndCredit()}){Text(t("Publish and credit prize"))}},dismissButton={TextButton(onClick={confirm=false}){Text(t("Cancel"))}})
    }
}
