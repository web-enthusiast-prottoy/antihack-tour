package bd.arena.prototype

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

internal fun canOpenAdmin(signedIn: Boolean, serverConfirmed: Boolean, admin: Boolean) = signedIn && serverConfirmed && admin

internal class AdminSession(context: Context) {
    private val app = FirebaseApp.getApps(context).find { it.name == "arena-native" }
        ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .apply {
                if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotEmpty()) {
                    setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                }
            }.build(), "arena-native")
    val auth = FirebaseAuth.getInstance(app)
    val db = FirebaseFirestore.getInstance(app)
    var restoring by mutableStateOf(true); private set
    var uid by mutableStateOf<String?>(null); private set
    var allowed by mutableStateOf(false); private set
    var owner by mutableStateOf(false); private set
    var checking by mutableStateOf(false); private set
    var error by mutableStateOf("")
    var busy by mutableStateOf(false)
    private var role: ListenerRegistration? = null
    private val listener = FirebaseAuth.AuthStateListener { a ->
        restoring = false; role?.remove(); allowed = false; owner = false; uid = a.currentUser?.uid
        checking = uid != null
        uid?.let { id -> role = db.collection("staffRoles").document(id)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, failure ->
                if (uid != id) return@addSnapshotListener
                checking = failure==null && (snap==null || snap.metadata.isFromCache)
                allowed = canOpenAdmin(auth.currentUser?.uid == id, snap != null && !snap.metadata.isFromCache, snap?.getBoolean("admin") == true)
                owner = allowed && snap?.getBoolean("owner") == true
                if (failure != null) error = "Could not verify access. Sign in again."
            }
        }
    }
    init {
        if (!configured) {
            if (BuildConfig.FIREBASE_USE_EMULATOR) {
                auth.useEmulator("10.0.2.2", 9099)
                db.useEmulator("10.0.2.2", 8080)
            }
            configured = true
        }
        auth.addAuthStateListener(listener)
    }
    fun close() { role?.remove(); auth.removeAuthStateListener(listener) }
    fun login(email: String, password: String, done: () -> Unit) {
        if (busy) return
        val invalid=registrationError(email,password,password)
        if(invalid!=null){error=invalid;return}
        busy = true; error = ""
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener {
            busy = false
            if (!it.isSuccessful) error = accountError(it.exception, false)
            else done()
        }
    }
    fun register(email: String, password: String, confirmation: String, done: () -> Unit) {
        if (busy) return
        val invalid = registrationError(email, password, confirmation)
        if (invalid != null) { error = invalid; return }
        busy = true; error = ""
        // Firebase signs the new user in; registration never writes a staff role or wallet.
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener {
            busy = false
            if (!it.isSuccessful) error = accountError(it.exception, true)
            else done()
        }
    }
    companion object { private var configured = false }
}

internal fun registrationError(email: String, password: String, confirmation: String): String? = when {
    !email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "Enter a valid email address."
    password.length < 6 -> "Use at least 6 password characters."
    password != confirmation -> "Passwords do not match."
    else -> null
}
private fun accountError(error: Exception?, registering: Boolean): String = when (error) {
    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "This email already has an account. Sign in."
    is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
    is com.google.firebase.FirebaseNetworkException -> "Connection unavailable. Check the local service and retry."
    is com.google.firebase.FirebaseTooManyRequestsException -> "Too many attempts. Try again later."
    else -> if (registering) "Account creation failed. Check email and connection, then retry." else "Sign in failed. Check email, password and connection."
}

