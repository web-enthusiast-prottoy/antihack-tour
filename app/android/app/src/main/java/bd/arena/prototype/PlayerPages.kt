package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

internal val playerPageIds=listOf("rules","help","about","privacy")
internal fun pageTitle(id:String,bangla:Boolean)=when(id){
    "rules"->if(bangla)"নিয়মাবলি" else "Rules"
    "help"->if(bangla)"যোগাযোগ" else "Contact"
    "about"->if(bangla)"অ্যাপ ও ডেভেলপার পরিচিতি" else "About and developer info"
    else->if(bangla)"গোপনীয়তা নীতি" else "Privacy policy"
}

@Composable
internal fun PlayerPageScreen(session:AdminSession,id:String,bangla:Boolean,back:()->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    var record by remember(id,session.uid){mutableStateOf<DocumentSnapshot?>(null)}
    var loading by remember(id){mutableStateOf(true)}
    var cached by remember(id){mutableStateOf(false)}
    var failed by remember(id){mutableStateOf(false)}
    var retry by remember(id){mutableStateOf(0)}
    val uriHandler=androidx.compose.ui.platform.LocalUriHandler.current
    var linkFailed by remember(id){mutableStateOf(false)}
    BackHandler{back()}
    DisposableEffect(id,session.uid,retry) {
        var active=true
        val uid=session.uid
        loading=true;failed=false;cached=false;record=null
        fun fetch(source:Source) {
            session.db.document("playerPages/$id").get(source).addOnCompleteListener {task ->
                if(!active||session.uid!=uid)return@addOnCompleteListener
                if(task.isSuccessful) {record=task.result.takeIf{it.exists()&&it.getBoolean("enabled")==true};cached=source==Source.CACHE;loading=false}
                else {
                    val code=(task.exception as? FirebaseFirestoreException)?.code
                    if(source==Source.SERVER&&code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))fetch(Source.CACHE)
                    else {loading=false;failed=code!=FirebaseFirestoreException.Code.PERMISSION_DENIED}
                }
            }
        }
        if(id in playerPageIds)fetch(Source.SERVER)else{loading=false;failed=true}
        onDispose{active=false}
    }
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(pageTitle(id,bangla),back=back,refresh={retry++},refreshEnabled=!loading)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(cached)Text(t("Offline · saved information may be outdated. Reconnect for the current policy.","অফলাইন · সংরক্ষিত তথ্য পুরোনো হতে পারে। বর্তমান নীতির জন্য সংযোগ দিন।"))
        if(failed) {
            Text(t("Could not load this page. Reconnect and retry.","পৃষ্ঠা লোড হয়নি। সংযোগ দিয়ে আবার চেষ্টা করুন।"),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={retry++}){Text(LocaleStrings.get("Retry"))}
        } else if(!loading&&record==null)Text(if(cached)t("No saved published copy is available. Reconnect and retry.","প্রকাশিত পৃষ্ঠার সংরক্ষিত কপি নেই। সংযোগ দিয়ে আবার চেষ্টা করুন।")else t("The operator has not published this information yet.","পরিচালক এখনো এই তথ্য প্রকাশ করেননি।"))
        record?.let {page ->
            val link=page.getString("link")?.takeIf{validPageLink(it)}
            InformationContent(page.getString("title").orEmpty(),page.getString("body").orEmpty(),link?.let{java.net.URI(it).host},linkFailed,
                open={if(link!=null)linkFailed=runCatching{uriHandler.openUri(link)}.isFailure})
        }
    }
}
