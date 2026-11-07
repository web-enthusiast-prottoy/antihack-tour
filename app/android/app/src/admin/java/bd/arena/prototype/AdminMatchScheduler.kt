package bd.arena.prototype

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.scheduleAwait():T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if(continuation.isActive)continuation.resume(it) }
    addOnFailureListener { if(continuation.isActive)continuation.resumeWithException(it) }
}

/** Runs only with a server-confirmed admin session. Offline transactions are never queued. */
internal class AdminMatchScheduler(private val session:AdminSession) {
    suspend fun advance(id:String,at:Long?=null) {
        var retries=0
        var observedSequence=-1L
        while(true) {
            try {
                val more=createNext(id,at)
                retries=0
                if(!more)return
            }catch(failure:FirebaseFirestoreException) {
                // A competing commit can invalidate the rules' coupled run/progress reads.
                if(failure.code !in listOf(FirebaseFirestoreException.Code.PERMISSION_DENIED,FirebaseFirestoreException.Code.ABORTED))throw failure
                val progress=session.db.document("matchScheduleProgress/$id").get(Source.SERVER).scheduleAwait()
                val sequence=progress.getLong("sequence")?:0L
                if(sequence!=observedSequence){observedSequence=sequence;retries=0}
                if(retries>=2)throw failure
                retries++
                kotlinx.coroutines.delay(retries*100L)
            }
            kotlinx.coroutines.yield()
        }
    }
    private suspend fun createNext(id:String,at:Long?):Boolean {
        val actor=checkNotNull(session.uid){"Reconnect with staff access to run schedules."}
        check(session.allowed){"Reconnect with staff access to run schedules."}
        val job=currentCoroutineContext()[Job]
        val db=session.db
        val ref=db.document("matchSchedules/$id")
        val progressRef=db.document("matchScheduleProgress/$id")
        return db.runTransaction {tx->
            check(job?.isActive!=false&&session.allowed&&session.uid==actor){"Reconnect with staff access to run schedules."}
            val now=at?:System.currentTimeMillis()
            val zone=java.time.ZoneId.of("Asia/Dhaka")
            val today=java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val dayStart=today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrow=today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val schedule=tx.get(ref)
            val progress=tx.get(progressRef)
            if(schedule.getString("state")!="active")return@runTransaction false
            val sequence=progress.getLong("sequence")?:0L
            val last=progress.getString("lastMatch")?.takeIf{it.isNotEmpty()}
            val previous=last?.let{tx.get(db.document("matches/$it"))}
            val lastRun=if(last!=null)tx.get(ref.collection("runs").document(sequence.toString()))else null
            val timing=MatchScheduleTiming(schedule.getString("dateFrom")!!,schedule.getString("dateTo")!!,schedule.getLong("fromMinute")!!.toInt(),schedule.getLong("toMinute")!!.toInt(),schedule.getLong("delayMinutes")!!.toInt())
            timing.validate()
            fun update(phase:String,nextAt:Timestamp?=null,error:String="") {
                if(progress.getString("phase")!=phase||progress.getTimestamp("nextAt")!=nextAt||progress.getString("error")!=error)
                    tx.set(progressRef,mapOf("sequence" to sequence,"lastMatch" to last.orEmpty(),"phase" to phase,"nextAt" to nextAt,"error" to error,"updatedAt" to FieldValue.serverTimestamp()))
            }
            fun complete() {
                val revision=schedule.getLong("revision")!!+1
                tx.update(ref,mapOf("state" to "completed","revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
                tx.set(db.document("auditRecords/schedule_${id}_$revision"),mapOf("action" to "saveMatchSchedule","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
                update("completed")
            }
            // Finish a draft left by an older app before generating the remaining slots.
            if(last!=null&&previous?.getString("status")=="draft") {
                val revision=(previous.getLong("revision")?:0)+1
                tx.update(previous.reference,mapOf("status" to "open","revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
                tx.set(previous.reference.collection("history").document(revision.toString()),mapOf("from" to "draft","to" to "open","reason" to "","at" to FieldValue.serverTimestamp()))
                tx.set(db.document("auditRecords/match_${last}_$revision"),mapOf("action" to "saveMatch","path" to previous.reference.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
                update("waiting")
                return@runTransaction true
            }
            if(last!=null&&previous?.exists()!=true) {
                update("blocked",error="The current generated match was deleted. Stop this schedule and review its history.")
                return@runTransaction false
            }
            // Anchor to creation time, so interrupted generation resumes the same fixed slots.
            val earliest=maxOf(dayStart,schedule.getTimestamp("createdAt")!!.toDate().time,
                ((lastRun?.getTimestamp("startsAt")?:previous?.getTimestamp("startsAt"))?.toDate()?.time?.plus(1))?:Long.MIN_VALUE)
            val due=timing.next(earliest)
            if(due==null){complete();return@runTransaction false}
            if(due>=tomorrow){update("queued",Timestamp(java.util.Date(java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli())));return@runTransaction false}
            val next=sequence+1
            val match=db.document("matches/schedule_${id}_$next")
            val run=ref.collection("runs").document(next.toString())
            // Another runner can commit between the progress read and these reads.
            if(tx.get(match).exists()||tx.get(run).exists())throw FirebaseFirestoreException("The next match identifier already exists. Review the schedule history.",FirebaseFirestoreException.Code.ABORTED)
            @Suppress("UNCHECKED_CAST") val template=schedule.get("template") as Map<String,Any>
            val prizes=(schedule.get("prizes") as List<*>).map{it as Long}
            check(prizes.isNotEmpty()&&prizes.size<=400&&prizes.all{it in 0..maxPaisa}&&prizes.any{it>0}&&prizes.fold(java.math.BigInteger.ZERO){sum,value->sum+java.math.BigInteger.valueOf(value)}<=java.math.BigInteger.valueOf(template["prizePaisa"] as Long)){"Check the saved schedule prizes."}
            val stamp=FieldValue.serverTimestamp()
            tx.set(match,template+mapOf("startsAt" to Timestamp(java.util.Date(due)),"joined" to 0L,"status" to "open","revision" to 1L,"cancellationReason" to "","independentPrizeTotal" to true,"prizesEnabled" to true,"prizeRank" to 0L,"prizeMaxRank" to prizes.size.toLong(),"updatedAt" to stamp,"updatedBy" to actor))
            prizes.forEachIndexed{index,amount->tx.set(match.collection("prizes").document((index+1).toString()),mapOf("rank" to (index+1).toLong(),"amountPaisa" to amount,"matchRevision" to 1L,"updatedAt" to stamp))}
            tx.set(match.collection("history").document("1"),mapOf("from" to "none","to" to "open","reason" to "","at" to stamp))
            tx.set(db.document("auditRecords/match_${match.id}_1"),mapOf("action" to "saveMatch","path" to match.path,"actor" to actor,"at" to stamp))
            tx.set(run,mapOf("sequence" to next,"match" to match.id,"createdAt" to stamp,"scheduleRevision" to schedule.getLong("revision")!!,"startsAt" to Timestamp(java.util.Date(due))))
            tx.set(progressRef,mapOf("sequence" to next,"lastMatch" to match.id,"phase" to "waiting","nextAt" to null,"error" to "","updatedAt" to stamp))
            true
        }.scheduleAwait()
    }
}
