package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultEditor(session:AdminSession,match:String) {
    fun t(text:String)=LocaleStrings.get(text)
    val operations=remember(match,session.uid){MatchOperations(session)}
    val settlement=remember(match,session.uid){WinningsSettlement(session)}
    var record by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var progress by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var entries by remember(match){mutableStateOf(BoundedFeedState())}
    var prizes by remember(match){mutableStateOf(BoundedFeedState())}
    var results by remember(match){mutableStateOf(BoundedFeedState())}
    var position by rememberSaveable(match){mutableStateOf<Long?>(null)}
    var participant by rememberSaveable(match,position){mutableStateOf("")}
    var kills by rememberSaveable(match,position){mutableStateOf("0")}
    var otherResults by rememberSaveable(match){mutableStateOf(false)}
    var participantMenu by remember{mutableStateOf(false)}
    var search by rememberSaveable(match,position){mutableStateOf("")}
    var shown by remember(position,search){mutableStateOf(20)}
    var draft by remember(match,participant){mutableStateOf<DocumentSnapshot?>(null)}
    var published by remember(match,participant){mutableStateOf<DocumentSnapshot?>(null)}
    var retry by remember(match){mutableStateOf(0)}
    var loadingMatch by remember(match){mutableStateOf(true)}
    var loadingResult by remember(match){mutableStateOf(false)}
    var flowBusy by remember(match){mutableStateOf(false)}
    var error by remember(match){mutableStateOf("")}
    val fixed=record?.getBoolean("prizesEnabled")==true
    DisposableEffect(match,session.uid,retry) {
        var active=true;loadingMatch=true;error=""
        Tasks.whenAllSuccess<DocumentSnapshot>(session.db.document("matches/$match").get(Source.SERVER),session.db.document("matchSettlements/$match").get(Source.SERVER)).addOnCompleteListener {task->
            if(active){loadingMatch=false;if(task.isSuccessful){record=task.result[0];progress=task.result[1]}else error="Could not load the record. Reconnect and retry."}
        }
        val entrants=BoundedFeed(session.db.collection("entries").whereEqualTo("match",match).whereEqualTo("status","confirmed"),automatic=true){entries=it}
        val outcomes=BoundedFeed(session.db.collection("results").whereEqualTo("match",match),automatic=true){results=it}
        entrants.start();outcomes.start()
        onDispose{active=false;entrants.close();outcomes.close()}
    }
    DisposableEffect(match,session.uid,fixed,retry) {
        if(fixed){val feed=BoundedFeed(session.db.collection("matches/$match/prizes"),automatic=true){prizes=it};feed.start();onDispose{feed.close()}}
        else {prizes=BoundedFeedState(loading=false,cached=false);onDispose{}}
    }
    DisposableEffect(participant,retry) {
        var active=true;draft=null;published=null
        loadingResult=participant.isNotBlank()
        if(participant.isNotBlank())session.db.document("resultDrafts/$participant").get(Source.SERVER).addOnCompleteListener {task->
            if(active){
                if(!task.isSuccessful){loadingResult=false;error="Could not load the record. Reconnect and retry."}
                else {
                    draft=task.result
                    val id=task.result.getString("publicId")
                    if(id==null)loadingResult=false else session.db.document("results/$id").get(Source.SERVER).addOnCompleteListener {loaded->
                        if(active){loadingResult=false;if(loaded.isSuccessful)published=loaded.result.takeIf{it.exists()}else error="Could not load the record. Reconnect and retry."}
                    }
                }
            }
        }
        onDispose{active=false}
    }
    val busy=operations.busy||settlement.busy||flowBusy
    val feedFailed=entries.error||prizes.error||results.error
    val loading=loadingMatch||loadingResult||entries.loading||entries.cached||results.loading||results.cached||(fixed&&(prizes.loading||prizes.cached))
    val joined=record?.getLong("joined")?:0
    val processed=progress?.getLong("processed")?:0
    val prizeRows=prizes.rows.filter{(it.getLong("amountPaisa")?:0)>0}.sortedBy{it.getLong("rank")}
    val saved=results.rows.find{it.getLong("placement")==position}
    val selected=entries.rows.find{it.id==participant}
    val calculated=runCatching{prizeAward(prizes.rows.find{it.getLong("rank")==position}?.getLong("amountPaisa")?:0,record?.getLong("perKillPaisa")?:0,kills.toLong())}.getOrNull()
    fun credit(result:DocumentSnapshot) {
        flowBusy=true
        settlement.credit(result,failed={flowBusy=false}){flowBusy=false;position=null;retry++}
    }
    fun publishAndCredit(savedDraft:DocumentSnapshot) {
        operations.publish(savedDraft,failed={flowBusy=false}){
            session.db.document("results/${savedDraft.getString("publicId")}").get(Source.SERVER).addOnCompleteListener {task->
                if(task.isSuccessful)credit(task.result)else{flowBusy=false;error="Could not load the record. Reconnect and retry."}
            }
        }
    }
    fun open(rank:Long) {position=rank;participant="";kills="0";search="";error=""}
    @Composable fun PositionRow(rank:Long,amount:Long) {
        val result=results.rows.find{it.getLong("placement")==rank}
        OutlinedCard(onClick={open(rank)},enabled=!busy&&!loading&&!feedFailed,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                LabeledValue("#$rank",money(amount))
                Text(result?.let{it.getString("username").orEmpty()+" · "+t(if(it.getString("settlementStatus")=="credited")"Prize credited to winnings." else "Credit pending") }?:t("Set winner"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(t("Prize positions"),style=MaterialTheme.typography.titleLarge)
        Text(t("Choose a position, select the participant and enter their kills. Saving credits the prize to their winnings."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        listOf(error,operations.error,settlement.error).filter{it.isNotBlank()}.forEach{Text(t(it),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
        if(feedFailed)Text(t("Could not load the record. Reconnect and retry."),color=MaterialTheme.colorScheme.error)
        if(settlement.message.isNotBlank())Text(t(settlement.message),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.primary)
        prizeRows.forEach{row->PositionRow(row.getLong("rank")!!,row.getLong("amountPaisa")!!)}
        if(!loading&&prizeRows.isEmpty())Text(t("Add prize positions in the Prizes tab before saving winners."))
        if((record?.getLong("perKillPaisa")?:0)>0)LabeledValue(t("Per kill"),money(record?.getLong("perKillPaisa")?:0))
        if(fixed&&joined>0) {
            TextButton(onClick={otherResults=!otherResults},enabled=!busy){Text(t(if(otherResults)"Hide other results" else "Other participant results"))}
            if(otherResults)(1L..joined).filter{rank->prizeRows.none{it.getLong("rank")==rank}}.forEach{PositionRow(it,0)}
        }
        Text(t("Settled results")+": $processed / $joined",style=MaterialTheme.typography.bodySmall)
        if(record?.getString("status")=="settled")Text(t("Match settlement complete."))
        else if(record?.getString("status")=="review")Button(onClick={settlement.complete(match){retry++}},enabled=!loading&&!busy&&processed==joined){Text(t("Complete settlement"))}
        RegisterRefresh(!busy){retry++}
    }
    if(position!=null)ModalBottomSheet(onDismissRequest={if(!busy)position=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(t("Position")+" #$position",style=MaterialTheme.typography.titleLarge)
            if(saved!=null) {
                Text(saved.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
                LabeledValue(t("Kills"),(saved.getLong("kills")?:0).toString())
                LabeledValue(t("Prize to winnings"),money(saved.getLong("awardPaisa")?:0))
                if(saved.getString("settlementStatus")=="pending")Button(onClick={credit(saved)},enabled=!busy&&!loading,modifier=Modifier.fillMaxWidth()){Text(t("Retry credit"))}
                else Text(t("Prize credited to winnings."),color=MaterialTheme.colorScheme.primary)
                Text(t("Saved winners cannot be assigned again."),style=MaterialTheme.typography.bodySmall)
            } else {
                Box {
                    OutlinedButton(onClick={participantMenu=true},enabled=!busy&&!loading&&!feedFailed,modifier=Modifier.fillMaxWidth().testTag("winner-participant-picker")){Text(t("Set winner"))}
                    DropdownMenu(participantMenu,{participantMenu=false},modifier=Modifier.fillMaxWidth(0.9f).heightIn(max=360.dp)) {
                        CompactSearch(search,{search=it},t("Search participants"))
                        val available=entries.rows.filter{entry->(search.isBlank()||entry.getString("username").orEmpty().contains(search.trim(),true)||entry.getString("uid").orEmpty().contains(search.trim(),true))}.sortedBy{it.getString("username")}
                        available.take(shown).forEach{entry->DropdownMenuItem(text={WinnerParticipant(session,entry)},onClick={participant=entry.id;participantMenu=false;error=""})}
                        if(available.size>shown)DropdownMenuItem(text={Text(t("Load more"))},onClick={shown+=20})
                        if(available.isEmpty())Text(t("No confirmed participants found."),Modifier.padding(16.dp))
                    }
                }
                selected?.let{WinnerParticipant(session,it)}
                OutlinedTextField(kills,{kills=it.filter{c->c.isDigit()}.take(5)},label={Text(t("Kills"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),enabled=!busy,isError=kills.isNotBlank()&&kills.toLongOrNull() !in 0L..10000)
                LabeledValue(t("Prize to winnings"),calculated?.let{money(it)}?:"—")
                if(published!=null)Text(t("This participant already has a saved result. Select another participant."),color=MaterialTheme.colorScheme.error)
                if(!busy&&(error.isNotBlank()||operations.error.isNotBlank()||settlement.error.isNotBlank()))TextButton(onClick={retry++}){Text(t("Retry"))}
                Button(onClick={
                    val entry=selected?:return@Button
                    val rank=position?:return@Button
                    error=""
                    operations.saveDraft(entry,rank,kills.toLong(),null,draft?.getLong("revision")?:0){
                        flowBusy=true
                        session.db.document("resultDrafts/${entry.id}").get(Source.SERVER).addOnCompleteListener {task->
                            if(task.isSuccessful)publishAndCredit(task.result)else{flowBusy=false;error="Could not load the record. Reconnect and retry."}
                        }
                    }
                },enabled=fixed&&selected!=null&&published==null&&!loading&&!busy&&!feedFailed&&calculated!=null&&error.isBlank(),modifier=Modifier.fillMaxWidth()){Text(t(if(busy)"Saving…" else "Save and credit"))}
            }
            listOf(error,operations.error,settlement.error).filter{it.isNotBlank()}.forEach{Text(t(it),color=MaterialTheme.colorScheme.error)}
            if(busy||loadingResult)LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick={position=null},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(t("Close"))}
        }
    }
}

@Composable
private fun WinnerParticipant(session:AdminSession,entry:DocumentSnapshot) {
    val uid=entry.getString("uid").orEmpty()
    var gameId by remember(uid){mutableStateOf<String?>(null)}
    DisposableEffect(uid) {
        var active=true
        session.db.document("users/$uid").get(Source.SERVER).addOnCompleteListener{task->if(active)gameId=if(task.isSuccessful)task.result.getString("ffUid").orEmpty().ifBlank{"—"} else LocaleStrings.get("Unavailable")}
        onDispose{active=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(entry.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
        Text("FF UID: "+(gameId?:"…"),style=MaterialTheme.typography.bodySmall)
        Text("UUID: $uid",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
