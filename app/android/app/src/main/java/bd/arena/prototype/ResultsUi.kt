package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultsScreen(session:AdminSession,inMatches:Boolean=false,back:()->Unit) {
    fun t(text:String)=LocaleStrings.get(text)
    val results=remember(session.uid){PublishedResults(session)}
    val history=remember(session.uid){MyMatchResults(session)}
    var now by remember{mutableStateOf(System.currentTimeMillis())}
    var own by rememberSaveable{mutableStateOf(session.uid!=null)}
    var input by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    fun refresh(){if(own)history.load()else results.load(false,search,true)}
    fun submit(){keyboard?.hide();if(search==input.trim())refresh()else search=input.trim()}
    BackHandler{back()}
    DisposableEffect(results,history){onDispose{results.close();history.close()}}
    LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)}}
    LaunchedEffect(session.uid){if(session.uid==null)own=false}
    LaunchedEffect(input){kotlinx.coroutines.delay(350);search=input.trim()}
    LaunchedEffect(history,own){if(own)history.load()else history.close()}
    LaunchedEffect(results,own,search){if(!own)results.load(false,search,true)}
    val state=results.state
    val ownMatches=participantHistoryMatches(history.matches,history.entries,history.published.keys,search,now)
    val loading=if(own)history.loading else state.loading
    val cached=if(own)history.cached else state.cached
    val error=if(own&&history.error)"Could not load results. Reconnect and retry." else if(own)"" else state.error
    LazyColumn(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {ScreenHeader(t(if(inMatches)"My matches" else "Results"),back=if(inMatches)null else back,refresh={refresh()},refreshEnabled=!loading)}
        if(inMatches)item {MatchSectionTabs(true,upcoming=back,history={})}
        if(session.uid!=null)item {FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(own,{own=true},label={Text(t("My results"))});FilterChip(!own,{own=false},label={Text(t("All published"))})}}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                CompactSearch(input,{input=it},t("Search match ID or title"),submit={submit()})

            }
        }
        if(cached)item {Text(t(if(own)"Saved match history · reconnect to refresh." else "Offline · showing saved published results. This list may be incomplete."))}
        if(error.isNotEmpty())item {Text(t(error),color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh()}){Text(t("Retry"))}}
        if(!loading&&error.isEmpty()&&(if(own)ownMatches.isEmpty()else state.rows.isEmpty()))item {Text(t(if(own)"No past participated matches found." else "No published results found."))}
        if(own)items(ownMatches,key={it.id}) {match->
            val row=history.published[match.id]
            if(row!=null)PublishedResultCard(row)else PendingResultCard(match,resultMatchTime(match.startsAtMillis))
        } else items(state.rows,key={it.id}) {row->PublishedResultCard(row)}
        if(loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(!own&&state.more&&!state.loading)item {OutlinedButton(onClick={results.load(own,search,false)},modifier=Modifier.fillMaxWidth()){Text(t("Load more"))}}
    }
}

private fun resultMatchTime(startsAt:Long):String = if(startsAt<=0)"" else java.time.Instant.ofEpochMilli(startsAt).atZone(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishedResultCard(row:com.google.firebase.firestore.DocumentSnapshot) {
    val model=ResultSummary(row.id,row.getString("match").orEmpty(),row.getString("title").orEmpty(),row.getString("mode").orEmpty(),row.getString("map").orEmpty(),
        resultMatchTime(row.getTimestamp("startsAt")?.toDate()?.time?:0),row.getString("username").orEmpty(),row.getLong("placement")?:0,row.getLong("kills")?:0,
        row.getLong("awardPaisa")?:0,row.getLong("creditedPaisa")?:0,row.getString("settlementStatus").orEmpty(),row.getLong("rewardVersion")?:0,
        row.getLong("placementPaisa")?:0,row.getLong("killPaisa")?:0,row.getBoolean("killFinalized")==true)
    var open by rememberSaveable(row.id){mutableStateOf(false)}
    ResultCard(model){open=true}
    if(open)ModalBottomSheet(onDismissRequest={open=false}) {
        androidx.compose.foundation.layout.Box(Modifier.navigationBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState())){ResultBreakdown(model)}
    }
}