private data class AdminSection(val title: String, val path: String)
private val sections = listOf(AdminSection("Dashboard","matches"),AdminSection("Matches","matches"),AdminSection("Deposits","depositRequests"),AdminSection("Withdrawals","withdrawals"),AdminSection("Players","users"),AdminSection("Results","results"),AdminSection("Announcements","notices"),AdminSection("Content","configuration"),AdminSection("Staff","staffRoles"),AdminSection("Audit","auditRecords"),AdminSection("Entries","entries"),AdminSection("Player pages","playerPages"),AdminSection("Support","disputes"),AdminSection("Payment settings","paymentInstructions"),AdminSection("Categories","categories"),AdminSection("Withdrawal settings","withdrawalPolicy"))
private val matchFields = linkedMapOf("title" to "Title", "categoryId" to "Category", "description" to "Description", "image" to "Image", "mode" to "Mode", "map" to "Map", "startsAt" to "Starts (Bangladesh: yyyy-MM-dd HH:mm)", "capacity" to "Capacity", "entryPaisa" to "Entry fee (taka)", "prizePaisa" to "Prize pool (taka)", "perKillPaisa" to "Per kill (taka)", "rules" to "Rules", "cancellationTerms" to "Cancellation terms", "status" to "Status", "cancellationReason" to "Cancellation reason")
private val choices = mapOf("image" to listOf("solo","duo","squad"),"mode" to listOf("solo","duo","squad"),"status" to listOf("draft","open","closed","live","review","settled","cancelled"))

@Composable
internal fun NativeAdmin(session: AdminSession,bangla:Boolean=false,back:()->Unit) {
    BackHandler{back()}
    NativeAdminContent(session,bangla,back)
}

