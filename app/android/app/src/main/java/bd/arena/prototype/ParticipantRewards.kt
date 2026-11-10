package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.util.Locale

internal fun participantKills(values:List<String>):Long = values.sumOf { value ->
    if(value.isBlank())0L else value.toLong().also { require(it in 0..10000){"Enter whole kill counts from 0 to 10000."} }
}.also { require(it in 0..10000){"Enter whole kill counts from 0 to 10000."} }

internal data class KillDraftInput(val entry:DocumentSnapshot,val values:List<String>,val revision:Long)
internal data class ReviewedKills(val entry:DocumentSnapshot,val revision:Long,val kills:Long,val rate:Long,val previousKills:Long)

/** Placement and kill credits have separate immutable keys; the public result is their summary. */
internal class ParticipantRewards(private val session:AdminSession) {
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    var message by mutableStateOf("");private set
    var completed by mutableStateOf(0);private set
    var total by mutableStateOf(0);private set
    private fun <T> run(task:()->Task<T>,done:(T)->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid
        busy=true;error="";message="";completed=0
        task().addOnCompleteListener { result -> if(session.uid==actor&&session.allowed) {
            busy=false
            if(result.isSuccessful)done(result.result) else error=workflowFailure(result.exception)
        } }
    }
    fun saveDrafts(inputs:List<KillDraftInput>,rate:Long,saved:(ReviewedKills)->Unit={},done:(List<ReviewedKills>)->Unit) {
        total=inputs.size
        run({
            inputs.fold(Tasks.forResult(emptyList<ReviewedKills>())) { chain,input -> chain.continueWithTask { previous ->
                if(!previous.isSuccessful)throw previous.exception?:IllegalStateException()
                saveDraft(input,rate).continueWith { result ->
                    if(!result.isSuccessful)throw result.exception?:IllegalStateException()
                    completed++;saved(result.result);previous.result+result.result
                }
            } }
        }){message="Kill draft saved. No wallet funds were credited.";done(it)}
    }
    private fun saveDraft(input:KillDraftInput,rate:Long):Task<ReviewedKills> {
        val actor=session.uid!!
        val fresh=session.db.collection("results").document().id
        val ref=session.db.document("killDrafts/${input.entry.id}")
        val rosterSize=(input.entry.get("roster") as? List<*>)?.size?:1
        val counts=if(input.values.size==1&&rosterSize>1)emptyList()else input.values.map { if(it.isBlank())0L else it.toLong() }
        val kills=participantKills(input.values)
        return session.db.runTransaction { tx ->
            val entry=tx.get(input.entry.reference)
            val old=tx.get(ref)
            val legacy=tx.get(session.db.document("resultDrafts/${entry.id}"))
            val paid=tx.get(session.db.document("prizeCredits/${entry.id}"))
            val final=tx.get(session.db.document("rewardCredits/${entry.id}_kills"))
            val match=entry.getString("match")!!
            val terms=tx.get(session.db.document("matches/$match"))
            require(entry.getString("status")=="confirmed"){"Only confirmed participants can receive results."}
            require(!final.exists()){"Kill rewards are already finalized. Refresh the results."}
            require((old.getLong("revision")?:0)==input.revision){"The kill draft changed. Refresh and review it again."}
            require(terms.getLong("perKillPaisa")==rate){"The match reward changed. Refresh and review it again."}
            require(kills>=(paid.getLong("kills")?:0)){"Previously credited kills cannot be reduced."}
            val id=old.getString("publicId")?:legacy.getString("publicId")?:fresh
            val revision=input.revision+1
            tx.set(ref,mapOf("uid" to entry.getString("uid")!!,"match" to match,"publicId" to id,"kills" to kills,"memberKills" to counts,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            tx.set(session.db.document("auditRecords/kills_${entry.id}_$revision"),mapOf("action" to "saveKillDraft","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            ReviewedKills(entry,revision,kills,rate,paid.getLong("kills")?:0)
        }
    }
    fun creditKills(rows:List<ReviewedKills>,reason:String,done:()->Unit) {
        total=rows.size
        run({rows.fold(Tasks.forResult("")) { chain,row -> chain.continueWithTask { previous ->
            if(!previous.isSuccessful)throw previous.exception?:IllegalStateException()
            credit(row.entry,"kills",0,row.revision,row.rate,reason).continueWith { paid ->
                if(!paid.isSuccessful)throw paid.exception?:IllegalStateException()
                completed++;paid.result
            }
        } }}){message="Kill rewards finalized.";done()}
    }
    fun creditPlacement(entry:DocumentSnapshot,rank:Long,matchRevision:Long,done:()->Unit) {
        total=1
        run({credit(entry,"placement",rank,matchRevision,0,"")}){message="Placement prize credited.";done()}
    }
    private fun credit(reviewed:DocumentSnapshot,kind:String,rank:Long,revision:Long,rate:Long,reason:String):Task<String> {
        val actor=session.uid!!
        val fresh=session.db.collection("results").document().id
        val key="${reviewed.id}_$kind"
        val creditRef=session.db.document("rewardCredits/$key")
        fun attempt():Task<String> = session.db.runTransaction { tx ->
            val existing=tx.get(creditRef)
            if(existing.exists()) {
                require(existing.getString("entry")==reviewed.id&&existing.getString("kind")==kind){"This entry already has another settlement. Review its history."}
                if(kind=="placement")require(existing.getLong("placement")==rank){"This placement or participant already has a winner."}
                if(kind=="kills")require(existing.getLong("draftRevision")==revision){"The kill draft changed. Refresh and review it again."}
            } else {
                val entry=tx.get(reviewed.reference)
                val uid=entry.getString("uid")!!;val matchId=entry.getString("match")!!
                val match=tx.get(session.db.document("matches/$matchId"))
                val draftRef=session.db.document("killDrafts/${entry.id}")
                val draft=tx.get(draftRef)
                val legacyDraft=tx.get(session.db.document("resultDrafts/${entry.id}"))
                val legacyCredit=tx.get(session.db.document("prizeCredits/${entry.id}"))
                val id=draft.getString("publicId")?:legacyDraft.getString("publicId")?:fresh
                val resultRef=session.db.document("results/$id")
                val before=tx.get(resultRef)
                val walletRef=session.db.document("wallets/$uid")
                val wallet=tx.get(walletRef)
                val progressRef=session.db.document("matchSettlements/$matchId")
                val progress=tx.get(progressRef)
                val placement=if(kind=="placement")rank else before.getLong("placement")?:0
                val claimRef=session.db.document("matchPlacements/${matchId}_$placement")
                val publicClaimRef=session.db.document("publishedPlacements/${matchId}_$placement")
                val claim=if(kind=="placement")tx.get(claimRef)else null
                val publicClaim=if(kind=="placement")tx.get(publicClaimRef)else null
                val prize=if(kind=="placement")tx.get(session.db.document("matches/$matchId/prizes/$rank"))else null
                require(entry.getString("status")=="confirmed"&&entry.id=="${uid}_$matchId"){"Only confirmed participants can receive results."}
                require(!before.exists()||before.getLong("rewardVersion")==2L||legacyCredit.exists()){"Retry the pending legacy credit before entering kills."}
                if(kind=="placement") {
                    require(match.getLong("revision")==revision){"The match reward changed. Refresh and review it again."}
                    require(rank in 1..(match.getLong("capacity")?:0)&&(prize?.getLong("amountPaisa")?:0)>0){"Check placement prize and kills."}
                    require((before.getLong("placement")?:0)==0L&&!legacyCredit.exists()&&claim?.exists()==false&&publicClaim?.exists()==false){"This placement or participant already has a winner."}
                } else {
                    require(draft.exists()&&draft.getLong("revision")==revision){"The kill draft changed. Refresh and review it again."}
                    require(match.getLong("perKillPaisa")==rate){"The match reward changed. Refresh and review it again."}
                }
                val oldKills=before.getLong("kills")?:0
                val kills=if(kind=="kills")draft.getLong("kills")!! else oldKills
                val oldKillPaisa=before.getLong("killPaisa")?:legacyCredit.let{(it.getLong("amountPaisa")?:0)-(it.getLong("placementPaisa")?:0)}
                val killPaisa=if(kind=="kills")prizeAward(0,match.getLong("perKillPaisa")?:0,kills)else oldKillPaisa
                require(kills>=oldKills&&killPaisa>=oldKillPaisa){"Previously credited kills cannot be reduced."}
                val placementPaisa=if(kind=="placement")prize!!.getLong("amountPaisa")!! else before.getLong("placementPaisa")?:legacyCredit.getLong("placementPaisa")?:0
                val amount=if(kind=="placement")placementPaisa else killPaisa-oldKillPaisa
                require(kind!="kills"||!legacyCredit.exists()||amount==0L||reason.trim().isNotEmpty()){"Enter a reason for the additional kill credit."}
                val credited=Math.addExact(before.getLong("creditedPaisa")?:0,amount)
                val balanceAfter=Math.addExact(wallet.getLong("winnings")?:0,amount)
                require(credited in 0..maxPaisa&&balanceAfter in 0..maxPaisa&&(wallet.getLong("reservedWinnings")?:0) in 0..(maxPaisa-balanceAfter)){"Winnings balance requires reconciliation."}
                val processedDelta=if(kind=="kills"&&!legacyCredit.exists())1L else 0L
                val processed=(progress.getLong("processed")?:0)+processedDelta
                val placementTotal=(progress.getLong("placementPaisa")?:0)+(if(kind=="placement")amount else 0)
                val creditedTotal=Math.addExact(progress.getLong("creditedPaisa")?:0,amount)
                require(processed<=(match.getLong("joined")?:0)&&placementTotal<=(match.getLong("prizePaisa")?:0)&&creditedTotal<=maxPaisa){"Settlement totals require review."}
                if(!draft.exists())tx.set(draftRef,mapOf("uid" to uid,"match" to matchId,"publicId" to id,"kills" to 0L,"memberKills" to emptyList<Long>(),"revision" to 1L,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
                val summary=if(before.exists())before.data!!.toMutableMap()else mutableMapOf<String,Any>("match" to matchId,"title" to match.getString("title").orEmpty(),"titleSearch" to match.getString("title").orEmpty().lowercase(Locale.ROOT),"mode" to match.getString("mode").orEmpty(),"map" to match.getString("map").orEmpty(),"startsAt" to match.getTimestamp("startsAt")!!,"username" to entry.getString("username").orEmpty(),"avatar" to "solo","publishedAt" to FieldValue.serverTimestamp())
                summary.putAll(mapOf("rewardVersion" to 2L,"placement" to placement,"kills" to kills,"placementPaisa" to placementPaisa,"killPaisa" to killPaisa,"awardPaisa" to credited,"creditedPaisa" to credited,"settlementStatus" to if(credited>0)"credited" else "not_applicable","killFinalized" to (kind=="kills"||before.getBoolean("killFinalized")==true),"lastReward" to key))
                tx.set(resultRef,summary)
                if(!before.exists())tx.set(session.db.document("resultLinks/$id"),mapOf("uid" to uid,"match" to matchId,"entry" to entry.id,"result" to id,"titleSearch" to summary["titleSearch"]!!,"publishedAt" to FieldValue.serverTimestamp()))
                tx.set(creditRef,mapOf("entry" to entry.id,"uid" to uid,"match" to matchId,"result" to id,"kind" to kind,"placement" to placement,"kills" to kills,"previousKills" to oldKills,"previousKillPaisa" to oldKillPaisa,"amountPaisa" to amount,"placementPaisa" to if(kind=="placement")amount else 0L,"processedDelta" to processedDelta,"draftRevision" to if(kind=="kills")revision else 0L,"reason" to reason.trim(),"balanceAfter" to balanceAfter,"at" to FieldValue.serverTimestamp(),"actor" to actor))
                tx.set(progressRef,mapOf("processed" to processed,"placementPaisa" to placementTotal,"creditedPaisa" to creditedTotal,"lastEntry" to entry.id,"lastReward" to key,"updatedAt" to FieldValue.serverTimestamp()))
                if(kind=="placement") {
                    val data=mapOf("entry" to entry.id,"result" to id,"match" to matchId,"placement" to rank,"at" to FieldValue.serverTimestamp())
                    tx.set(claimRef,data);tx.set(publicClaimRef,data)
                }
                if(amount>0) {
                    tx.set(walletRef,mapOf<String,Any>("winnings" to balanceAfter,"lastReward" to key)+(if(!wallet.exists())mapOf("balance" to 0L,"reservedWinnings" to 0L)else emptyMap()),SetOptions.merge())
                    tx.set(session.db.document("walletLedger/reward_$key"),mapOf("uid" to uid,"delta" to amount,"type" to "prize","status" to "completed","currency" to "BDT","match" to matchId,"result" to id,"reference" to matchId,"reward" to key,"component" to kind,"balanceAfter" to balanceAfter,"at" to FieldValue.serverTimestamp()))
                }
                tx.set(session.db.document("auditRecords/reward_$key"),mapOf("action" to "creditParticipantReward","path" to creditRef.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }
            key
        }
        fun retry(left:Int):Task<String> = attempt().continueWithTask { result ->
            if(!result.isSuccessful&&left>0&&(result.exception as? FirebaseFirestoreException)?.code==FirebaseFirestoreException.Code.PERMISSION_DENIED)retry(left-1)
            else if(!result.isSuccessful)Tasks.forException(result.exception?:IllegalStateException())else Tasks.forResult(result.result)
        }
        return retry(2)
    }
}
