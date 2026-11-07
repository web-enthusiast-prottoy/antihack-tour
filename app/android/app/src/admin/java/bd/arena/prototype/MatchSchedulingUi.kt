package bd.arena.prototype

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun st(text:String)=LocaleStrings.get(text)
private fun clock(minute:Int)=LocalTime.of(minute/60,minute%60).format(DateTimeFormatter.ofPattern("h:mm a"))
private fun day(value:String)=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}.getOrDefault(value)

@Composable
private fun ScheduleTimeField(label:String,minute:Int,enabled:Boolean,change:(Int)->Unit) {
    val context=LocalContext.current
    OutlinedButton(onClick={TimePickerDialog(context,{_,hour,min->change(hour*60+min)},minute/60,minute%60,false).show()},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text(st(label)+": "+clock(minute))}
}
@Composable
private fun ScheduleDateField(label:String,value:String,enabled:Boolean,change:(String)->Unit) {
    val context=LocalContext.current
    val selected=LocalDate.parse(value)
    OutlinedButton(onClick={DatePickerDialog(context,{_,y,m,d->change(LocalDate.of(y,m+1,d).toString())},selected.year,selected.monthValue-1,selected.dayOfMonth).show()},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text(st(label)+": "+day(value))}
}

@Composable
internal fun MatchSchedulingForm(enabled:Boolean,repeating:Boolean,setRepeating:(Boolean)->Unit,dateFrom:String,setDateFrom:(String)->Unit,dateTo:String,setDateTo:(String)->Unit,fromMinute:Int,setFromMinute:(Int)->Unit,toMinute:Int,setToMinute:(Int)->Unit,delay:String,setDelay:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text(st("Repeat this match automatically"),Modifier.weight(1f))
            Switch(repeating,setRepeating,enabled=enabled,modifier=Modifier.semantics{contentDescription=st("Repeat this match automatically")})
        }
        if(repeating) {
            Text(st("Daily time range · Bangladesh time"),style=MaterialTheme.typography.titleMedium)
            ScheduleTimeField("From time",fromMinute,enabled,setFromMinute)
            ScheduleTimeField("Until time",toMinute,enabled,setToMinute)
            OutlinedTextField(delay,{setDelay(it.filter(Char::isDigit).take(4))},label={Text(st("Minutes between match starts"))},singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            Text(st("Date range · both dates included"),style=MaterialTheme.typography.titleMedium)
            ScheduleDateField("First date",dateFrom,enabled,setDateFrom)
            ScheduleDateField("Last date",dateTo,enabled,setDateTo)
            Text(st("Create all of today’s upcoming matches now, spaced by this interval. The end time is included when it falls on the interval."))
            Text(st("The saved category, artwork, rules, entry fee and prizes repeat. Every match gets fresh participants, room details and results."),style=MaterialTheme.typography.bodySmall)
            Text(st("For a date range, each day’s batch is created on that day. Keep the admin app signed in and online for daily creation."),style=MaterialTheme.typography.bodySmall)
        } else Text(st("Turn this on to save a repeating schedule instead of a single match."))
    }
}

