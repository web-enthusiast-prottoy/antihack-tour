package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.util.Locale

internal data class ResultsState(val rows:List<DocumentSnapshot> = emptyList(),val loading:Boolean=true,val error:String="",val cached:Boolean=false,val more:Boolean=false)
internal class PublishedResults(private val session:AdminSession) {
    var state by mutableStateOf(ResultsState());private set
    private var generation=0
    private var cursors=mutableMapOf<String,DocumentSnapshot>()
    private var exhausted=mutableSetOf<String>()
    fun close(){generation++}
    fun load(own:Boolean,search:String,reset:Boolean) {
        if(!reset&&state.loading)return
        val uid=session.uid
        if(own&&uid==null)return
        val version=++generation
        if(reset){cursors.clear();exhausted.clear();state=ResultsState()}else state=state.copy(loading=true,error="")
        val old=state.rows
        var base:Query=session.db.collection(if(own)"resultLinks" else "results")
        if(own)base=base.whereEqualTo("uid",uid)
        val term=search.trim()
        val queries=if(term.isEmpty())mapOf("recent" to base.orderBy("publishedAt",Query.Direction.DESCENDING))else mapOf(
            "title" to base.orderBy("titleSearch").startAt(term.lowercase(Locale.ROOT)).endAt(term.lowercase(Locale.ROOT)+"\uf8ff"),
            "id" to base.whereEqualTo("match",term).orderBy("publishedAt",Query.Direction.DESCENDING)
        )
        val pending=queries.filterKeys{it !in exhausted}.mapValues{(key,query)->cursors[key]?.let{query.startAfter(it)}?:query}.toList()
        fun current()=generation==version&&session.uid==uid
        fun failure(){if(current())state=state.copy(loading=false,error="Could not load results. Reconnect and retry.")}
        fun fetch(source:Source) {
            Tasks.whenAllSuccess<QuerySnapshot>(pending.map{it.second.limit(21).get(source)}).addOnCompleteListener{task->
                if(!current())return@addOnCompleteListener
                if(!task.isSuccessful){
                    val code=(task.exception as? FirebaseFirestoreException)?.code
                    if(source==Source.SERVER&&reset&&code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))fetch(Source.CACHE)else failure()
                    return@addOnCompleteListener
                }
                val pages=task.result.map{it.documents.take(20)}
                val page=pages.flatten().distinctBy{it.id}
                fun complete(rows:List<DocumentSnapshot>) {
                    if(!current())return
                    pages.forEachIndexed{i,docs->docs.lastOrNull()?.let{cursors[pending[i].first]=it};if(task.result[i].size()<=20)exhausted.add(pending[i].first)}
                    state=ResultsState((old+rows).distinctBy{it.id},false,"",source==Source.CACHE,queries.keys.any{it !in exhausted})
                }
                if(!own||page.isEmpty())complete(page)
                else {
                    val ids=page.mapNotNull{it.getString("result")}.distinct()
                    if(ids.size!=page.size){failure();return@addOnCompleteListener}
                    Tasks.whenAllSuccess<QuerySnapshot>(ids.chunked(20).map{session.db.collection("results").whereIn(FieldPath.documentId(),it).limit(20).get(source)}).addOnCompleteListener{result->
                        if(current()) {
                            if(result.isSuccessful){
                                val indexed=result.result.flatMap{it.documents}.associateBy{it.id}
                                if(source==Source.SERVER&&ids.any{it !in indexed})failure()else complete(ids.mapNotNull{indexed[it]})
                            }else failure()
                        }
                    }
                }
            }
        }
        fetch(Source.SERVER)
    }
}
