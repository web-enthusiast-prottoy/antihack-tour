package bd.arena.prototype

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*

/** Requested entries do not charge funds or reserve a seat; confirmation does both atomically. */
internal class PaidEntries(private val session:AdminSession) {
    private fun retry(attempt:Int=0,run:()->Task<String>):Task<String> = run().continueWithTask{task->
        if(!task.isSuccessful&&attempt<2&&(task.exception as? FirebaseFirestoreException)?.code==FirebaseFirestoreException.Code.PERMISSION_DENIED)retry(attempt+1,run)else task
    }
    fun operate(action:String,values:Map<String,Any>,done:(String?,Exception?)->Unit) {
        val actor=session.uid?:return
        val joining=action=="join"
        if(!joining&&!session.allowed){done(null,IllegalArgumentException("Staff access required."));return}
        val id=if(joining)"${actor}_${values["match"]}" else values["id"]?.toString().orEmpty()
        if(!Regex("[A-Za-z0-9_-]{6,220}").matches(id)||action !in listOf("join","confirmEntry","refundEntry","rejectEntry")){done(null,IllegalArgumentException("Invalid entry reference."));return}
        val ref=session.db.document("entries/$id")
        retry {session.db.runTransaction{tx->
            val entry=tx.get(ref)
            if(joining) {
                if(!entry.exists()) {
                    val match=values["match"]!!.toString()
                    val m=tx.get(session.db.document("matches/$match"))
                    val p=tx.get(session.db.document("users/$actor"))
                    val wallet=tx.get(session.db.document("wallets/$actor"))
                    val name=p.getString("ffUsername").orEmpty()
                    val roster=(values["roster"] as? org.json.JSONArray)?.let{array->(0 until array.length()).map{array.getString(it)}}?:listOf(name)
                    val fee=m.getLong("entryPaisa")?:0
                    require(m.getString("status")=="open"&&(m.getLong("joined")?:0)<(m.getLong("capacity")?:0)){"Match full or closed."}
                    require(fee in 1..maxPaisa&&(wallet.getLong("balance")?:0)>=fee){"Not enough deposit balance for this entry."}
                    require(validRoster(m.getString("mode").orEmpty(),roster,name)){"Check roster size and distinct game names. The match format may have changed."}
                    tx.set(ref,mapOf("uid" to actor,"match" to match,"username" to name,"roster" to roster,"status" to "requested","fee" to fee,"createdAt" to FieldValue.serverTimestamp()))
                }
            }else {
                require(entry.exists()){ "Request not found." }
                val target=when(action){"confirmEntry"->"confirmed";"refundEntry"->"refunded";else->"rejected"}
                if(entry.getString("status")!=target){
                    val uid=entry.getString("uid")!!;val match=entry.getString("match")!!
                    val matchRef=session.db.document("matches/$match");val m=tx.get(matchRef)
                    val walletRef=session.db.document("wallets/$uid");val wallet=tx.get(walletRef)
                    val fee=entry.getLong("fee")?:0;val balance=wallet.getLong("balance")?:0
                    require(fee in 1..maxPaisa&&balance in 0..maxPaisa){"Wallet balance requires reconciliation."}
                    if(action=="rejectEntry"){
                        val reason=values["reason"]?.toString().orEmpty().trim()
                        require(entry.getString("status")=="requested"&&reason.isNotBlank()&&reason.length<=1000){"Enter a rejection reason (up to 1000 characters)."}
                        tx.update(ref,mapOf("status" to "rejected","decisionReason" to reason,"reviewedBy" to actor,"reviewedAt" to FieldValue.serverTimestamp()))
                    }else {
                        val refund=action=="refundEntry"
                        if(refund)require(entry.getString("status")=="confirmed"&&m.getString("status")=="cancelled"&&balance<=maxPaisa-fee){"Cancel the match before refunding a confirmed paid entry."}
                        else {
                            val p=tx.get(session.db.document("users/$uid"))
                            require(entry.getString("status")=="requested"&&m.getString("status")=="open"&&(m.getLong("joined")?:0)<(m.getLong("capacity")?:0)){"Match full, closed or entry already decided."}
                            @Suppress("UNCHECKED_CAST") val roster=entry.get("roster") as? List<String>?:listOf(entry.getString("username").orEmpty())
                            require(fee==m.getLong("entryPaisa")&&balance>=fee&&validRoster(m.getString("mode").orEmpty(),roster,entry.getString("username").orEmpty())){"Profile, funds or entry fee changed. Reload before deciding."}
                            val publicId=session.db.collection("participantLinks").document().id
                            val avatar=p.getString("avatar")?:"solo"
                            tx.set(session.db.document("participantLinks/$publicId"),mapOf("entry" to id))
                            tx.set(session.db.document("matches/$match/participants/$publicId"),mapOf("username" to entry.getString("username").orEmpty(),"roster" to roster,"avatar" to avatar))
                            tx.update(ref,mapOf("publicId" to publicId,"avatar" to avatar))
                            tx.update(matchRef,mapOf("joined" to (m.getLong("joined")?:0)+1,"lastEntry" to id))
                        }
                        val after=if(refund)balance+fee else balance-fee
                        tx.update(ref,mapOf("status" to target,"reviewedBy" to actor,"reviewedAt" to FieldValue.serverTimestamp()))
                        tx.update(walletRef,mapOf("balance" to after,(if(refund)"lastRefund" else "lastEntry") to id))
                        tx.set(session.db.document("walletLedger/${if(refund)"refund" else "entry"}_$id"),mapOf("uid" to uid,"delta" to if(refund)fee else -fee,"type" to if(refund)"refund" else "entry_fee","status" to "completed","currency" to "BDT","entry" to id,"match" to match,"reference" to match,"balanceAfter" to after,"at" to FieldValue.serverTimestamp()))
                    }
                    tx.set(session.db.document("auditRecords/${action}_$id"),mapOf("action" to action,"path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
                }
            }
            ref.path
        }}.continueWithTask{task->if(!task.isSuccessful)throw task.exception?:IllegalStateException();session.db.document(task.result).get(Source.SERVER)}.addOnCompleteListener{task->if(session.uid==actor&&(joining||session.allowed)){
            if(task.isSuccessful)done(if(joining&&task.result.getString("status")=="requested")"Entry saved. Paid entries await confirmation." else if(joining)"Existing entry: "+task.result.getString("status") else if(action=="refundEntry")"Entry fee refunded to deposit balance." else if(action=="rejectEntry")"Entry rejected. No funds charged." else "Entry confirmed. Deposit balance charged once.",null)else done(null,task.exception)
        }}
    }
}
