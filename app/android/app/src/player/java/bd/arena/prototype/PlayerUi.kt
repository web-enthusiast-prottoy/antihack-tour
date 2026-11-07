package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
internal fun AuthScreen(session:AdminSession,bangla:Boolean,language:(Boolean)->Unit) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    BackHandler(creating) {creating=false;session.error=""}
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.home_hero),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB3101311),Color(0xFF101311),Color(0xFF101311)))))
        AnimatedContent(targetState=creating,modifier=Modifier.align(Alignment.BottomCenter),transitionSpec={ (fadeIn(tween(220))+slideInHorizontally(tween(220)){it/12}) togetherWith fadeOut(tween(180)) },label="Authentication screen") { screenCreating ->
        Column(Modifier.widthIn(max=600.dp).fillMaxWidth().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).animateContentSize(tween(220)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            BrandLogo(Modifier.width(180.dp).height(150.dp))
            Text(t(if(screenCreating)"Make your mark." else "Your next victory awaits.",if(screenCreating)"আপনার যাত্রা শুরু করুন।" else "আপনার পরের জয়।"),style=MaterialTheme.typography.headlineMedium)
            TextButton(onClick={language(!bangla)}) {Text(if(bangla)"English" else "বাংলা")}
            OutlinedTextField(email,{email=it},label={Text(t("Email","ইমেইল"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth(),enabled=!session.busy)
            OutlinedTextField(password,{password=it},label={Text(t("Password","পাসওয়ার্ড"))},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),trailingIcon={TextButton(onClick={visible=!visible}) {Text(t(if(visible)"Hide" else "Show",if(visible)"লুকান" else "দেখুন"))}},modifier=Modifier.fillMaxWidth(),enabled=!session.busy)
            if(screenCreating) OutlinedTextField(confirmation,{confirmation=it},label={Text(t("Confirm password","পাসওয়ার্ড নিশ্চিত করুন"))},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),enabled=!session.busy)
            if(session.error.isNotBlank()) Text(LocaleStrings.get(session.error),color=MaterialTheme.colorScheme.error)
            Button(onClick={
                if(screenCreating) session.register(email,password,confirmation) {
                    password="";confirmation=""
                } else session.login(email,password) {password=""}
            },enabled=!session.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(t(if(session.busy)"Please wait…" else if(screenCreating)"Create account" else "Sign in",if(session.busy)"অপেক্ষা করুন…" else if(screenCreating)"অ্যাকাউন্ট খুলুন" else "সাইন ইন"))}
            TextButton(onClick={creating=!creating;password="";confirmation="";session.error=""},enabled=!session.busy) {Text(t(if(screenCreating)"Already have an account? Sign in" else "New here? Create account",if(screenCreating)"অ্যাকাউন্ট আছে? সাইন ইন" else "নতুন? অ্যাকাউন্ট খুলুন"))}
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlayerApp(session:AdminSession,bangla:Boolean,language:(Boolean)->Unit) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    if(session.restoring) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()};return }
    var tab by rememberSaveable {mutableStateOf("Home")}
    var resumeJoin by rememberSaveable {mutableStateOf(false)}
    var page by rememberSaveable {mutableStateOf<String?>(null)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    if(!accountAccess(session,bangla))return
    key(session.uid) {
        val data=remember(session.uid) {PlayerData(session)}
        DisposableEffect(data) {data.start(session.uid);onDispose{data.close()}}
        LaunchedEffect(Unit) { if (tab == "Admin") tab = "Profile" }
        var authenticating by rememberSaveable {mutableStateOf(false)}
        var filtersOpen by rememberSaveable {mutableStateOf(false)}
        var matchAccessRetry by remember(selected,session.uid){mutableStateOf(0)}
        val matchAccess=playerBanStatus(session,selected?.let{"matches/$it/bans/${session.uid}"},matchAccessRetry)
        var pending by rememberSaveable {mutableStateOf<String?>(null)}
        var requirements by rememberSaveable {mutableStateOf(false)}
        var reviewRoster by rememberSaveable {mutableStateOf(false)}
        LaunchedEffect(matchAccess.ready,matchAccess.active){if(matchAccess.active){reviewRoster=false;requirements=false}}
        var addingMoney by rememberSaveable {mutableStateOf(false)}
        var withdrawing by rememberSaveable(session.uid){mutableStateOf(false)}
        var withdrawalBusy by remember(session.uid){mutableStateOf(false)}
        var withdrawalRefresh by remember(session.uid){mutableStateOf(0)}
        val support=supportNumber(session)
        val supportUri=androidx.compose.ui.platform.LocalUriHandler.current
        var notice by rememberSaveable {mutableStateOf("")}
        var noticeDetail by rememberSaveable(stateSaver=androidx.compose.runtime.saveable.listSaver<Notice?,Any>(
            save={it?.let{n->listOf(n.id,n.title,n.body,n.updatedAt,n.revision)}?:emptyList()},
            restore={if(it.isEmpty())null else Notice(it[0] as String,it[1] as String,it[2] as String,it[3] as Long,it[4] as Long)}
        )) {mutableStateOf<Notice?>(null)}
        val scrollStates=rememberSaveable(saver=androidx.compose.runtime.saveable.listSaver<MutableMap<String,LazyListState>,Any>(
            save={states->states.flatMap{(route,state)->listOf(route,state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)}},
            restore={saved->saved.chunked(3).associate{(route,index,offset)->route as String to LazyListState(index as Int,offset as Int)}.toMutableMap()}
        )) { mutableMapOf<String,LazyListState>() }
        var categoryRetry by remember{mutableStateOf(0)}
        val categoryFeed=matchCategories(session,retry=categoryRetry)
        val categories=categoryFeed.rows
        var category by rememberSaveable {mutableStateOf("")}
        var mode by rememberSaveable {mutableStateOf("All")}
        var search by rememberSaveable {mutableStateOf("")}
        var feeFilter by rememberSaveable {mutableStateOf("All")}
        var availabilityFilter by rememberSaveable {mutableStateOf("All")}
        var timeFilter by rememberSaveable {mutableStateOf("Any time")}
        // A search or filter must include records beyond the first browsing page.
        LaunchedEffect(tab,selected,search,category,mode,feeFilter,availabilityFilter,timeFilter,data.matchesMore,data.matchesLoading) {
            val filtering=search.isNotBlank()||category.isNotBlank()||mode!="All"||feeFilter!="All"||availabilityFilter!="All"||timeFilter!="Any time"
            if(tab=="Explore"&&selected==null&&filtering&&data.matchesMore&&!data.matchesLoading)data.loadMoreMatches()
        }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(tab) { while(true) { now=System.currentTimeMillis(); kotlinx.coroutines.delay(30000) } }
        var noticeRetry by remember {mutableStateOf(0)}
        var feed by remember {mutableStateOf(NoticeFeed())}
        val context=androidx.compose.ui.platform.LocalContext.current
        val prefs=remember(session.uid) {context.getSharedPreferences("notice-${session.uid}",0)}
        var read by remember(prefs) {mutableStateOf(prefs.getStringSet("read",emptySet())!!.toSet())}
        fun openNotice(n:Notice) {noticeDetail=n;read=(read+n.readKey).toList().takeLast(200).toSet();prefs.edit().putStringSet("read",read).apply()}
        DisposableEffect(noticeRetry) {val stop=Notices.listen(context){feed=it};onDispose{stop()}}
        LaunchedEffect(session.uid) {
            if(session.uid!=null)authenticating=false
            else if(tab=="Wallet")tab="Home"
        }
        LaunchedEffect(session.uid,data.profileLoading,data.profile,data.balanceCached,data.entryBalance,selected,data.matches,matchAccess.ready,matchAccess.active) {
            if(resumeJoin&&session.uid!=null&&!data.profileLoading&&data.matches.any{it.id==selected}) {
                if(!matchAccess.ready)return@LaunchedEffect
                val target=data.matches.first{it.id==selected}
                if(target.fee>0&&data.balanceCached)return@LaunchedEffect
                resumeJoin=false
                if(matchAccess.active){notice=t("You are banned from this match.","আপনি এই ম্যাচে নিষিদ্ধ।");return@LaunchedEffect}
                if(target.closed||target.joined>=target.capacity)notice=t("Match full or closed.","ম্যাচ পূর্ণ বা বন্ধ।")
                else if(data.profile?.get("ffUsername").isNullOrBlank()||data.entryBalance<target.fee)requirements=true else reviewRoster=true
            }
        }
        val match=data.matches.find {it.id==selected}
        BackHandler(page!=null||selected!=null||tab!="Home") {if(page!=null)page=null else if(selected!=null)selected=null else tab="Home"}
        if(data.loading) {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}} else {
        Scaffold(bottomBar={
            if(match==null) NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
                val tabs=listOf("Home","Explore","Matches","Wallet","Profile")
                val names=if(bangla)listOf("হোম","খুঁজুন","আমার ম্যাচ","ওয়ালেট","প্রোফাইল") else listOf("Home","Explore","My matches","Wallet","Profile")
                val largeText=LocalDensity.current.fontScale>1.3f
                val icons=listOf(R.drawable.ic_home,R.drawable.ic_explore,R.drawable.ic_matches,R.drawable.ic_wallet,R.drawable.ic_profile)
                tabs.forEachIndexed {i,title -> if(title!="Wallet"||session.uid!=null) NavigationBarItem(tab==title||(tab=="Results"&&title=="Matches"),{page=null;if(title=="Explore"){category="";search="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time"};tab=title},icon={Icon(painterResource(icons[i]),if(largeText)names[i]else null)},label=if(largeText)null else {{Text(if(i==2)if(bangla)"ম্যাচ" else "Matches" else names[i],fontSize=12.sp,maxLines=1)}})}
            } else Surface {
                Column {
                Button(onClick={
                    if(session.uid==null){resumeJoin=true;authenticating=true}
                    else if(data.profile?.get("ffUsername").isNullOrBlank()||data.entryBalance<match.fee) requirements=true
                    else reviewRoster=true
                },enabled=matchAccess.ready&&!matchAccess.active&&!data.busy&&(session.uid==null||!data.profileLoading&&(match.fee==0L||!data.balanceCached))&&!match.closed&&match.joined<match.capacity&&data.entries[match.id]==null,modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min=52.dp)) {Text(if(matchAccess.active)t("Banned from match","ম্যাচে নিষিদ্ধ") else if(!matchAccess.ready)t("Checking access…","অ্যাক্সেস যাচাই হচ্ছে…") else if(data.busy)t("Please wait…","অপেক্ষা করুন…") else if(match.matchStatus=="cancelled")LocaleStrings.get("Cancelled") else data.entries[match.id]?.let{LocaleStrings.get(it)}?:if(match.closed)t("Registration closed","নিবন্ধন বন্ধ") else if(match.joined>=match.capacity)t("Match full","ম্যাচ পূর্ণ") else t("Join match","ম্যাচে যোগ দিন"))}
                }
            }
        }) {padding ->
            if(page!=null&&match==null) Box(Modifier.fillMaxSize().padding(padding)){if(page=="support")SupportScreen(session,bangla,back={page=null})else PlayerPageScreen(session,page!!,bangla){page=null}}
            else if(tab=="Results"&&match==null) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter){ResultsScreen(session){tab="Matches"}}
            else Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) { AnimatedContent(targetState=tab to selected,transitionSpec={
                if(initialState.second!=targetState.second) (fadeIn(tween(220))+slideInHorizontally(tween(220)){if(targetState.second!=null)it/8 else -it/8}) togetherWith (fadeOut(tween(180))+slideOutHorizontally(tween(220)){if(targetState.second!=null)-it/8 else it/8})
                else fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },label="Screen") { route ->
            val screen=route.first
            val match=data.matches.find { it.id==route.second }
            val routeState=scrollStates.getOrPut(route.second?.let { "details:$it" }?:screen) { LazyListState() }
            LazyColumn(Modifier.testTag("player-content").widthIn(max=640.dp).fillMaxWidth().fillMaxHeight().imePadding(),state=routeState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {ScreenHeader(if(match!=null)match.title else if(screen=="Home")"Antihack Tour" else LocaleStrings.get(if(screen=="Matches")"My matches" else screen),back=if(match!=null||screen=="Notifications")({if(match!=null)selected=null else tab="Home"})else null,actions={
                    if(screen=="Home" && match==null) {
                        val unread=unreadNoticeCount(feed.notices,read)
                        NotificationAction(unread){tab="Notifications"}
                    }
                })}
                if(data.error.isNotBlank()) item {Text(LocaleStrings.get(data.error),color=MaterialTheme.colorScheme.error);TextButton(onClick={data.start(session.uid)}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                if(pending!=null&&match==null) item {Button(onClick={selected=pending;pending=null}) {Text(t("Continue to match","ম্যাচে ফিরে যান"))}}
                if(match!=null) {
                    if(match.id==selected&&(!matchAccess.ready||matchAccess.active))item{MatchBanNotice(matchAccess,bangla){matchAccessRetry++}}
                    if(match.id==selected&&matchAccess.ready&&!matchAccess.active&&data.entries[match.id]=="confirmed")item{MatchRoomCard(session,match.id,true,data.entries[match.id])}
                    item {MatchOverview(session,match)}
                    item {PrizePanel(session,match.id)}
                    item {Text(t("Match rules","ম্যাচের নিয়ম"),style=MaterialTheme.typography.titleLarge);Text(match.rules)}
                    data.rosters[match.id]?.let { roster -> item {
                        Text(t("Your registered roster","আপনার নিবন্ধিত দল"),style=MaterialTheme.typography.titleLarge)
                        roster.forEachIndexed { index,name -> Text("${index+1}. $name") }
                    } }
                    if(match.id==selected&&matchAccess.ready&&!matchAccess.active&&data.entries[match.id]!="confirmed")item{MatchRoomCard(session,match.id,false,data.entries[match.id])}
                    item {ParticipantList(session,match.id,bangla)}
                } else when(screen) {
                    "Home","Explore","Matches" -> {
                        if(screen!="Home"&&data.matchesCached&&!data.matchesLoading) item { Text(t("Cached matches · reconnect to confirm availability","ক্যাশ করা ম্যাচ · আসন নিশ্চিত করতে সংযোগ দিন"),style=MaterialTheme.typography.bodySmall) }
                        if(screen=="Matches") item {
                            if(session.uid==null) Card(Modifier.fillMaxWidth()){
                                Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_profile),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(t("Sign in to see your matches","সাইন ইন করুন"),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick={authenticating=true}){Text(t("Sign in","সাইন ইন"))}
                                }
                            }
                            OutlinedButton(onClick={tab="Results"},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Results"))}
                        }
                        if(screen=="Home") {
                            item {AnnouncementPanel(feed,unreadNoticeCount(feed.notices,read),openAll={tab="Notifications"},openNotice={openNotice(it)},retry={noticeRetry++})}
                            if(categoryFeed.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                            if(!categoryFeed.loading&&!categoryFeed.error&&categories.isEmpty())item{Text(t("No categories yet.","এখনো কোনো বিভাগ নেই।"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            if(categoryFeed.cached&&!categoryFeed.loading)item{Text(t("Saved categories · reconnect to refresh","সংরক্ষিত বিভাগ · হালনাগাদ করতে সংযোগ দিন"),style=MaterialTheme.typography.bodySmall)}
                            categoryTiles(session,categories,category){
                                category=it;search="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time";tab="Explore"
                            }
                        }
                        if(screen=="Explore") item {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                CompactSearch(search,{search=it},LocaleStrings.get("Search match ID or title"),Modifier.weight(1f))
                                FilledTonalIconButton(onClick={filtersOpen=true}) {
                                    val count=listOf(mode!="All",feeFilter!="All",availabilityFilter!="All",timeFilter!="Any time",category.isNotBlank()).count{it}
                                    BadgedBox(badge={if(count>0)Badge{Text(count.toString())}}){Icon(painterResource(R.drawable.ic_filter),t("Filters","ফিল্টার"))}
                                }
                            }
                        }
                        if(categoryFeed.error)item{Text(t("Categories could not refresh.","বিভাগ হালনাগাদ করা যায়নি।"));TextButton(onClick={categoryRetry++}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                        if(screen=="Explore"&&category.isNotBlank())item{TextButton(onClick={category=""}){Text((categories.find{it.id==category}?.name.orEmpty())+t(" · Change category"," · বিভাগ বদলান"))}}
                        val list=if(screen=="Explore") browseMatches(data.matches.filter{category.isBlank()||it.categoryId==category},search,mode,feeFilter,availabilityFilter,timeFilter,now) else if(screen=="Home")emptyList() else data.matches.filter{it.id in data.entries}.sortedByDescending{it.startsAtMillis}
                        if(screen=="Explore") item {Text("${list.size} "+if(data.matchesMore||data.matchesLoading||data.matchesCached)t("matches loaded","ম্যাচ লোড হয়েছে")else t("matches","ম্যাচ"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(screen!="Home"&&data.matchesLoading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
                        if(screen!="Home"&&list.isEmpty()&&!data.matchesLoading&&!data.matchesError)item {Text(if(screen=="Explore")t("No loaded matches match these filters.","লোড হওয়া ম্যাচে এই ফিল্টারে কোনো ফল নেই।") else t("No matches available yet.","এখনো কোনো ম্যাচ নেই।"))}
                        items(list,key={it.id}) {m ->MatchSummaryCard(session,m,data.entries[m.id]){selected=m.id}}
                        if(screen!="Home"&&data.matchesError)item{Text(t("Could not load more matches.","আরও ম্যাচ লোড হয়নি।"),color=MaterialTheme.colorScheme.error);TextButton(onClick={data.retryMatches()}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                        if(screen!="Home"&&data.matchesMore)item{OutlinedButton(onClick={data.loadMoreMatches()},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Load more"))}}
                    }
                    "Notifications" -> {
                        item {TextButton(onClick={read=(read+feed.notices.map{it.readKey}).toSet();prefs.edit().putStringSet("read",read).apply()}) {Text(t("Mark all read","সব পড়া হয়েছে"))}}
                        items(feed.notices,key={it.id}) {n -> NoticeCard(n,n.readKey !in read) {openNotice(n)}}
                    }
                    "Profile" -> {
                        if(session.uid==null)item {
                            Card {Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text(t("Your tournament account","আপনার টুর্নামেন্ট অ্যাকাউন্ট"),style=MaterialTheme.typography.titleLarge)
                                Text(t("Sign in to manage your player details and entries.","খেলোয়াড়ের তথ্য ও ম্যাচ পরিচালনা করতে সাইন ইন করুন।"))
                                Button(onClick={authenticating=true}){Text(t("Sign in / Create account","সাইন ইন / অ্যাকাউন্ট খুলুন"))}
                            }}
                        } else {
                            item {ProfileSection(data,bangla)}
                            item {ProfileActionRow(t("Contact","যোগাযোগ")){page="help"}}
                        }
                        item {ProfileActionRow(t("Support · WhatsApp","সহায়তা · হোয়াটসঅ্যাপ"),enabled=!support.loading){
                            if(runCatching{supportUri.openUri(supportChatUrl(support.number))}.isFailure)notice=t("Could not open WhatsApp on this device.","এই ডিভাইসে হোয়াটসঅ্যাপ খোলা যায়নি।")
                        }}
                        item {Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("বাংলা");Switch(bangla,language,modifier=Modifier.semantics{contentDescription=t("App language: Bangla","অ্যাপের ভাষা: বাংলা")})}}
                        if(session.uid!=null)item {TextButton(onClick={session.auth.signOut()}){Text(t("Sign out","সাইন আউট"))}}
                    }
                    "Wallet" -> {
                        item {
                            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text(t("Deposit balance","জমা ব্যালেন্স"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(data.balance),style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.onSurface)
                                if(data.balanceCached)Text(t("Saved balance · reconnect to refresh","সংরক্ষিত ব্যালেন্স · হালনাগাদ করতে সংযোগ দিন"),style=MaterialTheme.typography.bodySmall)
                                LabeledValue(t("Available winnings","তোলার যোগ্য জয়ী অর্থ"),money(data.winnings))
                                Text(t("Match entry uses deposit balance first, then available winnings. Only winnings can be withdrawn.","ম্যাচের এন্ট্রিতে আগে জমা ব্যালেন্স, পরে ব্যবহারযোগ্য জয়ী অর্থ কাটা হবে। শুধু জয়ী অর্থ তোলা যাবে।"),style=MaterialTheme.typography.bodySmall)
                                if(data.reservedWinnings>0)LabeledValue(t("Pending withdrawal","অপেক্ষমাণ উত্তোলন"),money(data.reservedWinnings))
                                Button(onClick={addingMoney=true},modifier=Modifier.fillMaxWidth()){Text(t("Add money","টাকা যোগ করুন"))}
                                OutlinedButton(onClick={withdrawing=true},enabled=data.winnings>0&&!data.balanceCached,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Withdraw winnings"))}
                            }}
                        }
                        item {key(withdrawalRefresh){WalletHistory(session,data.deposits)}}
                    }
                }
            }
            }
        }
        }
        if(authenticating) androidx.compose.ui.window.Dialog(onDismissRequest={authenticating=false;resumeJoin=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Box(Modifier.fillMaxSize()) {
                AuthScreen(session,bangla,language)
                TextButton(onClick={authenticating=false;resumeJoin=false},modifier=Modifier.safeDrawingPadding().align(Alignment.TopStart)){Text(t("Back","ফিরুন"))}
            }
        }
        if(filtersOpen)ModalBottomSheet(onDismissRequest={filtersOpen=false}) {
            Column(Modifier.padding(24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(t("Filters","ফিল্টার"),style=MaterialTheme.typography.titleLarge)
                MatchFilter(t("Category","বিভাগ"),listOf(LocaleStrings.get("All"))+categories.map{it.name},categories.find{it.id==category}?.name?:LocaleStrings.get("All")){name->category=categories.find{it.name==name}?.id.orEmpty()}
                MatchFilter(LocaleStrings.get("Mode"),listOf("All","Solo","Duo","Squad"),mode){mode=it}
                MatchFilter(LocaleStrings.get("Entry fee"),listOf("All","Free","Paid"),feeFilter){feeFilter=it}
                MatchFilter(LocaleStrings.get("Availability"),listOf("All","Available","Full"),availabilityFilter){availabilityFilter=it}
                MatchFilter(LocaleStrings.get("Date"),listOf("Any time","Today","Tomorrow"),timeFilter){timeFilter=it}
                TextButton(onClick={category="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time"}){Text(LocaleStrings.get("Clear filters"))}
                Button(onClick={filtersOpen=false},modifier=Modifier.fillMaxWidth()){Text(t("Show matches","ম্যাচ দেখুন"))}
            }
        }
        if(withdrawing&&session.uid!=null)ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!withdrawalBusy){withdrawing=false;withdrawalRefresh++}}) {
            WithdrawalForm(session,{withdrawalBusy=it}){withdrawing=false;withdrawalRefresh++}
        }
        if(addingMoney)ModalBottomSheet(onDismissRequest={if(!data.busy)addingMoney=false}) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {DepositForm(data,bangla){addingMoney=false;notice=it}}
        }
        if(reviewRoster&&match!=null) ModalBottomSheet(onDismissRequest={if(!data.busy)reviewRoster=false}) {
            RosterForm(match,data.profile?.get("ffUsername").orEmpty(),data.busy,bangla,data.error) { roster ->
                data.join(match,roster) {notice=it;reviewRoster=false}
            }
        }
        if(requirements&&match!=null) ModalBottomSheet(onDismissRequest={requirements=false}) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                val missing=data.profile?.get("ffUsername").isNullOrBlank()
                if(missing)Text(t("Add your Free Fire username to join this match.","ম্যাচে যোগ দিতে আপনার Free Fire নাম যোগ করুন।"))
                if(data.entryBalance<match.fee)Text(t("Entry: ","প্রবেশ মূল্য: ")+money(match.fee)+t(". Available: ","। ব্যালেন্স: ")+money(data.entryBalance)+t(". Add: ","। যোগ করুন: ")+money(match.fee-data.entryBalance))
                Button(onClick={pending=selected;selected=null;tab=if(missing)"Profile" else "Wallet";requirements=false}){Text(if(missing)t("Add username","নাম যোগ করুন") else t("Add money","টাকা যোগ করুন"))}
            }
        }

        noticeDetail?.let { n -> AlertDialog(onDismissRequest={noticeDetail=null},title={Text(n.title)},text={Text(n.body)},confirmButton={TextButton(onClick={noticeDetail=null}){Text(t("OK","ঠিক আছে"))}}) }
        if(notice.isNotEmpty())AlertDialog(onDismissRequest={notice=""},text={Text(LocaleStrings.get(notice))},confirmButton={TextButton(onClick={notice=""}){Text(t("OK","ঠিক আছে"))}})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(data:PlayerData,bangla:Boolean,onSaved:()->Unit,cancel:(()->Unit)?=null) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    var username by rememberSaveable {mutableStateOf(data.profile?.get("ffUsername").orEmpty())}
    var uid by rememberSaveable {mutableStateOf(data.profile?.get("ffUid").orEmpty())}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(username,{username=it},label={Text(t("Free Fire username","Free Fire নাম"))},modifier=Modifier.fillMaxWidth(),enabled=!data.busy)
        OutlinedTextField(uid,{uid=it},label={Text(t("Free Fire UID (optional)","Free Fire UID (ঐচ্ছিক)"))},modifier=Modifier.fillMaxWidth(),enabled=!data.busy)
        Button(onClick={data.save(username,uid,onSaved)},enabled=!data.busy,modifier=Modifier.fillMaxWidth()){Text(t(if(data.busy)"Saving…" else "Save profile",if(data.busy)"সংরক্ষণ হচ্ছে…" else "প্রোফাইল সংরক্ষণ"))}
        if(cancel!=null)TextButton(onClick=cancel,enabled=!data.busy){Text(t("Cancel","বাতিল"))}
    }
}
@Composable
private fun DepositForm(data:PlayerData,bangla:Boolean,done:(String)->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    var amount by rememberSaveable {mutableStateOf("")}
    var reference by rememberSaveable {mutableStateOf("")}
    var sender by rememberSaveable {mutableStateOf("")}
    val requestId=rememberSaveable{java.util.UUID.randomUUID().toString()}
    var verifyingId by rememberSaveable {mutableStateOf("")}
    var verifyUntil by rememberSaveable {mutableStateOf(0L)}
    if(verifyingId.isNotEmpty()) {
        DepositVerification(data.session,verifyingId,verifyUntil,bangla,done)
        return
    }
    val receiving=paymentNumber(data.session)
    if(!receiving.enabled){
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){Text(t("Add money","টাকা যোগ করুন"),style=MaterialTheme.typography.titleLarge);if(receiving.loading)LinearProgressIndicator(Modifier.fillMaxWidth())else Text(t("Deposits are not enabled. Contact the admin.","টাকা জমা চালু নেই। অ্যাডমিনের সাথে যোগাযোগ করুন।"))}
        return
    }
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(receiving.number){mutableStateOf(false)}
    val validReference=Regex("[A-Za-z0-9-]{6,80}").matches(reference.trim())
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(t("Add money","টাকা যোগ করুন"),style=MaterialTheme.typography.titleLarge)
        Text(t("Already sent money? Enter your payment details.","টাকা পাঠিয়েছেন? নিচে লেনদেনের তথ্য দিন।"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(amount,{amount=it},label={Text(t("Amount sent (৳)","পাঠানো টাকার পরিমাণ (৳)"))},singleLine=true,enabled=!data.busy,isError=amount.isNotBlank()&&depositPaisa(amount)==0L,supportingText=if(amount.isNotBlank()&&depositPaisa(amount)==0L)({Text(t("Enter a valid amount, up to ৳100,000.","সঠিক পরিমাণ দিন, সর্বোচ্চ ৳১,০০,০০০।"))})else null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(reference,{reference=it},label={Text(t("Transaction ID","ট্রানজ্যাকশন আইডি"))},singleLine=true,enabled=!data.busy,isError=reference.isNotBlank()&&!validReference,supportingText=if(reference.isNotBlank()&&!validReference)({Text(t("Check the transaction ID from your payment.","পেমেন্টের ট্রানজ্যাকশন আইডি মিলিয়ে দিন।"))})else null,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(sender,{sender=it},label={Text(t("Sender’s mobile number","প্রেরকের মোবাইল নম্বর"))},singleLine=true,enabled=!data.busy,isError=sender.isNotBlank()&&!validDepositSender(sender),supportingText=if(sender.isNotBlank()&&!validDepositSender(sender))({Text(t("Enter the 11-digit number you sent from.","যে নম্বর থেকে পাঠিয়েছেন, সেই ১১ সংখ্যার নম্বর দিন।"))})else null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone,imeAction=ImeAction.Done),modifier=Modifier.fillMaxWidth())
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(t("Before you submit","জমা দেওয়ার আগে"),style=MaterialTheme.typography.titleSmall)
                Text(t("Send money through bKash to this number first. Then fill in the amount sent, transaction ID and the mobile number you used.","আগে নিচের নম্বরে বিকাশের মাধ্যমে টাকা পাঠান। তারপর পাঠানো টাকার পরিমাণ, ট্রানজ্যাকশন আইডি ও যে মোবাইল নম্বর থেকে পাঠিয়েছেন তা দিয়ে ফর্মটি পূরণ করুন।"))
                Text(receiving.number,style=MaterialTheme.typography.headlineSmall)
                TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(receiving.number));copied=true},enabled=!receiving.loading&&!receiving.cached&&!receiving.error){Text(t(if(copied)"Number copied" else "Copy number",if(copied)"নম্বর কপি হয়েছে" else "নম্বর কপি করুন"))}
                Text(t("Your balance will be added after we verify the payment.","পেমেন্ট যাচাই করার পর আপনার ব্যালেন্সে টাকা যোগ হবে।"),style=MaterialTheme.typography.bodySmall)
                if(receiving.loading||receiving.cached||receiving.error)Text(t("Reconnect to confirm the receiving number.","টাকা গ্রহণের নম্বর নিশ্চিত করতে সংযোগ দিন।"),style=MaterialTheme.typography.bodySmall)
            }
        }
        if(BuildConfig.LOCAL_OPERATIONS)Text(t("Test version · do not send real money.","পরীক্ষামূলক সংস্করণ · আসল টাকা পাঠাবেন না।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(data.error.isNotBlank())Text(LocaleStrings.get(data.error),color=MaterialTheme.colorScheme.error)
        Button(onClick={data.operation("deposit",mapOf("amount" to depositPaisa(amount),"reference" to reference,"sender" to sender,"requestId" to requestId,"receiver" to receiving.number)){_->
            verifyUntil=System.currentTimeMillis()+30_000
            verifyingId=data.session.uid+"_"+requestId.uppercase(java.util.Locale.ROOT)
        }},enabled=!data.busy&&!receiving.loading&&!receiving.cached&&!receiving.error&&depositPaisa(amount)>0&&validDepositSender(sender)&&validReference,modifier=Modifier.fillMaxWidth()){if(data.busy){CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))};Text(t(if(data.busy)"Submitting…" else "Submit request",if(data.busy)"জমা হচ্ছে…" else "অনুরোধ জমা দিন"))}
    }
}

@Composable
private fun ProfileSection(data:PlayerData,bangla:Boolean) {
    var editing by rememberSaveable{mutableStateOf(false)}
    var saved by remember{mutableStateOf(false)}
    if(editing)ProfileEditor(data,bangla,onSaved={editing=false;saved=true},cancel={editing=false})
    else Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(data.session.auth.currentUser?.email.orEmpty(),style=MaterialTheme.typography.titleMedium)
                    Text(data.profile?.get("ffUsername").orEmpty().ifBlank{if(bangla)"গেমের নাম যোগ করা হয়নি" else "Game username not added"},color=MaterialTheme.colorScheme.onSurfaceVariant)
                    data.profile?.get("ffUid")?.takeIf{it.isNotBlank()}?.let{Text("UID: $it",style=MaterialTheme.typography.bodySmall)}
                }
            }
            OutlinedButton(onClick={editing=true;saved=false}){Text(LocaleStrings.get("Edit profile"))}
            if(saved)Text(LocaleStrings.get("Profile saved."),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileActionRow(label:String,enabled:Boolean=true,open:()->Unit) {
    Surface(onClick=open,enabled=enabled,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min=56.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