@Composable
private fun NativeAdminContent(session: AdminSession,bangla:Boolean,back:()->Unit) {
    // The caller also gates this destination; never render protected content without a verified role.
    if (!session.allowed) return
    var refresh by remember{mutableStateOf(0)}
    var managedMatch by remember { mutableStateOf<DocumentSnapshot?>(null) }
    managedMatch?.let { MatchAdminScreen(session,it){managedMatch=null;refresh++}; return }
    var section by rememberSaveable { mutableStateOf(0) }
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    var records by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var queryText by remember{mutableStateOf("")}
    var statusFilter by remember{mutableStateOf("All")}
    var recordGeneration by remember{mutableStateOf(0)}
    var more by remember{mutableStateOf(false)}
    var cursor by remember{mutableStateOf<DocumentSnapshot?>(null)}
    var loading by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val editorScroll=rememberLazyListState()
    LaunchedEffect(section,editing){editorScroll.scrollToItem(0)}
    var expectedMatchRevision by rememberSaveable {mutableStateOf(0L)}
    var originalMatchStatus by rememberSaveable {mutableStateOf<String?>(null)}
    var lockedMatchTerms by rememberSaveable {mutableStateOf(false)}
    var positionedPrizes by rememberSaveable{mutableStateOf(false)}
    var originalMatchFee by rememberSaveable {mutableStateOf(0L)}
    var recordId by rememberSaveable { mutableStateOf("") }
    var artwork by rememberSaveable(stateSaver=artworkSelectionSaver){mutableStateOf(ArtworkSelection())}
    var values by rememberSaveable { mutableStateOf<Map<String,String>>(emptyMap()) }
    var flag by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val categories=matchCategories(session,true).rows
    val operations = remember { PlayerData(session) }
    var reviewingEntry by remember {mutableStateOf<DocumentSnapshot?>(null)}
    reviewingEntry?.let{entry->PaidEntryReview(session,entry){reviewingEntry=null;refresh++}}
    var reviewingDeposit by remember {mutableStateOf<DocumentSnapshot?>(null)}
    reviewingDeposit?.let{record->DepositReview(record,operations,bangla){reviewingDeposit=null;refresh++}}
    var reviewingWithdrawal by remember {mutableStateOf<DocumentSnapshot?>(null)}
    reviewingWithdrawal?.let{record->WithdrawalReview(session,record){reviewingWithdrawal=null;refresh++}}
    val path = if(sections[section].path=="depositRequests"&&!BuildConfig.LOCAL_OPERATIONS)"walletDeposits" else sections[section].path
    fun recordQuery():Query {
        val field=when(path){"matches","notices","configuration","playerPages"->"updatedAt";"depositRequests","walletDeposits","entries","withdrawals"->"createdAt";"results"->"publishedAt";"auditRecords"->"at";else->null}
        val collection=session.db.collection(path)
        return if(field==null)collection.orderBy(FieldPath.documentId())else collection.orderBy(field,Query.Direction.DESCENDING)
    }
    fun loadMore() {
        if(loading||!more)return
        loading=true
        val version=recordGeneration
        var query=recordQuery()
        cursor?.let{query=query.startAfter(it)}
        query.limit(26).get().addOnCompleteListener{task->
            if(version!=recordGeneration||!session.allowed)return@addOnCompleteListener
            loading=false
            if(task.isSuccessful){val page=task.result.documents.take(25);records=(records+page).distinctBy{it.id};cursor=page.lastOrNull()?:cursor;more=task.result.size()>25;connected=!task.result.metadata.isFromCache}
            else error="Could not load data. Check connection and access."
        }
    }
    DisposableEffect(section,path,session.uid,session.allowed,session.owner,refresh) {
        var active=true
        recordGeneration++
        records=emptyList();cursor=null;loading=true;connected=false;error=""
        queryText="";statusFilter="All"
        if(section==0||path=="withdrawalPolicy"||(path=="staffRoles"&&!session.owner))loading=false
        else recordQuery().limit(26).get().addOnCompleteListener{task->if(active){
            loading=false
            if(task.isSuccessful){records=task.result.documents.take(25);cursor=records.lastOrNull();more=task.result.size()>25;connected=!task.result.metadata.isFromCache}
            else error="Could not load data. Check connection and access."
        }}
        onDispose{active=false}
    }
    fun edit(record: DocumentSnapshot?) {
        recordId = record?.id ?: ""
        artwork=ArtworkSelection(record?.getString("artworkId").orEmpty())
        expectedMatchRevision=record?.getLong("revision")?:0
        originalMatchStatus=record?.getString("status")
        originalMatchFee=record?.getLong("entryPaisa")?:0
        lockedMatchTerms=(record?.getLong("joined")?:0)>0
        positionedPrizes=record?.getBoolean("prizesEnabled")==true
        values = if (path == "matches") matchFields.keys.associateWith { key ->
            if (key == "startsAt") record?.getTimestamp(key)?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.toLocalDateTime()?.toString()?.replace('T',' ')?.take(16) ?: LocalDateTime.now(ZoneId.of("Asia/Dhaka")).plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            else if(key in listOf("entryPaisa","prizePaisa","perKillPaisa")) java.math.BigDecimal.valueOf(record?.getLong(key)?:0,2).toPlainString()
            else record?.get(key)?.toString() ?: when(key) { "image","mode" -> "solo"; "status" -> "draft"; "capacity" -> "48"; "map" -> "Bermuda"; "entryPaisa","prizePaisa","perKillPaisa" -> "0"; else -> "" }
        } else mapOf("title" to (record?.getString("title") ?: ""),"body" to (record?.getString("body") ?: "")) + (if(path=="playerPages")mapOf("link" to record?.getString("link").orEmpty())else emptyMap())
        flag = record?.getBoolean(if(path == "staffRoles") "admin" else if(path == "notices") "published" else "enabled") ?: true
        error = ""; message = ""; editing = true
    }
    fun save() {
        if (!session.allowed || !connected || busy) return
        try {
            val data = mutableMapOf<String,Any>()
            if (path == "staffRoles") {
                require(session.owner && recordId.isNotBlank() && !recordId.contains('/') && recordId != session.uid) { "Enter a valid UID. You cannot change your own access." }
                data["admin"] = flag; data["notices"] = flag
            } else {
                require(values["title"].orEmpty().isNotBlank() && values["title"].orEmpty().length <= 80) { "Enter a title (up to 80 characters)." }
                data.putAll(values)
                if (path == "matches") {
                    data["startsAt"] = Timestamp(Date.from(LocalDateTime.parse(values.getValue("startsAt").replace(' ','T')).atZone(ZoneId.of("Asia/Dhaka")).toInstant()))
                    for (key in listOf("capacity","entryPaisa","prizePaisa","perKillPaisa")) {
                        if(key=="capacity") {
                            val n=values.getValue(key).toLongOrNull()
                            require(n!=null && n in 1..1000){"Enter capacity between 1 and 1000."}
                            data[key]=n
                        } else {
                            val n=try{java.math.BigDecimal(values.getValue(key)).movePointRight(2).longValueExact()}catch(_:Exception){throw IllegalArgumentException("Enter valid taka amounts with up to two decimals.")}
                            require(n>=0){"Enter valid taka amounts with up to two decimals."}
                            data[key]=n
                        }
                    }
                } else {
                    require(values["body"].orEmpty().isNotBlank()) { "Enter details." }
                    data[if(path == "notices") "published" else "enabled"] = flag
                }
            }
            if(path=="playerPages") {
                require(recordId in playerPageIds){"Choose rules, help, about or privacy as the page ID."}
                require(values["link"].isNullOrBlank()||validPageLink(values["link"].orEmpty())){"Use a valid HTTPS link without login details."}
            }
            data["updatedAt"] = FieldValue.serverTimestamp(); if(path!="playerPages")data["updatedBy"] = session.uid!!
            val ref = if(recordId.isBlank()) session.db.collection(path).document() else session.db.collection(path).document(recordId)
            val image=if(path=="matches"&&artwork.bytes!=null)session.db.collection("artwork").document()else null
            if(path=="matches")data["artworkId"]=image?.id?:artwork.id
            busy = true; error = ""; message = ""
            session.db.runTransaction { tx ->
                val old = tx.get(ref)
                if(path=="matches") {
                    require((old.getLong("revision")?:0)==expectedMatchRevision){"This match changed. Reload before saving."}
                    require((data["capacity"] as Long)>=(old.getLong("prizeMaxRank")?:0)){"Capacity must include all saved prize positions."}
                    val revision=expectedMatchRevision+1
                    val from=old.getString("status")?:"none"
                    val to=data.getValue("status").toString()
                    require(to in matchStatusChoices(old.getString("status"),old.getLong("entryPaisa")?:0)){"Use the next match status. Complete settlement from Results."}
                    data["revision"]=revision
                    val reason=if(to=="cancelled")values["cancellationReason"].orEmpty().trim()else ""
                    data["cancellationReason"]=reason
                    if(to=="cancelled"){require(reason.isNotBlank()&&reason.length<=1000){"Enter a cancellation reason (up to 1000 characters)."};data["cancelledAt"]=FieldValue.serverTimestamp()}
                    tx.set(ref.collection("history").document(revision.toString()),mapOf("from" to from,"to" to to,"reason" to reason,"at" to FieldValue.serverTimestamp()))
                }
                if (path == "notices") {
                    data["createdAt"] = old.get("createdAt") ?: FieldValue.serverTimestamp()
                    data["createdBy"] = old.getString("createdBy") ?: session.uid!!
                    data["revision"] = (old.getLong("revision") ?: 0) + 1
                }
                if(path=="playerPages")data["revision"]=(old.getLong("revision")?:0)+1
                image?.let{tx.set(it,artworkRecord(artwork.bytes!!,session.uid!!))}
                tx.set(ref,data,SetOptions.merge())
                tx.set(if(path=="matches")session.db.document("auditRecords/match_${ref.id}_${data["revision"]}")else if(path=="playerPages")session.db.document("auditRecords/page_${ref.id}_${data["revision"]}")else session.db.collection("auditRecords").document(),mapOf("action" to if(path=="matches")"saveMatch" else if(path=="playerPages")"savePage" else if(old.exists()) "update" else "create","path" to ref.path,"actor" to session.uid!!,"at" to FieldValue.serverTimestamp()))
            }.addOnCompleteListener { busy = false; if(it.isSuccessful) { editing = false; message = "Saved.";refresh++ } else error = "Save failed. Check form, connection and access." }
        } catch (_: java.time.DateTimeException) { error = "Use this time format: 2026-11-03 20:00" }
        catch (e: IllegalArgumentException) { error = e.message ?: "Check the form." }
    }
    BackHandler(editing||section!=0){if(editing)editing=false else section=0}
    val wide=LocalConfiguration.current.screenWidthDp>=840
    AdminWorkspace(sections.mapIndexedNotNull{i,s->if(s.path=="staffRoles"&&!session.owner)null else i to s.title}.sortedBy{listOf("Dashboard","Matches","Categories","Entries","Results","Deposits","Withdrawals","Withdrawal settings","Players","Announcements","Content","Player pages","Support","Payment settings","Staff","Audit").indexOf(it.second)},section,{if(!busy){section=it;editing=false;message="";scope.launch{drawer.close()}}},drawer) {
    if(path=="categories")CategoriesAdmin(session){section=0}
    else if(path=="withdrawalPolicy")WithdrawalSettings(session){section=0}
    else if(path=="paymentInstructions")PaymentSettings(session,bangla){section=0}
    else if(path=="disputes")SupportScreen(session,true,bangla,back={section=0})
    else Column(Modifier.widthIn(max=640.dp).fillMaxSize()) {
        Box(Modifier.padding(horizontal=16.dp,vertical=8.dp)){ScreenHeader(if(editing)if(path=="matches")LocaleStrings.get(if(recordId.isBlank())"Create match" else "Edit match")else LocaleStrings.get(if(recordId.isBlank())"Add new" else "Edit")else LocaleStrings.get(sections[section].title),back={if(editing)editing=false else back()},refresh=if(section!=0&&!editing)({refresh++})else null,refreshEnabled=!loading,actions={if(!wide)IconButton(onClick={scope.launch{drawer.open()}},enabled=!busy){Icon(painterResource(R.drawable.ic_menu),LocaleStrings.get("Admin navigation"))}})}
    LazyColumn(Modifier.testTag("admin-records").weight(1f).widthIn(max=640.dp).fillMaxWidth().imePadding(),state=editorScroll,contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(section==0)item{AdminDashboard(session){target->section=sections.indexOfFirst{it.path==target&&it.title!="Dashboard"}.coerceAtLeast(0)}}
        if(error.isNotBlank()&&!editing) item { Text(LocaleStrings.get(error),color = MaterialTheme.colorScheme.error) }
        if(operations.error.isNotBlank()) item { Text(LocaleStrings.get(operations.error),color = MaterialTheme.colorScheme.error) }
        if(message.isNotBlank()) item { Text(LocaleStrings.get(message)) }
        if(!editing&&(path=="playerPages" || section in listOf(1,6,7) || (section == 8 && session.owner))) item { Button(onClick = { edit(null) },enabled = connected && !busy) { Text(LocaleStrings.get(if(path=="matches")"Create match" else "Add new")) } }
        if(section!=0&&!editing)item {
            OutlinedTextField(queryText,{queryText=it},label={Text(LocaleStrings.get("Search loaded records"))},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(path in listOf("matches","depositRequests","walletDeposits","entries","withdrawals"))MatchFilter("Status",listOf("All")+records.mapNotNull{it.getString("status")}.distinct(),statusFilter){statusFilter=it}
        }
        if(editing) {
            if(path=="matches"&&lockedMatchTerms)item{Text(LocaleStrings.get("Entry terms are locked after the first registration."))}
            if(path in listOf("staffRoles","configuration","playerPages")) item { OutlinedTextField(recordId,{ recordId = it.trim() },label = { Text(LocaleStrings.get(if(path == "staffRoles") "Firebase Auth UID" else if(path=="playerPages")"Page ID: rules, help, about, privacy" else "Configuration ID")) },enabled = !busy && records.none { it.id == recordId },modifier = Modifier.fillMaxWidth()) }
            if(path != "staffRoles") {
                val fields = if(path == "matches") matchFields else linkedMapOf("title" to "Title","body" to "Details").also{if(path=="playerPages")it["link"]="Contact or information URL (optional HTTPS)"}
                fields.forEach { (key,label) ->
                    if(path=="matches"&&key in listOf("title","startsAt","entryPaisa","rules","status"))item {
                        Text(when(key){"title"->"Match details";"startsAt"->"Schedule & capacity";"entryPaisa"->"Fees & prizes";"rules"->"Match rules";else->"Publication"},style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))
                    }
                    item {
                    val fieldEnabled=!busy&&!(path=="matches"&&key=="prizePaisa"&&positionedPrizes)&&!(path=="matches"&&lockedMatchTerms&&key in listOf("mode","map","startsAt","capacity","entryPaisa","prizePaisa","perKillPaisa","rules","cancellationTerms"))
                    val options = if(key=="status"&&path=="matches")matchStatusChoices(originalMatchStatus,originalMatchFee)else choices[key]
                    if(key=="image"&&path=="matches")ArtworkEditor(session,artwork,fieldEnabled,modeArtwork(values["image"].orEmpty())){artwork=it}
                    else if(key=="categoryId"&&path=="matches") {
                        var expanded by remember{mutableStateOf(false)}
                        Box {
                            OutlinedButton(onClick={expanded=true},enabled=fieldEnabled,modifier=Modifier.fillMaxWidth()){Text("Category: "+(categories.find{it.id==values[key]}?.name?:"Uncategorized"))}
                            DropdownMenu(expanded,{expanded=false}) {
                                DropdownMenuItem(text={Text(LocaleStrings.get("Uncategorized"))},onClick={values=values+(key to "");expanded=false})
                                categories.filter{it.active||it.id==values[key]}.forEach{category->DropdownMenuItem(text={Text(category.name)},onClick={values=values+(key to category.id);expanded=false})}
                            }
                        }
                    }
                    else if(options != null && path == "matches")MatchFilter(LocaleStrings.get(label),options,values[key].orEmpty(),fieldEnabled){values=values+(key to it)}
                    else if(key=="startsAt")MatchScheduleField(values[key].orEmpty(),fieldEnabled){values=values+(key to it)}
                    else if(key!="cancellationReason"||values["status"]=="cancelled")OutlinedTextField(values[key].orEmpty(),{ values = values + (key to it) },label = { Text(LocaleStrings.get(label)) },enabled = fieldEnabled,modifier = Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(key in listOf("entryPaisa","prizePaisa","perKillPaisa"))KeyboardType.Decimal else if(key=="capacity")KeyboardType.Number else KeyboardType.Text),minLines = if(key in listOf("body","rules","description","cancellationTerms")) 3 else 1)
                } }
            }
            if(path=="matches")item {
                if(recordId.isBlank())Text(LocaleStrings.get("Save the match to add prize positions."),style=MaterialTheme.typography.bodySmall)
                else PrizePanel(session,recordId,true){revision,total->expectedMatchRevision=revision;positionedPrizes=true;values=values+("prizePaisa" to java.math.BigDecimal.valueOf(total,2).toPlainString())}
            }
            if(path != "matches") item { Row { Checkbox(flag,{ flag = it },enabled = !busy); Text(if(path == "staffRoles") "Admin access" else "Published / active") } }
            item { if(error.isNotBlank())Text(LocaleStrings.get(error),color=MaterialTheme.colorScheme.error)
                Text(if(path == "matches") "Choose a bundled image preset. Changing status does not settle funds." else if(path == "staffRoles") "Create the account first." else ""); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { save() },enabled = !busy && connected && !artwork.preparing) { Text(if(busy) "Saving…" else "Save") }; TextButton(onClick = { editing = false },enabled = !busy) { Text(LocaleStrings.get("Cancel")) } } }
        }
        if(!editing&&loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if(!editing&&section!=0&&records.isEmpty()) item { Text(LocaleStrings.get("No records yet.")) }
        val visible=records.filter{record->(statusFilter=="All"||record.getString("status")==statusFilter)&&(queryText.isBlank()||listOf(record.id,record.getString("title"),record.getString("email"),record.getString("ffUsername"),record.getString("reference"),record.getString("username")).any{it?.contains(queryText.trim(),true)==true})}
        if(!editing)items(visible,key = { it.id }) { r -> Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) { Column(Modifier.padding(18.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.getString("title") ?: r.getString("ffUsername") ?: r.getString("email") ?: r.getString("reference") ?: r.getString("username") ?: r.id,style = MaterialTheme.typography.titleMedium)
            Text(r.getString("status") ?: r.getString("action") ?: r.getString("body")?.take(160) ?: r.getString("ffUid")?.let{"Game UID: $it"} ?: r.id,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            if(path=="matches") {
                val matchTime=r.getTimestamp("startsAt")?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM · h:mm a")).orEmpty()
                Text(listOf(categories.find{it.id==r.getString("categoryId")}?.name,r.getString("mode"),matchTime).filterNotNull().joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {Text("${r.getLong("joined")?:0}/${r.getLong("capacity")?:0} entries");Text(money(r.getLong("entryPaisa")?:0))}
            }
            if(path=="withdrawals"){
                WithdrawalDetails(r,true)
                Button(onClick={reviewingWithdrawal=r},enabled=connected&&!busy){Text(LocaleStrings.get("Review request"))}
            }
            if(path=="results")Text("#${r.getLong("placement")} · ${r.getLong("kills")} kills · "+money(r.getLong("awardPaisa")?:0))
            if(path in listOf("depositRequests","walletDeposits","entries")) {
                Text(money(r.getLong(if(path=="entries") "fee" else "amount")?:0),style=MaterialTheme.typography.titleMedium)
                Text(LocaleStrings.get("Player ID")+": "+r.getString("uid").orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(path in listOf("depositRequests","walletDeposits")) {
                    Text(r.getString("sender").orEmpty())
                    Text(r.getString("provider").orEmpty()+" · "+r.getString("receiver").orEmpty())
                    r.getString("decisionReason")?.let{Text(it)}
                }
                if(r.getString("status") in listOf("pending","requested")) Button(onClick={
                    if(path in listOf("depositRequests","walletDeposits"))reviewingDeposit=r else reviewingEntry=r
                },enabled=!busy&&!operations.busy&&connected) {Text(LocaleStrings.get(if(operations.busy) "Please wait…" else "Approve after review"))}
            }
            if(path == "matches" && section == 1) OutlinedButton(onClick={managedMatch=r},enabled=connected&&!busy) {Text(LocaleStrings.get("Room and results"))}
            if(r.getBoolean("owner") == true) Text(LocaleStrings.get("Owner"))
            if(path=="playerPages" || section in listOf(1,6,7) || (section == 8 && session.owner && r.getBoolean("owner") != true)) TextButton(onClick = { edit(r) },enabled = !busy && connected && (path!="matches"||r.getString("status") !in listOf("cancelled","settled"))) { Text(LocaleStrings.get("Edit")) }
        } } }
        if(section!=0&&!editing)item {
            Text("${visible.size} shown · ${records.size} loaded records",style=MaterialTheme.typography.bodySmall)
            if(more)OutlinedButton(onClick={loadMore()},enabled=!loading,modifier=Modifier.fillMaxWidth()){Text("Load more")}
        }
    }
    }
    }
}
