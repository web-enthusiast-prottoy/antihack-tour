package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun ResultEditor(session:AdminSession,match:String) {
    fun t(text:String)=LocaleStrings.get(text)
    val rewards=remember(match,session.uid){ParticipantRewards(session)}
    val legacy=remember(match,session.uid){WinningsSettlement(session)}
    var record by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var progress by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    var feeds by remember(match){mutableStateOf(mapOf<String,BoundedFeedState>())}
    var profiles by remember(match){mutableStateOf(mapOf<String,DocumentSnapshot>())}
    var profileError by remember(match){mutableStateOf(false)}
    var loadingMatch by remember(match){mutableStateOf(true)}
    var retry by remember(match){mutableStateOf(0)}
    var error by remember(match){mutableStateOf("")}
    var tab by rememberSaveable(match){mutableStateOf("winners")}
    var search by rememberSaveable(match){mutableStateOf("")}
    var filter by rememberSaveable(match){mutableStateOf("All")}
    var values by rememberSaveable(match){mutableStateOf(mapOf<String,String>())}
    var revisions by rememberSaveable(match){mutableStateOf(mapOf<String,Long>())}
    var expanded by rememberSaveable(match){mutableStateOf(listOf<String>())}
    var position by rememberSaveable(match){mutableStateOf<Long?>(null)}
    var participant by rememberSaveable(match){mutableStateOf("")}
    var picker by remember{mutableStateOf(false)}
    var winnerSearch by rememberSaveable(match){mutableStateOf("")}
    var review by remember(match){mutableStateOf<List<ReviewedKills>?>(null)}
    var reason by rememberSaveable(match){mutableStateOf("")}
    val focus=LocalFocusManager.current
    DisposableEffect(match,session.uid,retry) {
        var active=true;loadingMatch=true;error=""
        Tasks.whenAllSuccess<DocumentSnapshot>(session.db.document("matches/$match").get(Source.SERVER),session.db.document("matchSettlements/$match").get(Source.SERVER)).addOnCompleteListener { task ->
            if(active){loadingMatch=false;if(task.isSuccessful){record=task.result[0];progress=task.result[1]}else error="Could not load the record. Reconnect and retry."}
        }
        val queries=mapOf(
            "entries" to session.db.collection("entries").whereEqualTo("match",match).whereEqualTo("status","confirmed"),
            "results" to session.db.collection("results").whereEqualTo("match",match),
            "links" to session.db.collection("resultLinks").whereEqualTo("match",match),
            "drafts" to session.db.collection("killDrafts").whereEqualTo("match",match),
            "legacy" to session.db.collection("prizeCredits").whereEqualTo("match",match),
            "prizes" to session.db.collection("matches/$match/prizes")
        )
        feeds=queries.mapValues{BoundedFeedState()}
        val subscriptions=queries.map{(key,query)->BoundedFeed(query,automatic=true){if(active)feeds=feeds+(key to it)}}
        subscriptions.forEach{it.start()}
        onDispose{active=false;subscriptions.forEach{it.close()}}
    }
    val entries=feeds["entries"]?.rows.orEmpty()
    DisposableEffect(entries.map{it.id},retry) {
        var active=true;profileError=false
        Tasks.whenAllSuccess<DocumentSnapshot>(entries.map{session.db.document("users/${it.getString("uid")}").get(Source.SERVER)}).addOnCompleteListener{task->
            if(active){profileError=!task.isSuccessful;if(task.isSuccessful)profiles=task.result.associateBy{it.id}}
        }
        onDispose{active=false}
    }
    val results=feeds["results"]?.rows.orEmpty()
    val drafts=feeds["drafts"]?.rows.orEmpty().associateBy{it.id}
    val oldCredits=feeds["legacy"]?.rows.orEmpty().associateBy{it.id}
    val links=feeds["links"]?.rows.orEmpty().associateBy{it.getString("entry")}
    val prizeRows=feeds["prizes"]?.rows.orEmpty().filter{(it.getLong("amountPaisa")?:0)>0}.sortedBy{it.getLong("rank")}
    val busy=rewards.busy||legacy.busy
    val loading=loadingMatch||feeds.size!=6||feeds.values.any{it.loading||it.cached}
    val failed=feeds.values.any{it.error}||error.isNotBlank()
    val ready=!busy&&!loading&&!failed
    val rate=record?.getLong("perKillPaisa")?:0
    fun result(entry:DocumentSnapshot)=results.find{it.id==(drafts[entry.id]?.getString("publicId")?:links[entry.id]?.getString("result")?:oldCredits[entry.id]?.getString("result"))}
    fun finalized(entry:DocumentSnapshot)=result(entry)?.getBoolean("killFinalized")==true
    fun names(entry:DocumentSnapshot)=(entry.get("roster") as? List<*>)?.filterIsInstance<String>().orEmpty().ifEmpty{listOf(entry.getString("username").orEmpty())}
    fun counts(entry:DocumentSnapshot):List<String> {
        val draft=drafts[entry.id]
        val saved=(draft?.get("memberKills") as? List<*>)?.filterIsInstance<Number>().orEmpty()
        val aggregate=(oldCredits[entry.id]?.getLong("kills")?:0)>0&&saved.isEmpty()
        val size=if(aggregate)1 else names(entry).size
        return (0 until size).map{index->if(finalized(entry)) (saved.getOrNull(index)?.toLong()?:if(index==0)result(entry)?.getLong("kills")?:0 else 0).toString() else values["${entry.id}:$index"]?:saved.getOrNull(index)?.toLong()?.toString()?:if(index==0)(draft?.getLong("kills")?:oldCredits[entry.id]?.getLong("kills")?:0).toString()else "0"}
    }
    val pending=entries.filterNot{finalized(it)}
    val legacyPending=entries.filter{result(it)!=null&&result(it)?.getLong("rewardVersion")!=2L&&oldCredits[it.id]==null}
    val valid=pending.all{entry->runCatching{participantKills(counts(entry))>=(oldCredits[entry.id]?.getLong("kills")?:0)}.getOrDefault(false)}
    val totalReward=runCatching{pending.sumOf{entry->prizeAward(0,rate,participantKills(counts(entry)))-((oldCredits[entry.id]?.getLong("amountPaisa")?:0)-(oldCredits[entry.id]?.getLong("placementPaisa")?:0))}}.getOrNull()
    val shown=entries.filter{entry->
        val term=search.trim()
        (term.isBlank()||entry.getString("username").orEmpty().contains(term,true)||profiles[entry.getString("uid")]?.getString("ffUid").orEmpty().contains(term,true)||entry.getString("uid").orEmpty().contains(term,true))&&
            (filter=="All"||filter=="Credited"&&finalized(entry)||filter=="Pending"&&!finalized(entry))
    }.sortedWith(compareBy({it.getString("username").orEmpty().lowercase()},{it.id}))
    fun save(showReview:Boolean) {
        focus.clearFocus()
        val inputs=pending.map{KillDraftInput(it,counts(it),revisions[it.id]?:drafts[it.id]?.getLong("revision")?:0)}
        rewards.saveDrafts(inputs,rate,saved={row->revisions=revisions+(row.entry.id to row.revision)}){saved->
            revisions=revisions+saved.associate{it.entry.id to it.revision}
            if(showReview)review=saved
        }
    }
    @Composable fun Identity(entry:DocumentSnapshot) {
        Text(entry.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
        Text("Free Fire UID: "+(profiles[entry.getString("uid")]?.getString("ffUid").orEmpty().ifBlank{"—"}),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t("Account ID")+": "+entry.getString("uid").orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FilterChip(tab=="winners",{tab="winners"},enabled=!busy,label={Text(t("Winners"))})
            FilterChip(tab=="kills",{tab="kills"},enabled=!busy,label={Text(t("Player kills"))})
        }
        if(loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        val feedback=listOf(error,rewards.error,legacy.error,if(feeds.values.any{it.error})"Could not load the record. Reconnect and retry." else "").filter{it.isNotBlank()}
        if(feedback.isNotEmpty()) {
            Text(t(feedback.first()),color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            TextButton(onClick={retry++},enabled=!busy){Text(t("Refresh"))}
            if(rewards.error.contains("changed"))TextButton(onClick={values=emptyMap();revisions=emptyMap();review=null;retry++},enabled=!busy){Text(t("Reload saved draft"))}
        }
        if(rewards.message.isNotBlank())Text(t(rewards.message),style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        if(busy)Text("${rewards.completed} / ${rewards.total}",style=MaterialTheme.typography.bodySmall)
        if(tab=="winners")LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("winner-positions"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text(t("Prize positions"),style=MaterialTheme.typography.titleLarge);Text(t("Select winners here. Enter everyone’s kill rewards in Player kills."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(prizeRows,key={it.id}){prize->
                val rank=prize.getLong("rank")!!
                val saved=results.find{it.getLong("placement")==rank}
                OutlinedCard(onClick={position=rank;participant="";winnerSearch=""},enabled=ready,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    LabeledValue("#$rank",money(prize.getLong("amountPaisa")?:0))
                    Text(saved?.getString("username")?:t("Set winner"))
                    if(saved!=null)Text(t(if(saved.getString("settlementStatus")=="pending")"Credit pending" else "Placement prize credited."),style=MaterialTheme.typography.bodySmall)
                }}
            }
            if(prizeRows.isEmpty()&&!loading)item{Text(t("Add prize positions in the Prizes tab before saving winners."))}
            item{Text(t("Crediting rewards does not change match status."),style=MaterialTheme.typography.bodySmall);Text(t("Settled results")+": ${progress?.getLong("processed")?:0} / ${record?.getLong("joined")?:0}")}
            if(record?.getString("status")=="review")item{Button(onClick={legacy.complete(match){retry++}},enabled=ready&&(progress?.getLong("processed")?:0)==(record?.getLong("joined")?:0)&&prizeRows.all{prize->results.any{it.getLong("placement")==prize.getLong("rank")&&it.getString("settlementStatus")!="pending"}}){Text(t("Complete settlement"))}}
            if(record?.getString("status")=="settled")item{Text(t("Match settlement complete."))}
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("player-kills-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("Per kill")+": "+money(rate),style=MaterialTheme.typography.titleMedium)
                    Text(t("Unset kills count as 0."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CompactSearch(search,{search=it},t("Search game name or Free Fire UID"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("All","Pending","Credited").forEach{choice->FilterChip(filter==choice,{filter=choice},label={Text(t(choice))})}}
                    if(profileError)Text(t("Some game IDs could not load. Refresh to try again."),style=MaterialTheme.typography.bodySmall)
                    Text(t("Finalized kill results")+": ${entries.count{finalized(it)}} / ${entries.size}",style=MaterialTheme.typography.bodySmall)
                } }
                items(shown,key={it.id}){entry->
                    val saved=result(entry);val final=finalized(entry);val input=counts(entry)
                    val kills=if(final)saved?.getLong("kills")?:0 else runCatching{participantKills(input)}.getOrNull()
                    val previous=oldCredits[entry.id]?.getLong("kills")?:0
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Identity(entry)
                        saved?.getLong("placement")?.takeIf{it>0}?.let{Text(t("Position")+" #$it",color=MaterialTheme.colorScheme.primary)}
                        if(previous>0)Text(t("Previously credited kills")+": $previous",style=MaterialTheme.typography.bodySmall)
                        if(input.size>1)TextButton(onClick={expanded=if(entry.id in expanded)expanded-entry.id else expanded+entry.id}){Text(t(if(entry.id in expanded)"Hide team players" else "Enter team player kills"))}
                        if(input.size==1||entry.id in expanded)input.forEachIndexed{index,value->
                            val label=if(input.size==1)t(if(names(entry).size>1)"Team kills" else "Kills") else names(entry)[index]+" · "+t("Kills")
                            OutlinedTextField(value,{next->
                                if(entry.id !in revisions)revisions=revisions+(entry.id to (drafts[entry.id]?.getLong("revision")?:0))
                                values=values+("${entry.id}:$index" to next.take(12))
                            },label={Text(label)},placeholder={Text("0")},singleLine=true,enabled=ready&&!final,
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Next)}),
                                isError=kills==null||kills<previous,modifier=Modifier.fillMaxWidth().testTag("kills-${entry.id}-$index").semantics{contentDescription=entry.getString("username").orEmpty()+" · "+label})
                        }
                        if(kills==null||kills<previous)Text(t(if(kills==null)"Enter whole kill counts from 0 to 10000." else "Previously credited kills cannot be reduced."),color=MaterialTheme.colorScheme.error)
                        LabeledValue(t(if(final)"Kill reward credited" else "Kill reward"),kills?.let{runCatching{money(prizeAward(0,rate,it))}.getOrNull()}?:"—")
                        if(names(entry).size>1){Text(t("Team kills")+": "+(kills?.toString()?:"—"));Text(t("Paid to registering account."),style=MaterialTheme.typography.bodySmall)}
                        Text(t(if(final)"Kill rewards finalized." else "Pending"),style=MaterialTheme.typography.bodySmall)
                        if(entry in legacyPending)OutlinedButton(onClick={saved?.let{legacy.credit(it){retry++}}},enabled=ready){Text(t("Retry credit"))}
                        HorizontalDivider()
                    }
                }
                if(shown.isEmpty()&&!loading)item{Text(t("No confirmed participants found."))}
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={save(false)},enabled=ready&&pending.isNotEmpty()&&valid&&totalReward!=null&&totalReward>=0,modifier=Modifier.fillMaxWidth()){Text(t("Save draft"))}
                Button(onClick={save(true)},enabled=ready&&pending.isNotEmpty()&&valid&&legacyPending.isEmpty()&&totalReward!=null&&totalReward>=0,modifier=Modifier.fillMaxWidth().testTag("review-kill-credits")){Text(t("Review kill credits")+" · "+(totalReward?.let{money(it)}?:"—"))}
            }
        }
        RegisterRefresh(!busy){retry++}
    }
    position?.let{rank->
        val saved=results.find{it.getLong("placement")==rank}
        ModalBottomSheet(onDismissRequest={if(!busy)position=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(t("Position")+" #$rank",style=MaterialTheme.typography.titleLarge)
                if(saved!=null) {
                    Text(saved.getString("username").orEmpty())
                    LabeledValue(t("Prize to winnings"),money(saved.getLong("creditedPaisa")?:0))
                    if(saved.getString("settlementStatus")=="pending")Button(onClick={legacy.credit(saved){position=null;retry++}},enabled=ready){Text(t("Retry credit"))}
                    Text(t("Saved winners cannot be assigned again."))
                } else {
                    Box {
                        OutlinedButton(onClick={picker=true},enabled=ready,modifier=Modifier.fillMaxWidth().testTag("winner-participant-picker")){Text(t("Set winner"))}
                        DropdownMenu(picker,{picker=false},modifier=Modifier.fillMaxWidth(0.9f).heightIn(max=360.dp)) {
                            CompactSearch(winnerSearch,{winnerSearch=it},t("Search participants"))
                            entries.filter{(result(it)?.getLong("placement")?:0)==0L}.filter{(it.getString("username").orEmpty()+" "+it.getString("uid").orEmpty()+" "+profiles[it.getString("uid")]?.getString("ffUid").orEmpty()).contains(winnerSearch.trim(),true)}.sortedBy{it.getString("username")}.forEach{entry->DropdownMenuItem(text={Column{Identity(entry)}},onClick={participant=entry.id;picker=false})}
                        }
                    }
                    val selected=entries.find{it.id==participant}
                    selected?.let{Identity(it)}
                    val amount=prizeRows.find{it.getLong("rank")==rank}?.getLong("amountPaisa")?:0
                    LabeledValue(t("Placement reward"),money(amount))
                    Text(t("Placement prize only. Kill rewards are credited from Player kills."),style=MaterialTheme.typography.bodySmall)
                    Button(onClick={selected?.let{rewards.creditPlacement(it,rank,record?.getLong("revision")?:0){position=null;retry++}}},enabled=ready&&selected!=null,modifier=Modifier.fillMaxWidth().testTag("credit-placement")){Text(t("Save and credit")+" · "+money(amount))}
                }
                if(rewards.error.isNotBlank())Text(t(rewards.error),color=MaterialTheme.colorScheme.error)
                TextButton(onClick={position=null},enabled=!busy){Text(t("Close"))}
            }
        }
    }
    review?.let{rows->
        val unpaid=rows.filterNot{finalized(it.entry)}
        val amount=runCatching{unpaid.sumOf{row->prizeAward(0,row.rate,row.kills)-((oldCredits[row.entry.id]?.getLong("amountPaisa")?:0)-(oldCredits[row.entry.id]?.getLong("placementPaisa")?:0))}}.getOrDefault(0)
        val additional=rows.any{it.entry.id in oldCredits&&it.kills>=(oldCredits[it.entry.id]?.getLong("kills")?:0)&&prizeAward(0,it.rate,it.kills)>((oldCredits[it.entry.id]?.getLong("amountPaisa")?:0)-(oldCredits[it.entry.id]?.getLong("placementPaisa")?:0))}
        ModalBottomSheet(onDismissRequest={if(!busy)review=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding().testTag("kill-credit-review"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(t("Review kill credits"),style=MaterialTheme.typography.titleLarge)
                Text(t("Unset kills count as 0. Confirming finalizes every listed participant."))
                rows.forEach{row->Column {Text(row.entry.getString("username").orEmpty());Text(t("Account ID")+": "+row.entry.getString("uid").orEmpty(),style=MaterialTheme.typography.bodySmall);if(finalized(row.entry))Text(t("Kill rewards finalized."))else LabeledValue("${row.kills} × ${money(row.rate)}",money(prizeAward(0,row.rate,row.kills)-((oldCredits[row.entry.id]?.getLong("amountPaisa")?:0)-(oldCredits[row.entry.id]?.getLong("placementPaisa")?:0))))}}
                Text(t("Previously paid rewards are excluded."),style=MaterialTheme.typography.bodySmall)
                LabeledValue(t("New wallet credits"),money(amount))
                if(additional)OutlinedTextField(reason,{reason=it.take(1000)},label={Text(t("Reason for additional kill credit"))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                if(rewards.error.isNotBlank())Text(t(rewards.error),color=MaterialTheme.colorScheme.error)
                if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("${rewards.completed} / ${rewards.total}")}
                Button(onClick={rewards.creditKills(rows,reason){review=null;values=emptyMap();revisions=emptyMap();retry++}},enabled=ready&&(!additional||reason.isNotBlank()),modifier=Modifier.fillMaxWidth().testTag("confirm-kill-credits")){Text(t(if(rewards.error.isBlank())"Confirm and credit" else "Retry pending credits")+" · "+money(amount))}
                TextButton(onClick={review=null},enabled=!busy){Text(t("Back to editing"))}
            }
        }
    }
}
