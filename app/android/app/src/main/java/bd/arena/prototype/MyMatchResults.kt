package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.firestore.*

/** Own confirmed entries drive history, including matches whose scores are not published yet. */
internal class MyMatchResults(private val session:AdminSession) {
    var matches by mutableStateOf<List<Tournament>>(emptyList());private set
    var entries by mutableStateOf<Map<String,String>>(emptyMap());private set
    var published by mutableStateOf<Map<String,DocumentSnapshot>>(emptyMap());private set
    var loading by mutableStateOf(true);private set
    var cached by mutableStateOf(false);private set
    var error by mutableStateOf(false);private set
    private var feeds=mutableListOf<BoundedFeed>()
    private val listeners=mutableMapOf<String,ListenerRegistration>()
    private val documents=mutableMapOf<String,DocumentSnapshot>()
    private val pending=mutableSetOf<String>()
    private val failures=mutableSetOf<String>()
    private var entryState=BoundedFeedState()
    private var linkState=BoundedFeedState()
    private var generation=0

    fun close(){generation++;feeds.forEach{it.close()};feeds.clear();listeners.values.forEach{it.remove()};listeners.clear()}
    fun load() {
        close();documents.clear();pending.clear();failures.clear()
        matches=emptyList();entries=emptyMap();published=emptyMap();loading=true;cached=false;error=false
        entryState=BoundedFeedState();linkState=BoundedFeedState()
        val uid=session.uid?:return
        val version=generation
        fun current()=version==generation&&session.uid==uid
        fun publish() {
            if(!current())return
            val confirmed=entryState.rows.filter{it.getString("status")=="confirmed"}
            // An ownership link also preserves previously published history if the original match/entry was removed.
            entries=(confirmed+linkState.rows).associate{it.getString("match").orEmpty() to "confirmed"}
            published=linkState.rows.mapNotNull{link->documents["results/${link.getString("result")}"]?.takeIf{it.exists()}?.let{link.getString("match").orEmpty() to it}}.toMap()
            matches=entries.keys.mapNotNull{id->(published[id]?:documents["matches/$id"]?.takeIf{it.exists()})?.let{d->
                Tournament(id,d.getString("title").orEmpty(),d.getString("mode").orEmpty(),"",0,0,
                    startsAtMillis=d.getTimestamp("startsAt")?.toDate()?.time?:0,map=d.getString("map").orEmpty(),matchStatus=d.getString("status").orEmpty())
            }}
            loading=entryState.loading||linkState.loading||pending.isNotEmpty()
            cached=entryState.cached||linkState.cached||documents.values.any{it.metadata.isFromCache||it.metadata.hasPendingWrites()}
            error=entryState.error||linkState.error||failures.isNotEmpty()
        }
        fun synchronize() {
            if(!current())return
            val linked=linkState.rows.map{it.getString("match")}.toSet()
            val paths=(entryState.rows.filter{it.getString("status")=="confirmed"&&it.getString("match") !in linked}.map{"matches/${it.getString("match")}"}+
                linkState.rows.map{"results/${it.getString("result")}"}).toSet()
            listeners.keys.filter{it !in paths}.toList().forEach{listeners.remove(it)?.remove();documents.remove(it);pending.remove(it);failures.remove(it)}
            paths.filter{it !in listeners}.forEach { path ->
                pending+=path
                listeners[path]=session.db.document(path).addSnapshotListener(MetadataChanges.INCLUDE) {doc,failure->
                    if(current()) {
                        pending-=path
                        if(failure!=null||doc==null)failures+=path else {failures-=path;documents[path]=doc}
                        publish()
                    }
                }
            }
            publish()
        }
        feeds+=BoundedFeed(session.db.collection("entries").whereEqualTo("uid",uid),automatic=true){entryState=it;synchronize()}.also{it.start()}
        feeds+=BoundedFeed(session.db.collection("resultLinks").whereEqualTo("uid",uid),automatic=true){linkState=it;synchronize()}.also{it.start()}
    }
}
