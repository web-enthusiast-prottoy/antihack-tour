package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.google.firebase.firestore.MetadataChanges

internal data class PlayerBanStatus(val ready:Boolean=false,val active:Boolean=false,val reason:String="",val failed:Boolean=false)

@Composable
internal fun playerBanStatus(session:AdminSession,path:String?,retry:Int):PlayerBanStatus {
    val uid=session.uid
    if(uid==null||path==null)return PlayerBanStatus(ready=true)
    var state by remember(uid,path,retry){mutableStateOf(PlayerBanStatus())}
    DisposableEffect(uid,path,retry) {
        val listener=session.db.document(path).addSnapshotListener(MetadataChanges.INCLUDE){snap,error->
            state=PlayerBanStatus(
                ready=error==null&&snap!=null&&!snap.metadata.isFromCache&&!snap.metadata.hasPendingWrites(),
                active=if(snap!=null)snap.getBoolean("active")==true else state.active,
                reason=if(snap!=null)snap.getString("reason").orEmpty() else state.reason,
                failed=error!=null
            )
        }
        onDispose{listener.remove()}
    }
    return state
}

@Composable
private fun BanReason(reason:String,bangla:Boolean) {
    Text(if(bangla)"নিষেধাজ্ঞার কারণ" else "Ban reason",style=MaterialTheme.typography.titleMedium)
    SelectionContainer(Modifier.semantics{liveRegion=LiveRegionMode.Polite}) {
        Text(reason.ifBlank{if(bangla)"কারণ এখন পাওয়া যাচ্ছে না। সংযোগ দিয়ে আবার চেষ্টা করুন।" else "The reason is unavailable. Reconnect and retry."})
    }
}

@Composable
internal fun MatchBanNotice(status:PlayerBanStatus,bangla:Boolean,retry:()->Unit) {
    if(status.ready&&!status.active)return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(status.active)if(bangla)"আপনি এই ম্যাচে নিষিদ্ধ।" else "You are banned from this match." else if(bangla)"ম্যাচের অ্যাক্সেস যাচাই হচ্ছে…" else "Checking match access…",style=MaterialTheme.typography.titleMedium)
            if(status.active) {
                BanReason(status.reason,bangla)
                Text(if(bangla)"এই ম্যাচে নিবন্ধন ও রুমের অ্যাক্সেস বন্ধ। অ্যাপের অন্য সুবিধা ব্যবহার করতে পারবেন।" else "Registration and room access are blocked for this match. You can still use the rest of the app.")
            }
            if(!status.ready)Text(if(bangla)"বর্তমান অ্যাক্সেস যাচাই করতে সংযোগ দিন।" else "Connect to verify current access.")
            if(!status.ready&&!status.failed)LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick=retry){Text(if(bangla)"আবার চেষ্টা করুন" else "Retry")}
        }
    }
}

@Composable
internal fun accountAccess(session:AdminSession,bangla:Boolean):Boolean {
    val uid=session.uid?:return true
    var refresh by remember(uid){mutableStateOf(0)}
    val status=playerBanStatus(session,"accountBans/$uid",refresh)
    // Account checks must not remove the current screen while connecting or refreshing.
    // Firestore rules continue to authorize every private read and write.
    if(!status.active)return true
    AccountBlockedScreen(status,bangla,retry={refresh++},signOut={session.auth.signOut()})
    return false
}

@Composable
internal fun AccountBlockedScreen(status:PlayerBanStatus,bangla:Boolean,retry:()->Unit,signOut:()->Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(status.active)if(bangla)"এই অ্যাকাউন্ট নিষিদ্ধ।" else "This account is banned." else if(bangla)"অ্যাক্সেস যাচাই করতে সংযোগ দিন।" else "Connect to verify account access.",style=MaterialTheme.typography.headlineSmall)
            if(status.active) {
                BanReason(status.reason,bangla)
                Text(if(bangla)"এই অ্যাকাউন্ট দিয়ে অ্যাপের সাইন ইন করা সুবিধা ব্যবহার করা যাবে না। কারণটি পড়তে, আবার যাচাই করতে বা সাইন আউট করতে পারবেন।" else "Signed-in features are unavailable for this account. You can read the reason, retry the access check, or sign out.")
            }
            if(!status.ready&&status.active)Text(if(bangla)"সর্বশেষ সংরক্ষিত কারণ দেখানো হচ্ছে। বর্তমান অবস্থা যাচাই করতে সংযোগ দিন।" else "Showing the last saved reason. Connect to verify the current status.")
            if(!status.active&&!status.failed)CircularProgressIndicator()
            TextButton(onClick=retry){Text(if(bangla)"আবার চেষ্টা করুন" else "Retry")}
            TextButton(onClick=signOut){Text(if(bangla)"সাইন আউট" else "Sign out")}
        }
    }
}
