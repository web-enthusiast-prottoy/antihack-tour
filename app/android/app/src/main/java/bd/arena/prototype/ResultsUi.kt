package bd.arena.prototype

import androidx.activity.compose.BackHandler
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
internal fun ResultsScreen(session:AdminSession,back:()->Unit) {
    fun t(text:String)=LocaleStrings.get(text)
    val results=remember(session.uid){PublishedResults(session)}
    var own by rememberSaveable{mutableStateOf(session.uid!=null)}
    var input by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    fun submit(){keyboard?.hide();if(search==input.trim())results.load(own,search,true)else search=input.trim()}
    BackHandler{back()}
    DisposableEffect(results){onDispose{results.close()}}
    LaunchedEffect(session.uid){if(session.uid==null)own=false}
    LaunchedEffect(input){kotlinx.coroutines.delay(350);search=input.trim()}
    LaunchedEffect(results,own,search){results.load(own,search,true)}
    val state=results.state
    LazyColumn(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {ScreenHeader(t("Results"),back=back,refresh={results.load(own,search,true)},refreshEnabled=!state.loading)}
        if(session.uid!=null)item {FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(own,{own=true},label={Text(t("My results"))});FilterChip(!own,{own=false},label={Text(t("All published"))})}}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                CompactSearch(input,{input=it},t("Search match ID or title"),submit={submit()})

            }
        }
        if(state.cached)item {Text(t("Offline · showing saved published results. This list may be incomplete."))}
        if(state.error.isNotEmpty())item {Text(t(state.error),color=MaterialTheme.colorScheme.error);TextButton(onClick={results.load(own,search,true)}){Text(t("Retry"))}}
        if(!state.loading&&state.rows.isEmpty())item {Text(t("No published results found."))}
        items(state.rows,key={it.id}) {row ->
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(row.getString("title").orEmpty(),style=MaterialTheme.typography.titleLarge)
                Text(t("Match ID")+": "+row.getString("match").orEmpty(),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                Text(listOf(t(row.getString("mode").orEmpty()),row.getString("map").orEmpty(),row.getTimestamp("startsAt")?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))?:"").filter{it.isNotEmpty()}.joinToString(" · "))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(row.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp)) {Text(t("Placement")+": #${row.getLong("placement")} ");Text(t("Kills")+": ${row.getLong("kills")}")}
                Text(t("Announced award")+": "+money(row.getLong("awardPaisa")?:0),color=MaterialTheme.colorScheme.primary)
                Text(if(row.getString("settlementStatus")=="credited")t("Credited to winnings")+": "+money(row.getLong("creditedPaisa")?:0) else t(if(row.getString("settlementStatus")=="pending")"Not credited · awaiting settlement" else "No prize credit applies"),style=MaterialTheme.typography.bodySmall)
            }}
        }
        if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(state.more&&!state.loading)item {OutlinedButton(onClick={results.load(own,search,false)},modifier=Modifier.fillMaxWidth()){Text(t("Load more"))}}
    }
}
