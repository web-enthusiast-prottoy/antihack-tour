package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*

internal const val maxPaisa=9007199254740991L
internal fun prizeAward(placementPrize:Long,perKill:Long,kills:Long):Long {
    require(placementPrize>=0&&perKill>=0&&kills in 0..10000){"Check placement prize and kills."}
    val total=java.math.BigDecimal.valueOf(placementPrize).add(java.math.BigDecimal.valueOf(perKill).multiply(java.math.BigDecimal.valueOf(kills)))
    require(total<=java.math.BigDecimal.valueOf(maxPaisa)){"Prize amount exceeds the supported limit."}
    return total.longValueExact()
}

/** A private credit, public status, winnings delta, ledger, placement and progress commit together. */
internal class WinningsSettlement(private val session:AdminSession) {
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    var message by mutableStateOf("");private set
    private fun retry(attempt:Int=0,operation:()->Task<String>):Task<String> = operation().continueWithTask {task->
        if(!task.isSuccessful&&attempt<2&&(task.exception as? FirebaseFirestoreException)?.code==FirebaseFirestoreException.Code.PERMISSION_DENIED)retry(attempt+1,operation)else task
    }
    fun complete(matchId:String,done:()->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        busy=true;error="";message=""
        val ref=session.db.document("matches/$matchId")
        session.db.runTransaction {tx->
            val match=tx.get(ref)
            val progressRef=session.db.document("matchSettlements/$matchId")
            val progress=tx.get(progressRef)
            if(match.getString("status")!="settled") {
                require(match.getString("status")=="review"){"Move the match to results review first."}
                val joined=match.getLong("joined")?:0
                require((progress.getLong("processed")?:0)==joined){"Some confirmed participants still need result settlement."}
                require((progress.getLong("placementPaisa")?:0)<=(match.getLong("prizePaisa")?:0)&&(progress.getLong("creditedPaisa")?:0)<=maxPaisa){"Settlement totals require review."}
                if(!progress.exists())tx.set(progressRef,mapOf("processed" to 0L,"placementPaisa" to 0L,"creditedPaisa" to 0L,"lastEntry" to "","updatedAt" to FieldValue.serverTimestamp()))
                val revision=(match.getLong("revision")?:0)+1
                tx.update(ref,mapOf("status" to "settled","revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
                tx.set(ref.collection("history").document(revision.toString()),mapOf("from" to "review","to" to "settled","reason" to "","at" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/match_${matchId}_$revision"),mapOf("action" to "saveMatch","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }
        }.continueWithTask {task->if(!task.isSuccessful)throw task.exception?:IllegalStateException();ref.get(Source.SERVER)}.addOnCompleteListener {task->if(session.uid==actor&&session.allowed){
            busy=false
            if(task.isSuccessful&&task.result.getString("status")=="settled"){message="Match settlement complete.";done()}else error=workflowFailure(task.exception)
        }}
    }
    fun credit(reviewed:DocumentSnapshot,failed:()->Unit={},done:()->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        val resultId=reviewed.id
        busy=true;error="";message=""
        retry {session.db.runTransaction {tx->
            val link=tx.get(session.db.document("resultLinks/$resultId"))
            val entryId=link.getString("entry")?:throw IllegalArgumentException("Result account link is missing.")
            val uid=link.getString("uid")?:throw IllegalArgumentException("Result account link is missing.")
            val matchId=link.getString("match")?:throw IllegalArgumentException("Result account link is missing.")
            val creditRef=session.db.document("prizeCredits/$entryId")
            val existing=tx.get(creditRef)
            if(existing.exists()) {
                require(existing.getString("uid")==uid&&existing.getString("result")==resultId&&existing.getLong("amountPaisa")==reviewed.getLong("awardPaisa")){"This entry already has another settlement. Review its history."}
            } else {
                val result=tx.get(reviewed.reference)
                val entry=tx.get(session.db.document("entries/$entryId"))
                val match=tx.get(session.db.document("matches/$matchId"))
                val placement=result.getLong("placement")?:0
                val kills=result.getLong("kills")?:0
                val amount=result.getLong("awardPaisa")?:0
                val walletRef=session.db.document("wallets/$uid")
                val wallet=tx.get(walletRef)
                val progressRef=session.db.document("matchSettlements/$matchId")
                val progress=tx.get(progressRef)
                val claimRef=session.db.document("matchPlacements/${matchId}_$placement")
                val claim=tx.get(claimRef)
                val prize=tx.get(session.db.document("matches/$matchId/prizes/$placement"))
                require(entryId=="${uid}_$matchId"&&entry.getString("uid")==uid&&entry.getString("match")==matchId&&entry.getString("status")=="confirmed"&&result.getString("username")==entry.getString("username")){"Only the linked confirmed participant can receive this result."}
                require(amount==reviewed.getLong("awardPaisa")&&placement==reviewed.getLong("placement")&&kills==reviewed.getLong("kills")){"The result changed. Reload and review it."}
                require(match.getString("status")=="review"&&placement in 1..(match.getLong("joined")?:0)){"Check the confirmed participant count and results-review status."}
                require(!claim.exists()){ "This placement is already assigned. Choose the correct result before publishing." }
                val killReward=prizeAward(0,match.getLong("perKillPaisa")?:0,kills)
                val placementAmount=maxOf(0,amount-killReward)
                require(amount in 0..maxPaisa&&placementAmount<=(match.getLong("prizePaisa")?:0)){"Check placement, kills and award."}
                if(match.getBoolean("prizesEnabled")==true)require(amount==prizeAward(prize.getLong("amountPaisa")?:0,match.getLong("perKillPaisa")?:0,kills)){"The result does not match the saved prize terms."}
                val processed=(progress.getLong("processed")?:0)+1
                val placementTotal=Math.addExact(progress.getLong("placementPaisa")?:0,placementAmount)
                val creditedTotal=Math.addExact(progress.getLong("creditedPaisa")?:0,amount)
                require(processed<=(match.getLong("joined")?:0)&&placementTotal<=(match.getLong("prizePaisa")?:0)&&creditedTotal<=maxPaisa){"Settlement totals require review."}
                val oldWinnings=wallet.getLong("winnings")?:0
                val reserved=wallet.getLong("reservedWinnings")?:0
                val balanceAfter=Math.addExact(oldWinnings,amount)
                require(oldWinnings>=0&&reserved>=0&&balanceAfter in 0..maxPaisa&&balanceAfter<=maxPaisa-reserved){"Winnings balance requires reconciliation."}
                tx.set(creditRef,mapOf("uid" to uid,"match" to matchId,"result" to resultId,"placement" to placement,"kills" to kills,"amountPaisa" to amount,"placementPaisa" to placementAmount,"balanceAfter" to balanceAfter,"at" to FieldValue.serverTimestamp()))
                tx.update(result.reference,mapOf("creditedPaisa" to amount,"settlementStatus" to if(amount>0)"credited" else "not_applicable"))
                tx.set(claimRef,mapOf("entry" to entryId,"result" to resultId,"match" to matchId,"placement" to placement,"at" to FieldValue.serverTimestamp()))
                tx.set(progressRef,mapOf("processed" to processed,"placementPaisa" to placementTotal,"creditedPaisa" to creditedTotal,"lastEntry" to entryId,"updatedAt" to FieldValue.serverTimestamp()))
                if(amount>0) {
                    tx.set(walletRef,mapOf<String,Any>("winnings" to balanceAfter,"lastPrize" to entryId)+(if(!wallet.exists())mapOf("balance" to 0L,"reservedWinnings" to 0L)else emptyMap()),SetOptions.merge())
                    tx.set(session.db.document("walletLedger/prize_$entryId"),mapOf("uid" to uid,"delta" to amount,"type" to "prize","status" to "completed","currency" to "BDT","match" to matchId,"result" to resultId,"reference" to matchId,"balanceAfter" to balanceAfter,"at" to FieldValue.serverTimestamp()))
                }
                tx.set(session.db.document("auditRecords/prize_$entryId"),mapOf("action" to "creditPrize","path" to creditRef.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }
            creditRef.path
        }}.continueWithTask {task->
            if(!task.isSuccessful)throw task.exception?:IllegalStateException("Settlement failed")
            session.db.document(task.result).get(Source.SERVER)
        }.addOnCompleteListener {task->if(session.uid==actor&&session.allowed){
            busy=false
            if(task.isSuccessful&&task.result.getString("result")==resultId&&task.result.getLong("amountPaisa")==reviewed.getLong("awardPaisa")){message=if((task.result.getLong("amountPaisa")?:0)>0)"Prize credited to winnings." else "Zero-prize result settled.";done()}
            else {error=workflowFailure(task.exception);failed()}
        }}
    }
}
