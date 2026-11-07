package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun SupportScreen(
    session: AdminSession,
    bangla: Boolean,
    back: () -> Unit,
    reply: (@Composable (SupportData, DocumentSnapshot, () -> Unit) -> Unit)? = null
) {
    val staff = reply != null
    fun t(en:String,bn:String)=if(bangla)bn else en
    if(staff&&!session.allowed)return
    val data=remember(session.uid,staff){SupportData(session,staff)}
    var selected by remember {mutableStateOf<DocumentSnapshot?>(null)}
    var newRequest by rememberSaveable {mutableStateOf(false)}
    var draftId by rememberSaveable {mutableStateOf(java.util.UUID.randomUUID().toString())}
    var subject by rememberSaveable {mutableStateOf("")}
    var body by rememberSaveable {mutableStateOf("")}
    var reference by rememberSaveable {mutableStateOf("")}
    DisposableEffect(data){data.load();onDispose{data.close()}}
    fun goBack(){if(!data.busy){if(selected!=null)selected=null else back()}}
    BackHandler{if(newRequest&&!data.busy)newRequest=false else goBack()}
    LazyColumn(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {ScreenHeader(t(if(staff)"Support queue" else "Support requests",if(staff)"সহায়তার তালিকা" else "সহায়তার অনুরোধ"),back={goBack()},refresh={selected=null;data.load()},refreshEnabled=!data.loading&&!data.busy)}
        if(data.error.isNotBlank())item{Text(LocaleStrings.get(data.error),color=MaterialTheme.colorScheme.error)}
        if(selected==null) {
            if (!staff) item {Text(t("Private requests for match or payment questions. Replies do not change wallet balances or guarantee a payout. Do not include passwords, PINs or OTPs.","ম্যাচ বা পেমেন্টের প্রশ্নের জন্য ব্যক্তিগত অনুরোধ। উত্তরে ওয়ালেট বদলায় না বা পেআউট নিশ্চিত হয় না। পাসওয়ার্ড, পিন বা ওটিপি দেবেন না।"))}
            if(!staff)item{Button(onClick={newRequest=true},enabled=!data.busy){Text(t("New request","নতুন অনুরোধ"))}}
            if(data.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(!data.loading&&data.rows.isEmpty()&&data.error.isEmpty())item{Text(t("No support requests yet.","এখনো সহায়তার অনুরোধ নেই।"))}
            items(data.rows,key={it.id}){row -> Card(onClick={selected=row},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(row.getString("subject").orEmpty(),style=MaterialTheme.typography.titleMedium)
                Text(supportStatus(row.getString("status").orEmpty(),bangla))
                if (!staff) Text(row.id,style=MaterialTheme.typography.bodySmall)
                Text(supportDate(row.getTimestamp("createdAt")),style=MaterialTheme.typography.bodySmall)
            }}}
            if(data.more)item{OutlinedButton(onClick={data.load(false)},enabled=!data.loading){Text(LocaleStrings.get("Load more"))}}
            if(data.error.isNotEmpty())item{TextButton(onClick={data.load()}){Text(LocaleStrings.get("Retry"))}}
        } else {
            val record=selected!!
            item {SelectionContainer{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(record.getString("subject").orEmpty(),style=MaterialTheme.typography.titleLarge)
                Text(record.id,style=MaterialTheme.typography.bodySmall)
                Text(supportStatus(record.getString("status").orEmpty(),bangla))
                if(!record.getString("reference").isNullOrBlank())Text(t("Related reference: ","সংশ্লিষ্ট রেফারেন্স: ")+record.getString("reference"))
                Text(record.getString("body").orEmpty())
            }}}
            item {SupportReplies(record,bangla)}
            if (reply != null) item { reply(data, record) { selected = null } }
        }
    }
    if(newRequest)ModalBottomSheet(onDismissRequest={if(!data.busy)newRequest=false}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(t("New support request","সহায়তার নতুন অনুরোধ"),style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(subject,{subject=it},label={Text(t("Subject (up to 80 characters)","বিষয় (সর্বোচ্চ ৮০ অক্ষর)"))},modifier=Modifier.fillMaxWidth(),enabled=!data.busy,isError=subject.trim().length>80)
            OutlinedTextField(reference,{reference=it},label={Text(t("Match or request reference (optional)","ম্যাচ বা অনুরোধের রেফারেন্স (ঐচ্ছিক)"))},modifier=Modifier.fillMaxWidth(),enabled=!data.busy,isError=reference.trim().length>200)
            OutlinedTextField(body,{body=it},label={Text(t("Describe the issue (up to 4000 characters)","সমস্যা লিখুন (সর্বোচ্চ ৪০০০ অক্ষর)"))},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=!data.busy,isError=body.trim().length>4000)
            Text(t("Keep credentials and payment PINs/OTPs out of this request. You can read staff replies here after submitting.","পাসওয়ার্ড ও পেমেন্ট পিন/ওটিপি এখানে দেবেন না। জমা দেওয়ার পর এখানেই অ্যাডমিনের উত্তর পড়তে পারবেন।"))
            if(data.error.isNotEmpty())Text(LocaleStrings.get(data.error),color=MaterialTheme.colorScheme.error)
            Button(onClick={data.submit(draftId,subject.trim(),body.trim(),reference.trim()){newRequest=false;subject="";body="";reference="";draftId=java.util.UUID.randomUUID().toString()}},enabled=!data.busy&&validSupportText(subject.trim(),body.trim(),reference.trim()),modifier=Modifier.fillMaxWidth()){Text(t(if(data.busy)"Submitting…" else "Submit request",if(data.busy)"জমা হচ্ছে…" else "অনুরোধ জমা দিন"))}
        }
    }
}
internal fun supportStatus(status:String,bangla:Boolean)=if(!bangla)when(status){"open"->"Open";"review"->"Under review";else->"Resolved"}else when(status){"open"->"খোলা";"review"->"পর্যালোচনাধীন";else->"সমাধান হয়েছে"}

