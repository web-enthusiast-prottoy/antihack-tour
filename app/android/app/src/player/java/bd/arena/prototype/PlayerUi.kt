package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlayerApp(session:AdminSession,bangla:Boolean,language:(Boolean)->Unit,announcementRequest:Int=0) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    if(session.restoring) {HomeStartup();return}
    var tab by rememberSaveable {mutableStateOf("Home")}
    var resumeJoin by rememberSaveable {mutableStateOf(false)}
    var page by rememberSaveable {mutableStateOf<String?>(null)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(announcementRequest) {if(announcementRequest>0){tab="Notifications";page=null;selected=null}}
    if(!accountAccess(session,bangla))return
    key(session.uid) {
        val data=remember(session.uid) {PlayerData(session)}
        DisposableEffect(data) {data.start(session.uid);onDispose{data.close()}}
        LaunchedEffect(Unit) { if (tab == "Admin") tab = "Profile" }
        var authenticating by rememberSaveable {mutableStateOf(false)}
        var filtersOpen by rememberSaveable {mutableStateOf(false)}
        var matchAccessRetry by remember(selected,session.uid){mutableStateOf(0)}
        var detailRefresh by remember(selected){mutableIntStateOf(0)}
        val matchAccess=playerBanStatus(session,selected?.let{"matches/$it/bans/${session.uid}"},matchAccessRetry)
        var pending by rememberSaveable {mutableStateOf<String?>(null)}
        var requirements by rememberSaveable {mutableStateOf(false)}
        var reviewRoster by rememberSaveable {mutableStateOf(false)}
        LaunchedEffect(matchAccess.ready,matchAccess.active){if(matchAccess.active){reviewRoster=false;requirements=false}}
        var balanceInfo by rememberSaveable{mutableStateOf(false)}
        var languageSheet by rememberSaveable{mutableStateOf(false)}
        var signOutSheet by rememberSaveable{mutableStateOf(false)}
        var paymentOutcome by rememberSaveable{mutableStateOf<String?>(null)}
        var addingMoney by rememberSaveable {mutableStateOf(false)}
        var roomShortcut by rememberSaveable {mutableStateOf<String?>(null)}
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
        LaunchedEffect(tab,selected,data.entries,data.matches,data.matchesMore,data.matchesLoading,data.matchesError) {
            if(tab=="Matches"&&selected==null&&needsUpcomingPage(data.entries.keys,data.matches,data.matchesMore,data.matchesLoading,data.matchesError))data.loadMoreMatches()
        }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(tab) { while(true) { now=System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
        LaunchedEffect(tab,selected,now,data.matchesMore,data.matchesLoading) {
            if(tab=="Explore"&&selected==null&&data.matchesMore&&!data.matchesLoading&&data.matches.none{!matchInResultHistory(it.startsAtMillis,now)})data.loadMoreMatches()
        }
        var noticeRetry by remember {mutableStateOf(0)}
        var feed by remember {mutableStateOf(NoticeFeed())}
        val context=androidx.compose.ui.platform.LocalContext.current
        val prefs=remember(session.uid) {context.getSharedPreferences("notice-${session.uid}",0)}
        var read by remember(prefs) {mutableStateOf(prefs.getStringSet("read",emptySet())!!.toSet())}
        fun openNotice(n:Notice) {noticeDetail=n;read=(read+n.readKey).toList().takeLast(200).toSet();prefs.edit().putStringSet("read",read).apply()}
        DisposableEffect(noticeRetry) {val stop=Notices.listen(context){feed=it;AnnouncementPolling.observe(context,it)};onDispose{stop()}}
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
        if(data.loading) {
            if(tab=="Home"&&selected==null)HomeStartup()
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        } else {
        Scaffold(bottomBar={
            if(match==null) NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
                val tabs=listOf("Home","Explore","Matches","Wallet","Profile")
                val names=if(bangla)listOf("হোম","খুঁজুন","আমার ম্যাচ","ওয়ালেট","প্রোফাইল") else listOf("Home","Explore","My matches","Wallet","Profile")
                val largeText=LocalDensity.current.fontScale>1.3f
                val icons=listOf(R.drawable.ic_home,R.drawable.ic_explore,R.drawable.ic_matches,R.drawable.ic_wallet,R.drawable.ic_profile)
                tabs.forEachIndexed {i,title -> if(title!="Wallet"||session.uid!=null) NavigationBarItem(tab==title||(tab=="Results"&&title=="Matches"),{page=null;if(title=="Explore"){category="";search="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time"};tab=title},icon={Icon(painterResource(icons[i]),if(largeText)names[i]else null)},label=if(largeText)null else {{Text(if(i==2)if(bangla)"ম্যাচ" else "Matches" else names[i],fontSize=12.sp,maxLines=1)}})}
            } else Surface {
                Column {
                MatchJoinBar(match.fee,
                    label=if(matchAccess.active)t("Banned from match","ম্যাচে নিষিদ্ধ") else if(!matchAccess.ready)t("Checking access…","অ্যাক্সেস যাচাই হচ্ছে…") else if(data.busy)t("Please wait…","অপেক্ষা করুন…") else if(match.matchStatus=="cancelled")LocaleStrings.get("Cancelled") else data.entries[match.id]?.let{LocaleStrings.get(it)}?:if(match.closed)t("Registration closed","নিবন্ধন বন্ধ") else if(match.joined>=match.capacity)t("Match full","ম্যাচ পূর্ণ") else t("Join match","ম্যাচে যোগ দিন"),
                    enabled=matchAccess.ready&&!matchAccess.active&&!data.busy&&(session.uid==null||!data.profileLoading&&(match.fee==0L||!data.balanceCached))&&!match.closed&&match.joined<match.capacity&&data.entries[match.id]==null,
                    busy=data.busy,join={
                    if(session.uid==null){resumeJoin=true;authenticating=true}
                    else if(data.profile?.get("ffUsername").isNullOrBlank()||data.entryBalance<match.fee) requirements=true
                    else reviewRoster=true
                })
                }
            }
        }) {padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { FixedRefreshHost(reserveSpace=tab!="Wallet"||match!=null) {
                if(match!=null&&page==null)RegisterRefresh(!data.matchesLoading){data.retryMatches();matchAccessRetry++;detailRefresh++}
                else if(tab=="Wallet"&&page==null)RegisterRefresh(!data.walletRefreshing){data.refreshWallet();withdrawalRefresh++}
            if(page!=null&&match==null) Box(Modifier.fillMaxSize()){if(page=="support")SupportScreen(session,bangla,back={page=null})else PlayerPageScreen(session,page!!,bangla){page=null}}
            else if(tab=="Results"&&match==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){ResultsScreen(session,inMatches=true){tab="Matches"}}
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) { AnimatedContent(targetState=tab to selected,transitionSpec={
                if(initialState.second!=targetState.second) (fadeIn(tween(220))+slideInHorizontally(tween(220)){if(targetState.second!=null)it/8 else -it/8}) togetherWith (fadeOut(tween(180))+slideOutHorizontally(tween(220)){if(targetState.second!=null)-it/8 else it/8})
                else fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },label="Screen") { route ->
            val screen=route.first
            val match=data.matches.find { it.id==route.second }
            var detailTab by rememberSaveable(route.second){mutableStateOf("Overview")}
            val routeState=scrollStates.getOrPut(route.second?.let { "details:$it" }?:screen) { LazyListState() }
            val bottomPadding=if(screen=="Wallet"&&match==null)FixedRefreshClearance else 16.dp
            LazyColumn(Modifier.testTag("player-content").widthIn(max=640.dp).fillMaxWidth().fillMaxHeight().imePadding(),state=routeState,contentPadding=PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=bottomPadding),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {ScreenHeader(if(match!=null)LocaleStrings.get("Match details") else if(screen=="Home")"Antihack Tour" else LocaleStrings.get(if(screen=="Matches")"My matches" else screen),back=if(match!=null||screen=="Notifications")({if(match!=null)selected=null else tab="Home"})else null,actions={
                    if(screen=="Notifications"&&match==null)IconButton(onClick={read=(read+feed.notices.map{it.readKey}).toSet();prefs.edit().putStringSet("read",read).apply()}){Icon(painterResource(R.drawable.ic_check),LocaleStrings.get("Mark all read"))}
                    if(screen=="Wallet"&&match==null)IconButton(onClick={balanceInfo=true}){Icon(painterResource(R.drawable.ic_info),LocaleStrings.get("Your balances"))}
                    if(screen=="Home" && match==null) {
                        val unread=unreadNoticeCount(feed.notices,read)
                        NotificationAction(unread){tab="Notifications"}
                    }
                })}
                if(data.error.isNotBlank()) item {Text(LocaleStrings.get(data.error),color=MaterialTheme.colorScheme.error);TextButton(onClick={data.start(session.uid)}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                if(pending!=null&&match==null) item {Button(onClick={selected=pending;pending=null}) {Text(t("Continue to match","ম্যাচে ফিরে যান"))}}
                if(match!=null) {
                    if(match.id==selected&&(!matchAccess.ready||matchAccess.active))item{MatchBanNotice(matchAccess,bangla){matchAccessRetry++}}
                    item {MatchDetailsHero(session,match,now)}
                    item {MatchDetailsTabs(detailTab){detailTab=it}}
                    when(detailTab) {
                        "Overview" -> item {MatchOverviewPanel(match) {
                            if(match.id==selected&&matchAccess.ready&&!matchAccess.active)key(detailRefresh){MatchRoomCard(session,match.id,data.entries[match.id]=="confirmed",data.entries[match.id])}
                        }}
                        "Prizes" -> item {key(detailRefresh){PrizePanel(session,match.id)}}
                        "Players" -> {
                            data.rosters[match.id]?.let {roster->item {
                                Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(t("Your registered roster","আপনার নিবন্ধিত দল"),style=MaterialTheme.typography.titleMedium)
                                    roster.forEachIndexed {index,name->Text("${index+1}. $name")}
                                }}
                            }}
                            item {key(detailRefresh){ParticipantList(session,match.id)}}
                        }
                    }
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
                            MatchSectionTabs(false,upcoming={},history={tab="Results"})
                        }
                        if(screen=="Home") {
                            if(feed.loading||feed.notices.isNotEmpty()||feed.error)item {HomeAnnouncement(feed,open={openNotice(it)},retry={noticeRetry++})}
                            item {HomeCategoryHeading()}
                            if(categoryFeed.loading&&categories.isEmpty())item{HomeCategoryLoading()}
                            if(!categoryFeed.loading&&!categoryFeed.error&&categories.isEmpty())item{Text(t("No categories yet.","এখনো কোনো বিভাগ নেই।"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            if(categoryFeed.cached&&!categoryFeed.loading)item{Text(t("Saved categories · reconnect to refresh","সংরক্ষিত বিভাগ · হালনাগাদ করতে সংযোগ দিন"),style=MaterialTheme.typography.bodySmall)}
                            categoryTiles(session,categories,category){
                                category=it;search="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time";tab="Explore"
                            }
                        }
                        if(screen=="Explore") item {
                            ExploreControls(search,{search=it},mode,{mode=it},
                                listOf(mode!="All",feeFilter!="All",availabilityFilter!="All",timeFilter!="Any time",category.isNotBlank()).count{it},
                                openFilters={filtersOpen=true})
                        }
                        if(categoryFeed.error)item{Text(t("Categories could not refresh.","বিভাগ হালনাগাদ করা যায়নি।"));TextButton(onClick={categoryRetry++}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                        if(screen=="Home")item {HomeExploreShortcut {
                            category="";search="";mode="All";feeFilter="All";availabilityFilter="All";timeFilter="Any time";tab="Explore"
                        }}
                        if(screen=="Explore"&&category.isNotBlank())item{TextButton(onClick={category=""}){Text((categories.find{it.id==category}?.name.orEmpty())+t(" · Change category"," · বিভাগ বদলান"))}}
                        val list=if(screen=="Explore") browseMatches(data.matches.filter{category.isBlank()||it.categoryId==category},search,mode,feeFilter,availabilityFilter,timeFilter,now) else if(screen=="Home")emptyList() else data.matches.filter{it.id in data.entries&&!(data.entries[it.id]=="confirmed"&&matchInResultHistory(it.startsAtMillis,now))}.sortedBy{it.startsAtMillis}
                        if(screen=="Explore") item {Text("${list.size} "+if(data.matchesMore||data.matchesLoading||data.matchesCached)t("matches loaded","ম্যাচ লোড হয়েছে")else t("matches","ম্যাচ"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(screen=="Explore"&&data.matchesLoading&&list.isEmpty())item {ExploreLoadingCard()}
                        else if(screen!="Home"&&data.matchesLoading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
                        if(screen!="Home"&&list.isEmpty()&&!data.matchesLoading&&!data.matchesError&&!(screen=="Matches"&&needsUpcomingPage(data.entries.keys,data.matches,data.matchesMore,data.matchesLoading,data.matchesError)))item {Text(if(screen=="Explore")t("No loaded matches match these filters.","লোড হওয়া ম্যাচে এই ফিল্টারে কোনো ফল নেই।") else t("No matches available yet.","এখনো কোনো ম্যাচ নেই।"))}
                        items(list,key={it.id}) {m ->
                            if(screen=="Explore")ExploreMatchCard(session,m,data.entries[m.id],now){selected=m.id}
                            else MyMatchCard(session,m,data.entries[m.id],now,open={selected=m.id},room={roomShortcut=m.id})
                        }
                        if(screen!="Home"&&data.matchesError)item{Text(t("Could not load more matches.","আরও ম্যাচ লোড হয়নি।"),color=MaterialTheme.colorScheme.error);TextButton(onClick={data.retryMatches()}){Text(t("Retry","আবার চেষ্টা করুন"))}}
                        if(screen=="Explore"&&data.matchesMore)item {MatchFeedPaging(screen,data.matchesMore,data.matchesLoading){data.loadMoreMatches()}}
                    }
                    "Notifications" -> {
                        if(BuildConfig.NOTICE_BACKEND=="live")item {TextButton(onClick={
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
                        }){Text(t("Notification settings","বিজ্ঞপ্তির সেটিংস"))}}
                        item {NotificationFeedStatus(feed){noticeRetry++}}
                        feed.notices.groupBy{noticeDateGroup(it,now,bangla)}.forEach{(group,rows)->
                            item {Text(group,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            items(rows,key={it.id}){n->NotificationItem(n,n.readKey !in read){openNotice(n)}}
                        }
                    }
                    "Profile" -> {
                        if(session.uid==null)item {
                            Card {Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text(t("Your tournament account","আপনার টুর্নামেন্ট অ্যাকাউন্ট"),style=MaterialTheme.typography.titleLarge)
                                Text(t("Sign in to manage your player details and entries.","খেলোয়াড়ের তথ্য ও ম্যাচ পরিচালনা করতে সাইন ইন করুন।"))
                                Button(onClick={authenticating=true}){Text(t("Sign in / Create account","সাইন ইন / অ্যাকাউন্ট খুলুন"))}
                            }}
                        } else {
                            item {ProfileSection(data)}
                        }
                        item {ProfileLinks(bangla,session.uid!=null,!support.loading,contact={page="help"},support={
                            if(runCatching{supportUri.openUri(supportChatUrl(support.number))}.isFailure)notice=t("Could not open WhatsApp on this device.","এই ডিভাইসে হোয়াটসঅ্যাপ খোলা যায়নি।")
                        },language={languageSheet=true},signOut={signOutSheet=true})}
                    }
                    "Wallet" -> {
                        item {WalletBalances(data.balance,data.winnings,data.reservedWinnings,data.balanceCached,add={addingMoney=true},withdraw={withdrawing=true})}
                        item {WalletHistory(session,data.deposits,withdrawalRefresh)}
                    }
                }
            }
            }
        }
        } }
        }
        if(authenticating) androidx.compose.ui.window.Dialog(onDismissRequest={authenticating=false;resumeJoin=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            AuthScreen(session,bangla,language){authenticating=false;resumeJoin=false}
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
        if(paymentOutcome!=null)ModalBottomSheet(onDismissRequest={paymentOutcome=null}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {PaymentOutcome(paymentOutcome!!){paymentOutcome=null}}
        }
        if(balanceInfo)ModalBottomSheet(onDismissRequest={balanceInfo=false}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {WalletBalanceInfo(data.balance,data.winnings,data.reservedWinnings)}
        }
        if(languageSheet)ModalBottomSheet(onDismissRequest={languageSheet=false}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {LanguageChoices(bangla){language(it);languageSheet=false}}
        }
        if(signOutSheet)ModalBottomSheet(onDismissRequest={signOutSheet=false}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {SignOutConfirmation(confirm={signOutSheet=false;session.auth.signOut()},cancel={signOutSheet=false})}
        }
        if(roomShortcut!=null&&session.uid!=null)ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={roomShortcut=null}) {
            val id=roomShortcut!!
            Column(Modifier.verticalScroll(rememberScrollState())) {MyMatchRoom(session,id,data.entries[id],bangla,data.matches.find{it.id==id}?.title.orEmpty())}
        }
        if(withdrawing&&session.uid!=null)ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!withdrawalBusy){withdrawing=false;withdrawalRefresh++}}) {
            WithdrawalForm(session,{withdrawalBusy=it}){withdrawing=false;withdrawalRefresh++}
        }
        if(addingMoney)ModalBottomSheet(onDismissRequest={if(!data.busy)addingMoney=false}) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {DepositForm(data,bangla){addingMoney=false;paymentOutcome=it}}
        }
        if(reviewRoster&&match!=null) ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!data.busy)reviewRoster=false}) {
            RosterForm(match,data.profile?.get("ffUsername").orEmpty(),data.busy,bangla,data.error,
                balance=data.entryBalance.takeIf{!data.balanceCached},
                canJoin=matchAccess.ready&&!matchAccess.active&&!match.closed&&match.joined<match.capacity&&data.entries[match.id]==null&&(match.fee==0L||!data.balanceCached&&data.entryBalance>=match.fee),
                artwork={modifier->ArtworkImage(session,match.artworkId,modeArtwork(match.image),modifier)}) { roster ->
                data.join(match,roster) {notice=it;reviewRoster=false}
            }
        }
        if(requirements&&match!=null) ModalBottomSheet(onDismissRequest={requirements=false}) {
            val missing=data.profile?.get("ffUsername").isNullOrBlank()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                JoinRequirements(match,missing,data.entryBalance){pending=selected;selected=null;tab=if(missing)"Profile" else "Wallet";requirements=false}
            }
        }

        noticeDetail?.let { n -> ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={noticeDetail=null}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())){AnnouncementReader(n)}
        }}
        if(notice.isNotEmpty())AlertDialog(onDismissRequest={notice=""},text={Text(LocaleStrings.get(notice))},confirmButton={TextButton(onClick={notice=""}){Text(t("OK","ঠিক আছে"))}})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(data:PlayerData,onSaved:()->Unit,cancel:(()->Unit)?=null) {
    PlayerProfileEditor(data.session.uid.orEmpty(),data.profile?.get("ffUsername").orEmpty(),data.profile?.get("ffUid").orEmpty(),data.busy,data.error,
        save={username,uid->data.save(username,uid,onSaved)},cancel=cancel)
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
    DepositContent(receiving,amount,reference,sender,data.busy,data.error,{amount=it},{reference=it},{sender=it}) {
        data.operation("deposit",mapOf("amount" to depositPaisa(amount),"reference" to reference,"sender" to sender,"requestId" to requestId,"receiver" to receiving.number)){_->
            verifyUntil=System.currentTimeMillis()+30_000
            verifyingId=data.session.uid+"_"+requestId.uppercase(java.util.Locale.ROOT)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSection(data:PlayerData) {
    var editing by rememberSaveable{mutableStateOf(false)}
    var saved by remember{mutableStateOf(false)}
    ProfileSummary(data.session.auth.currentUser?.email.orEmpty(),data.profile?.get("ffUsername").orEmpty(),data.profile?.get("ffUid").orEmpty(),saved,editEnabled=!data.profileLoading){editing=true;saved=false}
    if(editing)ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!data.busy)editing=false}) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Player details"),style=MaterialTheme.typography.titleLarge)
            ProfileEditor(data,onSaved={editing=false;saved=true},cancel={editing=false})
        }
    }
}
