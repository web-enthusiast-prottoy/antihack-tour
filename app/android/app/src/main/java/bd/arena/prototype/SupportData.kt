package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.firestore.*

internal fun validSupportText(subject:String,body:String,reference:String)=subject.isNotBlank()&&subject.length<=80&&body.isNotBlank()&&body.length<=4000&&reference.length<=200
internal class SupportData(private val session:AdminSession,private val staff:Boolean) {
    var rows by mutableStateOf<List<DocumentSnapshot>>(emptyList());private set
    var loading by mutableStateOf(false);private set
    var more by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    var busy by mutableStateOf(false);private set
    private var cursor:DocumentSnapshot?=null
    private var generation=0
    fun close(){generation++}
    fun load(reset:Boolean=true) {
        if(loading&&!reset)return
        val uid=session.uid?:return
        val version=++generation
        if(reset){cursor=null;rows=emptyList()}
        loading=true;error=""
        var q:Query=session.db.collection("disputes")
        if(!staff)q=q.whereEqualTo("uid",uid)
        q=q.orderBy("createdAt",Query.Direction.DESCENDING)
        cursor?.let{q=q.startAfter(it)}
        q.limit(21).get(Source.SERVER).addOnCompleteListener {task ->
            if(version==generation&&session.uid==uid) {
                loading=false
                if(task.isSuccessful){val page=task.result.documents.take(20);cursor=page.lastOrNull()?:cursor;rows=(rows+page).distinctBy{it.id};more=task.result.size()>20}
                else error="Could not load support requests. Reconnect and retry."
            }
        }
    }
    fun submit(id:String,subject:String,body:String,reference:String,done:()->Unit) {
        if(busy||!validSupportText(subject,body,reference))return
        val uid=session.uid?:return
        val ref=session.db.document("disputes/$id")
        busy=true;error=""
        session.db.runTransaction {tx ->
            val old=tx.get(ref)
            if(old.exists())require(old.getString("uid")==uid&&old.getString("subject")==subject&&old.getString("body")==body&&old.getString("reference")==reference){"This request was already submitted with different text. Start a new request."}
            else tx.set(ref,mapOf("uid" to uid,"subject" to subject,"body" to body,"reference" to reference,"status" to "open","response" to "","revision" to 0L,"createdAt" to FieldValue.serverTimestamp(),"updatedAt" to FieldValue.serverTimestamp()))
        }.continueWithTask {task -> if(!task.isSuccessful)throw task.exception?:IllegalStateException();ref.get(Source.SERVER)}.addOnCompleteListener {task ->
            if(session.uid==uid){busy=false;if(task.isSuccessful){done();load()}else error=workflowFailure(task.exception)}
        }
    }
    fun reply(record:DocumentSnapshot,body:String,status:String,done:()->Unit) {
        if(busy||!session.allowed||body.isBlank()||body.length>4000||status !in listOf("open","review","resolved"))return
        val uid=session.uid?:return
        val revision=(record.getLong("revision")?:0)+1
        val response=record.reference.collection("responses").document(revision.toString())
        busy=true;error=""
        session.db.runTransaction {tx ->
            val current=tx.get(record.reference)
            val existing=tx.get(response)
            if(existing.exists())require(existing.getString("body")==body&&existing.getString("status")==status){"This request changed. Refresh before replying."}
            else {
                require(current.getLong("revision")==revision-1){"This request changed. Refresh before replying."}
                tx.update(record.reference,mapOf("status" to status,"response" to body,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp()))
                tx.set(response,mapOf("body" to body,"status" to status,"at" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/support_${record.id}_$revision"),mapOf("action" to "replySupport","path" to record.reference.path,"actor" to uid,"at" to FieldValue.serverTimestamp()))
            }
        }.continueWithTask {task -> if(!task.isSuccessful)throw task.exception?:IllegalStateException();response.get(Source.SERVER)}.addOnCompleteListener {task ->
            if(session.uid==uid&&session.allowed){busy=false;if(task.isSuccessful){done();load()}else error=workflowFailure(task.exception)}
        }
    }
}
