package bd.arena.prototype

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.util.concurrent.TimeUnit

/** Client-rules fixture: follows the same audited lifecycle as staff UI; emulator only. */
internal fun saveLifecycleFixture(session:AdminSession,id:String,values:Map<String,Any>) {
    check(BuildConfig.FIREBASE_USE_EMULATOR)
    val ref=session.db.document("matches/$id")
    val before=Tasks.await(ref.get(Source.SERVER),20,TimeUnit.SECONDS)
    val target=values["status"]?.toString()?:"draft"
    val stages=listOf("draft","open","closed","live","review")
    val start=before.getString("status")
    val steps=if(start==target)listOf(target)else stages.subList(if(start==null)0 else stages.indexOf(start)+1,stages.indexOf(target)+1)
    for(status in steps)Tasks.await(session.db.runTransaction {tx ->
        val old=tx.get(ref);val revision=(old.getLong("revision")?:0)+1
        val data=(old.data.orEmpty()+values+mapOf("status" to status,"revision" to revision,"cancellationReason" to "","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
        tx.set(ref,data)
        tx.set(ref.collection("history").document(revision.toString()),mapOf("from" to (old.getString("status")?:"none"),"to" to status,"reason" to "","at" to FieldValue.serverTimestamp()))
        tx.set(session.db.document("auditRecords/match_${id}_$revision"),mapOf("action" to "saveMatch","path" to ref.path,"actor" to session.uid!!,"at" to FieldValue.serverTimestamp()))
    },20,TimeUnit.SECONDS)
}
