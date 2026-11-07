package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@Composable
internal fun ParticipantModeration(session:AdminSession,match:String,entry:DocumentSnapshot,close:()->Unit) {
    val uid=entry.getString("uid").orEmpty()
    var account by remember(uid){mutableStateOf<DocumentSnapshot?>(null)}
    var matchBan by remember(uid){mutableStateOf<DocumentSnapshot?>(null)}
    var loaded by remember(uid){mutableStateOf(false)}
    var failure by remember(uid){mutableStateOf("")}
    var busy by remember(uid){mutableStateOf(false)}
    var reason by remember(uid){mutableStateOf("")}
    var action by remember(uid){mutableStateOf<String?>(null)}
    var refresh by remember(uid){mutableStateOf(0)}
    fun t(text:String)=LocaleStrings.get(text)
    LaunchedEffect(uid,refresh) {
        loaded=false;failure=""
        session.db.document("accountBans/$uid").get(Source.SERVER).addOnCompleteListener {a->
            if(a.isSuccessful) {
                account=a.result
                session.db.document("matches/$match/bans/$uid").get(Source.SERVER).addOnCompleteListener {m->
                    if(m.isSuccessful){matchBan=m.result;loaded=true}else failure=t("Could not load moderation. Reconnect and retry.")
                }
            }else failure=t("Could not load moderation. Reconnect and retry.")
        }
    }
    fun save(scope:String) {
        val actor=session.uid?:return
        if(busy||!session.allowed)return
        val previous=if(scope=="account")account else matchBan
        val active=previous?.getBoolean("active")!=true
        val revision=previous?.getLong("revision")?:0L
        val path=if(scope=="account")"accountBans/$uid" else "matches/$match/bans/$uid"
        val key=if(scope=="account")"account_$uid" else "match_${match}_$uid"
        busy=true;failure=""
        session.db.runTransaction {tx->
            val ref=session.db.document(path)
            val current=tx.get(ref)
            require((current.getLong("revision")?:0L)==revision){"The record changed. Reload and try again."}
            tx.set(ref,mapOf("uid" to uid,"active" to active,"reason" to reason.trim(),"revision" to revision+1,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            tx.set(session.db.document("auditRecords/ban_${key}_${revision+1}"),mapOf("action" to "moderatePlayer","path" to path,"reason" to reason.trim(),"actor" to actor,"at" to FieldValue.serverTimestamp()))
        }.addOnCompleteListener {
            busy=false;action=null
            if(it.isSuccessful){reason="";refresh++}else failure=t("Could not save ban. Reconnect and check staff access. Staff accounts cannot be banned.")
        }
    }
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text(t("View player"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PlayerIdentity(session,uid,entry.getString("username").orEmpty())
            Text("${t("Account ID")}: $uid",style=MaterialTheme.typography.bodySmall)
            Text(t("Bans apply to the registered account. They do not block a phone or another account."),style=MaterialTheme.typography.bodySmall)
            if(!loaded&&failure.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth())
            if(loaded) {
                Text(t(if(account?.getBoolean("active")==true)"App account: banned" else "App account: allowed"))
                account?.getString("reason")?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                Text(t(if(matchBan?.getBoolean("active")==true)"This match: banned" else "This match: allowed"))
                matchBan?.getString("reason")?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                OutlinedTextField(reason,{reason=it},label={Text(t("Reason shown to player"))},supportingText={Text("${reason.trim().length}/1000")},isError=reason.trim().length>1000,enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={action="match"},enabled=!busy&&reason.trim().isNotEmpty()&&reason.trim().length<=1000,modifier=Modifier.fillMaxWidth()) {
                    Text(t(if(matchBan?.getBoolean("active")==true)"Lift match ban" else "Ban from match"))
                }
                OutlinedButton(onClick={action="account"},enabled=!busy&&reason.trim().isNotEmpty()&&reason.trim().length<=1000,modifier=Modifier.fillMaxWidth()) {
                    Text(t(if(account?.getBoolean("active")==true)"Lift account ban" else "Ban app account"))
                }
            }
            if(failure.isNotEmpty()){Text(failure,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++},enabled=!busy){Text(t("Retry"))}}
        }
    },confirmButton={TextButton(onClick=close,enabled=!busy){Text(t("Close"))}})
    action?.let {scope->
        val active=(if(scope=="account")account else matchBan)?.getBoolean("active")==true
        AlertDialog(onDismissRequest={if(!busy)action=null},title={Text(t(if(active)"Lift ban?" else "Confirm ban?"))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(t(if(active)"This restores access for this ban only." else if(scope=="account")"This account will lose signed-in app access." else "This account will lose registration and room access for this match."))
                Text(t("Entries, occupied seats and payment records are retained. This does not refund fees or remove someone already inside the game room."))
                Text(t("This reason will be shown to the player."))
                Text(reason.trim())
            }
        },confirmButton={TextButton(onClick={save(scope)},enabled=!busy){Text(t(if(busy)"Saving…" else "Confirm"))}},dismissButton={TextButton(onClick={action=null},enabled=!busy){Text(t("Cancel"))}})
    }
}
