package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@Composable
internal fun ParticipantAdmin(session:AdminSession,match:String) {
    val feed=remember(session.uid,match){ParticipantFeed(session,match,true)}
    val operations=remember(session.uid,match){MatchOperations(session)}
    DisposableEffect(feed){feed.load();onDispose{feed.close()}}
    var selected by remember(match){mutableStateOf<DocumentSnapshot?>(null)}
    val state=feed.state
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        LabeledValue(LocaleStrings.get("Participants"),state.rows.size.toString() + if(state.more)"+" else "")
        state.rows.forEachIndexed { index,entry ->
            Card(onClick={selected=entry},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("#${index+1}",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                    PlayerIdentity(session,entry.getString("uid").orEmpty(),entry.getString("username").orEmpty())
                    ((entry.get("roster") as? List<*>)?.filterIsInstance<String>().orEmpty()).drop(1).forEach{Text(it,style=MaterialTheme.typography.bodyMedium)}
                    Text(LocaleStrings.get("View player"),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                    if(entry.getString("publicId")==null)OutlinedButton(onClick={operations.publishParticipant(entry){feed.load()}},enabled=!operations.busy){Text(LocaleStrings.get("Publish participant"))}
                }
            }
        }
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.error.isNotEmpty())Text(LocaleStrings.get(state.error),color=MaterialTheme.colorScheme.error)
        if(operations.error.isNotEmpty())Text(LocaleStrings.get(operations.error),color=MaterialTheme.colorScheme.error)
        if(operations.message.isNotEmpty())Text(LocaleStrings.get(operations.message))
        if(!state.loading&&state.rows.isEmpty())Text(LocaleStrings.get("No confirmed participants yet."))
        if(state.more)OutlinedButton(onClick={feed.load(false)},enabled=!state.loading){Text(LocaleStrings.get("Load more"))}
        RegisterRefresh(!state.loading&&!operations.busy){feed.load()}
    }
    selected?.let{ParticipantModeration(session,match,it){selected=null}}
}