@Composable
internal fun ScheduledMatchesScreen(session:AdminSession,back:()->Unit,openMatch:(DocumentSnapshot)->Unit) {
    var feed by remember{mutableStateOf(BoundedFeedState())}
    var loader by remember{mutableStateOf<BoundedFeed?>(null)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var retry by remember{mutableIntStateOf(0)}
    BackHandler{if(selected!=null)selected=null else back()}
    DisposableEffect(session.uid,retry) {
        val source=BoundedFeed(session.db.collection("matchSchedules")){feed=it};loader=source;source.start()
        onDispose{source.close()}
    }
    Column(Modifier.widthIn(max=720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(st("Schedules"),back=if(selected!=null)({selected=null})else back,refresh={retry++})
        Text(st("Each day’s matches are created together while the admin app is online. Reopen the app if Android stops it."),style=MaterialTheme.typography.bodySmall)
        Text(st(MatchSchedulerStatus.message),color=if(MatchSchedulerStatus.running)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if(feed.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(feed.error||feed.cached)Text(st("Connect to confirm schedules and their current state."),color=MaterialTheme.colorScheme.error)
        if(!feed.loading&&feed.rows.isEmpty()&&!feed.error)Text(st("No match schedules yet. Create a match and open Scheduling."))
        val rows=feed.rows.filter{selected==null||it.id==selected}.sortedByDescending{it.getTimestamp("createdAt")}
        rows.forEach{record->ScheduleCard(session,record,selected==record.id,{selected=record.id},openMatch)}
        if(feed.more)TextButton(onClick={loader?.loadMore()}){Text(st("Load more"))}
        RegisterRefresh{retry++}
    }
}

@Composable
private fun ScheduleCard(session:AdminSession,record:DocumentSnapshot,expanded:Boolean,expand:()->Unit,openMatch:(DocumentSnapshot)->Unit) {
    var progress by remember(record.id){mutableStateOf<DocumentSnapshot?>(null)}
    var runs by remember(record.id){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    var cursor by remember(record.id){mutableStateOf<DocumentSnapshot?>(null)}
    var more by remember(record.id){mutableStateOf(false)}
    var busy by remember(record.id){mutableStateOf(false)}
    var failed by remember(record.id){mutableStateOf(false)}
    var error by remember(record.id){mutableStateOf("")}
    var stopConfirm by rememberSaveable(record.id){mutableStateOf(false)}
    val operations=remember(session.uid){MatchSchedules(session)}
    val state=record.getString("state").orEmpty()
    DisposableEffect(record.id) {
        val listener=session.db.document("matchScheduleProgress/${record.id}").addSnapshotListener(MetadataChanges.INCLUDE){doc,failure->
            failed=failure!=null||doc?.metadata?.isFromCache==true;progress=doc
        };onDispose{listener.remove()}
    }
    fun loadRuns(first:Boolean=false) {
        busy=true
        var query:Query=record.reference.collection("runs").orderBy("sequence",Query.Direction.DESCENDING)
        if(!first)cursor?.let{query=query.startAfter(it)}
        query.limit(21).get(Source.SERVER).addOnCompleteListener{task->
            busy=false
            if(task.isSuccessful){val page=task.result.documents.take(20);runs=(if(first)emptyList()else runs)+page;cursor=page.lastOrNull();more=task.result.size()>20}else error="Could not load generated matches. Reconnect and retry."
        }
    }
    LaunchedEffect(expanded,progress?.getLong("sequence")){if(expanded)loadRuns(true)}
    fun change(next:String) {busy=true;error="";operations.changeState(record,next){failure->busy=false;if(failure!=null)error=workflowFailure(failure)}}
    val phase=if(state=="active")progress?.getString("phase")?:"starting" else state
    val label=when(phase){"waiting"->"Creating today’s matches";"queued"->"Waiting for the next day";"paused"->"Paused";"stopped"->"Stopped";"completed"->"All matches created";"blocked"->"Needs attention";else->"Waiting for the online admin app"}
    val template=record.get("template") as? Map<*,*>
    OutlinedCard(onClick=expand,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(template?.get("title")?.toString().orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text(st(label),color=MaterialTheme.colorScheme.primary)
            Text(day(record.getString("dateFrom").orEmpty())+" – "+day(record.getString("dateTo").orEmpty()))
            Text(clock((record.getLong("fromMinute")?:0).toInt())+" – "+clock((record.getLong("toMinute")?:0).toInt())+" · "+st("Bangladesh time"))
            LabeledValue(st("Interval between starts"),(record.getLong("delayMinutes")?:0).toString()+" "+st("minutes"))
            if(state=="active")progress?.getTimestamp("nextAt")?.let{next->
                LabeledValue(st("Next daily batch"),next.toDate().toInstant().atZone(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ofPattern("dd MMM · h:mm a")))
                if(next.toDate().time<System.currentTimeMillis()-120000)Text(st("Waiting to create. Keep the admin app online to resume."),color=MaterialTheme.colorScheme.error)
            }
            if(failed)Text(st("Schedule progress could not be confirmed."),color=MaterialTheme.colorScheme.error)
            if(expanded) {
                Text(st("Generated matches")+": "+(progress?.getLong("sequence")?:0))
                if(state in listOf("active","paused")) {
                    Text(st("Pausing stops new batches. Already-created matches continue."),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={change(if(state=="active")"paused" else "active")},enabled=!busy&&!record.metadata.isFromCache){Text(st(if(state=="active")"Pause schedule" else "Resume schedule"))}
                    TextButton(onClick={stopConfirm=true},enabled=!busy){Text(st("Stop schedule"))}
                }
                progress?.getString("error")?.takeIf{it.isNotBlank()}?.let{Text(st(it),color=MaterialTheme.colorScheme.error)}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(st(error),color=MaterialTheme.colorScheme.error)
                runs.forEach{run->OutlinedButton(onClick={
                    busy=true
                    session.db.document("matches/${run.getString("match")}").get(Source.SERVER).addOnCompleteListener{task->busy=false;if(task.isSuccessful&&task.result.exists())openMatch(task.result)else error="The generated match could not be loaded."}
                },enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("#${run.getLong("sequence")} · "+((run.getTimestamp("startsAt")?:run.getTimestamp("createdAt"))?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(DateTimeFormatter.ofPattern("dd MMM · h:mm a"))?:st("Loading…")))}}
                if(more)TextButton(onClick={loadRuns()},enabled=!busy){Text(st("Load more"))}
                if(error.isNotBlank())TextButton(onClick={error="";loadRuns(true)},enabled=!busy){Text(st("Retry"))}
            }
        }
    }
    if(stopConfirm)AlertDialog(onDismissRequest={stopConfirm=false},title={Text(st("Stop this schedule?"))},text={Text(st("No more matches will be created. Existing matches and results are retained."))},confirmButton={TextButton(onClick={stopConfirm=false;change("stopped")}){Text(st("Stop schedule"))}},dismissButton={TextButton(onClick={stopConfirm=false}){Text(st("Cancel"))}})
}
