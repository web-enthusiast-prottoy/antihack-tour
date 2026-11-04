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
internal fun PaidEntryReview(session:AdminSession,entry:DocumentSnapshot,close:()->Unit) {
    val operations=remember(entry.id){PlayerData(session)}
    var reason by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest={if(!operations.busy)close()},title={Text(LocaleStrings.get("Review paid entry"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(entry.getString("username").orEmpty(),style=MaterialTheme.typography.titleLarge)
        Text(LocaleStrings.get("Account reference")+": "+entry.getString("uid"))
        Text(LocaleStrings.get("Match ID")+": "+entry.getString("match"))
        LabeledValue(LocaleStrings.get("Entry fee"),money(entry.getLong("fee")?:0))
        Text(LocaleStrings.get("Confirmation charges this entry fee from the player's deposit balance and reserves one roster slot."))
        OutlinedTextField(reason,{reason=it},label={Text(LocaleStrings.get("Rejection reason"))},enabled=!operations.busy,isError=reason.length>1000,modifier=Modifier.fillMaxWidth())
        if(operations.error.isNotBlank())Text(LocaleStrings.get(operations.error),color=MaterialTheme.colorScheme.error)
        TextButton(onClick={operations.operation("rejectEntry",mapOf("id" to entry.id,"reason" to reason)){close()}},enabled=!operations.busy&&reason.isNotBlank()&&reason.trim().length<=1000){Text(LocaleStrings.get("Reject entry"))}
    }},confirmButton={Button(onClick={operations.operation("confirmEntry",mapOf("id" to entry.id)){close()}},enabled=!operations.busy){Text(LocaleStrings.get(if(operations.busy)"Please wait…" else "Confirm and charge entry"))}},dismissButton={TextButton(onClick=close,enabled=!operations.busy){Text(LocaleStrings.get("Back"))}})
}

@Composable
internal fun PaidRefunds(session:AdminSession,match:String) {
    val operations=remember(match){PlayerData(session)}
    var state by remember(match){mutableStateOf(BoundedFeedState())}
    var cancelled by remember(match){mutableStateOf(false)}
    var retry by remember{mutableStateOf(0)}
    var message by remember{mutableStateOf("")}
    var processing by remember{mutableStateOf(false)}
    DisposableEffect(match,retry){var active=true;cancelled=false
        session.db.document("matches/$match").get(Source.SERVER).addOnSuccessListener{if(active)cancelled=it.getString("status")=="cancelled"}
        val feed=BoundedFeed(session.db.collection("entries").whereEqualTo("match",match).whereEqualTo("status","confirmed"),automatic=true){state=it};feed.start()
        onDispose{active=false;feed.close()}
    }
    val paid=state.rows.filter{(it.getLong("fee")?:0)>0}
    fun refund(remaining:List<DocumentSnapshot>){
        if(remaining.isEmpty()){processing=false;message="All loaded paid entries refunded.";return}
        operations.operation("refundEntry",mapOf("id" to remaining.first().id)){refund(remaining.drop(1))}
    }
    LaunchedEffect(operations.error){if(operations.error.isNotBlank())processing=false}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Cancelled paid matches return the original entry fee to each player's deposit balance. Completed refunds cannot pay twice."))
        if(!cancelled)Text(LocaleStrings.get("Cancel the match with a reason before issuing entry refunds."))
        Text(LocaleStrings.get("Entries awaiting refund")+": "+paid.size)
        if(state.loading||processing)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(operations.error.isNotBlank())Text(LocaleStrings.get(operations.error),color=MaterialTheme.colorScheme.error)
        if(state.error)Text(LocaleStrings.get("Could not load the record. Reconnect and retry."),color=MaterialTheme.colorScheme.error)
        if(message.isNotBlank())Text(LocaleStrings.get(message))
        Button(onClick={processing=true;message="";refund(paid.toList())},enabled=cancelled&&!processing&&!state.loading&&!state.cached&&!state.error&&paid.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Refund remaining entries"))}
        TextButton(onClick={retry++},enabled=!processing){Text(LocaleStrings.get("Refresh"))}
    }
}
