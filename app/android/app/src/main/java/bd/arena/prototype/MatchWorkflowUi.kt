package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.firestore.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun wt(text:String)=LocaleStrings.get(text)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchRoomCard(session:AdminSession,match:String,confirmed:Boolean,entryStatus:String?=null) {
    val access=remember(session,match,confirmed){RoomAccess(session)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(access,confirmed){if(confirmed)access.open(match)}
    DisposableEffect(access,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)access.close()}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);access.close()}
    }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(wt("Match room"),style=MaterialTheme.typography.titleLarge)
            if(!confirmed)Text(wt(if(entryStatus=="requested")"Your entry is awaiting staff confirmation. Room details unlock after payment confirmation and room release." else "Room details are available only to confirmed participants after release."))
            else when(val state=access.state) {
                RoomState.Hidden->{Text(wt("Room details require a current server connection and the staff release time."));Button(onClick={access.open(match)}){Text(wt("Show room"))}}
                RoomState.Loading->{Text(wt("Checking room availability…"),style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(Modifier.fillMaxWidth())}
                is RoomState.Unavailable->{Text(wt(state.message));OutlinedButton(onClick={access.open(match)}){Text(wt("Refresh room"))}}
                is RoomState.Ready->{
                    Text(wt("Room ready"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    RoomCredential(wt("Room ID"),state.roomId,wt("Copy room ID"))
                    RoomCredential(wt("Room password"),state.password,wt("Copy password"))
                    Text(wt("Use these details to enter the game room."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick={access.open(match)}){Text(wt("Refresh room"))}
                        TextButton(onClick={access.close()}){Text(wt("Hide"))}
                    }
                }
            }
        }
    }
}
@Composable
private fun RoomCredential(label:String,value:String,copyLabel:String) {
    val clipboard=LocalClipboardManager.current
    var copied by remember(value){mutableStateOf(false)}
    LaunchedEffect(copied){if(copied){kotlinx.coroutines.delay(2000);copied=false}}
    Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer{Text(value,style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.Monospace))}
            }
            TextButton(onClick={clipboard.setText(AnnotatedString(value));copied=true}){Text(wt(if(copied)"Copied" else copyLabel))}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchAdminScreen(session:AdminSession,match:DocumentSnapshot,back:()->Unit) {
    BackHandler {back()}
    var section by remember {mutableStateOf("room")}
    val scroll=rememberScrollState()
    LaunchedEffect(section){scroll.scrollTo(0)}
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding().verticalScroll(scroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(match.getString("title").orEmpty(),back=back)
        Text(wt("Match ID")+": "+match.id)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(section=="room",{section="room"},label={Text(wt("Match room"))})
            FilterChip(section=="prizes",{section="prizes"},label={Text(wt("Prizes"))})
            FilterChip(section=="results",{section="results"},label={Text(wt("Results"))})
            FilterChip(section=="participants",{section="participants"},label={Text(wt("Participants"))})
            if((match.getLong("entryPaisa")?:0)>0)FilterChip(section=="refunds",{section="refunds"},label={Text(wt("Entry refunds"))})
            FilterChip(section=="history",{section="history"},label={Text(wt("Match history"))})
        }
        if(section=="room") RoomEditor(session,match.id) else if(section=="prizes")PrizePanel(session,match.id,true) else if(section=="results") ResultEditor(session,match.id) else if(section=="participants")ParticipantAdmin(session,match.id)else if(section=="refunds")PaidRefunds(session,match.id)else MatchHistory(session,match.id)
    }
}

@Composable
private fun RoomEditor(session:AdminSession,match:String) {
    val operations=remember {MatchOperations(session)}
    var roomId by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var time by remember {mutableStateOf(LocalDateTime.now(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))}
    var enabled by remember {mutableStateOf(false)};var revision by remember {mutableStateOf(0L)}
    var loaded by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(true)};var error by remember {mutableStateOf("")}
    var retry by remember {mutableStateOf(0)}
    DisposableEffect(retry) {
        var active=true
        loading=true;loaded=false;error=""
        session.db.document("rooms/$match").get(Source.SERVER).addOnCompleteListener {
            if(active) {loading=false;if(it.isSuccessful) {loaded=true;val s=it.result;roomId=s.getString("roomId").orEmpty();password=s.getString("password").orEmpty();enabled=s.getBoolean("enabled")?:false;revision=s.getLong("revision")?:0;s.getTimestamp("releaseAt")?.let{ts->time=ts.toDate().toInstant().atZone(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}} else error="Could not load the record. Reconnect and retry."}
        }
        onDispose {active=false}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(wt("Keep the room disabled until the ID, password and release time have been checked."))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotEmpty()) {Text(wt(error),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text(wt("Retry"))}}
        OutlinedTextField(roomId,{roomId=it},label={Text(wt("Room ID"))},modifier=Modifier.fillMaxWidth(),enabled=!loading&&!operations.busy)
        OutlinedTextField(password,{password=it},label={Text(wt("Room password"))},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=!loading&&!operations.busy)
        OutlinedTextField(time,{time=it},label={Text(wt("Release (Bangladesh: yyyy-MM-dd HH:mm)"))},modifier=Modifier.fillMaxWidth(),enabled=!loading&&!operations.busy)
        Row {Checkbox(enabled,{enabled=it},enabled=!loading&&!operations.busy,modifier=Modifier.semantics{contentDescription=wt("Enable room release")});Text(wt("Enable room release"))}
        Text(wt("Players must also have a confirmed entry. Rooms are available only while registration is closed or the match is live."))
        if(operations.error.isNotEmpty())Text(wt(operations.error),color=MaterialTheme.colorScheme.error)
        if(operations.message.isNotEmpty())Text(wt(operations.message))
        Button(onClick={try {error="";operations.saveRoom(match,roomId,password,releaseTimestamp(time),enabled,revision){retry++}} catch(_:Exception){error="Use a valid Bangladesh date and time: yyyy-MM-dd HH:mm."}},enabled=loaded&&!loading&&!operations.busy) {Text(wt(if(operations.busy)"Saving…" else "Save room"))}
        TextButton(onClick={retry++},enabled=!loading&&!operations.busy) {Text(wt("Reload record"))}
    }
}

