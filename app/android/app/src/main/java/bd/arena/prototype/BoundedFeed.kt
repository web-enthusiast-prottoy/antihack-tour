package bd.arena.prototype

import com.google.firebase.firestore.*

internal data class BoundedFeedState(val rows:List<DocumentSnapshot> = emptyList(),val loading:Boolean=true,val cached:Boolean=true,val more:Boolean=false,val error:Boolean=false)

/** Live document-ID pages keep each request within the public rules' 100-record limit. */
internal class BoundedFeed(query:Query,private val automatic:Boolean=false,private val changed:(BoundedFeedState)->Unit) {
    private val query=query.orderBy(FieldPath.documentId())
    private val pages=sortedMapOf<Int,QuerySnapshot>()
    private val listeners=mutableMapOf<Int,ListenerRegistration>()
    private val versions=mutableMapOf<Int,Int>()
    private val pending=mutableSetOf<Int>()
    private val errors=mutableSetOf<Int>()
    private var requested=1
    private var version=0
    private var closed=false
    var state=BoundedFeedState();private set
    fun start(){subscribe(0,null)}
    fun loadMore() {
        if(closed||state.loading||!state.more)return
        val last=pages.lastKey()
        requested=maxOf(requested,last+2)
        subscribe(last+1,pages.getValue(last).documents.last().id)
    }
    fun retry(){if(!closed)subscribe(0,null)}
    fun close(){closed=true;listeners.values.forEach{it.remove()};listeners.clear();versions.clear();pending.clear()}
    private fun publish() {
        state=BoundedFeedState(pages.values.flatMap{it.documents}.distinctBy{it.id},pending.isNotEmpty(),pending.isNotEmpty()||errors.isNotEmpty()||pages.values.any{it.metadata.isFromCache},pending.isEmpty()&&errors.isEmpty()&&pages.isNotEmpty()&&pages.getValue(pages.lastKey()).size()==100,errors.isNotEmpty())
        changed(state)
    }
    private fun subscribe(index:Int,cursor:String?) {
        if(closed)return
        // Ignore late callbacks from every replaced downstream page.
        listeners.keys.filter{it>=index}.forEach{listeners.remove(it)?.remove();versions.remove(it);pending.remove(it);errors.remove(it)}
        val token=++version
        versions[index]=token;pending+=index;publish()
        val page=(if(cursor==null)query else query.startAfter(cursor)).limit(100)
        listeners[index]=page.addSnapshotListener(MetadataChanges.INCLUDE) {snapshot,error->
            if(closed||versions[index]!=token)return@addSnapshotListener
            pending-=index
            if(error!=null||snapshot==null){errors+=index;publish();return@addSnapshotListener}
            errors-=index
            val previous=pages[index]?.documents?.lastOrNull()?.id
            pages[index]=snapshot
            val end=snapshot.documents.lastOrNull()?.id
            if(snapshot.size()<100) {
                listeners.keys.filter{it>index}.forEach{listeners.remove(it)?.remove();versions.remove(it);pending.remove(it);errors.remove(it)}
                pages.keys.filter{it>index}.forEach{pages.remove(it)}
            } else if(automatic||requested>index+1) {
                if(automatic)requested=maxOf(requested,index+2)
                if(previous!=end||!listeners.containsKey(index+1))subscribe(index+1,end)
            }
            publish()
        }
    }
}
