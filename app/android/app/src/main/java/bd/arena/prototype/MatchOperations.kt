package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

internal fun releaseTimestamp(text:String):Timestamp = Timestamp(Date.from(LocalDateTime.parse(text.trim().replace(' ','T')).atZone(ZoneId.of("Asia/Dhaka")).toInstant()))
internal fun awardPaisa(text:String):Long = java.math.BigDecimal(text.trim()).movePointRight(2).longValueExact().also { require(it in 0..9007199254740991L) }
internal fun workflowFailure(error:Exception?):String {
    var cause:Throwable?=error
    while(cause!=null) {
        if(cause is IllegalArgumentException) return cause.message?:"Check the form."
        cause=cause.cause
    }
    return "Could not confirm the change. Check connection and staff access, then retry."
}

internal class MatchOperations(private val session:AdminSession) {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    private fun audit(tx:Transaction,id:String,action:String,path:String,actor:String) {
        tx.set(session.db.document("auditRecords/$id"),mapOf("action" to action,"path" to path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
    }
    fun savePrizePosition(match:String,rank:Long,amount:Long,expectedRevision:Long,done:(Long,Long)->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        busy=true;error="";message=""
        val ref=session.db.document("matches/$match")
        val position=ref.collection("prizes").document(rank.toString())
        session.db.runTransaction {tx->
            val old=tx.get(ref);val before=tx.get(position)
            require(old.getLong("revision")==expectedRevision){"This match changed. Reload and select the position again."}
            require((old.getLong("joined")?:0)==0L&&old.getString("status") in listOf("draft","open")){"Prize terms are locked."}
            require(rank in 1..(old.getLong("capacity")?:0)){"Position must be within the saved match capacity."}
            val enabled=old.getBoolean("prizesEnabled")==true
            require(enabled||!before.exists())
            val total=updatedPrizeTotal(enabled,old.getLong("prizePaisa")?:0,before.getLong("amountPaisa")?:0,amount)
            val revision=expectedRevision+1
            tx.set(position,mapOf("rank" to rank,"amountPaisa" to amount,"matchRevision" to revision,"updatedAt" to FieldValue.serverTimestamp()))
            tx.update(ref,mapOf("prizesEnabled" to true,"prizeRank" to rank,"prizeMaxRank" to maxOf(old.getLong("prizeMaxRank")?:0,rank),"prizePaisa" to total,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            val status=old.getString("status").orEmpty()
            tx.set(ref.collection("history").document(revision.toString()),mapOf("from" to status,"to" to status,"reason" to "","at" to FieldValue.serverTimestamp()))
            audit(tx,"match_${match}_$revision","saveMatch",ref.path,actor)
            revision to total
        }.addOnCompleteListener {if(session.uid==actor&&session.allowed){busy=false;if(it.isSuccessful){message="Prize saved.";done(it.result.first,it.result.second)}else error=workflowFailure(it.exception)}}
    }
    fun publishParticipant(entry:DocumentSnapshot,done:()->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        busy=true;error="";message=""
        val newId=session.db.collection("participantLinks").document().id
        session.db.runTransaction {tx ->
            val e=tx.get(entry.reference)
            require(e.getString("status")=="confirmed") {"Only confirmed participants can be published."}
            val match=e.getString("match")!!
            val id=e.getString("publicId")?:newId
            val pub=session.db.document("matches/$match/participants/$id")
            val existing=tx.get(pub)
            val profile=tx.get(session.db.document("users/${e.getString("uid")}"))
            if(!existing.exists()) {
                val avatar=profile.getString("avatar")?:"solo"
                tx.update(entry.reference,mapOf("publicId" to id,"avatar" to avatar))
                tx.set(session.db.document("participantLinks/$id"),mapOf("entry" to entry.id))
                tx.set(pub,mapOf("username" to e.getString("username").orEmpty(),"roster" to (e.get("roster")?:listOf(e.getString("username").orEmpty())),"avatar" to avatar))
                audit(tx,"participant_$id","publishParticipant",pub.path,actor)
            }
        }.addOnCompleteListener {if(session.uid==actor&&session.allowed){busy=false;if(it.isSuccessful){message="Participant published.";done()}else error=workflowFailure(it.exception)}}
    }
    fun saveRoom(match:String,roomId:String,password:String,releaseAt:Timestamp,enabled:Boolean,expectedRevision:Long,done:()->Unit) {
        if(busy || !session.allowed)return
        val actor=session.uid?:return
        if(roomId.isBlank()||roomId.trim().length>100||password.isBlank()||password.length>100) {error="Enter a room ID and password (up to 100 characters each).";return}
        busy=true;error="";message=""
        val ref=session.db.document("rooms/$match")
        session.db.runTransaction {tx ->
            val old=tx.get(ref)
            require((old.getLong("revision")?:0)==expectedRevision) {"This record changed. Reload before saving."}
            val revision=expectedRevision+1
            tx.set(ref,mapOf("roomId" to roomId.trim(),"password" to password,"releaseAt" to releaseAt,"enabled" to enabled,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            audit(tx,"room_${match}_$revision","saveRoom",ref.path,actor)
        }.addOnCompleteListener {
            if(session.uid==actor && session.allowed) {busy=false;if(it.isSuccessful){message="Room saved.";done()}else error=workflowFailure(it.exception)}
        }
    }
    fun saveDraft(entry:DocumentSnapshot,placement:Long,kills:Long,award:Long?,expectedRevision:Long,done:()->Unit) {
        if(busy || !session.allowed)return
        val actor=session.uid?:return
        busy=true;error="";message=""
        val ref=session.db.document("resultDrafts/${entry.id}")
        val freshId=session.db.collection("results").document().id
        session.db.runTransaction {tx ->
            val e=tx.get(entry.reference)
            val old=tx.get(ref)
            require(e.getString("status")=="confirmed") {"Only confirmed participants can receive results."}
            require((old.getLong("revision")?:0)==expectedRevision) {"This record changed. Reload before saving."}
            val uid=e.getString("uid")!!;val match=e.getString("match")!!
            val m=tx.get(session.db.document("matches/$match"))
            val profile=tx.get(session.db.document("users/$uid"))
            val publicId=old.getString("publicId")?:freshId
            val published=tx.get(session.db.document("results/$publicId"))
            require(!published.exists()) {"Published results are immutable. Use the correction workflow when available."}
            require(m.getString("status")=="review") {"Move the match to results review first."}
            require(placement in 1..(m.getLong("joined")?:0) && kills in 0..10000){"Check placement against confirmed participants and check kills."}
            val position=tx.get(session.db.document("matches/$match/prizes/$placement"))
            val computed=prizeAward(position.getLong("amountPaisa")?:0,m.getLong("perKillPaisa")?:0,kills)
            val amount=if(m.getBoolean("prizesEnabled")==true)computed else requireNotNull(award){"Enter the legacy award amount."}
            require(award==null||award==amount){"The award must match the saved prize terms."}
            val killReward=prizeAward(0,m.getLong("perKillPaisa")?:0,kills)
            require(amount in 0..maxPaisa&&maxOf(0,amount-killReward)<=(m.getLong("prizePaisa")?:0)){"Check placement, kills and award."}
            val revision=expectedRevision+1
            tx.set(ref,mapOf("uid" to uid,"match" to match,"publicId" to publicId,"username" to e.getString("username").orEmpty(),"avatar" to (profile.getString("avatar")?:"solo"),"placement" to placement,"kills" to kills,"awardPaisa" to amount,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            audit(tx,"draft_${entry.id}_$revision","saveResultDraft",ref.path,actor)
        }.addOnCompleteListener {
            if(session.uid==actor && session.allowed) {busy=false;if(it.isSuccessful){message="Draft saved. Review it before publishing.";done()}else error=workflowFailure(it.exception)}
        }
    }
    fun publish(draft:DocumentSnapshot,failed:()->Unit={},done:()->Unit) {
        if(busy || !session.allowed)return
        val actor=session.uid?:return
        val id=draft.getString("publicId")?:return
        busy=true;error="";message=""
        val result=session.db.document("results/$id")
        val link=session.db.document("resultLinks/$id")
        var transactionFailure:Exception?=null
        session.db.runTransaction {tx ->
            val existing=tx.get(result)
            if(!existing.exists()) {
                val d=tx.get(draft.reference)
                require(d.getLong("revision")==draft.getLong("revision")) {"The draft changed. Reload and review it before publishing."}
                val match=d.getString("match")!!
                val m=tx.get(session.db.document("matches/$match"))
                val e=tx.get(session.db.document("entries/${draft.id}"))
                require(e.getString("status")=="confirmed" && m.getString("status")=="review") {"Move the match to results review first."}
                val placement=tx.get(session.db.document("publishedPlacements/${match}_${d.getLong("placement")}"))
                require(!placement.exists()){"This placement is already assigned. Choose the correct result before publishing."}
                val award=d.getLong("awardPaisa")?:0
                require(java.math.BigDecimal.valueOf(award)<=java.math.BigDecimal.valueOf(m.getLong("prizePaisa")?:0).add(java.math.BigDecimal.valueOf(m.getLong("perKillPaisa")?:0).multiply(java.math.BigDecimal.valueOf(d.getLong("kills")?:0)))) {"Check placement, kills and award."}
                tx.set(result,mapOf("match" to match,"title" to m.getString("title").orEmpty(),"titleSearch" to m.getString("title").orEmpty().lowercase(Locale.ROOT),"mode" to m.getString("mode").orEmpty(),"map" to m.getString("map").orEmpty(),"startsAt" to m.getTimestamp("startsAt")!!,"username" to d.getString("username").orEmpty(),"avatar" to d.getString("avatar").orEmpty(),"placement" to d.getLong("placement")!!,"kills" to d.getLong("kills")!!,"awardPaisa" to award,"creditedPaisa" to 0L,"settlementStatus" to if(award==0L)"not_applicable" else "pending","publishedAt" to FieldValue.serverTimestamp()))
                tx.set(link,mapOf("uid" to d.getString("uid")!!,"match" to match,"entry" to draft.id,"result" to id,"titleSearch" to m.getString("title").orEmpty().lowercase(Locale.ROOT),"publishedAt" to FieldValue.serverTimestamp()))
                tx.set(placement.reference,mapOf("entry" to draft.id,"result" to id,"match" to match,"placement" to d.getLong("placement")!!,"at" to FieldValue.serverTimestamp()))
                audit(tx,"result_$id","publishResult",result.path,actor)
            }
        }.continueWithTask {tx ->
            // A concurrent publisher may have committed the same immutable outcome before our write was denied.
            transactionFailure=tx.exception
            result.get(Source.SERVER)
        }.continueWithTask {published ->
            if(!published.isSuccessful || !published.result.exists()) throw transactionFailure?:published.exception?:IllegalStateException("Publication failed")
            link.get(Source.SERVER)
        }.addOnCompleteListener {
            if(session.uid==actor && session.allowed) {
                busy=false
                if(it.isSuccessful && it.result.getString("entry")==draft.id) {message="Result published. No wallet funds were credited.";done()}
                else {error=workflowFailure(it.exception?:transactionFailure);failed()}
            }
        }
    }
}
