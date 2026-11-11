package bd.arena.prototype

import androidx.compose.runtime.*
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

@Composable
internal fun ParticipantList(session:AdminSession,match:String) {
    val feed=remember(session.uid,match){ParticipantFeed(session,match)}
    DisposableEffect(feed){feed.load();onDispose{feed.close()}}
    val state=feed.state
    PublicParticipantContent(state.rows.map { row ->
        PublicParticipant(row.id,row.getString("username").orEmpty(),(row.get("roster") as? List<*>)?.filterIsInstance<String>().orEmpty(),(row.get("ffUid") as? String).orEmpty())
    },state.loading,state.cached,state.more,state.error.isNotEmpty(),refresh={feed.load()},loadMore={feed.load(false)})
}
