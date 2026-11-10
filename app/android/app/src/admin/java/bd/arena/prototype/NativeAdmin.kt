package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch

private val matchFields = linkedMapOf("title" to "Title", "categoryId" to "Category", "description" to "Description", "image" to "Image", "mode" to "Mode", "map" to "Map", "startsAt" to "Starts (Bangladesh: yyyy-MM-dd HH:mm)", "capacity" to "Capacity", "entryPaisa" to "Entry fee (taka)", "prizePaisa" to "Total prize (taka)", "perKillPaisa" to "Per kill (taka)", "rules" to "Rules", "cancellationTerms" to "Cancellation terms", "status" to "Status", "cancellationReason" to "Cancellation reason")
private val choices = mapOf("image" to listOf("solo","duo","squad"),"mode" to listOf("solo","duo","squad"),"status" to listOf("draft","open","closed","live","review","settled","cancelled"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminRecords(
    session: AdminSession,
    page: AdminPage,
    bangla: Boolean,
    back: () -> Unit,
    open: (AdminPage) -> Unit,
    smsRefresh: Int = 0
) {
    if (!session.allowed || (page == AdminPage.Staff && !session.owner)) return
    val path = page.path
    val canCreate = page in setOf(AdminPage.Matches, AdminPage.Notices, AdminPage.Content, AdminPage.Staff, AdminPage.PlayerPages)
    var refresh by remember { mutableStateOf(0) }
    var managedMatch by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var details by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var records by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var queryText by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("All") }
    var recordGeneration by remember{mutableStateOf(0)}
    var more by remember{mutableStateOf(false)}
    var cursor by remember{mutableStateOf<DocumentSnapshot?>(null)}
    var loading by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editorSection by rememberSaveable { mutableStateOf("Details") }
    var repeating by rememberSaveable {mutableStateOf(false)}
    var scheduleId by rememberSaveable {mutableStateOf(session.db.collection("matchSchedules").document().id)}
    var matchDate by rememberSaveable {mutableStateOf(java.time.LocalDate.now(ZoneId.of("Asia/Dhaka")).toString())}
    var releaseDate by rememberSaveable {mutableStateOf(matchDate)}
    var fromMinute by rememberSaveable {mutableIntStateOf(540)}
    var toMinute by rememberSaveable {mutableIntStateOf(1380)}
    var delayMinutes by rememberSaveable {mutableStateOf("20")}
    val scheduling=remember(session.uid){MatchSchedules(session)}
    val scheduleScope=rememberCoroutineScope()
    val editorScroll=rememberLazyListState()
    LaunchedEffect(editing){editorScroll.scrollToItem(0)}
    var expectedMatchRevision by rememberSaveable {mutableStateOf(0L)}
    var originalMatchStatus by rememberSaveable {mutableStateOf<String?>(null)}
    var lockedMatchTerms by rememberSaveable {mutableStateOf(false)}
    var initialPrizes by rememberSaveable {mutableStateOf(listOf<String>())}
    var independentPrizeTotal by rememberSaveable {mutableStateOf(false)}
    var positionedPrizes by rememberSaveable{mutableStateOf(false)}
    var originalMatchFee by rememberSaveable {mutableStateOf(0L)}
    var recordId by rememberSaveable { mutableStateOf("") }
    var artwork by rememberSaveable(stateSaver=artworkSelectionSaver){mutableStateOf(ArtworkSelection())}
    var values by rememberSaveable { mutableStateOf<Map<String,String>>(emptyMap()) }
    var flag by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val categories = if (page == AdminPage.Matches) matchCategories(session, true).rows else emptyList()
    val operations = remember(session.uid) { PlayerData(session) }
    var reviewingDeposit by remember {mutableStateOf<DocumentSnapshot?>(null)}
    reviewingDeposit?.let{record->DepositReview(record,operations,bangla){reviewingDeposit=null;refresh++}}
    var reviewingWithdrawal by remember {mutableStateOf<DocumentSnapshot?>(null)}
    reviewingWithdrawal?.let{record->WithdrawalReview(session,record){reviewingWithdrawal=null;refresh++}}
    fun recordQuery():Query {
        val field=when(path){"matches","notices","configuration","playerPages"->"updatedAt";"depositRequests","walletDeposits","entries","withdrawals"->"createdAt";"results"->"publishedAt";"auditRecords"->"at";else->null}
        val collection=session.db.collection(path)
        return if(field==null)collection.orderBy(FieldPath.documentId())else collection.orderBy(field,Query.Direction.DESCENDING)
    }
    fun loadMore() {
        if(loading||!more)return
        loading=true
        error=""
        val version=recordGeneration
        var query=recordQuery()
        cursor?.let{query=query.startAfter(it)}
        query.limit(26).get(Source.SERVER).addOnCompleteListener{task->
            if(version!=recordGeneration||!session.allowed)return@addOnCompleteListener
            loading=false
            if(task.isSuccessful){val page=task.result.documents.take(25);records=(records+page).distinctBy{it.id};cursor=page.lastOrNull()?:cursor;more=task.result.size()>25;connected=!task.result.metadata.isFromCache}
            else error="Could not load data. Check connection and access."
        }
    }
    DisposableEffect(session.uid,refresh,smsRefresh) {
        var active=true
        recordGeneration++
        records=emptyList();cursor=null;more=false;loading=true;connected=false;error=""
        recordQuery().limit(26).get(Source.SERVER).addOnCompleteListener{task->if(active){
            loading=false
            if(task.isSuccessful){records=task.result.documents.take(25);cursor=records.lastOrNull();more=task.result.size()>25;connected=!task.result.metadata.isFromCache}
            else error="Could not load data. Check connection and access."
        }}
        onDispose{active=false;recordGeneration++}
    }
    fun edit(record: DocumentSnapshot?) {
        repeating=false
        scheduleId=session.db.collection("matchSchedules").document().id
        matchDate=java.time.LocalDate.now(ZoneId.of("Asia/Dhaka")).toString();releaseDate=matchDate
        fromMinute=540;toMinute=1380;delayMinutes="20";editorSection="Details"
        initialPrizes=emptyList()
        independentPrizeTotal=record==null||record.getBoolean("independentPrizeTotal")==true
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
            else record?.get(key)?.toString() ?: when(key) { "image","mode" -> "solo"; "status" -> "open"; "capacity" -> "48"; "map" -> "Bermuda"; "entryPaisa","prizePaisa","perKillPaisa" -> "0"; else -> "" }
        } else mapOf("title" to (record?.getString("title") ?: ""),"body" to (record?.getString("body") ?: "")) + (if(path=="playerPages")mapOf("link" to record?.getString("link").orEmpty())else emptyMap())
        flag = record?.getBoolean(if(path == "staffRoles") "admin" else if(path == "notices") "published" else "enabled") ?: true
        error = ""; message = ""; editing = true
    }
    fun save() {
        if (!session.allowed || !connected || busy) return
        val actor = session.uid ?: return
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
                            require(n in 0..9007199254740991L){"Enter valid taka amounts with up to two decimals."}
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
            data["updatedAt"] = FieldValue.serverTimestamp(); if(path!="playerPages")data["updatedBy"] = actor
            val prizes = if(path=="matches"&&recordId.isBlank()) initialPrizes.map { try { awardPaisa(it) } catch (_:Exception) { throw IllegalArgumentException("Enter valid taka amounts with up to two decimals.") } } else emptyList()
            if(prizes.isNotEmpty())require(prizes.size <= (data["capacity"] as Long)){"Capacity must include all saved prize positions."}
            if(path=="matches"&&recordId.isBlank()&&repeating) {
                val timing=MatchScheduleTiming(matchDate,matchDate,fromMinute,toMinute,delayMinutes.toIntOrNull()?:0,releaseDate)
                timing.validate()
                busy=true;error="";message=""
                try {
                    scheduling.create(scheduleId,data,prizes,timing,artwork){failure->
                        if(failure!=null){busy=false;error=workflowFailure(failure)}
                        else scheduleScope.launch {
                            try {AdminMatchScheduler(session).advance(scheduleId);editing=false;open(AdminPage.Schedules)}
                            catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                            catch(problem:Exception){error=workflowFailure(problem)}
                            finally{busy=false}
                        }
                    }
                }catch(failure:Exception){busy=false;throw failure}
                return
            }
            val ref = if(recordId.isBlank()) session.db.collection(path).document() else session.db.collection(path).document(recordId)
            val imageBytes = artwork.bytes
            val expectedRevision = expectedMatchRevision
            val cancellationReason = values["cancellationReason"].orEmpty().trim()
            val image=if(path=="matches"&&imageBytes!=null)session.db.collection("artwork").document()else null
            if(path=="matches")data["artworkId"]=image?.id?:artwork.id
            busy = true; error = ""; message = ""
            session.db.runTransaction { tx ->
                val old = tx.get(ref)
                if(path=="matches") {
                    require((old.getLong("revision")?:0)==expectedRevision){"This match changed. Reload before saving."}
                    data["startsAt"]=matchEditorTimestamp(old.getTimestamp("startsAt"),values.getValue("startsAt"))
                    require((data["capacity"] as Long)>=(old.getLong("prizeMaxRank")?:0)){"Capacity must include all saved prize positions."}
                    if(!old.exists()) {
                        data["independentPrizeTotal"]=true
                        data["prizesEnabled"]=prizes.isNotEmpty()
                        data["prizeRank"]=0L
                        data["prizeMaxRank"]=prizes.size.toLong()
                        prizes.forEachIndexed { index, amount ->
                            val rank=(index+1).toLong()
                            tx.set(ref.collection("prizes").document(rank.toString()),mapOf("rank" to rank,"amountPaisa" to amount,"matchRevision" to 1L,"updatedAt" to FieldValue.serverTimestamp()))
                        }
                    }
                    val revision=expectedRevision+1
                    val from=old.getString("status")?:"none"
                    val to=data.getValue("status").toString()
                    require(to in matchStatusChoices(old.getString("status"),old.getLong("entryPaisa")?:0)){"Use the next match status. Complete settlement from Results."}
                    data["revision"]=revision
                    val reason=if(to=="cancelled")cancellationReason else ""
                    data["cancellationReason"]=reason
                    if(to=="cancelled"){require(reason.isNotBlank()&&reason.length<=1000){"Enter a cancellation reason (up to 1000 characters)."};data["cancelledAt"]=FieldValue.serverTimestamp()}
                    tx.set(ref.collection("history").document(revision.toString()),mapOf("from" to from,"to" to to,"reason" to reason,"at" to FieldValue.serverTimestamp()))
                }
                if (path == "notices") {
                    data["createdAt"] = old.get("createdAt") ?: FieldValue.serverTimestamp()
                    data["createdBy"] = old.getString("createdBy") ?: actor
                    data["revision"] = (old.getLong("revision") ?: 0) + 1
                }
                if(path=="playerPages")data["revision"]=(old.getLong("revision")?:0)+1
                image?.let{tx.set(it,artworkRecord(checkNotNull(imageBytes),actor))}
                tx.set(ref,data,SetOptions.merge())
                tx.set(if(path=="matches")session.db.document("auditRecords/match_${ref.id}_${data["revision"]}")else if(path=="playerPages")session.db.document("auditRecords/page_${ref.id}_${data["revision"]}")else session.db.collection("auditRecords").document(),mapOf("action" to if(path=="matches")"saveMatch" else if(path=="playerPages")"savePage" else if(old.exists()) "update" else "create","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }.addOnCompleteListener {
                if (session.uid == actor && session.allowed) {
                    busy = false
                    if (it.isSuccessful) { editing = false; message = "Saved."; refresh++ }
                    else error = workflowFailure(it.exception)
                }
            }
        } catch (_: java.time.DateTimeException) { error = "Use this time format: 2026-11-03 20:00" }
        catch (e: IllegalArgumentException) { error = e.message ?: "Check the form." }
    }
    managedMatch?.let { match ->
        MatchAdminScreen(session, match, { managedMatch = null; refresh++ }) {
            managedMatch = null
            edit(match)
        }
        return
    }
    details?.let { record -> AdminRecordDetails(session, page, record) { details = null } }
    BackHandler(editing) { if (!busy) editing = false }
    val editorTitle = when {
        page == AdminPage.Matches -> if (recordId.isBlank()) "Create match" else "Edit match"
        page == AdminPage.Notices -> if (recordId.isBlank()) "New announcement" else "Edit announcement"
        else -> if (recordId.isBlank()) "Add new" else "Edit"
    }
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScreenHeader(
                adminText(if (editing) editorTitle else page.title),
                back = if (editing) ({ if (!busy) editing = false }) else if (page != AdminPage.Matches) back else null,
                refresh = if (!editing) ({ refresh++ }) else null,
                refreshEnabled = !loading && !busy
            )
        }
        if(editing&&path=="matches")FlowRow(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf("Details","Schedule","Prizes","Rules","Publication")+if(recordId.isBlank())listOf("Scheduling")else emptyList()).forEach{section->FilterChip(editorSection==section,{editorSection=section},label={Text(adminText(section))})}
        }
        LazyColumn(
            Modifier.testTag("admin-records").weight(1f).fillMaxWidth().imePadding(),
            state = editorScroll, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (!editing && page == AdminPage.Deposits) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmsDepositCheck(session, bangla) { refresh++ }
            }
        }
        if (error.isNotBlank() && !editing) item { Text(adminText(error), color = MaterialTheme.colorScheme.error) }
        if (message.isNotBlank() && !editing) item { Text(adminText(message)) }
        if (!editing && canCreate) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canCreate) {
                    val create = when (page) {
                        AdminPage.Matches -> "Create match"
                        AdminPage.Notices -> "New announcement"
                        else -> "Add new"
                    }
                    Button(onClick = { edit(null) }, enabled = connected && !busy) { Text(adminText(create)) }
                }
                if (page == AdminPage.Matches) {
                    OutlinedButton(onClick = { open(AdminPage.Results) }) { Text(adminText("Results")) }
                    OutlinedButton(onClick = { open(AdminPage.Schedules) }) { Text(adminText("Schedules")) }
                }
            }
        }
        if (!editing && (records.isNotEmpty() || queryText.isNotBlank())) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(queryText, { queryText = it }, label = { Text(adminText("Search loaded records")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (page in listOf(AdminPage.Matches, AdminPage.Deposits, AdminPage.Withdrawals)) {
                    val states = listOf("All") + (records.mapNotNull { it.getString("status") } + statusFilter).filter { it != "All" }.distinct()
                    MatchFilter(adminText("Status"), states, statusFilter) { statusFilter = it }
                }
            }
        }
        if(editing) {
            if(path=="matches"&&lockedMatchTerms)item{Text(LocaleStrings.get("Entry terms are locked after the first registration."))}
            if(path in listOf("staffRoles","configuration","playerPages")) item { OutlinedTextField(recordId,{ recordId = it.trim() },label = { Text(LocaleStrings.get(if(path == "staffRoles") "Firebase Auth UID" else if(path=="playerPages")"Page ID: rules, help, about, privacy" else "Configuration ID")) },enabled = !busy && records.none { it.id == recordId },modifier = Modifier.fillMaxWidth()) }
            if(path != "staffRoles") {
                val fields = if(path == "matches") matchFields else linkedMapOf("title" to "Title","body" to "Details").also{if(path=="playerPages")it["link"]="Contact or information URL (optional HTTPS)"}
                fields.filterKeys { key -> path!="matches" || when(editorSection) {
                    "Details" -> key in listOf("title","categoryId","description","image","mode","map")
                    "Schedule" -> key in listOf("startsAt","capacity","entryPaisa")
                    "Prizes" -> key in listOf("prizePaisa","perKillPaisa")
                    "Rules" -> key in listOf("rules","cancellationTerms")
                    "Scheduling" -> false
                    else -> key in listOf("status","cancellationReason")
                }}.forEach { (key,label) ->
                    if(path=="matches"&&key in listOf("title","startsAt","entryPaisa","rules","status"))item {
                        Text(adminText(when(key){"title"->"Match details";"startsAt"->"Schedule & capacity";"entryPaisa"->"Fees & prizes";"rules"->"Match rules";else->"Publication"}),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))
                    }
                    item {
                    val fieldEnabled=!busy&&!(path=="matches"&&key=="prizePaisa"&&positionedPrizes&&!independentPrizeTotal)&&!(path=="matches"&&lockedMatchTerms&&key in listOf("mode","map","startsAt","capacity","entryPaisa","perKillPaisa","rules","cancellationTerms"))
                    val options = if(key=="status"&&path=="matches")matchStatusChoices(originalMatchStatus,originalMatchFee)else choices[key]
                    if(key=="image"&&path=="matches")ArtworkEditor(session,artwork,fieldEnabled,modeArtwork(values["image"].orEmpty())){artwork=it}
                    else if(key=="categoryId"&&path=="matches") {
                        var expanded by remember{mutableStateOf(false)}
                        Box {
                            OutlinedButton(onClick={expanded=true},enabled=fieldEnabled,modifier=Modifier.fillMaxWidth()){Text(adminText("Category") + ": " + (categories.find { it.id == values[key] }?.name ?: adminText("Uncategorized")))}
                            DropdownMenu(expanded,{expanded=false}) {
                                DropdownMenuItem(text={Text(LocaleStrings.get("Uncategorized"))},onClick={values=values+(key to "");expanded=false})
                                categories.filter{it.active||it.id==values[key]}.forEach{category->DropdownMenuItem(text={Text(category.name)},onClick={values=values+(key to category.id);expanded=false})}
                            }
                        }
                    }
                    else if(repeating&&key=="status")Text(adminText("Generated matches open for registration automatically."))
                    else if(options != null && path == "matches")MatchFilter(LocaleStrings.get(label),options,values[key].orEmpty(),fieldEnabled){values=values+(key to it)}
                    else if(key=="startsAt"&&repeating)Text(adminText("Start times come from Scheduling."))
                    else if(key=="startsAt")MatchScheduleField(values[key].orEmpty(),fieldEnabled){values=values+(key to it)}
                    else if(key!="cancellationReason"||values["status"]=="cancelled")OutlinedTextField(values[key].orEmpty(),{ values = values + (key to it) },label = { Text(LocaleStrings.get(label)) },enabled = fieldEnabled,modifier = Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(key in listOf("entryPaisa","prizePaisa","perKillPaisa"))KeyboardType.Decimal else if(key=="capacity")KeyboardType.Number else KeyboardType.Text),minLines = if(key in listOf("body","rules","description","cancellationTerms")) 3 else 1)
                } }
            }
            if(path=="matches"&&editorSection=="Scheduling"&&recordId.isBlank())item {
                MatchSchedulingForm(!busy,repeating,{repeating=it},matchDate,{matchDate=it},releaseDate,{releaseDate=it},fromMinute,{fromMinute=it},toMinute,{toMinute=it},delayMinutes,{delayMinutes=it})
            }
            if(path=="matches"&&editorSection=="Prizes")item {
                if(recordId.isBlank())Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(adminText("Prize distribution"),style=MaterialTheme.typography.titleMedium)
                    initialPrizes.forEachIndexed { index, amount ->
                        OutlinedTextField(amount,{value->initialPrizes=initialPrizes.toMutableList().also{it[index]=value}},label={Text(adminText("Prize (taka)")+" · #${index+1}")},isError=runCatching{awardPaisa(amount)}.isFailure,supportingText=if(runCatching{awardPaisa(amount)}.isFailure)({Text(adminText("Enter valid taka amounts with up to two decimals."))})else null,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick={initialPrizes=initialPrizes+"0"},enabled=!busy&&initialPrizes.size<minOf(values["capacity"]?.toIntOrNull()?:0,400)){Text(adminText("Add prize position"))}
                    if(initialPrizes.isNotEmpty())TextButton(onClick={initialPrizes=initialPrizes.dropLast(1)},enabled=!busy){Text(adminText("Remove last position"))}
                }
                else PrizePanel(session,recordId,true){revision,total->expectedMatchRevision=revision;positionedPrizes=true;values=values+("prizePaisa" to java.math.BigDecimal.valueOf(total,2).toPlainString())}
            }
            if (path != "matches") item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    val label = adminText(when (page) {
                        AdminPage.Staff -> "Admin access"
                        AdminPage.Notices -> "Published"
                        else -> "Enabled"
                    })
                    Text(label, Modifier.weight(1f))
                    Switch(
                        flag, { flag = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = label }
                    )
                }
            }
        }
        val visible = records.filter { record ->
            (statusFilter == "All" || record.getString("status") == statusFilter) &&
                (queryText.isBlank() || listOf(record.id, record.getString("title"), record.getString("email"),
                    record.getString("ffUsername"), record.getString("reference"), record.getString("username"))
                    .any { it?.contains(queryText.trim(), true) == true })
        }
        if (!editing) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (error.isBlank() && visible.isEmpty()) item {
                Text(adminText(if (records.isEmpty()) "No records yet." else "No matching records"))
            }
            items(visible, key = { it.id }) { record ->
                val status = record.getString("status")
                val title = adminRecordTitle(page, record)
                val detail = when (page) {
                    AdminPage.Matches -> listOfNotNull(
                        categories.find { it.id == record.getString("categoryId") }?.name,
                        record.getString("mode")?.let(::adminText),
                        adminMatchTime(record)
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    AdminPage.Players -> record.getString("ffUsername")
                    AdminPage.Results -> (record.getLong("placement")?.takeIf{it>0}?.let{"#$it"}?:adminText("Placement not recorded"))+" · ${money(record.getLong("awardPaisa") ?: 0)}"
                    AdminPage.Deposits, AdminPage.Withdrawals -> record.getTimestamp("createdAt")?.let(::recordTime)
                    AdminPage.Audit -> record.getTimestamp("at")?.let(::recordTime)
                    else -> null
                }
                val label = when (page) {
                    AdminPage.Notices -> adminText(if (record.getBoolean("published") == true) "Published" else "Hidden")
                    AdminPage.Staff -> adminText(if (record.getBoolean("owner") == true) "Owner" else if (record.getBoolean("admin") == true) "Enabled" else "Disabled")
                    else -> status?.let(::adminStatus)
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column {
                    if(record.getString("uid")!=null)Box(Modifier.padding(horizontal=12.dp,vertical=8.dp)){PlayerIdentity(session,record.getString("uid").orEmpty(),record.getString("username").orEmpty())}
                    AdminRow(title, detail, label) {
                        when {
                            page == AdminPage.Matches -> managedMatch = record
                            !connected || busy -> details = record
                            page == AdminPage.Deposits && status == "pending" -> reviewingDeposit = record
                            page == AdminPage.Withdrawals -> reviewingWithdrawal = record
                            canCreate && record.getBoolean("owner") != true -> edit(record)
                            else -> details = record
                        }
                    }
                    }
                }
            }
            if (more) item {
                OutlinedButton(onClick = { loadMore() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(adminText("Load more"))
                }
            }
        }
        }
        if(editing)Surface(shadowElevation=4.dp) {
            Column(Modifier.fillMaxWidth().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(error.isNotBlank())Text(adminText(error),color=MaterialTheme.colorScheme.error)
                Button(onClick={save()},enabled=!busy&&connected&&!artwork.preparing,modifier=Modifier.fillMaxWidth()){Text(adminText(if(busy)"Saving…" else if(path=="matches"&&recordId.isBlank()&&repeating)"Create schedule" else "Save"))}
            }
        }
    }
}

