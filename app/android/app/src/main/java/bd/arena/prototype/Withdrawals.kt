package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import java.util.Locale

internal fun withdrawalTotal(amount:Long,fee:Long):Long {
    require(amount in 1..maxPaisa&&fee in 0..maxPaisa&&amount<=maxPaisa-fee){"Check withdrawal amount and fee."}
    return amount+fee
}

internal class Withdrawals(private val session:AdminSession) {
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    private fun retry(attempt:Int=0,operation:()->Task<String>):Task<String> = operation().continueWithTask {task->
        if(!task.isSuccessful&&attempt<2&&(task.exception as? FirebaseFirestoreException)?.code==FirebaseFirestoreException.Code.PERMISSION_DENIED)retry(attempt+1,operation)else task
    }
    private fun finish(task:Task<String>,actor:String,staff:Boolean,done:(DocumentSnapshot)->Unit) {
        task.continueWithTask {result->if(!result.isSuccessful)throw result.exception?:IllegalStateException();session.db.document(result.result).get(Source.SERVER)}.addOnCompleteListener {result->
            if(session.uid==actor&&(!staff||session.allowed)){busy=false;if(result.isSuccessful&&result.result.exists())done(result.result)else error=workflowFailure(result.exception)}
        }
    }
    fun policy(minimum:Long,fee:Long,enabled:Boolean,expectedRevision:Long,done:()->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        if(minimum<1||fee<0||minimum>maxPaisa-fee){error="Check withdrawal minimum and fee.";return}
        busy=true;error=""
        val ref=session.db.document("withdrawalPolicy/bkash")
        finish(session.db.runTransaction {tx->
            val old=tx.get(ref)
            require((old.getLong("revision")?:0)==expectedRevision){"Withdrawal settings changed. Reload before saving."}
            val revision=expectedRevision+1
            tx.set(ref,mapOf("minimumPaisa" to minimum,"feePaisa" to fee,"enabled" to enabled,"currency" to "BDT","revision" to revision,"updatedAt" to FieldValue.serverTimestamp()))
            tx.set(session.db.document("auditRecords/withdrawal_policy_bkash_$revision"),mapOf("action" to "saveWithdrawalPolicy","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            ref.path
        },actor,true){done()}
    }
    fun submit(id:String,amount:Long,destination:String,reviewedPolicy:DocumentSnapshot,done:(DocumentSnapshot)->Unit) {
        if(busy)return
        val actor=session.uid?:return
        val fee=reviewedPolicy.getLong("feePaisa")?:0
        val minimum=reviewedPolicy.getLong("minimumPaisa")?:0
        val revision=reviewedPolicy.getLong("revision")?:0
        if(!Regex("[A-Za-z0-9]{20}").matches(id)||!Regex("01[3-9][0-9]{8}").matches(destination)||amount<minimum){error="Check the amount and payout number.";return}
        val total=try{withdrawalTotal(amount,fee)}catch(_:Exception){error="Check withdrawal amount and fee.";return}
        busy=true;error=""
        val ref=session.db.document("withdrawals/$id")
        finish(retry {session.db.runTransaction {tx->
            val old=tx.get(ref)
            if(old.exists())require(old.getString("uid")==actor&&old.getLong("amountPaisa")==amount&&old.getLong("feePaisa")==fee&&old.getString("destination")==destination){"This request already has different details. Reload your requests."}
            else {
                val policy=tx.get(session.db.document("withdrawalPolicy/bkash"))
                val walletRef=session.db.document("wallets/$actor")
                val wallet=tx.get(walletRef)
                val profile=tx.get(session.db.document("users/$actor"))
                require(policy.getBoolean("enabled")==true&&policy.getLong("revision")==revision&&policy.getLong("feePaisa")==fee&&policy.getLong("minimumPaisa")==minimum&&minimum>=1&&amount>=minimum){"Withdrawal settings changed. Reload and review the request."}
                val available=wallet.getLong("winnings")?:0
                val reserved=wallet.getLong("reservedWinnings")?:0
                require(available>=total&&reserved>=0&&reserved<=maxPaisa-total){"Not enough available winnings."}
                tx.set(ref,mapOf("uid" to actor,"username" to profile.getString("ffUsername").orEmpty(),"requestId" to id,"provider" to "bkash","currency" to "BDT","amountPaisa" to amount,"feePaisa" to fee,"totalPaisa" to total,"minimumPaisa" to minimum,"policyRevision" to revision,"destination" to destination,"status" to "pending","revision" to 1L,"createdAt" to FieldValue.serverTimestamp()))
                tx.update(walletRef,mapOf("winnings" to available-total,"reservedWinnings" to reserved+total,"lastWithdrawal" to id))
            }
            ref.path
        }},actor,false,done)
    }
    fun decide(reviewed:DocumentSnapshot,target:String,reason:String="",reference:String="",noTransfer:Boolean=false,sentConfirmed:Boolean=false,done:()->Unit) {
        if(busy||!session.allowed)return
        val actor=session.uid?:return
        val ref=reviewed.reference
        val receipt=reference.trim().uppercase(Locale.ROOT)
        val explanation=reason.trim()
        if(target !in listOf("approved","rejected","sent")||target=="rejected"&&(!noTransfer||explanation.isBlank()||explanation.length>1000)||target=="sent"&&(!sentConfirmed||!Regex("[A-Z0-9-]{6,80}").matches(receipt))){error="Confirm transfer status and check the decision details.";return}
        busy=true;error=""
        finish(retry {session.db.runTransaction {tx->
            val old=tx.get(ref)
            if(old.getString("status")==target)require(target!="sent"||old.getString("payoutReference")==receipt){"This request has another payout reference. Reload it."}
            else {
                require(old.getLong("revision")==reviewed.getLong("revision")){"This request changed. Reload before deciding."}
                require(old.getString("status") in (if(target=="sent")listOf("approved")else if(target=="approved")listOf("pending")else listOf("pending","approved"))){"This request has already been decided."}
                val uid=old.getString("uid")!!
                val amount=old.getLong("amountPaisa")?:0
                val total=withdrawalTotal(amount,old.getLong("feePaisa")?:0)
                require(old.getLong("totalPaisa")==total&&old.getString("provider")=="bkash"&&old.getString("currency")=="BDT"){"Review withdrawal totals before deciding."}
                val walletRef=session.db.document("wallets/$uid")
                val wallet=tx.get(walletRef)
                val available=wallet.getLong("winnings")?:0
                val reserved=wallet.getLong("reservedWinnings")?:0
                require(available>=0&&reserved>=total){"Withdrawal reserves require reconciliation."}
                val payout=session.db.document("payoutReferences/bkash_$receipt")
                if(target=="sent")require(!tx.get(payout).exists()){ "This transfer reference has already been used." }
                val revision=(old.getLong("revision")?:0)+1
                val values=mutableMapOf<String,Any>("status" to target,"revision" to revision,"decidedBy" to actor,"decidedAt" to FieldValue.serverTimestamp(),"reason" to if(target=="rejected")explanation else "")
                if(target=="rejected")values["noTransferConfirmed"]=true
                if(target=="sent")values["payoutReference"]=receipt
                tx.update(ref,values)
                if(target!="approved")tx.update(walletRef,mapOf("winnings" to if(target=="rejected")Math.addExact(available,total)else available,"reservedWinnings" to reserved-total,"lastWithdrawal" to ref.id))
                if(target=="sent") {
                    tx.set(payout,mapOf("withdrawal" to ref.id,"amountPaisa" to amount,"destination" to old.getString("destination").orEmpty(),"at" to FieldValue.serverTimestamp()))
                    tx.set(session.db.document("walletLedger/withdrawal_${ref.id}"),mapOf("uid" to uid,"delta" to -total,"type" to "withdrawal","status" to "completed","currency" to "BDT","withdrawal" to ref.id,"reference" to receipt,"balanceAfter" to available,"at" to FieldValue.serverTimestamp()))
                }
                tx.set(session.db.document("auditRecords/withdrawal_${ref.id}_$revision"),mapOf("action" to if(target=="approved")"approveWithdrawal" else if(target=="sent")"sendWithdrawal" else "rejectWithdrawal","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }
            ref.path
        }},actor,true){done()}
    }
}
