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
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var hidden by remember(match,confirmed) { mutableStateOf(false) }
    LaunchedEffect(access,confirmed,active,hidden) {
        if (confirmed && active && !hidden) {
            access.open(match)
            try {
                while (true) {
                    kotlinx.coroutines.delay(10_000)
                    access.open(match,refresh=true)
                }
            } finally { access.close() }
        }
    }
    DisposableEffect(access,lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_START)active=true
            if(event==Lifecycle.Event.ON_STOP){active=false;access.close()}
        }
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);access.close()}
    }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(wt("Match room"),style=MaterialTheme.typography.titleLarge)
            if(!confirmed)Text(wt(if(entryStatus=="requested")"Your entry is awaiting staff confirmation. Room details unlock after payment confirmation and room release." else "Room details are available only to confirmed participants after release."))
            else when(val state=access.state) {
                RoomState.Hidden->{Text(wt("Room details require a current server connection and the staff release time."));Button(onClick={hidden=false;access.open(match)}){Text(wt("Show room"))}}
                RoomState.Loading->{Text(wt("Checking room availability…"),style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(Modifier.fillMaxWidth())}
                is RoomState.Unavailable->{Text(wt(state.message));OutlinedButton(onClick={access.open(match)}){Text(wt("Refresh room"))}}
                is RoomState.Ready->{
                    Text(wt("Room ready"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    RoomCredential(wt("Room ID"),state.roomId,wt("Copy room ID"))
                    RoomCredential(wt("Room password"),state.password,wt("Copy password"))
                    Text(wt("Use these details to enter the game room."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick={access.open(match)}){Text(wt("Refresh room"))}
                        TextButton(onClick={hidden=true;access.close()}){Text(wt("Hide"))}
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