private fun recordTime(time: Timestamp): String =
    time.toDate().toInstant().atZone(ZoneId.of("Asia/Dhaka"))
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))

private fun adminRecordTitle(page: AdminPage, record: DocumentSnapshot): String = when (page) {
    AdminPage.Deposits, AdminPage.Withdrawals ->
        money(record.getLong(when (page) {
            AdminPage.Deposits -> "amount"
            AdminPage.Withdrawals -> "amountPaisa"
            else -> "fee"
        }) ?: 0)
    AdminPage.Audit -> record.getString("action") ?: adminText("Activity")
    AdminPage.Players -> record.getString("email")?.takeIf { it.isNotBlank() }
        ?: adminText("Player")
    AdminPage.Staff -> if (record.getBoolean("owner") == true) adminText("Owner") else "…${record.id.takeLast(6)}"
    else -> record.getString("title") ?: record.getString("username") ?: adminText(page.title)
}

@Composable
private fun AdminRecordDetails(session:AdminSession, page: AdminPage, record: DocumentSnapshot, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(adminRecordTitle(page, record)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(record.getString("uid")!=null)PlayerIdentity(session,record.getString("uid").orEmpty(),record.getString("username").orEmpty())
                else if(page==AdminPage.Players)LabeledValue("Email",record.getString("email").orEmpty())
                else LabeledValue(adminText("Record ID"), record.id)
                val fields = when (page) {
                    AdminPage.Players -> listOf("email" to "Email", "ffUid" to "Game UID")
                    AdminPage.Deposits -> listOf("uid" to "Player ID", "reference" to "Transaction ID", "sender" to "Sender", "receiver" to "Receiver", "provider" to "Provider", "decisionReason" to "Reason")
                    AdminPage.Results -> listOf("match" to "Match ID", "username" to "Username")
                    AdminPage.Audit -> listOf("actor" to "Staff", "path" to "Record")
                    else -> emptyList()
                }
                if (page == AdminPage.Results) {
                    record.getLong("kills")?.let { LabeledValue(adminText("Kills"), it.toString()) }
                    if(record.getLong("rewardVersion")==2L) {
                        LabeledValue(adminText("Placement reward"),money(record.getLong("placementPaisa")?:0))
                        LabeledValue(adminText("Kill reward credited"),money(record.getLong("killPaisa")?:0))
                    }
                }
                fields.forEach { (field, label) ->
                    record.getString(field)?.takeIf { it.isNotBlank() }?.let { LabeledValue(adminText(label), it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(adminText("Close")) } }
    )
}
