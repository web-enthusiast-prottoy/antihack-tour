package bd.arena.prototype

import com.google.firebase.firestore.*
import java.util.Locale

/** Direct client transactions; every monetary write is coupled and authorized by deployed rules. */
internal class LiveWallet(private val session:AdminSession) {
    fun operate(action:String,values:Map<String,Any>,done:(String?,Exception?)->Unit) {
        val uid=session.uid?:return
        if(action=="deposit") {
            val amount=(values["amount"] as? Number)?.toLong()?:0
            val sender=values["sender"]?.toString().orEmpty().trim()
            val reference=values["reference"]?.toString().orEmpty().trim().uppercase(Locale.ROOT)
            val requestId=values["requestId"]?.toString().orEmpty().uppercase(Locale.ROOT)
            val receiver=values["receiver"]?.toString().orEmpty()
            if(amount !in 1..10000000||!validDepositSender(sender)||!Regex("[A-Z0-9-]{6,80}").matches(reference)||!Regex("[A-Z0-9-]{6,80}").matches(requestId)){done(null,IllegalArgumentException("Check the amount, transaction ID and sending number."));return}
            val ref=session.db.document("walletDeposits/${uid}_$requestId")
            session.db.runTransaction {tx->
                val old=tx.get(ref)
                if(old.exists())require(old.getString("uid")==uid&&old.getLong("amount")==amount&&old.getString("reference")==reference&&old.getString("sender")==sender&&old.getString("receiver")==receiver){"This request already has different details. Start a new request."}
                else {
                    val settings=tx.get(session.db.document("paymentInstructions/bkash"))
                    require(settings.getBoolean("enabled")==true){"Deposits are not enabled. Contact the admin."}
                    require(receiver==(settings.getString("number"))){"The receiving number changed. Reopen Add Money before submitting."}
                    tx.set(ref,mapOf("uid" to uid,"requestId" to requestId,"amount" to amount,"reference" to reference,"sender" to sender,"receiver" to receiver,"provider" to "bkash","currency" to "BDT","status" to "pending","createdAt" to FieldValue.serverTimestamp()))
                }
            }.continueWithTask {task->if(!task.isSuccessful)throw task.exception?:IllegalStateException();ref.get(Source.SERVER)}.addOnCompleteListener {task->
                if(session.uid==uid){if(task.isSuccessful)done(if(task.result.getString("status")=="pending")"Deposit submitted for review. No funds have been credited." else "Existing request: "+task.result.getString("status"),null)else done(null,task.exception)}
            }
            return
        }
        if(action !in listOf("approveDeposit","rejectDeposit")||!session.allowed){done(null,IllegalArgumentException("Staff access required."));return}
        val id=values["id"]?.toString().orEmpty()
        if(!Regex("[A-Za-z0-9_-]{6,220}").matches(id)){done(null,IllegalArgumentException("Invalid request reference."));return}
        val ref=session.db.document("walletDeposits/$id")
        val target=if(action=="approveDeposit")"approved" else "rejected"
        retryDecision { session.db.runTransaction {tx->
            val claim=tx.get(ref)
            require(claim.exists()){ "Request not found." }
            if(claim.getString("status")!=target) {
                require(claim.getString("status")=="pending"){"This request has already been decided. Refresh the queue."}
                val owner=claim.getString("uid")!!
                val amount=claim.getLong("amount")!!
                val reference=claim.getString("reference")!!
                val receiver=claim.getString("receiver")!!
                val reason: String
                if(action=="approveDeposit") {
                    require(values["receiptConfirmed"]==true&&(values["verifiedAmount"] as? Number)?.toLong()==amount&&values["verifiedReference"]?.toString()?.trim()?.uppercase(Locale.ROOT)==reference&&values["verifiedSender"]?.toString()?.trim()==claim.getString("sender")&&values["verifiedReceiver"]==receiver){"Receipt details differ. Reject with a reason rather than changing the claim."}
                    val wallet=session.db.document("wallets/$owner")
                    val receipt=session.db.document("verifiedReceipts/${receiver}_$reference")
                    val old=tx.get(wallet)
                    val used=tx.get(receipt)
                    require(!used.exists()){ "This transaction has already been credited." }
                    val balance=old.getLong("balance")?:0L
                    require(amount>0&&balance>=0&&balance<=9007199254740991L-amount){"Wallet balance requires reconciliation."}
                    tx.set(wallet,mapOf("balance" to balance+amount,"lastDeposit" to id),SetOptions.merge())
                    tx.set(receipt,mapOf("deposit" to id,"provider" to "bkash","receiver" to receiver,"reference" to reference))
                    tx.set(session.db.document("walletLedger/live_deposit_$id"),mapOf("uid" to owner,"delta" to amount,"type" to "deposit","status" to "completed","currency" to "BDT","deposit" to id,"reference" to reference,"receiver" to receiver,"balanceAfter" to balance+amount,"at" to FieldValue.serverTimestamp()))
                    reason="Receipt checked against the submitted claim."
                } else {
                    reason=values["reason"]?.toString().orEmpty().trim()
                    require(reason.isNotBlank()&&reason.length<=1000){"Enter a rejection reason (up to 1000 characters)."}
                }
                tx.update(ref,mapOf("status" to target,"decisionReason" to reason,"reviewedBy" to uid,"reviewedAt" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/live_deposit_$id"),mapOf("deposit" to id,"action" to action,"path" to ref.path,"actor" to uid,"at" to FieldValue.serverTimestamp()))
            }
        } }.continueWithTask {task->if(!task.isSuccessful)throw task.exception?:IllegalStateException();ref.get(Source.SERVER)}.addOnCompleteListener {task->
            if(session.uid==uid&&session.allowed){if(task.isSuccessful)done(if(target=="approved")"Approved. Wallet credited." else "Rejected. No funds credited.",null)else done(null,task.exception)}
        }
    }
    // Rules may see another admin's wallet commit before the SDK reports its version conflict.
    // Retry from a fresh transaction; authorization failures still fail after this bounded retry.
    private fun <T> retryDecision(attempt:Int=0,run:()->com.google.android.gms.tasks.Task<T>):com.google.android.gms.tasks.Task<T> =
        run().continueWithTask {task->
            if(!task.isSuccessful&&attempt<2&&(task.exception as? FirebaseFirestoreException)?.code==FirebaseFirestoreException.Code.PERMISSION_DENIED)retryDecision(attempt+1,run)else task
        }
}
