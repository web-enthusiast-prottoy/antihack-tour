package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.firestore.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.os.Handler
import android.os.Looper

internal data class DepositActivity(val amount:Long,val reference:String,val status:String,val id:String="",val reason:String="",val createdAt:com.google.firebase.Timestamp?=null,val reviewedAt:com.google.firebase.Timestamp?=null)

internal class PlayerData(val session: AdminSession) {
    var profileLoading by mutableStateOf(true)
    var profile by mutableStateOf<Map<String,String>?>(null)
    var balance by mutableStateOf(0L)
    var winnings by mutableStateOf(0L)
    val entryBalance:Long get()=balance+winnings
    var reservedWinnings by mutableStateOf(0L)
    var balanceCached by mutableStateOf(true)
    var walletRefreshing by mutableStateOf(false);private set
    fun refreshWallet() {
        val uid=session.uid?:return
        if(walletRefreshing)return
        walletRefreshing=true
        session.db.document("wallets/$uid").get(Source.SERVER).addOnCompleteListener {task->
            if(session.uid==uid) {
                walletRefreshing=false
                // The existing live listener remains the single writer of balance values.
                if(!task.isSuccessful) {
                    balanceCached=true;error="Wallet could not refresh. Reconnect and retry."
                }else if(error=="Wallet could not refresh. Reconnect and retry.")error=""
            }
        }
    }
    var matchesLoading by mutableStateOf(true)
    var matches by mutableStateOf<List<Tournament>>(emptyList())
    var matchesCached by mutableStateOf(true)
    var matchesMore by mutableStateOf(false)
    var matchesError by mutableStateOf(false)
    private var matchFeed:BoundedFeed?=null
    fun loadMoreMatches(){matchFeed?.loadMore()}
    fun retryMatches(){matchFeed?.retry()}
    var entries by mutableStateOf<Map<String,String>>(emptyMap())
    var rosters by mutableStateOf<Map<String,List<String>>>(emptyMap())
    var deposits by mutableStateOf<List<DepositActivity>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf("")
    var busy by mutableStateOf(false)
    private val listeners = mutableListOf<ListenerRegistration>()
    fun start(uid: String?) {
        close(); profileLoading=uid!=null; profile=null; balance=0; winnings=0; reservedWinnings=0; balanceCached=true; entries=emptyMap(); rosters=emptyMap(); matches=emptyList(); matchesLoading=true; matchesCached=true; deposits=emptyList(); loading=true; error=""
        fun listen(query: Query, block: (QuerySnapshot)->Unit) { listeners += query.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s,e -> if(session.uid==uid) { if(e!=null){matchesLoading=false;error="Could not refresh data. Check connection and retry."}; if(s!=null){if(error=="Could not refresh data. Check connection and retry.")error="";block(s)} } } }
        matchesMore=false;matchesError=false
        matchFeed=BoundedFeed(session.db.collection("matches").whereIn("status",listOf("open","closed","live","review","settled","cancelled"))) {state->
            matchesLoading=state.loading;matchesCached=state.cached;matchesMore=state.more;matchesError=state.error
            matches=state.rows.map {d->Tournament(d.id,d.getString("title")?:"",d.getString("mode").orEmpty().replaceFirstChar { it.uppercase() }, d.getTimestamp("startsAt")?.toDate()?.toInstant()?.atZone(java.time.ZoneId.of("Asia/Dhaka"))?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM · h:mm a",java.util.Locale.ENGLISH))?:"",(d.getLong("joined")?:0).toInt(),(d.getLong("capacity")?:0).toInt(),d.getString("status")!="open",d.getLong("entryPaisa")?:0,d.getString("rules")?:"",d.getLong("prizePaisa")?:0,d.getString("description")?:"",d.getString("image").orEmpty().replaceFirstChar { it.uppercase() }, startsAtMillis=d.getTimestamp("startsAt")?.toDate()?.time?:0, map=d.getString("map").orEmpty(), perKill=d.getLong("perKillPaisa")?:0, matchStatus=d.getString("status").orEmpty(), cancellationReason=d.getString("cancellationReason").orEmpty(), categoryId=d.getString("categoryId").orEmpty(), artworkId=d.getString("artworkId").orEmpty())}
        }.also{it.start()}
        loading=false
        if(uid==null)return
        listeners += session.db.document("users/$uid").addSnapshotListener { s,e -> if(session.uid==uid) { loading=false; profileLoading=false; if(e!=null) error="Profile unavailable. Reconnect and retry." else profile=s?.data?.mapValues { it.value.toString() } } }
        listeners += session.db.document("wallets/$uid").addSnapshotListener(MetadataChanges.INCLUDE) { s,e ->
            if(session.uid==uid) {
                balanceCached=e!=null||s==null||s.metadata.isFromCache||s.metadata.hasPendingWrites()
                if(s!=null&&!s.metadata.hasPendingWrites()){balance=s.getLong("balance")?:0;winnings=s.getLong("winnings")?:0;reservedWinnings=s.getLong("reservedWinnings")?:0}
                if(e!=null)error="Wallet could not refresh. Reconnect and retry."
            }
        }
        listen(session.db.collection("entries").whereEqualTo("uid",uid).limit(100)) { s -> rosters=s.documents.associate { it.getString("match").orEmpty() to ((it.get("roster") as? List<*>)?.filterIsInstance<String>()?:listOf(it.getString("username").orEmpty())) }; entries=s.documents.associate { it.getString("match").orEmpty() to it.getString("status").orEmpty() } }
        listen(session.db.collection(if(BuildConfig.LOCAL_OPERATIONS)"depositRequests" else "walletDeposits").whereEqualTo("uid",uid).limit(100)) { s -> deposits=s.documents.map {DepositActivity(it.getLong("amount")?:0,it.getString("reference").orEmpty(),it.getString("status").orEmpty(),it.id,it.getString("decisionReason").orEmpty(),it.getTimestamp("createdAt"),it.getTimestamp("reviewedAt"))} }
    }
    fun close() { matchFeed?.close();matchFeed=null; listeners.forEach { it.remove() }; listeners.clear() }
    fun join(match: Tournament, roster:List<String> = listOf(profile?.get("ffUsername").orEmpty()), done: (String)->Unit) {
        if (match.fee != 0L) { operation("join",mapOf("match" to match.id,"fee" to match.fee,"roster" to org.json.JSONArray(roster)),done); return }
        if (busy) return
        val uid=session.uid?:return
        busy=true; error=""
        val entry=session.db.document("entries/${uid}_${match.id}")
        val ref=session.db.document("matches/${match.id}")
        val publicId=session.db.collection("participantLinks").document().id
        session.db.runTransaction { tx ->
            // Reading the deterministic entry first makes reconnect/retry return the existing outcome.
            val existing=tx.get(entry)
            if (existing.exists()) {
                require(existing.getString("status")=="confirmed") { "This entry is awaiting staff confirmation." }
            } else {
                val current=tx.get(ref)
                val player=tx.get(session.db.document("users/$uid"))
                val username=player.getString("ffUsername").orEmpty().trim()
                require(username.isNotEmpty()) { "Add your Free Fire username first." }
                require(validRoster(current.getString("mode").orEmpty(),roster,username)) { "Check roster size and distinct game names. The match format may have changed." }
                require(current.getString("status")=="open" && (current.getLong("joined")?:0)<(current.getLong("capacity")?:0)) { "Match full or closed." }
                require(current.getLong("entryPaisa")==0L) { "Entry fee changed. Refresh the match and try again." }
                tx.set(entry,mapOf("uid" to uid,"match" to match.id,"username" to username,"roster" to roster,"publicId" to publicId,"avatar" to (player.getString("avatar")?:"solo"),"status" to "confirmed","fee" to 0L,"createdAt" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("participantLinks/$publicId"),mapOf("entry" to entry.id))
                tx.set(ref.collection("participants").document(publicId),mapOf("username" to username,"roster" to roster,"avatar" to (player.getString("avatar")?:"solo")))
                tx.update(ref,"joined",(current.getLong("joined")?:0)+1)
            }
        }.continueWithTask { transaction ->
            if(!transaction.isSuccessful) throw transaction.exception ?: IllegalStateException("Registration failed")
            // A read-only duplicate transaction can finish from cache. Confirm online before reporting success.
            entry.get(Source.SERVER)
        }.addOnCompleteListener { task ->
            if(session.uid==uid) {
                busy=false
                if(task.isSuccessful && task.result.getString("status")=="confirmed") done("Registration confirmed.")
                else error=(task.exception as? IllegalArgumentException)?.message ?: "Registration could not be confirmed. Reconnect and retry."
            }
        }
    }
    fun save(username:String, gameUid:String,done:()->Unit={}) {
        if(busy)return
        val uid=session.uid?:return
        if(username.length>80||gameUid.length>40) {error="Check your profile fields.";return}
        busy=true;error=""
        session.db.document("users/$uid").set(mapOf("ffUsername" to username.trim(),"ffUid" to gameUid.trim(),"email" to session.auth.currentUser?.email.orEmpty())).addOnCompleteListener { busy=false; if(!it.isSuccessful) error="Profile could not be saved. Retry setup; your account already exists." else done() }
    }
    fun operation(action:String, values:Map<String,Any>, done:(String)->Unit) {
        if(busy)return
        if(action in listOf("join","confirmEntry","refundEntry","rejectEntry")){
            busy=true;error=""
            PaidEntries(session).operate(action,values){message,failure->busy=false;if(failure!=null)error=workflowFailure(failure)else done(message.orEmpty())}
            return
        }
        if(!BuildConfig.LOCAL_OPERATIONS) {
            if(action !in listOf("deposit","approveDeposit","autoApproveDeposit","rejectDeposit")){error="Paid match confirmation is not available yet.";return}
            busy=true;error=""
            LiveWallet(session).operate(action,values){message,failure->busy=false;if(failure!=null)error=workflowFailure(failure)else done(message.orEmpty())}
            return
        }
        val user=session.auth.currentUser?:return
        busy=true;error=""
        user.getIdToken(false).addOnCompleteListener { token ->
            if(!token.isSuccessful) {busy=false;error="Sign in again to continue.";return@addOnCompleteListener}
            Thread {
                var message="";var failure=""
                try {
                    val c=URL(BuildConfig.OPERATIONS_URL).openConnection() as HttpURLConnection
                    c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true
                    c.setRequestProperty("Authorization","Bearer ${token.result.token}");c.setRequestProperty("Content-Type","application/json")
                    c.outputStream.use { it.write(JSONObject(values+mapOf("action" to action)).toString().toByteArray()) }
                    val response=JSONObject((if(c.responseCode==200)c.inputStream else c.errorStream).bufferedReader().use { it.readText() })
                    if(response.has("error")) failure=response.getString("error") else message=response.getString("message")
                    c.disconnect()
                } catch(_:Exception) {failure="Connection unavailable. Retry to check the saved result."}
                Handler(Looper.getMainLooper()).post { if(session.uid==user.uid) {busy=false;error=failure;if(failure.isEmpty())done(message)} }
            }.start()
        }
    }
}
internal fun money(paisa:Long)="৳"+java.math.BigDecimal.valueOf(paisa,2).toPlainString()
