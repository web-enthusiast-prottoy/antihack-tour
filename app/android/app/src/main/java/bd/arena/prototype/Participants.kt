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

internal data class ParticipantState(val rows:List<DocumentSnapshot> = emptyList(),val loading:Boolean=false,val cached:Boolean=false,val more:Boolean=false,val error:String="")
internal class ParticipantFeed(private val session:AdminSession,private val match:String,private val staff:Boolean=false) {
    var state by mutableStateOf(ParticipantState());private set
    private var cursor:DocumentSnapshot?=null
    private var generation=0
    fun close(){generation++}
    fun load(reset:Boolean=true) {
        if(state.loading&&!reset)return
        val version=++generation
        val uid=session.uid
        if(staff&&uid==null)return
        val old=if(reset)emptyList()else state.rows
        if(reset)cursor=null
        state=ParticipantState(old,true)
        var query:Query=if(staff)session.db.collection("entries").whereEqualTo("match",match).whereEqualTo("status","confirmed") else session.db.collection("matches/$match/participants")
        query=query.orderBy(FieldPath.documentId())
        cursor?.let {query=query.startAfter(it)}
        val pageQuery=query.limit(21)
        fun fetch(source:Source) {
            pageQuery.get(source).addOnCompleteListener {task ->
                if(version!=generation||uid!=session.uid)return@addOnCompleteListener
                if(!task.isSuccessful) {
                    val code=(task.exception as? FirebaseFirestoreException)?.code
                    if(!staff&&reset&&source==Source.SERVER&&code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))fetch(Source.CACHE)
                    else state=state.copy(loading=false,error="Could not load participants. Reconnect and retry.")
                } else {
                    val page=task.result.documents.take(20)
                    cursor=page.lastOrNull()?:cursor
                    state=ParticipantState((old+page).distinctBy{it.id},false,source==Source.CACHE,source!=Source.CACHE&&task.result.size()>20)
                }
            }
        }
        fetch(Source.SERVER)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParticipantList(session:AdminSession,match:String,bangla:Boolean) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    val feed=remember(session.uid,match){ParticipantFeed(session,match)}
    DisposableEffect(feed){feed.load();onDispose{feed.close()}}
    val state=feed.state
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(t("Participants","অংশগ্রহণকারী"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(state.rows.isNotEmpty())Text("${state.rows.size}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            }
            if(state.cached) Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal=10.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_refresh),null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t("Offline","অফলাইন"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.rows.forEachIndexed { index,row -> ParticipantRow(index,row) }
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.error.isNotEmpty()) Text(t("Could not load.","লোড হয়নি।"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(!state.loading&&state.rows.isEmpty()&&state.error.isEmpty()) Text(t("No participants yet.","এখনো কেউ নেই।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.more) OutlinedButton(onClick={feed.load(false)},enabled=!state.loading,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Load more"))}
            Spacer(Modifier.height(72.dp))
        }
        FloatingActionButton(
            onClick={feed.load()},
            modifier=Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=8.dp).semantics{contentDescription=LocaleStrings.get(if(state.error.isEmpty())"Refresh" else "Retry")},
            containerColor=MaterialTheme.colorScheme.surfaceVariant,
            contentColor=MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            if(state.loading) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.primary)
            else Icon(painterResource(R.drawable.ic_refresh),null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantRow(index:Int,row:DocumentSnapshot) {
    val username=row.getString("username").orEmpty()
    val roster=(row.get("roster") as? List<*>)?.filterIsInstance<String>().orEmpty()
    val teammates=if(roster.size>1)roster.drop(1) else emptyList()
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                Text("${index+1}",Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(username,style=MaterialTheme.typography.titleSmall)
                if(teammates.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    teammates.forEach { name ->
                        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                            Text(name,Modifier.padding(horizontal=8.dp,vertical=2.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ParticipantAdmin(session:AdminSession,match:String) {
    val feed=remember(session.uid,match){ParticipantFeed(session,match,true)}
    val operations=remember(session.uid,match){MatchOperations(session)}
    DisposableEffect(feed){feed.load();onDispose{feed.close()}}
    val state=feed.state
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(LocaleStrings.get("Publish game names and preset avatar only. New confirmations appear automatically; publish older confirmed entries here."))
        state.rows.forEach {entry ->
            Text(entry.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
            Text(((entry.get("roster") as? List<*>)?:listOf(entry.getString("username"))).joinToString(" · "))
            OutlinedButton(onClick={operations.publishParticipant(entry){feed.load()}},enabled=!operations.busy&&entry.getString("publicId")==null){Text(LocaleStrings.get(if(entry.getString("publicId")==null)"Publish participant" else "Published"))}
        }
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.error.isNotEmpty())Text(LocaleStrings.get(state.error),color=MaterialTheme.colorScheme.error)
        if(operations.error.isNotEmpty())Text(LocaleStrings.get(operations.error),color=MaterialTheme.colorScheme.error)
        if(operations.message.isNotEmpty())Text(LocaleStrings.get(operations.message))
        if(!state.loading&&state.rows.isEmpty())Text(LocaleStrings.get("No confirmed participants yet."))
        if(state.more)OutlinedButton(onClick={feed.load(false)},enabled=!state.loading){Text(LocaleStrings.get("Load more"))}
        TextButton(onClick={feed.load()},enabled=!state.loading&&!operations.busy){Text(LocaleStrings.get("Refresh"))}
    }
}