@Composable
private fun SupportReplies(record:DocumentSnapshot,bangla:Boolean) {
    var replies by remember(record.id){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    var cursor by remember(record.id){mutableStateOf<DocumentSnapshot?>(null)}
    var page by remember(record.id){mutableStateOf(0)}
    var more by remember(record.id){mutableStateOf(false)}
    var error by remember(record.id){mutableStateOf(false)}
    var loading by remember(record.id){mutableStateOf(true)}
    DisposableEffect(record.id,page) {
        var active=true;loading=true;error=false
        var query:Query=record.reference.collection("responses").orderBy("at",Query.Direction.DESCENDING)
        cursor?.let{query=query.startAfter(it)}
        query.limit(21).get(Source.SERVER).addOnCompleteListener {task->if(active){loading=false;if(task.isSuccessful){val rows=task.result.documents.take(20);replies=(replies+rows).distinctBy{it.id};cursor=rows.lastOrNull()?:cursor;more=task.result.size()>20}else error=true}}
        onDispose{active=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(bangla)"অ্যাডমিনের উত্তর" else "Staff replies",style=MaterialTheme.typography.titleLarge)
        replies.forEach {reply->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(supportStatus(reply.getString("status").orEmpty(),bangla));SelectionContainer{Text(reply.getString("body").orEmpty())};Text(supportDate(reply.getTimestamp("at")),style=MaterialTheme.typography.bodySmall)}}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading&&replies.isEmpty()&&!error)Text(if(bangla)"এখনো উত্তর আসেনি।" else "No staff reply yet.")
        if(error)Text(if(bangla)"উত্তর লোড হয়নি। সংযোগ দিয়ে আবার চেষ্টা করুন।" else "Could not load replies. Reconnect and retry.",color=MaterialTheme.colorScheme.error)
        if(more||error)TextButton(onClick={page++},enabled=!loading){Text(LocaleStrings.get(if(error)"Retry" else "Load more"))}
    }
}

private fun supportDate(time:com.google.firebase.Timestamp?)=time?.toDate()?.toInstant()?.atZone(java.time.ZoneId.of("Asia/Dhaka"))?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))?:""
